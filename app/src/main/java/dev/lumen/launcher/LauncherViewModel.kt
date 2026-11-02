package dev.lumen.launcher

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import android.os.UserHandle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import org.json.JSONArray
import org.json.JSONObject

data class AppEntry(val key: String, val label: String, val component: ComponentName)

const val GRID_COLS = 4

/** A widget placed on a widget page's grid, in cell units. */
data class WidgetItem(val id: Int, val col: Int, val row: Int, val colSpan: Int, val rowSpan: Int) {
    fun overlaps(o: WidgetItem) =
        col < o.col + o.colSpan && o.col < col + colSpan && row < o.row + o.rowSpan && o.row < row + rowSpan
}

class LauncherViewModel(private val app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("lumen", Context.MODE_PRIVATE)
    private val launcherApps = app.getSystemService(LauncherApps::class.java)
    private val me: UserHandle = Process.myUserHandle()

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    var pinned by mutableStateOf(loadPinned())
        private set
    /** Widget pages (the home page is separate and always first). */
    var pages by mutableStateOf(loadPages())
        private set
    /** Number of grid rows that fit on a widget page; reported by the UI once measured. */
    var gridRows by mutableIntStateOf(6)
    /** Incremented when the Home button is pressed, so the UI can reset itself. */
    var homeSignal by mutableIntStateOf(0)
        private set

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh()
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh()
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
    }

    init {
        refresh()
        launcherApps.registerCallback(callback)
    }

    override fun onCleared() {
        launcherApps.unregisterCallback(callback)
    }

    fun onHomePressed() { homeSignal++ }

    fun refresh() {
        apps = launcherApps.getActivityList(null, me)
            .filter { it.componentName.packageName != app.packageName }
            .map { AppEntry(it.componentName.flattenToString(), it.label.toString(), it.componentName) }
            .sortedBy { it.label.lowercase() }
    }

    // ---- launching -------------------------------------------------------------------------

    fun launch(entry: AppEntry) {
        launcherApps.startMainActivity(entry.component, me, null, null)
    }

    fun showAppInfo(entry: AppEntry) {
        launcherApps.startAppDetailsActivity(entry.component, me, null, null)
    }

    fun uninstall(entry: AppEntry) {
        app.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:${entry.component.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // ---- pinned apps -----------------------------------------------------------------------

    fun togglePin(entry: AppEntry) {
        pinned = if (entry.key in pinned) pinned - entry.key else pinned + entry.key
        prefs.edit().putString("pinned", JSONArray(pinned).toString()).apply()
    }

    private fun loadPinned(): List<String> {
        val arr = JSONArray(prefs.getString("pinned", "[]"))
        return List(arr.length()) { arr.getString(it) }
    }

    // ---- widget pages ----------------------------------------------------------------------

    fun addPage(): Int {
        updatePages(pages + listOf(emptyList()))
        return pages.size - 1
    }

    /** Returns widget ids that were on the page so the caller can release them from the host. */
    fun removePage(index: Int): List<Int> {
        val removed = pages.getOrNull(index)?.map { it.id }.orEmpty()
        updatePages(pages.filterIndexed { i, _ -> i != index })
        return removed
    }

    /** Places a widget in the first free spot. Returns false if the page has no room. */
    fun addWidget(page: Int, id: Int, colSpan: Int, rowSpan: Int): Boolean {
        val items = pages.getOrNull(page) ?: return false
        val cs = colSpan.coerceIn(1, GRID_COLS)
        val rs = rowSpan.coerceIn(1, gridRows)
        for (r in 0..gridRows - rs) for (c in 0..GRID_COLS - cs) {
            val candidate = WidgetItem(id, c, r, cs, rs)
            if (items.none { it.overlaps(candidate) }) {
                updatePages(pages.mapIndexed { i, w -> if (i == page) w + candidate else w })
                return true
            }
        }
        return false
    }

    /** Moves/resizes a widget if the result stays inside the grid and doesn't overlap others. */
    fun tryUpdate(page: Int, new: WidgetItem): Boolean {
        val items = pages.getOrNull(page) ?: return false
        val inside = new.col >= 0 && new.row >= 0 && new.colSpan >= 1 && new.rowSpan >= 1 &&
            new.col + new.colSpan <= GRID_COLS && new.row + new.rowSpan <= gridRows
        if (!inside || items.any { it.id != new.id && it.overlaps(new) }) return false
        if (items.firstOrNull { it.id == new.id } == new) return true
        updatePages(pages.mapIndexed { i, w -> if (i == page) w.map { if (it.id == new.id) new else it } else w })
        return true
    }

    fun removeWidget(page: Int, id: Int) =
        updatePages(pages.mapIndexed { i, w -> if (i == page) w.filter { it.id != id } else w })

    /** Widget ids stored by the old (full-width list) layout; returned once so they can be released. */
    fun takeLegacyWidgetIds(): List<Int> {
        val raw = prefs.getString("pages", null) ?: return emptyList()
        prefs.edit().remove("pages").apply()
        val ids = mutableListOf<Int>()
        runCatching {
            val outer = JSONArray(raw)
            for (p in 0 until outer.length()) {
                val inner = outer.getJSONArray(p)
                for (w in 0 until inner.length()) ids += inner.getJSONObject(w).getInt("id")
            }
        }
        return ids
    }

    private fun updatePages(new: List<List<WidgetItem>>) {
        pages = new
        val json = JSONArray()
        new.forEach { page ->
            json.put(JSONArray().also { a ->
                page.forEach {
                    a.put(
                        JSONObject().put("id", it.id).put("c", it.col).put("r", it.row)
                            .put("cs", it.colSpan).put("rs", it.rowSpan)
                    )
                }
            })
        }
        prefs.edit().putString("pages_v2", json.toString()).apply()
    }

    private fun loadPages(): List<List<WidgetItem>> {
        val raw = prefs.getString("pages_v2", null) ?: return listOf(emptyList())
        val outer = JSONArray(raw)
        return List(outer.length()) { p ->
            val inner = outer.getJSONArray(p)
            List(inner.length()) { w ->
                val o = inner.getJSONObject(w)
                WidgetItem(o.getInt("id"), o.getInt("c"), o.getInt("r"), o.getInt("cs"), o.getInt("rs"))
            }
        }
    }
}

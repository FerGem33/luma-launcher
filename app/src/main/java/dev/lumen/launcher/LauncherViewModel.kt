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

/** A widget placed on a widget page. [heightDp] is user-adjustable. */
data class WidgetItem(val id: Int, val heightDp: Int)

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

    fun addWidget(page: Int, item: WidgetItem) =
        updatePages(pages.mapIndexed { i, w -> if (i == page) w + item else w })

    fun removeWidget(page: Int, id: Int) =
        updatePages(pages.mapIndexed { i, w -> if (i == page) w.filter { it.id != id } else w })

    fun resizeWidget(page: Int, id: Int, deltaDp: Int) =
        updatePages(pages.mapIndexed { i, w ->
            if (i != page) w
            else w.map { if (it.id == id) it.copy(heightDp = (it.heightDp + deltaDp).coerceIn(60, 800)) else it }
        })

    private fun updatePages(new: List<List<WidgetItem>>) {
        pages = new
        val json = JSONArray()
        new.forEach { page ->
            json.put(JSONArray().also { a ->
                page.forEach { a.put(JSONObject().put("id", it.id).put("h", it.heightDp)) }
            })
        }
        prefs.edit().putString("pages", json.toString()).apply()
    }

    private fun loadPages(): List<List<WidgetItem>> {
        val raw = prefs.getString("pages", null) ?: return listOf(emptyList())
        val outer = JSONArray(raw)
        return List(outer.length()) { p ->
            val inner = outer.getJSONArray(p)
            List(inner.length()) { w ->
                val o = inner.getJSONObject(w)
                WidgetItem(o.getInt("id"), o.getInt("h"))
            }
        }
    }
}

package dev.luma.launcher

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
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class AppEntry(val key: String, val label: String, val component: ComponentName)

const val GRID_COLS = 4
private const val ICON_PX = 144

/** A widget placed on a widget page's grid, in cell units. */
data class WidgetItem(val id: Int, val col: Int, val row: Int, val colSpan: Int, val rowSpan: Int) {
    fun overlaps(o: WidgetItem) =
        col < o.col + o.colSpan && o.col < col + colSpan && row < o.row + o.rowSpan && o.row < row + rowSpan
}

class LauncherViewModel(private val app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("luma", Context.MODE_PRIVATE)
    private val launcherApps = app.getSystemService(LauncherApps::class.java)
    private val me: UserHandle = Process.myUserHandle()

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    var pinned by mutableStateOf(loadPinned())
        private set
    /** Widget pages, in left-to-right order, excluding the home page. */
    var pages by mutableStateOf(loadPages())
        private set
    /** How many widget pages sit to the left of the home page. */
    var homeIndex by mutableIntStateOf(prefs.getInt("home_index", 0).coerceIn(0, pages.size))
        private set
    /** Whether app names show their icon (home list and drawer). */
    var showIcons by mutableStateOf(prefs.getBoolean("show_icons", false))
        private set
    /** Number of grid rows that fit on a widget page; reported by the UI once measured. */
    var gridRows by mutableIntStateOf(6)
    var themeId by mutableStateOf(prefs.getString("theme", null) ?: LumaThemes.first().id)
        private set

    // ---- day / night themes ----------------------------------------------------------------

    /** Whether the theme follows the time of day (a day theme and a night theme). */
    var dayNight by mutableStateOf(prefs.getBoolean("dn_enabled", false))
        private set
    var dayThemeId by mutableStateOf(prefs.getString("dn_day", null) ?: themeId)
        private set
    var nightThemeId by mutableStateOf(prefs.getString("dn_night", null) ?: PaperDarkTheme.id)
        private set
    /** Minutes since midnight at which the day theme / the night theme begins. */
    var dayStart by mutableIntStateOf(prefs.getInt("dn_day_start", ThemeSchedule.DEFAULT_DAY_START))
        private set
    var nightStart by mutableIntStateOf(prefs.getInt("dn_night_start", ThemeSchedule.DEFAULT_NIGHT_START))
        private set
    /** The clock the schedule is judged by; refreshed regularly so the theme flips on time. */
    private var minuteOfDay by mutableIntStateOf(ThemeSchedule.minuteOfDay())

    /** The theme showing now: the chosen one, or the day or night one when scheduling is on. */
    val activeThemeId: String
        get() = if (!dayNight) themeId else if (ThemeSchedule.isDay(minuteOfDay, dayStart, nightStart)) dayThemeId else nightThemeId
    val theme: LumaTheme get() = themeById(activeThemeId)

    fun refreshClock() { minuteOfDay = ThemeSchedule.minuteOfDay() }

    fun updateDayNight(on: Boolean) {
        dayNight = on
        prefs.edit().putBoolean("dn_enabled", on).apply()
        refreshClock()
    }

    fun setDayTheme(id: String) {
        dayThemeId = id
        prefs.edit().putString("dn_day", id).apply()
    }

    fun setNightTheme(id: String) {
        nightThemeId = id
        prefs.edit().putString("dn_night", id).apply()
    }

    fun updateDayStart(minute: Int) {
        dayStart = minute
        prefs.edit().putInt("dn_day_start", minute).apply()
        refreshClock()
    }

    fun updateNightStart(minute: Int) {
        nightStart = minute
        prefs.edit().putInt("dn_night_start", minute).apply()
        refreshClock()
    }

    fun updateShowIcons(show: Boolean) {
        showIcons = show
        prefs.edit().putBoolean("show_icons", show).apply()
    }

    fun setTheme(id: String) {
        themeId = id
        prefs.edit().putString("theme", id).apply()
    }

    // ---- app icons ---------------------------------------------------------------------------

    private val iconCache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(300)

    fun cachedIcon(entry: AppEntry): androidx.compose.ui.graphics.ImageBitmap? = iconCache.get(entry.key)

    /** Loads (and caches) an app's icon. Call off the main thread. */
    fun loadIcon(entry: AppEntry): androidx.compose.ui.graphics.ImageBitmap? = cachedIcon(entry) ?: runCatching {
        uniformIcon(app.packageManager.getActivityIcon(entry.component), ICON_PX).asImageBitmap()
            .also { iconCache.put(entry.key, it) }
    }.getOrNull()

    // ---- custom wallpapers -----------------------------------------------------------------

    private val wallpaperDir = java.io.File(app.filesDir, "wallpapers").apply { mkdirs() }
    private fun listWallpapers(): List<java.io.File> =
        wallpaperDir.listFiles { f -> f.extension == "jpg" }?.sortedBy { it.name }.orEmpty()

    /** Images chosen for the Custom theme. */
    var wallpapers by mutableStateOf(listWallpapers())
        private set

    /** With several images, one is shown per day. */
    val currentWallpaper: java.io.File?
        get() = wallpapers.takeIf { it.isNotEmpty() }?.let { it[(java.time.LocalDate.now().toEpochDay() % it.size).toInt()] }

    fun addWallpapers(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val stamp = System.currentTimeMillis()
            uris.forEachIndexed { i, uri -> importWallpaper(app.contentResolver, uri, wallpaperDir, "w_${stamp}_$i") }
            val updated = listWallpapers()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { wallpapers = updated }
        }
    }

    fun removeWallpaper(file: java.io.File) {
        file.delete()
        wallpapers = listWallpapers()
    }

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
        viewModelScope.launch {
            while (true) { kotlinx.coroutines.delay(20_000); refreshClock() }
        }
        refresh()
        launcherApps.registerCallback(callback)
    }

    override fun onCleared() {
        launcherApps.unregisterCallback(callback)
    }

    fun onHomePressed() { homeSignal++; frictionFor = null; refreshClock() }

    fun refresh() {
        iconCache.evictAll() // an updated app may have a new icon
        apps = launcherApps.getActivityList(null, me)
            .filter { it.componentName.packageName != app.packageName }
            .map { AppEntry(it.componentName.flattenToString(), it.label.toString(), it.componentName) }
            .sortedBy { it.label.lowercase() }
    }

    // ---- launching -------------------------------------------------------------------------

    /** Opens an app, or holds it behind the pause screen if it is marked as distracting. */
    fun launch(entry: AppEntry) {
        val pkg = entry.component.packageName
        if (entry.key in distracting && !TimeLimit.hasGrace(app, pkg)) frictionFor = entry else launchNow(entry)
    }

    /** Minutes a distracting app stays open after its pause was passed. */
    var graceMinutes by mutableIntStateOf(TimeLimit.minutes(app))
        private set

    fun updateGraceMinutes(m: Int) {
        graceMinutes = m
        TimeLimit.setMinutes(app, m)
    }

    /** Passes the pause: the app may be opened and reopened for [graceMinutes]. */
    fun passFriction(entry: AppEntry) {
        TimeLimit.grant(app, entry.component.packageName)
        launchNow(entry)
    }

    /** Opens an app without any friction (after the pause, or for apps that aren't marked). */
    fun launchNow(entry: AppEntry) {
        frictionFor = null
        launcherApps.startMainActivity(entry.component, me, null, null)
    }

    fun dismissFriction() { frictionFor = null }

    // ---- distracting apps ------------------------------------------------------------------

    /** Apps that wait behind a pause screen before they open (keys of [AppEntry]). */
    var distracting by mutableStateOf(loadDistracting())
        private set
    /** The marked app waiting behind the pause screen, if any. */
    var frictionFor by mutableStateOf<AppEntry?>(null)
        private set

    fun toggleDistracting(entry: AppEntry) {
        distracting = if (entry.key in distracting) distracting - entry.key else distracting + entry.key
        prefs.edit().putString("distracting", JSONArray(distracting.toList()).toString()).apply()
    }

    private fun loadDistracting(): Set<String> {
        val arr = JSONArray(prefs.getString("distracting", "[]"))
        return List(arr.length()) { arr.getString(it) }.toSet()
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

    /** Applies a new order to the pinned apps that are currently shown; hidden ones keep their place at the end. */
    fun reorderPinned(visibleOrder: List<String>) {
        pinned = visibleOrder + pinned.filter { it !in visibleOrder }
        prefs.edit().putString("pinned", JSONArray(pinned).toString()).apply()
    }

    private fun loadPinned(): List<String> {
        val arr = JSONArray(prefs.getString("pinned", "[]"))
        return List(arr.length()) { arr.getString(it) }
    }

    // ---- widget pages ----------------------------------------------------------------------

    /** Pager index (home included) of the widget page at [widgetPage] in [pages]. */
    fun pagerIndexOf(widgetPage: Int) = if (widgetPage < homeIndex) widgetPage else widgetPage + 1

    /** Index in [pages] of the page shown at [pagerIndex], or null for the home page. */
    fun widgetPageAt(pagerIndex: Int): Int? = when {
        pagerIndex < homeIndex -> pagerIndex
        pagerIndex == homeIndex -> null
        else -> pagerIndex - 1
    }

    /** Adds an empty widget page left or right of home. Returns the pager index of the new page. */
    fun addPage(left: Boolean): Int {
        return if (left) {
            updatePages(listOf(emptyList<WidgetItem>()) + pages)
            updateHomeIndex(homeIndex + 1)
            0
        } else {
            updatePages(pages + listOf(emptyList()))
            pages.size
        }
    }

    private fun updateHomeIndex(n: Int) {
        homeIndex = n
        prefs.edit().putInt("home_index", n).apply()
    }

    /** Returns widget ids that were on the page so the caller can release them from the host. */
    fun removePage(index: Int): List<Int> {
        val removed = pages.getOrNull(index)?.map { it.id }.orEmpty()
        updatePages(pages.filterIndexed { i, _ -> i != index })
        if (index < homeIndex) updateHomeIndex(homeIndex - 1)
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

    /** Whether [candidate] fits inside the grid on [page] without overlapping another widget. */
    fun canPlace(page: Int, candidate: WidgetItem): Boolean {
        val items = pages.getOrNull(page) ?: return false
        val inside = candidate.col >= 0 && candidate.row >= 0 && candidate.colSpan >= 1 && candidate.rowSpan >= 1 &&
            candidate.col + candidate.colSpan <= GRID_COLS && candidate.row + candidate.rowSpan <= gridRows
        return inside && items.none { it.id != candidate.id && it.overlaps(candidate) }
    }

    /** Places a widget at the given cell if it fits there, otherwise in the first free spot. */
    fun addWidgetAt(page: Int, id: Int, colSpan: Int, rowSpan: Int, col: Int?, row: Int?): Boolean {
        if (col != null && row != null) {
            val candidate = WidgetItem(id, col, row, colSpan.coerceIn(1, GRID_COLS), rowSpan.coerceIn(1, gridRows))
            if (canPlace(page, candidate)) {
                updatePages(pages.mapIndexed { i, w -> if (i == page) w + candidate else w })
                return true
            }
        }
        return addWidget(page, id, colSpan, rowSpan)
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

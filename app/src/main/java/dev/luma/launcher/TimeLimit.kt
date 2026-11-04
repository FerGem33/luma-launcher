package dev.luma.launcher

import android.content.ComponentName
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The time a distracting app is allowed after its pause was passed. Kept in the same preferences as the
 * view model, and read directly, so the accessibility service can use it without the UI running.
 */
object TimeLimit {
    val MINUTE_CHOICES = listOf(1, 2, 3, 5, 10, 15, 20, 30, 45, 60)

    private fun prefs(c: Context) = c.getSharedPreferences("luma", Context.MODE_PRIVATE)

    fun minutes(c: Context) = prefs(c).getInt("grace_minutes", 5)
    fun setMinutes(c: Context, m: Int) = prefs(c).edit().putInt("grace_minutes", m).apply()

    /** Packages of the apps marked as distracting. */
    fun distractingPackages(c: Context): Set<String> {
        val arr = JSONArray(prefs(c).getString("distracting", "[]"))
        return List(arr.length()) { ComponentName.unflattenFromString(arr.getString(it))?.packageName }
            .filterNotNull().toSet()
    }

    private fun graceMap(c: Context): JSONObject = JSONObject(prefs(c).getString("grace", "{}"))

    /** Uptime-independent wall-clock time until which [pkg] may stay open, or 0. */
    fun graceUntil(c: Context, pkg: String): Long = graceMap(c).optLong(pkg, 0L)

    fun hasGrace(c: Context, pkg: String) = graceUntil(c, pkg) > System.currentTimeMillis()

    fun grant(c: Context, pkg: String) {
        val now = System.currentTimeMillis()
        val map = graceMap(c)
        map.keys().asSequence().toList().forEach { if (map.optLong(it) <= now) map.remove(it) }
        map.put(pkg, now + minutes(c) * 60_000L)
        prefs(c).edit().putString("grace", map.toString()).apply()
    }

    fun clear(c: Context, pkg: String) {
        prefs(c).edit().putString("grace", graceMap(c).apply { remove(pkg) }.toString()).apply()
    }
}

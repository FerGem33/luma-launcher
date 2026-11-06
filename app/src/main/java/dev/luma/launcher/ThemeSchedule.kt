package dev.luma.launcher

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalTime

/**
 * Day/Night themes: one theme from [dayStart] until [nightStart], another from then until the next [dayStart].
 * Times are minutes since midnight. Kept in the view model's preferences and read directly elsewhere
 * (the time-up screen), so everything agrees on which theme is showing.
 */
object ThemeSchedule {
    const val DEFAULT_DAY_START = 7 * 60
    const val DEFAULT_NIGHT_START = 20 * 60

    fun minuteOfDay(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

    /** Whether [minute] falls in the day span; works when the night starts after midnight too. */
    fun isDay(minute: Int, dayStart: Int, nightStart: Int): Boolean =
        if (dayStart <= nightStart) minute in dayStart until nightStart else minute >= dayStart || minute < nightStart

    fun format(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

    /** The id of the theme that is showing right now. */
    fun activeId(c: Context): String {
        val p: SharedPreferences = c.getSharedPreferences("luma", Context.MODE_PRIVATE)
        val manual = p.getString("theme", null) ?: LumaThemes.first().id
        if (!p.getBoolean("dn_enabled", false)) return manual
        val day = isDay(minuteOfDay(), p.getInt("dn_day_start", DEFAULT_DAY_START), p.getInt("dn_night_start", DEFAULT_NIGHT_START))
        return (if (day) p.getString("dn_day", null) else p.getString("dn_night", null))
            ?: (if (day) manual else PaperDarkTheme.id)
    }
}

package dev.luma.launcher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat

/**
 * Sends you home when a distracting app is in front without time left: either its allowed time ran out,
 * or it was opened some way other than through Luma's pause. Only the foreground package name is looked at.
 */
class TimeLimitService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var watching: String? = null
    private val expire = Runnable { watching?.let { enforce(it) } }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Leaving the blocked app (home gesture, recents) takes the screen away; Luma's own window and the
        // system UI (which fire events when the screen is added or the shade is pulled) don't count.
        if (overlay != null && pkg != overlayFor && pkg != packageName && pkg != "com.android.systemui") hideBlock()
        if (pkg !in TimeLimit.distractingPackages(this)) return
        handler.removeCallbacks(expire)
        watching = pkg
        val left = TimeLimit.graceUntil(this, pkg) - System.currentTimeMillis()
        if (left <= 0) enforce(pkg) else handler.postDelayed(expire, left + 200)
    }

    private fun enforce(pkg: String) {
        // Only act if the app really is the one in front (the timer may fire after you left it).
        if (rootInActiveWindow?.packageName?.toString() != pkg) return
        if (TimeLimit.hasGrace(this, pkg)) return
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
        if (TimeLimit.graceUntil(this, pkg) > 0L) {
            showBlock(pkg, "Time's up", "Your ${TimeLimit.minutes(this)} ${if (TimeLimit.minutes(this) == 1) "minute" else "minutes"} in $label ${if (TimeLimit.minutes(this) == 1) "is" else "are"} over.")
        } else {
            showBlock(pkg, "Open $label from Luma", "$label waits behind a pause. Open it from Luma to continue.")
        }
    }

    private var overlay: View? = null
    private var overlayFor: String? = null

    /** A full-screen page over the app, in the theme's colours, with a Close button that sends you home. */
    private fun showBlock(pkg: String, title: String, message: String) {
        if (overlay != null) return
        val theme = themeById(ThemeSchedule.activeId(this))
        val fg = theme.fg.toArgb()
        val fg2 = theme.fg2.toArgb()
        val font = ResourcesCompat.getFont(this, if (theme.id.startsWith("paper")) R.font.plexmono_regular else R.font.outfit)
        val pad = (32 * resources.displayMetrics.density).toInt()
        fun text(t: String, size: Float, color: Int, top: Int = 0) = TextView(this).apply {
            this.text = t; textSize = size; setTextColor(color); typeface = font
            setPadding(0, (top * resources.displayMetrics.density).toInt(), 0, 0)
        }
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(theme.overlay.toArgb() or (0xFF shl 24))
            setPadding(pad, pad, pad, pad)
            isClickable = true
            addView(text(title, 28f, fg))
            addView(text(message, 18f, fg2, 12))
            addView(text("Close", 22f, fg, 48).apply {
                setPadding(0, (60 * resources.displayMetrics.density).toInt(), 0, (12 * resources.displayMetrics.density).toInt())
                setOnClickListener { hideBlock(); performGlobalAction(GLOBAL_ACTION_HOME) }
            })
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE,
        )
        getSystemService(WindowManager::class.java).addView(view, params)
        overlay = view
        overlayFor = pkg
    }

    private fun hideBlock() {
        overlay?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        overlay = null
        overlayFor = null
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(expire)
        hideBlock()
        super.onDestroy()
    }

    companion object {
        fun isEnabled(c: Context): Boolean {
            val me = ComponentName(c, TimeLimitService::class.java).flattenToString()
            val list = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            return list?.split(':')?.any { it.equals(me, ignoreCase = true) } == true
        }
    }
}

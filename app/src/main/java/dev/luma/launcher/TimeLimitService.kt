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
 * Covers a distracting app that is in front without time left: with a "Time's up" page when its allowed time ran
 * out, or with the same pause Luma shows when it was opened some other way (a notification, say).
 * Only the foreground package name is looked at.
 */
class TimeLimitService : AccessibilityService() {
    private val uiHandler = Handler(Looper.getMainLooper())
    private var watching: String? = null
    private val expire = Runnable { watching?.let { enforce(it, expired = true) } }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Leaving the blocked app (home gesture, recents) takes the screen away; Luma's own window and the
        // system UI (which fire events when the screen is added or the shade is pulled) don't count.
        if (overlay != null && pkg != overlayFor && pkg != packageName && pkg != "com.android.systemui") hideBlock()
        if (pkg !in TimeLimit.distractingPackages(this)) return
        uiHandler.removeCallbacks(expire)
        watching = pkg
        val left = TimeLimit.graceUntil(this, pkg) - System.currentTimeMillis()
        if (left <= 0) enforce(pkg, expired = false) else uiHandler.postDelayed(expire, left + 200)
    }

    /** [expired]: the allowed time just ran out while the app was in use; otherwise it was just opened. */
    private fun enforce(pkg: String, expired: Boolean) {
        // Only act if the app really is the one in front (the timer may fire after you left it).
        if (rootInActiveWindow?.packageName?.toString() != pkg) return
        if (TimeLimit.hasGrace(this, pkg)) return
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
        val minutes = TimeLimit.minutes(this)
        if (expired) {
            showBlock(pkg, getString(R.string.times_up), resources.getQuantityString(R.plurals.times_up_body, minutes, minutes, label), pause = false)
        } else {
            showBlock(pkg, getString(R.string.open_app_question, label), "", pause = true)
        }
    }

    private var overlay: View? = null
    private var overlayFor: String? = null

    private var tick: Runnable? = null

    /**
     * A full-screen page over the app, in the theme's colours. With [pause] it is Luma's pause (a countdown, "Not
     * now", then "Open for N min" which lets the app through); otherwise it only has a Close button.
     * Both send you home.
     */
    private fun showBlock(pkg: String, title: String, message: String, pause: Boolean) {
        if (overlay != null) return
        val theme = themeById(ThemeSchedule.activeId(this))
        val fg = theme.fg.toArgb()
        val fg2 = theme.fg2.toArgb()
        val font = ResourcesCompat.getFont(this, if (theme.id.startsWith("paper")) R.font.plexmono_regular else R.font.outfit)
        val density = resources.displayMetrics.density
        val pad = (32 * density).toInt()
        fun text(t: String, size: Float, color: Int, top: Int = 0) = TextView(this).apply {
            this.text = t; textSize = size; setTextColor(color); typeface = font
            setPadding(0, (top * density).toInt(), 0, 0)
        }
        fun button(t: String, size: Float, color: Int, top: Int, onClick: () -> Unit) = text(t, size, color).apply {
            setPadding(0, (top * density).toInt(), 0, (12 * density).toInt())
            setOnClickListener { onClick() }
        }
        val goHome = { hideBlock(); performGlobalAction(GLOBAL_ACTION_HOME); Unit }
        val minutes = TimeLimit.minutes(this)
        val body = text(if (pause) getString(R.string.take_a_breath, FRICTION_SECONDS) else message, 18f, fg2, 12)
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(theme.overlay.toArgb() or (0xFF shl 24))
            setPadding(pad, pad, pad, pad)
            isClickable = true
            addView(text(title, 28f, fg))
            addView(body)
            if (pause) {
                addView(button(getString(R.string.not_now), 22f, fg, 48, goHome))
                val open = button(getString(R.string.open_for_minutes, minutes), 18f, fg2, 8) {
                    TimeLimit.grant(this@TimeLimitService, pkg)
                    hideBlock()
                    // Keep watching: cover the app again when this new allowance runs out.
                    watching = pkg
                    uiHandler.removeCallbacks(expire)
                    uiHandler.postDelayed(expire, minutes * 60_000L + 200)
                }.apply { visibility = View.GONE }
                addView(open)
                var left = FRICTION_SECONDS
                val t = object : Runnable {
                    override fun run() {
                        left--
                        if (left > 0) {
                            body.text = getString(R.string.take_a_breath, left)
                            uiHandler.postDelayed(this, 1000)
                        } else {
                            body.text = getString(R.string.still_want)
                            open.visibility = View.VISIBLE
                        }
                    }
                }
                tick = t
                uiHandler.postDelayed(t, 1000)
            } else {
                addView(button(getString(R.string.action_close), 22f, fg, 60, goHome))
            }
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
        tick?.let { uiHandler.removeCallbacks(it) }
        tick = null
        overlay?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        overlay = null
        overlayFor = null
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        uiHandler.removeCallbacks(expire)
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

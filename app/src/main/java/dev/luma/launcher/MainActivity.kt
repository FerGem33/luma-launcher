package dev.luma.launcher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()
    private lateinit var widgetHost: AppWidgetHost
    private lateinit var widgetManager: AppWidgetManager

    // State of an in-flight "add widget" flow (bind permission -> configure -> done).
    private var pendingPage = -1
    private var pendingId = -1
    private var pendingCols = 1
    private var pendingCol: Int? = null
    private var pendingRow: Int? = null
    private var pendingRows = 1

    private val bindLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            if (r.resultCode == Activity.RESULT_OK) configureOrFinish() else cancelPending()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        widgetHost = AppWidgetHost(applicationContext, HOST_ID)
        widgetManager = AppWidgetManager.getInstance(this)
        vm.takeLegacyWidgetIds().forEach { widgetHost.deleteAppWidgetId(it) }

        setContent {
            val theme = vm.theme
            val scheme = remember(theme) {
                val base = if (theme.isLight) lightColorScheme() else darkColorScheme()
                base.copy(
                    primary = theme.accent ?: theme.fg,
                    onSurface = theme.fg,
                    onSurfaceVariant = theme.fg2,
                    surface = theme.menu,
                    surfaceContainer = theme.menu,
                    surfaceContainerHigh = theme.menu,
                    background = theme.background ?: theme.menu,
                    onBackground = theme.fg,
                )
            }
            // Dark status/navigation bar icons on light themes.
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = theme.isLight
                    isAppearanceLightNavigationBars = theme.isLight
                }
            }
            MaterialTheme(colorScheme = scheme) {
                CompositionLocalProvider(LocalLuma provides theme) {
                    ProvideTextStyle(
                        TextStyle(
                            fontFamily = theme.font,
                            color = theme.fg,
                            shadow = if (theme.textShadow) Shadow(androidx.compose.ui.graphics.Color(0x99000000), Offset(0f, 2f), 8f) else null,
                        ),
                    ) {
                        LauncherRoot(
                            vm = vm,
                            widgetHost = widgetHost,
                            widgetManager = widgetManager,
                            onPickWidget = ::startAddWidget,
                            onReleaseWidget = { widgetHost.deleteAppWidgetId(it) },
                            onConfigureWidget = ::openWidgetSettings,
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        widgetHost.startListening()
    }

    override fun onStop() {
        widgetHost.stopListening()
        super.onStop()
    }

    @Deprecated("Required by AppWidgetHost.startAppWidgetConfigureActivityForResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CONFIGURE) {
            if (resultCode == Activity.RESULT_OK) finishPending() else cancelPending()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && Intent.CATEGORY_HOME in (intent.categories ?: emptySet())) {
            vm.onHomePressed()
        }
    }

    /** Same as One UI Home's "Settings": Samsung's own settings activity if it has one, else the standard one. */
    private fun openWidgetSettings(id: Int) {
        val info = widgetManager.getAppWidgetInfo(id) ?: return
        val sem = samsungConfigureActivity(this, info)
        val started = sem != null && runCatching {
            startActivity(
                Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                    .setComponent(sem)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            )
        }.isSuccess
        if (!started) widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_RECONFIGURE, null)
    }

    // ---- add-widget flow -------------------------------------------------------------------

    private fun startAddWidget(page: Int, info: AppWidgetProviderInfo, cols: Int?, rows: Int?, col: Int?, row: Int?) {
        pendingPage = page
        pendingCol = col
        pendingRow = row
        pendingId = widgetHost.allocateAppWidgetId()
        val (defCols, defRows) = defaultSpan(this, info)
        val cols = cols ?: defCols
        val rows = rows ?: defRows
        pendingCols = cols
        pendingRows = rows
        val density = resources.displayMetrics.density
        val cellDp = ((resources.displayMetrics.widthPixels / density) - 16) / GRID_COLS
        val options = widgetOptions(this, (cellDp * cols).toInt() - 8, (cellDp * rows).toInt() - 8, cols, rows)
        if (widgetManager.bindAppWidgetIdIfAllowed(pendingId, info.provider, options)) {
            configureOrFinish()
        } else {
            bindLauncher.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options)
            )
        }
    }

    private fun configureOrFinish() {
        val info = widgetManager.getAppWidgetInfo(pendingId)
        if (info?.configure != null) {
            widgetHost.startAppWidgetConfigureActivityForResult(this, pendingId, 0, REQ_CONFIGURE, null)
        } else {
            finishPending()
        }
    }

    private fun finishPending() {
        if (widgetManager.getAppWidgetInfo(pendingId) == null || !vm.addWidgetAt(pendingPage, pendingId, pendingCols, pendingRows, pendingCol, pendingRow)) {
            if (widgetManager.getAppWidgetInfo(pendingId) != null) {
                Toast.makeText(this, "No space left on this page", Toast.LENGTH_SHORT).show()
            }
            cancelPending()
            return
        }
        pendingId = -1
    }

    private fun cancelPending() {
        if (pendingId != -1) widgetHost.deleteAppWidgetId(pendingId)
        pendingId = -1
    }

    private companion object {
        const val HOST_ID = 1024
        const val REQ_CONFIGURE = 4242
        const val REQ_RECONFIGURE = 4243
    }
}

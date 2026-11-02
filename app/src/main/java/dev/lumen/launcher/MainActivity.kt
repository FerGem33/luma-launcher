package dev.lumen.launcher

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
import androidx.compose.material3.darkColorScheme

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()
    private lateinit var widgetHost: AppWidgetHost
    private lateinit var widgetManager: AppWidgetManager

    // State of an in-flight "add widget" flow (bind permission -> configure -> done).
    private var pendingPage = -1
    private var pendingId = -1
    private var pendingCols = 1
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
            MaterialTheme(colorScheme = darkColorScheme()) {
                LauncherRoot(
                    vm = vm,
                    widgetHost = widgetHost,
                    widgetManager = widgetManager,
                    onPickWidget = ::startAddWidget,
                    onReleaseWidget = { widgetHost.deleteAppWidgetId(it) },
                    onConfigureWidget = { id ->
                        widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_RECONFIGURE, null)
                    },
                )
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

    // ---- add-widget flow -------------------------------------------------------------------

    private fun startAddWidget(page: Int, info: AppWidgetProviderInfo, cols: Int?, rows: Int?) {
        pendingPage = page
        pendingId = widgetHost.allocateAppWidgetId()
        val (defCols, defRows) = defaultSpan(this, info)
        val cols = cols ?: defCols
        val rows = rows ?: defRows
        pendingCols = cols
        pendingRows = rows
        val density = resources.displayMetrics.density
        val cellDp = ((resources.displayMetrics.widthPixels / density) - 16) / GRID_COLS
        val options = widgetOptions((cellDp * cols).toInt() - 8, (cellDp * rows).toInt() - 8)
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
        if (widgetManager.getAppWidgetInfo(pendingId) == null || !vm.addWidget(pendingPage, pendingId, pendingCols, pendingRows)) {
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

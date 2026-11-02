package dev.lumen.launcher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
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

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                LauncherRoot(
                    vm = vm,
                    widgetHost = widgetHost,
                    widgetManager = widgetManager,
                    onPickWidget = ::startAddWidget,
                    onReleaseWidget = { widgetHost.deleteAppWidgetId(it) },
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

    private fun startAddWidget(page: Int, info: AppWidgetProviderInfo) {
        pendingPage = page
        pendingId = widgetHost.allocateAppWidgetId()
        if (widgetManager.bindAppWidgetIdIfAllowed(pendingId, info.provider)) {
            configureOrFinish()
        } else {
            bindLauncher.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
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
        val info = widgetManager.getAppWidgetInfo(pendingId)
        if (info == null) {
            cancelPending()
            return
        }
        val density = resources.displayMetrics.density
        val heightDp = (info.minHeight / density).toInt().coerceIn(80, 600)
        vm.addWidget(pendingPage, WidgetItem(pendingId, heightDp))
        pendingId = -1
    }

    private fun cancelPending() {
        if (pendingId != -1) widgetHost.deleteAppWidgetId(pendingId)
        pendingId = -1
    }

    private companion object {
        const val HOST_ID = 1024
        const val REQ_CONFIGURE = 4242
    }
}

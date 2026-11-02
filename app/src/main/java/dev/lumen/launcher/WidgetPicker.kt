package dev.lumen.launcher

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

@Composable
fun WidgetPicker(onPick: (AppWidgetProviderInfo, cols: Int?, rows: Int?) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val groups = remember {
        val pm = ctx.packageManager
        AppWidgetManager.getInstance(ctx).installedProviders
            // Some apps register several providers that are indistinguishable to the user
            // (same name, preview, size); show each such widget once.
            .map { it to samsungVariants(ctx, it) }
            .distinctBy { (it, variants) ->
                listOf(
                    it.provider.packageName, it.loadLabel(pm), it.previewImage, it.previewLayout,
                    it.minWidth, it.minHeight, it.resizeMode, it.configure, variants,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) it.targetCellWidth to it.targetCellHeight else null,
                )
            }
            // A widget that declares several sizes becomes one entry per size, like One UI Home.
            .flatMap { (info, variants) ->
                if (variants.size >= 2) variants.map { PickerEntry(info, it) } else listOf(PickerEntry(info, null))
            }
            .groupBy { pm.getApplicationLabel(pm.getApplicationInfo(it.info.provider.packageName, 0)).toString() }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }
    val density = ctx.resources.displayMetrics.density
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val filtered = remember(groups, q) {
        if (q.isEmpty()) groups
        else groups.filterKeys { it.contains(q, ignoreCase = true) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xF2000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(24.dp)) {
            Text("Add widget", color = Color.White, fontSize = 22.sp, modifier = Modifier.weight(1f))
            Text("Cancel", color = Color(0xFF999999), fontSize = 16.sp, modifier = Modifier.clickable(onClick = onDismiss))
        }
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
            cursorBrush = SolidColor(Color.White),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                        .background(Color(0xFF1C1C1E), RoundedCornerShape(24.dp))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    if (query.isEmpty()) Text("Search apps", color = Color(0xFF777777), fontSize = 18.sp)
                    inner()
                }
            },
        )
        LazyColumn(Modifier.weight(1f)) {
            filtered.forEach { (app, providers) ->
                item {
                    Text(app, color = Color(0xFF999999), fontSize = 14.sp,
                        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 6.dp))
                }
                items(providers) { entry ->
                    val info = entry.info
                    val v = entry.variant
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(info, v?.cols, v?.rows) }
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                    ) {
                        WidgetPreview(info, v)
                        Column(Modifier.padding(start = 16.dp).align(Alignment.CenterVertically)) {
                            Text(info.loadLabel(ctx.packageManager), color = Color.White, fontSize = 17.sp)
                            Text(
                                (v?.let { it.cols to it.rows } ?: defaultSpan(ctx, info)).let { (c, r) -> "$c × $r" },
                                color = Color(0xFF777777), fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val PreviewWidth = 140.dp
private val PreviewHeight = 90.dp

/**
 * Preview for a widget, trying the same sources One UI does, best first:
 *  1. generated preview (Android 15+), rendered live by the widget's app
 *  2. preview layout (Android 12+), inflated from the widget's package
 *  3. static preview image
 *  4. the app icon
 */
@Composable
private fun WidgetPreview(info: AppWidgetProviderInfo, variant: SizeVariant?) {
    val ctx = LocalContext.current
    val density = ctx.resources.displayMetrics.density
    val boxW = PreviewWidth.value * density
    val boxH = PreviewHeight.value * density

    val live = remember(info, variant) { buildLivePreview(ctx, info, variant?.previewLayout) }
    val image = remember(info) {
        if (live != null) null
        else runCatching { info.loadPreviewImage(ctx, 0)?.toBitmap()?.asImageBitmap() }.getOrNull()
    }
    val icon = remember(info) {
        if (live != null || image != null) null
        else runCatching { info.loadIcon(ctx, 0)?.toBitmap()?.asImageBitmap() }.getOrNull()
    }

    Box(
        Modifier.size(PreviewWidth, PreviewHeight).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1C1C1E)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            live != null -> {
                // Lay the preview out at the widget's real size, then scale it down to fit the box.
                val (cols, rows) = variant?.let { it.cols to it.rows } ?: defaultSpan(ctx, info)
                val w = (gridCellPx(ctx) * cols).toInt()
                val h = (gridCellPx(ctx) * rows).toInt()
                val scale = minOf(boxW / w, boxH / h, 1f)
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { c ->
                        object : FrameLayout(c) {
                            // Previews are display-only: never let them react to touches.
                            override fun onInterceptTouchEvent(e: MotionEvent) = true
                            override fun onTouchEvent(e: MotionEvent) = false
                        }.apply {
                            live.layoutParams = FrameLayout.LayoutParams(w, h)
                            live.pivotX = 0f
                            live.pivotY = 0f
                            live.scaleX = scale
                            live.scaleY = scale
                            live.translationX = (boxW - w * scale) / 2f
                            live.translationY = (boxH - h * scale) / 2f
                            addView(live)
                        }
                    },
                )
            }
            image != null -> Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            icon != null -> Image(icon, null, Modifier.size(40.dp))
        }
    }
}

private fun buildLivePreview(ctx: Context, info: AppWidgetProviderInfo, layoutOverride: Int?): View? {
    val holder = FrameLayout(ctx)
    if (layoutOverride == null && Build.VERSION.SDK_INT >= 35) {
        runCatching {
            val rv = AppWidgetManager.getInstance(ctx)
                .getWidgetPreview(info.provider, null, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
            if (rv != null) return rv.apply(ctx, holder)
        }
    }
    val layoutId = layoutOverride ?: info.previewLayout
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && layoutId != 0) {
        val pkg = info.provider.packageName
        // Plain framework layouts inflate fine from the resources alone.
        runCatching {
            return LayoutInflater.from(ctx.createPackageContext(pkg, 0)).inflate(layoutId, holder, false)
        }
        // Some (e.g. Samsung's) use the widget app's own view classes such as ConstraintLayout.
        // Those can only be inflated by loading that app's code, which is limited to
        // system-flagged apps (preinstalled ones like Samsung's) so we never run third-party code.
        val appInfo = ctx.packageManager.getApplicationInfo(pkg, 0)
        if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) {
            runCatching {
                val withCode = ctx.createPackageContext(
                    pkg, Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
                )
                return LayoutInflater.from(withCode).inflate(layoutId, holder, false)
            }
        }
    }
    return null
}

/** One row in the picker: a widget, optionally pinned to one of the sizes it declares. */
private class PickerEntry(val info: AppWidgetProviderInfo, val variant: SizeVariant?)

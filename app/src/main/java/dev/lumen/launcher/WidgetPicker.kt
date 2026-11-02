package dev.lumen.launcher

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
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
            // Providers an app marks "hide from picker" are legacy or duplicate registrations of a
            // widget that is offered through another provider; One UI Home skips them too.
            .filter { it.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER == 0 }
            .map { it to samsungVariants(ctx, it) }
            // Some apps still register several providers that look identical to the user
            // (same name, preview, size); show each such widget once.
            .distinctBy { (it, variants) ->
                listOf(
                    it.provider.packageName, it.loadLabel(pm), it.previewImage, it.previewLayout,
                    it.minWidth, it.minHeight, it.resizeMode, it.configure, variants,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) it.targetCellWidth to it.targetCellHeight else null,
                )
            }
            .map { (info, variants) ->
                // A widget that declares several sizes offers each of them, like One UI Home.
                PickerWidget(info, info.loadLabel(pm).toString(), if (variants.size >= 2) variants else emptyList())
            }
            .groupBy { pm.getApplicationLabel(pm.getApplicationInfo(it.info.provider.packageName, 0)).toString() }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }
    val density = ctx.resources.displayMetrics.density
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    // Matches the app name (then all its widgets are shown) or an individual widget's name.
    val filtered = remember(groups, q) {
        if (q.isEmpty()) groups
        else groups.mapNotNull { (app, widgets) ->
            val shown = if (app.contains(q, ignoreCase = true)) widgets else widgets.filter { it.label.contains(q, ignoreCase = true) }
            if (shown.isEmpty()) null else app to shown
        }.toMap()
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
                    if (query.isEmpty()) Text("Search apps and widgets", color = Color(0xFF777777), fontSize = 18.sp)
                    inner()
                }
            },
        )
        LazyColumn(Modifier.weight(1f)) {
            filtered.forEach { (app, widgets) ->
                item {
                    Text(app, color = Color(0xFF999999), fontSize = 14.sp,
                        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 6.dp))
                }
                items(widgets) { w -> WidgetCard(w, onPick) }
            }
        }
    }
}

/** One widget: its name, then a preview per size it offers (or one preview at its default size). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetCard(w: PickerWidget, onPick: (AppWidgetProviderInfo, Int?, Int?) -> Unit) {
    val ctx = LocalContext.current
    val sizes: List<SizeVariant?> = w.variants.ifEmpty { listOf(null) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF1C1C1E))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(w.label, color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center)
        FlowRow(
            Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            sizes.forEach { v ->
                val (cols, rows) = v?.let { it.cols to it.rows } ?: defaultSpan(ctx, w.info)
                Column(
                    Modifier.clickable { onPick(w.info, v?.cols, v?.rows) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    WidgetPreview(w.info, v, cols, rows)
                    Text("$cols×$rows", color = Color(0xFF999999), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

/** Size of one grid cell in the picker's previews, so previews keep their true relative sizes. */
private val PreviewCell = 48.dp

/**
 * Preview for a widget, trying the same sources One UI does, best first:
 *  1. generated preview (Android 15+), rendered live by the widget's app
 *  2. preview layout (Android 12+), inflated from the widget's package
 *  3. static preview image
 *  4. the app icon
 */
@Composable
private fun WidgetPreview(info: AppWidgetProviderInfo, variant: SizeVariant?, cols: Int, rows: Int) {
    val ctx = LocalContext.current
    val density = ctx.resources.displayMetrics.density
    val tileCols = cols.coerceIn(1, GRID_COLS)
    val tileRows = rows.coerceIn(1, 4)
    val boxW = PreviewCell.value * tileCols * density
    val boxH = PreviewCell.value * tileRows * density

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
        Modifier.size(PreviewCell * tileCols, PreviewCell * tileRows).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2C2C2E)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            live != null -> {
                // Lay the preview out at the widget's real size, then scale it down to fit the box.
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
        // Inflated without a parent: with one, the inflater builds layout params for the root from its
        // (often @dimen) size attributes, which fails for many Samsung layouts and isn't needed here.
        // Plain framework layouts inflate fine from the resources alone.
        runCatching {
            return LayoutInflater.from(ctx.createPackageContext(pkg, 0)).inflate(layoutId, null, false)
        }
        // Some (e.g. Samsung's) use the widget app's own view classes such as ConstraintLayout.
        // Those can only be inflated by loading that app's code, which is limited to apps that are
        // as trusted as the OS itself, so we never run third-party code.
        if (isOsTrusted(ctx, pkg)) {
            runCatching {
                val withCode = ctx.createPackageContext(
                    pkg, Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
                )
                return LayoutInflater.from(withCode).inflate(layoutId, null, false)
            }
        }
    }
    return null
}

/** Preinstalled (system) apps, or apps signed with the platform key such as Samsung's updated system apps. */
private fun isOsTrusted(ctx: Context, pkg: String): Boolean = runCatching {
    val pm = ctx.packageManager
    pm.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
        pm.checkSignatures("android", pkg) == PackageManager.SIGNATURE_MATCH
}.getOrDefault(false)

/** A widget in the picker with the sizes it declares (empty if it declares none). */
private class PickerWidget(val info: AppWidgetProviderInfo, val label: String, val variants: List<SizeVariant>)

package dev.luma.launcher

import androidx.compose.ui.res.stringResource
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

@Composable
fun WidgetPicker(
    onPick: (AppWidgetProviderInfo, cols: Int?, rows: Int?) -> Unit,
    /** A widget is being dragged out of the list (null when the drag ends). */
    onDrag: (PickerDrag?) -> Unit,
    /** The drag was released; return true if it landed somewhere valid (the picker then closes). */
    onDrop: (PickerDrag) -> Boolean,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val t = LocalLuma.current
    // While a widget is dragged out, the list hides so the page behind shows, and a ghost follows the finger.
    var drag by remember { mutableStateOf<PickerDrag?>(null) }
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDrop by rememberUpdatedState(onDrop)
    fun setDrag(d: PickerDrag?) { drag = d; currentOnDrag(d) }
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
                    it.provider.packageName, it.loadLabel(pm), it.previewImage,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) it.previewLayout else 0,
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

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .alpha(if (drag != null) 0f else 1f)
            .background(t.overlay)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(24.dp)) {
            Text(stringResource(R.string.add_widget), color = t.fg, fontSize = 22.sp, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.action_cancel), color = t.fg2, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onDismiss))
        }
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = t.fg, fontSize = 18.sp, fontFamily = t.font),
            cursorBrush = SolidColor(t.fg),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                        .background(t.surface, RoundedCornerShape(24.dp))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    if (query.isEmpty()) Text(stringResource(R.string.search_widgets), color = t.fg2, fontSize = 18.sp)
                    inner()
                }
            },
        )
        LazyColumn(Modifier.weight(1f)) {
            filtered.forEach { (app, widgets) ->
                item(key = "app:$app") {
                    Text(app, color = t.fg2, fontSize = 14.sp,
                        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 6.dp))
                }
                // Keyed, so a row keeps its own widget (and preview) as the search narrows or widens the list.
                items(widgets, key = { it.info.provider.flattenToString() }) { w ->
                    WidgetCard(
                        w, onPick,
                        onDragStart = { setDrag(it) },
                        onDragBy = { delta -> drag?.let { d -> setDrag(PickerDrag(d.info, d.variant, d.cols, d.rows, d.pointer + delta)) } },
                        onDragEnd = { commit ->
                            val d = drag
                            if (commit && d != null) currentOnDrop(d)
                            setDrag(null)
                        },
                    )
                }
            }
        }
    }
    drag?.let { d ->
        val density = LocalDensity.current
        val w = with(density) { PreviewCell.toPx() } * d.cols.coerceIn(1, GRID_COLS)
        val h = with(density) { PreviewCell.toPx() } * d.rows.coerceIn(1, 4)
        Box(
            Modifier
                .offset { IntOffset((d.pointer.x - w / 2).roundToInt(), (d.pointer.y - h / 2).roundToInt()) }
                .alpha(0.92f),
        ) { WidgetPreview(d.info, d.variant, d.cols, d.rows) }
    }
    }
}

/** One widget: its name, then a preview per size it offers (or one preview at its default size). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetCard(
    w: PickerWidget,
    onPick: (AppWidgetProviderInfo, Int?, Int?) -> Unit,
    onDragStart: (PickerDrag) -> Unit,
    onDragBy: (Offset) -> Unit,
    onDragEnd: (commit: Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    val t = LocalLuma.current
    val sizes: List<SizeVariant?> = w.variants.ifEmpty { listOf(null) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(t.surface)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(w.label, color = t.fg, fontSize = 15.sp, textAlign = TextAlign.Center)
        FlowRow(
            Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            sizes.forEach { v ->
              key(v?.cols, v?.rows) {
                val (cols, rows) = v?.let { it.cols to it.rows } ?: defaultSpan(ctx, w.info)
                var rootPos by remember { mutableStateOf(Offset.Zero) }
                Column(
                    Modifier
                        .onGloballyPositioned { rootPos = it.positionInRoot() }
                        .clickable { onPick(w.info, v?.cols, v?.rows) }
                        // Long-press, then drag: lift the widget out and drop it on the page behind.
                        .pointerInput(w.info, v) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { local -> onDragStart(PickerDrag(w.info, v, cols, rows, rootPos + local)) },
                                onDrag = { change, amount -> change.consume(); onDragBy(amount) },
                                onDragEnd = { onDragEnd(true) },
                                onDragCancel = { onDragEnd(false) },
                            )
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    WidgetPreview(w.info, v, cols, rows)
                    Text("$cols×$rows", color = t.fg2, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
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
        Modifier.size(PreviewCell * tileCols, PreviewCell * tileRows).clip(RoundedCornerShape(12.dp)).background(LocalLuma.current.surfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        when {
            live != null -> {
                // Lay the preview out at the widget's real size, then scale it down to fit the box.
                val w = (gridCellPx(ctx) * cols).toInt()
                val h = (gridCellPx(ctx) * rows).toInt()
                val scale = minOf(boxW / w, boxH / h, 1f)
                key(live) {
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
    val layoutId = layoutOverride ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) info.previewLayout else 0
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

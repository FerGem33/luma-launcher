package dev.luma.launcher

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.tanh

/**
 * A widget page: a fixed [GRID_COLS]-column grid with square cells.
 *  - long-press empty space: page menu (add widget / remove page)
 *  - long-press a widget: select it; keep dragging to move it (snaps to cells, with a grid shown)
 *  - selected widget: drag its border to resize (elastic, snaps to cells), plus remove / settings
 *  - [dropPreview]: the spot a widget dragged out of the picker would take
 */
@Composable
fun WidgetPage(
    vm: LauncherViewModel,
    pageIndex: Int,
    widgetHost: AppWidgetHost,
    widgetManager: AppWidgetManager,
    dropPreview: DropPreview?,
    onGeometry: (GridGeometry) -> Unit,
    onAddWidget: () -> Unit,
    onRemovePage: (Int) -> Unit,
    onConfigureWidget: (Int) -> Unit,
) {
    val widgets = vm.pages.getOrNull(pageIndex) ?: return
    val t = LocalLuma.current
    val density = LocalDensity.current
    var selectedId by remember { mutableStateOf<Int?>(null) }
    var menuAt by remember { mutableStateOf<DpOffset?>(null) }
    var dragId by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var resizing by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    LaunchedEffect(vm.homeSignal) { selectedId = null; menuAt = null }
    BackHandler(enabled = selectedId != null) { selectedId = null }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            containerColor = t.menu,
            titleContentColor = t.fg,
            textContentColor = t.fg2,
            title = { Text(stringResource(R.string.remove_page_title)) },
            text = {
                Text(
                    pluralStringResource(R.plurals.remove_page_body, widgets.size, widgets.size),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; onRemovePage(pageIndex) }) {
                    Text(stringResource(R.string.action_remove), color = Color(0xFFE5534B))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.action_cancel), color = t.fg) }
            },
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
        val availH = maxHeight
        val margin = 8.dp
        val cellPx = with(density) { (maxWidth - margin * 2).toPx() } / GRID_COLS
        val cellDp = with(density) { cellPx.toDp() }
        val rows = max(1, floor(with(density) { (availH - 8.dp).toPx() } / cellPx).toInt())
        LaunchedEffect(rows) { vm.gridRows = rows }

        // Where a widget being moved would land (None while nothing is moving).
        val movePreview = dragId?.let { id ->
            widgets.firstOrNull { it.id == id }?.let { item ->
                val col = ((item.col * cellPx + dragOffset.x) / cellPx).roundToInt()
                val row = ((item.row * cellPx + dragOffset.y) / cellPx).roundToInt()
                DropPreview(col, row, item.colSpan, item.rowSpan, vm.canPlace(pageIndex, item.copy(col = col, row = row)))
            }
        }
        val target = dropPreview ?: movePreview
        val gridAlpha by animateFloatAsState(if (target != null || resizing) 1f else 0f, label = "gridAlpha")
        val good = t.accent ?: t.fg
        val bad = Color(0xFFE5534B)

        // The (non-scrolling) vertical scroll container only exists so the swipe-up-for-drawer
        // gesture reaches the nested-scroll connection in LauncherRoot.
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(
                Modifier
                    .height(availH)
                    .fillMaxWidth()
                    .padding(horizontal = margin)
                    .onGloballyPositioned { onGeometry(GridGeometry(it.boundsInRoot(), cellPx)) }
                    .drawBehind {
                        // Faint cell outlines while a widget is being moved, resized or dragged in.
                        if (gridAlpha > 0f) {
                            val inset = 5.dp.toPx()
                            val color = t.fg.copy(alpha = 0.18f * gridAlpha)
                            for (r in 0 until rows) for (c in 0 until GRID_COLS) {
                                drawRoundRect(
                                    color,
                                    topLeft = Offset(c * cellPx + inset, r * cellPx + inset),
                                    size = Size(cellPx - 2 * inset, cellPx - 2 * inset),
                                    cornerRadius = CornerRadius(14.dp.toPx()),
                                    style = Stroke(1.5.dp.toPx()),
                                )
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { selectedId = null },
                            onLongPress = { p ->
                                selectedId = null
                                menuAt = with(density) { DpOffset(p.x.toDp(), p.y.toDp()) }
                            },
                        )
                    },
            ) {
                widgets.forEach { item ->
                    key(item.id) {
                        val info = remember(item.id) { widgetManager.getAppWidgetInfo(item.id) }
                        WidgetCell(
                            item = item,
                            info = info,
                            widgetHost = widgetHost,
                            cellPx = cellPx,
                            cellDp = cellDp,
                            selected = selectedId == item.id,
                            dragOffset = if (dragId == item.id) dragOffset else Offset.Zero,
                            onDragStart = { selectedId = item.id; dragId = item.id; dragOffset = Offset.Zero },
                            onDragBy = { dragOffset += it },
                            onDragEnd = { cancelled ->
                                if (!cancelled) {
                                    val col = ((item.col * cellPx + dragOffset.x) / cellPx).roundToInt()
                                    val row = ((item.row * cellPx + dragOffset.y) / cellPx).roundToInt()
                                    vm.tryUpdate(pageIndex, item.copy(col = col, row = row))
                                }
                                dragId = null
                                dragOffset = Offset.Zero
                            },
                            onResize = { new -> vm.tryUpdate(pageIndex, new) },
                            onResizeActive = { resizing = it },
                            onRemove = {
                                selectedId = null
                                vm.removeWidget(pageIndex, item.id)
                                widgetHost.deleteAppWidgetId(item.id)
                            },
                            onConfigure = { onConfigureWidget(item.id) },
                        )
                    }
                }

                // The cells the dragged widget would take: tinted when free, red when blocked.
                Box(
                    Modifier.fillMaxSize().zIndex(2f).drawBehind {
                        target?.let { p ->
                            val inset = 5.dp.toPx()
                            val color = if (p.valid) good else bad
                            val topLeft = Offset(p.col * cellPx + inset, p.row * cellPx + inset)
                            val size = Size(p.cols * cellPx - 2 * inset, p.rows * cellPx - 2 * inset)
                            drawRoundRect(color.copy(alpha = 0.22f), topLeft, size, CornerRadius(18.dp.toPx()))
                            drawRoundRect(color, topLeft, size, CornerRadius(18.dp.toPx()), style = Stroke(2.dp.toPx()))
                        }
                    },
                )

                LumaMenu(
                    expanded = menuAt != null,
                    onDismiss = { menuAt = null },
                    offset = menuAt ?: DpOffset.Zero,
                ) {
                    LumaMenuItem(stringResource(R.string.add_widget), LumaIcons.Add, onClick = { menuAt = null; onAddWidget() })
                    LumaMenuItem(stringResource(R.string.remove_page), LumaIcons.Delete, danger = true, onClick = {
                        menuAt = null
                        // An empty page goes at once; one with widgets asks first, since the widgets go with it.
                        if (widgets.isEmpty()) onRemovePage(pageIndex) else confirmRemove = true
                    })
                }
            }
        }
    }
}

@Composable
private fun WidgetCell(
    item: WidgetItem,
    info: AppWidgetProviderInfo?,
    widgetHost: AppWidgetHost,
    cellPx: Float,
    cellDp: Dp,
    selected: Boolean,
    dragOffset: Offset,
    onDragStart: () -> Unit,
    onDragBy: (Offset) -> Unit,
    onDragEnd: (cancelled: Boolean) -> Unit,
    onResize: (WidgetItem) -> Boolean,
    onResizeActive: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onConfigure: () -> Unit,
) {
    val inset = 4.dp
    val widthDp = (cellDp * item.colSpan - inset * 2).value.toInt()
    val heightDp = (cellDp * item.rowSpan - inset * 2).value.toInt()
    val shape = RoundedCornerShape(16.dp)

    Box(
        Modifier
            .offset { IntOffset((item.col * cellPx + dragOffset.x).roundToInt(), (item.row * cellPx + dragOffset.y).roundToInt()) }
            .size(cellDp * item.colSpan, cellDp * item.rowSpan)
            .zIndex(if (selected) 1f else 0f)
            .padding(inset),
    ) {
        Box(Modifier.fillMaxSize().clip(shape)) {
            if (info != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WidgetFrame(ctx).apply {
                            // Older widgets get default padding around their background; One UI Home doesn't
                            // keep it, and it would leave the rounded clip cutting empty space.
                            addView(widgetHost.createView(ctx, item.id, info).apply { setPadding(0, 0, 0, 0) })
                        }
                    },
                    update = { frame ->
                        frame.onLongPress = onDragStart
                        frame.onDragBy = { dx, dy -> onDragBy(Offset(dx, dy)) }
                        frame.onDragEnd = onDragEnd
                        (frame.getChildAt(0) as? AppWidgetHostView)?.applySize(widthDp, heightDp, item.colSpan, item.rowSpan)
                    },
                )
            } else {
                Text(stringResource(R.string.widget_unavailable), color = LocalLuma.current.fg2, modifier = Modifier.padding(12.dp))
            }
        }

        if (selected) {
            SelectionFrame(
                item, info, cellPx, shape,
                onDragStart, onDragBy, onDragEnd, onResize, onResizeActive, onRemove, onConfigure,
            )
        }
    }
}

/**
 * Drawn over a selected widget: a dimming scrim, remove/settings buttons and a border you can grab anywhere
 * along its edges (or corners) to resize. The border stretches with your finger like a rubber band while the
 * widget snaps to whole cells, and springs back to the snapped size when you let go. Dragging from the middle
 * (after a long press) moves the widget.
 */
@Composable
private fun SelectionFrame(
    item: WidgetItem,
    info: AppWidgetProviderInfo?,
    cellPx: Float,
    shape: RoundedCornerShape,
    onDragStart: () -> Unit,
    onDragBy: (Offset) -> Unit,
    onDragEnd: (cancelled: Boolean) -> Unit,
    onResize: (WidgetItem) -> Boolean,
    onResizeActive: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onConfigure: () -> Unit,
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val currentItem by rememberUpdatedState(item)
    val currentResize by rememberUpdatedState(onResize)
    val currentResizeActive by rememberUpdatedState(onResizeActive)
    val currentDragStart by rememberUpdatedState(onDragStart)
    val currentDragBy by rememberUpdatedState(onDragBy)
    val currentDragEnd by rememberUpdatedState(onDragEnd)

    val minW = max(1, ceil(info?.minResizeWidth?.let { it / cellPx } ?: 1f).toInt())
    val minH = max(1, ceil(info?.minResizeHeight?.let { it / cellPx } ?: 1f).toInt())
    val mode = info?.resizeMode ?: 0
    val horizontal = mode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
    val vertical = mode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
    val band = with(density) { 30.dp.toPx() }
    // The theme's text colour: visible on light and dark pages alike (white would vanish on Paper).
    val frameColor = LocalLuma.current.fg

    // How far the border is pulled outward from the snapped widget bounds, per edge: left, top, right, bottom.
    val pull = remember { mutableStateListOf(0f, 0f, 0f, 0f) }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val left = -pull[0]
                val top = -pull[1]
                val right = size.width + pull[2]
                val bottom = size.height + pull[3]
                val white = frameColor
                drawRoundRect(
                    white, Offset(left, top), Size(right - left, bottom - top),
                    CornerRadius(16.dp.toPx()), style = Stroke(2.5.dp.toPx()),
                )
                // A small pill in the middle of each edge that can be dragged.
                val long = 28.dp.toPx()
                val thick = 5.dp.toPx()
                fun pill(cx: Float, cy: Float, wide: Boolean) {
                    val w = if (wide) long else thick
                    val h = if (wide) thick else long
                    drawRoundRect(white, Offset(cx - w / 2, cy - h / 2), Size(w, h), CornerRadius(thick / 2))
                }
                if (horizontal) {
                    pill(left, (top + bottom) / 2, wide = false)
                    pill(right, (top + bottom) / 2, wide = false)
                }
                if (vertical) {
                    pill((left + right) / 2, top, wide = true)
                    pill((left + right) / 2, bottom, wide = true)
                }
            }
            .pointerInput(item.id, horizontal, vertical, cellPx, minW, minH) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Handled here: keeps the page behind from also treating this touch as a long press.
                    down.consume()
                    val w = size.width
                    val h = size.height
                    var zone = 0 // bit per edge: 1 left, 2 top, 4 right, 8 bottom
                    if (horizontal) {
                        if (down.position.x < band && down.position.x <= w / 2f) zone = zone or 1
                        if (down.position.x > w - band && down.position.x > w / 2f) zone = zone or 4
                    }
                    if (vertical) {
                        if (down.position.y < band && down.position.y <= h / 2f) zone = zone or 2
                        if (down.position.y > h - band && down.position.y > h / 2f) zone = zone or 8
                    }

                    if (zone != 0) {
                        // ---- resize: the border follows the finger, the widget snaps to cells ----
                        currentResizeActive(true)
                        var cur = currentItem
                        val applied = IntArray(4)
                        var total = Offset.Zero
                        drag(down.id) { change ->
                            // Read the movement before consuming: consumed changes report none.
                            total += change.positionChange()
                            change.consume()
                            for (e in 0..3) {
                                if (zone and (1 shl e) == 0) continue
                                val outward = when (e) { 0 -> -total.x; 2 -> total.x; 1 -> -total.y; else -> total.y }
                                val wanted = (outward / cellPx).roundToInt()
                                while (applied[e] < wanted) {
                                    val next = stepEdge(cur, e, +1, minW, minH) ?: break
                                    if (!currentResize(next)) break
                                    cur = next; applied[e]++
                                }
                                while (applied[e] > wanted) {
                                    val next = stepEdge(cur, e, -1, minW, minH) ?: break
                                    if (!currentResize(next)) break
                                    cur = next; applied[e]--
                                }
                                val residual = outward - applied[e] * cellPx
                                // Free to follow the finger up to half a cell; beyond that (blocked) it resists.
                                pull[e] = if (applied[e] == wanted) residual else rubberBand(residual, cellPx * 0.4f)
                            }
                        }
                        currentResizeActive(false)
                        val from = pull.toList()
                        scope.launch {
                            animate(0f, 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) { f, _ ->
                                for (e in 0..3) pull[e] = from[e] * (1f - f)
                            }
                        }
                    } else {
                        // ---- move: long press, then drag ----
                        val press = awaitLongPressOrCancellation(down.id)
                        if (press != null) {
                            currentDragStart()
                            val finished = drag(press.id) { change ->
                                val delta = change.positionChange()
                                change.consume()
                                currentDragBy(delta)
                            }
                            currentDragEnd(!finished)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().clip(shape).background(Color(0x66000000)))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ActionIcon(LumaIcons.Delete, stringResource(R.string.remove_widget), onRemove)
            if (info != null && canReconfigure(ctx, info)) ActionIcon(LumaIcons.Settings, stringResource(R.string.widget_settings), onConfigure)
        }
    }
}

/** One cell of growth ([step] = +1) or shrinkage (-1) on edge [edge] (0 left, 1 top, 2 right, 3 bottom). */
private fun stepEdge(cur: WidgetItem, edge: Int, step: Int, minW: Int, minH: Int): WidgetItem? {
    val next = when (edge) {
        0 -> cur.copy(col = cur.col - step, colSpan = cur.colSpan + step)
        2 -> cur.copy(colSpan = cur.colSpan + step)
        1 -> cur.copy(row = cur.row - step, rowSpan = cur.rowSpan + step)
        else -> cur.copy(rowSpan = cur.rowSpan + step)
    }
    return if (step < 0 && (next.colSpan < minW || next.rowSpan < minH)) null else next
}

/** Like pulling on a rubber band: follows the finger at first, then resists. */
private fun rubberBand(x: Float, limit: Float): Float = limit * tanh(x / limit)

private fun canReconfigure(ctx: android.content.Context, info: AppWidgetProviderInfo): Boolean =
    samsungConfigureActivity(ctx, info) != null ||
        info.configure != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
        info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0

/** A round icon button shown over a selected widget (which is dimmed, so it is always light-on-dark). */
@Composable
private fun ActionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(0xE61C1C1E))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

package dev.lumen.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A widget page: a fixed [GRID_COLS]-column grid with square cells.
 *  - long-press empty space: page menu (add widget / remove page)
 *  - long-press a widget: select it; keep dragging to move it (snaps to cells)
 *  - selected widget: edge handles resize it, plus Remove / Settings actions
 */
@Composable
fun WidgetPage(
    vm: LauncherViewModel,
    pageIndex: Int,
    widgetHost: AppWidgetHost,
    widgetManager: AppWidgetManager,
    onAddWidget: () -> Unit,
    onRemovePage: (Int) -> Unit,
    onConfigureWidget: (Int) -> Unit,
) {
    val widgets = vm.pages.getOrNull(pageIndex) ?: return
    val density = LocalDensity.current
    var selectedId by remember { mutableStateOf<Int?>(null) }
    var menuAt by remember { mutableStateOf<DpOffset?>(null) }
    var dragId by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(vm.homeSignal) { selectedId = null; menuAt = null }
    BackHandler(enabled = selectedId != null) { selectedId = null }

    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
        val availH = maxHeight
        val margin = 8.dp
        val cellPx = with(density) { (maxWidth - margin * 2).toPx() } / GRID_COLS
        val cellDp = with(density) { cellPx.toDp() }
        val rows = max(1, floor(with(density) { (availH - 8.dp).toPx() } / cellPx).toInt())
        LaunchedEffect(rows) { vm.gridRows = rows }

        // The (non-scrolling) vertical scroll container only exists so the swipe-up-for-drawer
        // gesture reaches the nested-scroll connection in LauncherRoot.
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(
                Modifier
                    .height(availH)
                    .fillMaxWidth()
                    .padding(horizontal = margin)
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
                if (widgets.isEmpty()) {
                    Text(
                        "Long-press to add a widget",
                        color = LocalLumen.current.fg2, fontSize = 15.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

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
                            onRemove = {
                                selectedId = null
                                vm.removeWidget(pageIndex, item.id)
                                widgetHost.deleteAppWidgetId(item.id)
                            },
                            onConfigure = { onConfigureWidget(item.id) },
                        )
                    }
                }

                LumenMenu(
                    expanded = menuAt != null,
                    onDismiss = { menuAt = null },
                    offset = menuAt ?: DpOffset.Zero,
                ) {
                    LumenMenuItem("Add widget", LumenIcons.Add, onClick = { menuAt = null; onAddWidget() })
                    LumenMenuItem("Remove page", LumenIcons.Delete, danger = true, onClick = { menuAt = null; onRemovePage(pageIndex) })
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
    cellDp: androidx.compose.ui.unit.Dp,
    selected: Boolean,
    dragOffset: Offset,
    onDragStart: () -> Unit,
    onDragBy: (Offset) -> Unit,
    onDragEnd: (cancelled: Boolean) -> Unit,
    onResize: (WidgetItem) -> Boolean,
    onRemove: () -> Unit,
    onConfigure: () -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
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
                Text("Widget unavailable", color = LocalLumen.current.fg2, modifier = Modifier.padding(12.dp))
            }
        }

        if (selected) {
            val currentDragStart by rememberUpdatedState(onDragStart)
            val currentDragBy by rememberUpdatedState(onDragBy)
            val currentDragEnd by rememberUpdatedState(onDragEnd)
            // Covers the widget (so it stops reacting to touches) and lets you long-press-drag again.
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(Color(0x66000000))
                    .border(2.dp, Color.White, shape)
                    .pointerInput(item.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { currentDragStart() },
                            onDrag = { c, d -> c.consume(); currentDragBy(d) },
                            onDragEnd = { currentDragEnd(false) },
                            onDragCancel = { currentDragEnd(true) },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ActionIcon(LumenIcons.Delete, "Remove widget", onRemove)
                    if (info != null && canReconfigure(ctx, info)) ActionIcon(LumenIcons.Settings, "Widget settings", onConfigure)
                }
            }

            val minW = max(1, ceil(info?.minResizeWidth?.let { it / cellPx } ?: 1f).toInt())
            val minH = max(1, ceil(info?.minResizeHeight?.let { it / cellPx } ?: 1f).toInt())
            val mode = info?.resizeMode ?: 0
            val horizontal = mode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
            val vertical = mode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0

            if (horizontal) {
                ResizeHandle(Modifier.align(Alignment.CenterEnd), vertical = false, stepPx = cellPx) { dir ->
                    item.colSpan + dir >= minW && onResize(item.copy(colSpan = item.colSpan + dir))
                }
                ResizeHandle(Modifier.align(Alignment.CenterStart), vertical = false, stepPx = cellPx) { dir ->
                    item.colSpan - dir >= minW && onResize(item.copy(col = item.col + dir, colSpan = item.colSpan - dir))
                }
            }
            if (vertical) {
                ResizeHandle(Modifier.align(Alignment.BottomCenter), vertical = true, stepPx = cellPx) { dir ->
                    item.rowSpan + dir >= minH && onResize(item.copy(rowSpan = item.rowSpan + dir))
                }
                ResizeHandle(Modifier.align(Alignment.TopCenter), vertical = true, stepPx = cellPx) { dir ->
                    item.rowSpan - dir >= minH && onResize(item.copy(row = item.row + dir, rowSpan = item.rowSpan - dir))
                }
            }
        }
    }
}

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
        androidx.compose.material3.Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

/**
 * Edge handle. Dragging accumulates pixels and calls [onStep] with +1/-1 for every [stepPx] moved
 * along the handle's axis; [onStep] returns whether the change was applied.
 */
@Composable
private fun ResizeHandle(modifier: Modifier, vertical: Boolean, stepPx: Float, onStep: (Int) -> Boolean) {
    val currentOnStep by rememberUpdatedState(onStep)
    Box(
        modifier
            .size(44.dp)
            .pointerInput(stepPx, vertical) {
                var acc = 0f
                detectDragGestures(onDragStart = { acc = 0f }) { change, drag ->
                    change.consume()
                    acc += if (vertical) drag.y else drag.x
                    while (abs(acc) >= stepPx) {
                        val dir = if (acc > 0) 1 else -1
                        if (currentOnStep(dir)) acc -= dir * stepPx else { acc = 0f; break }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(Color.White))
    }
}

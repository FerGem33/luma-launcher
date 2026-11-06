package dev.luma.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun LauncherRoot(
    vm: LauncherViewModel,
    widgetHost: AppWidgetHost,
    widgetManager: AppWidgetManager,
    onPickWidget: (page: Int, info: AppWidgetProviderInfo, cols: Int?, rows: Int?, col: Int?, row: Int?) -> Unit,
    onReleaseWidget: (Int) -> Unit,
    onConfigureWidget: (Int) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = vm.homeIndex) { 1 + vm.pages.size }
    val scope = rememberCoroutineScope()
    var drawerOpen by remember { mutableStateOf(false) }
    // Set when the drawer was opened from the right-hand strip: it then starts with the search field focused.
    var drawerSearch by remember { mutableStateOf(false) }
    var gestureFromEdge by remember { mutableStateOf(false) }
    // Index (in vm.pages) of the widget page the picker was opened from.
    var pickerForPage by remember { mutableStateOf<Int?>(null) }
    var pickerDrag by remember { mutableStateOf<PickerDrag?>(null) }
    val geometries = remember { mutableStateMapOf<Int, GridGeometry>() }
    var settingsOpen by remember { mutableStateOf(false) }
    val theme = LocalLuma.current

    LaunchedEffect(vm.homeSignal) {
        drawerOpen = false
        drawerSearch = false
        pickerForPage = null
        pickerDrag = null
        settingsOpen = false
        pagerState.animateScrollToPage(vm.homeIndex)
    }

    /** The cells a widget dragged out of the picker would take on [page], or null if it isn't over the grid. */
    fun previewFor(d: PickerDrag, page: Int): DropPreview? {
        val geo = geometries[page] ?: return null
        if (!geo.bounds.contains(d.pointer)) return null
        val cols = d.cols.coerceIn(1, GRID_COLS)
        val rows = d.rows.coerceIn(1, vm.gridRows)
        val col = ((d.pointer.x - geo.bounds.left - cols * geo.cellPx / 2f) / geo.cellPx).roundToInt().coerceIn(0, GRID_COLS - cols)
        val row = ((d.pointer.y - geo.bounds.top - rows * geo.cellPx / 2f) / geo.cellPx).roundToInt().coerceIn(0, vm.gridRows - rows)
        return DropPreview(col, row, cols, rows, vm.canPlace(page, WidgetItem(-1, col, row, cols, rows)))
    }

    BackHandler {
        when {
            vm.frictionFor != null -> vm.dismissFriction()
            settingsOpen -> settingsOpen = false
            pickerForPage != null -> pickerForPage = null
            drawerOpen -> drawerOpen = false
            pagerState.currentPage != vm.homeIndex -> scope.launch { pagerState.animateScrollToPage(vm.homeIndex) }
        }
    }

    // Swipe up on any page (home or widget page, once it can't scroll further) opens the drawer.
    val openDrawerOnSwipeUp = remember {
        object : NestedScrollConnection {
            var acc = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) acc += available.y
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (acc < -100f) { drawerSearch = gestureFromEdge; drawerOpen = true }
                acc = 0f
                return Velocity.Zero
            }
        }
    }

    Box(Modifier.fillMaxSize().then(theme.background?.let { Modifier.background(it) } ?: Modifier)) {
        // Wallpaper themes: the chosen image, or else the system wallpaper through the window, dimmed by a scrim.
        val wallpaper = rememberImage(if (theme.background == null) vm.currentWallpaper else null, 2600)
        if (wallpaper != null) {
            Image(wallpaper, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (theme.background == null && theme.scrim > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = theme.scrim)))
        }
        // Over a wallpaper the overlays are translucent, so hide the pages under them instead of letting them ghost through.
        val overlayOpen = drawerOpen || settingsOpen || (pickerForPage != null && pickerDrag == null)
        Column(
            Modifier
                .fillMaxSize()
                .alpha(if (overlayOpen && theme.background == null) 0f else 1f)
                // Note where a touch starts (without consuming it): the right-hand strip opens the drawer with search.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        gestureFromEdge = down.position.x > size.width * EDGE_STRIP_FRACTION
                    }
                }
                .nestedScroll(openDrawerOnSwipeUp),
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                val widgetPage = vm.widgetPageAt(page)
                if (widgetPage == null) {
                    HomePage(vm)
                } else {
                    WidgetPage(
                        vm = vm,
                        pageIndex = widgetPage,
                        widgetHost = widgetHost,
                        widgetManager = widgetManager,
                        dropPreview = if (pickerForPage == widgetPage) pickerDrag?.let { previewFor(it, widgetPage) } else null,
                        onGeometry = { geometries[widgetPage] = it },
                        onAddWidget = { pickerForPage = widgetPage },
                        onRemovePage = { idx -> vm.removePage(idx).forEach(onReleaseWidget) },
                        onConfigureWidget = onConfigureWidget,
                    )
                }
            }
            PageIndicator(
                count = pagerState.pageCount,
                current = pagerState.currentPage,
                homeIndex = vm.homeIndex,
                onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
                onAddLeft = { scope.launch { pagerState.animateScrollToPage(vm.addPage(left = true)) } },
                onAddRight = { scope.launch { pagerState.animateScrollToPage(vm.addPage(left = false)) } },
            )
        }

        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically { it / 3 } + fadeIn(),
            exit = slideOutVertically { it / 3 } + fadeOut(),
        ) {
            NoShadow {
                AppDrawer(
                    vm = vm,
                    focusSearch = drawerSearch,
                    onOpenSettings = { drawerOpen = false; settingsOpen = true },
                    onLaunch = { vm.launch(it); if (vm.frictionFor == null) drawerOpen = false },
                    onClose = { drawerOpen = false },
                )
            }
        }

        pickerForPage?.let { page ->
            NoShadow {
                WidgetPicker(
                    onPick = { info, cols, rows -> pickerForPage = null; onPickWidget(page, info, cols, rows, null, null) },
                    onDrag = { pickerDrag = it },
                    onDrop = { d ->
                        val spot = previewFor(d, page)
                        if (spot != null && spot.valid) {
                            pickerForPage = null
                            pickerDrag = null
                            onPickWidget(page, d.info, d.cols, d.rows, spot.col, spot.row)
                            true
                        } else false
                    },
                    onDismiss = { pickerForPage = null; pickerDrag = null },
                )
            }
        }

        if (settingsOpen) NoShadow { SettingsScreen(vm, onClose = { settingsOpen = false }) }

        vm.frictionFor?.let { entry -> NoShadow { FrictionScreen(entry, vm) } }
    }
}

/** A swipe up starting right of this fraction of the screen's width opens the drawer with search focused. */
private const val EDGE_STRIP_FRACTION = 2f / 3f

// ---- Home page (Niagara-style) ---------------------------------------------------------------

@Composable
private fun HomePage(vm: LauncherViewModel) {
    val t = LocalLuma.current
    val pinnedApps = remember(vm.pinned, vm.apps) {
        val byKey = vm.apps.associateBy { it.key }
        vm.pinned.mapNotNull { byKey[it] }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullHeight = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(
                Modifier
                    .height(fullHeight)
                    .statusBarsPadding()
                    .padding(horizontal = 32.dp, vertical = 24.dp)
                    .padding(top = t.clock.topOffset),
                horizontalAlignment = t.align.horizontal,
            ) {
                Clock(t)
                Spacer(Modifier.weight(1f))
                if (pinnedApps.isEmpty()) {
                    Text(
                        "Swipe up for all apps.\nLong-press an app to pin it here.",
                        color = t.fg2, fontSize = 15.sp, textAlign = t.align.text,
                    )
                } else if (t.list.rule) {
                    Box(Modifier.padding(bottom = 10.dp).size(36.dp, 2.dp).background(t.accent ?: t.fg2))
                }
                PinnedList(pinnedApps, vm, t)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * The pinned apps. Long-press an app and drag to move it up or down the list; long-press and let go without
 * moving to open its menu.
 */
@Composable
private fun PinnedList(apps: List<AppEntry>, vm: LauncherViewModel, t: LumaTheme) {
    var rowHeight by remember { mutableIntStateOf(0) }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    val keys = apps.map { it.key }
    val from = dragKey?.let { keys.indexOf(it) } ?: -1
    val to = if (from >= 0 && rowHeight > 0) (from + (dragDy / rowHeight).roundToInt()).coerceIn(0, apps.lastIndex) else from
    apps.forEachIndexed { i, entry ->
        key(entry.key) {
            // The apps the dragged one passes over make room by one row.
            val makeRoom = when {
                from < 0 || i == from -> 0f
                from < to && i in (from + 1)..to -> -rowHeight.toFloat()
                from > to && i in to until from -> rowHeight.toFloat()
                else -> 0f
            }
            val shift by animateFloatAsState(makeRoom, label = "pinShift")
            PinnedItem(
                entry, vm, t,
                modifier = Modifier
                    .onSizeChanged { rowHeight = it.height }
                    .zIndex(if (i == from) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (i == from) dragDy else if (from >= 0) shift else 0f
                        val scale = if (i == from) 1.04f else 1f
                        scaleX = scale
                        scaleY = scale
                    },
                onReorderStart = { dragKey = entry.key; dragDy = 0f },
                onReorderBy = { dragDy += it },
                onReorderEnd = { commit ->
                    if (commit && from >= 0 && to != from) {
                        vm.reorderPinned(keys.toMutableList().apply { add(to, removeAt(from)) })
                    }
                    dragKey = null
                    dragDy = 0f
                },
            )
        }
    }
}

@Composable
private fun PinnedItem(
    entry: AppEntry,
    vm: LauncherViewModel,
    t: LumaTheme,
    modifier: Modifier,
    onReorderStart: () -> Unit,
    onReorderBy: (Float) -> Unit,
    onReorderEnd: (commit: Boolean) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val slop = LocalViewConfiguration.current.touchSlop
    val start by rememberUpdatedState(onReorderStart)
    val by by rememberUpdatedState(onReorderBy)
    val end by rememberUpdatedState(onReorderEnd)
    Box(modifier.fillMaxWidth()) {
        AppNameRow(
            entry, vm,
            label = if (t.list.lowercase) entry.label.lowercase() else entry.label,
            color = if (pressed) (t.accent ?: t.fg.copy(alpha = 0.5f)) else t.fg,
            fontSize = t.list.size, fontWeight = t.list.weight, align = t.align,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(entry.key) {
                    detectTapGestures(
                        onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                        onLongPress = { /* handled by the drag detector below */ },
                        onTap = { vm.launch(entry) },
                    )
                }
                .pointerInput(entry.key) {
                    var moved = 0f
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            moved = 0f
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            start()
                        },
                        onDrag = { change, amount -> change.consume(); moved += abs(amount.y); by(amount.y) },
                        // Let go without moving: show the menu instead of reordering.
                        onDragEnd = { if (moved < slop) { end(false); menu = true } else end(true) },
                        onDragCancel = { end(false) },
                    )
                }
                .padding(vertical = t.list.rowPadding),
        )
        AppActionsMenu(menu, { menu = false }, true, entry, vm)
    }
}

@Composable
private fun Clock(t: LumaTheme) {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    val c = t.clock
    val time = now.format(DateTimeFormatter.ofPattern(if (c.use24h) "HH:mm" else "h:mm"))
    val meridiem = now.format(DateTimeFormatter.ofPattern("a")).uppercase()
    val date = now.format(DateTimeFormatter.ofPattern(c.datePattern)).let {
        when (c.dateCase) {
            TextCase.Lower -> it.lowercase()
            TextCase.Upper -> it.uppercase()
            TextCase.Normal -> it
        }
    }
    val dateText = @Composable {
        Text(
            date,
            color = if (c.dateAccent) t.accent ?: t.fg2 else t.fg2,
            fontSize = c.dateSize, fontWeight = c.dateWeight, letterSpacing = c.dateTracking,
        )
    }
    Column(horizontalAlignment = t.align.horizontal) {
        if (c.dateAbove) {
            dateText()
            Spacer(Modifier.height(10.dp))
        }
        Row {
            Text(
                time,
                color = t.fg, fontSize = c.size, fontWeight = c.weight, letterSpacing = c.tracking,
                lineHeight = c.size, fontFamily = t.font, style = TextStyle(fontFeatureSettings = "tnum"),
                modifier = Modifier.alignByBaseline(),
            )
            if (c.meridiem == Meridiem.Inline) {
                Text(
                    meridiem,
                    color = t.fg2, fontSize = 20.sp, fontWeight = FontWeight.Light,
                    modifier = Modifier.alignByBaseline().padding(start = 6.dp),
                )
            }
        }
        if (c.meridiem == Meridiem.Below) {
            Text(
                meridiem,
                color = t.fg2, fontSize = 16.sp, letterSpacing = 0.12.em,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (!c.dateAbove) {
            Spacer(Modifier.height(8.dp))
            dateText()
        }
    }
}

@Composable
fun AppActionsMenu(expanded: Boolean, onDismiss: () -> Unit, pinned: Boolean, entry: AppEntry, vm: LauncherViewModel) {
    LumaMenu(expanded, onDismiss) {
        LumaMenuItem(
            if (pinned) "Unpin from home" else "Pin to home",
            if (pinned) LumaIcons.Unpin else LumaIcons.Add,
            onClick = { vm.togglePin(entry); onDismiss() },
        )
        LumaMenuItem("App info", LumaIcons.Info, onClick = { vm.showAppInfo(entry); onDismiss() })
        LumaMenuItem("Uninstall", LumaIcons.Delete, danger = true, onClick = { vm.uninstall(entry); onDismiss() })
    }
}

// ---- Page indicator --------------------------------------------------------------------------

@Composable
private fun PageIndicator(
    count: Int,
    current: Int,
    homeIndex: Int,
    onSelect: (Int) -> Unit,
    onAddLeft: () -> Unit,
    onAddRight: () -> Unit,
) {
    val t = LocalLuma.current
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("+", color = t.fg2, fontSize = 18.sp, modifier = Modifier.clickable(onClick = onAddLeft).padding(horizontal = 10.dp))
        repeat(count) { i ->
            val active = i == current
            val color = if (active) t.fg else t.fg2.copy(alpha = 0.5f)
            if (i == homeIndex) {
                // The home page is a ring, so you can tell where it is among the widget pages.
                Box(
                    Modifier
                        .padding(horizontal = 6.dp)
                        .size(11.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, color, CircleShape)
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (active) Box(Modifier.size(5.dp).clip(CircleShape).background(color))
                }
            } else {
                Box(
                    Modifier
                        .padding(horizontal = 6.dp)
                        .size(if (active) 9.dp else 7.dp)
                        .clip(CircleShape)
                        .background(color)
                        .clickable { onSelect(i) },
                )
            }
        }
        Text("+", color = t.fg2, fontSize = 18.sp, modifier = Modifier.clickable(onClick = onAddRight).padding(horizontal = 10.dp))
    }
}

/** The theme's text shadow is for text drawn over the wallpaper (the home page); overlays turn it off. */
@Composable
fun NoShadow(content: @Composable () -> Unit) {
    androidx.compose.material3.ProvideTextStyle(TextStyle(shadow = androidx.compose.ui.graphics.Shadow.None), content)
}

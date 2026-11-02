package dev.lumen.launcher

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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.mutableStateOf
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
    onPickWidget: (page: Int, info: AppWidgetProviderInfo, cols: Int?, rows: Int?) -> Unit,
    onReleaseWidget: (Int) -> Unit,
    onConfigureWidget: (Int) -> Unit,
) {
    val pagerState = rememberPagerState { 1 + vm.pages.size }
    val scope = rememberCoroutineScope()
    var drawerOpen by remember { mutableStateOf(false) }
    var pickerForPage by remember { mutableStateOf<Int?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    val theme = LocalLumen.current

    LaunchedEffect(vm.homeSignal) {
        drawerOpen = false
        pickerForPage = null
        settingsOpen = false
        pagerState.animateScrollToPage(0)
    }

    BackHandler {
        when {
            settingsOpen -> settingsOpen = false
            pickerForPage != null -> pickerForPage = null
            drawerOpen -> drawerOpen = false
            pagerState.currentPage != 0 -> scope.launch { pagerState.animateScrollToPage(0) }
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
                if (acc < -100f) drawerOpen = true
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
        val overlayOpen = drawerOpen || settingsOpen || pickerForPage != null
        Column(
            Modifier
                .fillMaxSize()
                .alpha(if (overlayOpen && theme.background == null) 0f else 1f)
                .nestedScroll(openDrawerOnSwipeUp),
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                if (page == 0) {
                    HomePage(vm, onOpenSettings = { settingsOpen = true })
                } else {
                    WidgetPage(
                        vm = vm,
                        pageIndex = page - 1,
                        widgetHost = widgetHost,
                        widgetManager = widgetManager,
                        onAddWidget = { pickerForPage = page - 1 },
                        onRemovePage = { idx -> vm.removePage(idx).forEach(onReleaseWidget) },
                        onConfigureWidget = onConfigureWidget,
                    )
                }
            }
            PageIndicator(
                count = pagerState.pageCount,
                current = pagerState.currentPage,
                onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
                onAddPage = {
                    val idx = vm.addPage()
                    scope.launch { pagerState.animateScrollToPage(idx + 1) }
                },
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
                    onLaunch = { vm.launch(it); drawerOpen = false },
                    onClose = { drawerOpen = false },
                )
            }
        }

        pickerForPage?.let { page ->
            NoShadow {
                WidgetPicker(
                    onPick = { info, cols, rows -> pickerForPage = null; onPickWidget(page, info, cols, rows) },
                    onDismiss = { pickerForPage = null },
                )
            }
        }

        if (settingsOpen) NoShadow { SettingsScreen(vm, onClose = { settingsOpen = false }) }
    }
}

// ---- Home page (Niagara-style) ---------------------------------------------------------------

@Composable
private fun HomePage(vm: LauncherViewModel, onOpenSettings: () -> Unit) {
    val t = LocalLumen.current
    val density = LocalDensity.current
    val pinnedApps = remember(vm.pinned, vm.apps) {
        val byKey = vm.apps.associateBy { it.key }
        vm.pinned.mapNotNull { byKey[it] }
    }
    var pageMenu by remember { mutableStateOf<DpOffset?>(null) }
    BoxWithConstraints(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onLongPress = { p -> pageMenu = with(density) { DpOffset(p.x.toDp(), p.y.toDp()) } })
        },
    ) {
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
                pinnedApps.forEach { entry -> PinnedItem(entry, vm, t) }
                Spacer(Modifier.height(8.dp))
            }
        }
        LumenMenu(
            expanded = pageMenu != null,
            onDismiss = { pageMenu = null },
            offset = pageMenu ?: DpOffset.Zero,
        ) {
            LumenMenuItem("Settings", LumenIcons.Settings, onClick = { pageMenu = null; onOpenSettings() })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedItem(entry: AppEntry, vm: LauncherViewModel, t: LumenTheme) {
    var menu by remember { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(Modifier.fillMaxWidth()) {
        Text(
            if (t.list.lowercase) entry.label.lowercase() else entry.label,
            color = if (pressed) (t.accent ?: t.fg.copy(alpha = 0.5f)) else t.fg,
            fontSize = t.list.size,
            fontWeight = t.list.weight,
            textAlign = t.align.text,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = source,
                    indication = null,
                    onClick = { vm.launch(entry) },
                    onLongClick = { menu = true },
                )
                .padding(vertical = t.list.rowPadding),
        )
        AppActionsMenu(menu, { menu = false }, true, entry, vm)
    }
}

@Composable
private fun Clock(t: LumenTheme) {
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
    LumenMenu(expanded, onDismiss) {
        LumenMenuItem(
            if (pinned) "Unpin from home" else "Pin to home",
            if (pinned) LumenIcons.Unpin else LumenIcons.Add,
            onClick = { vm.togglePin(entry); onDismiss() },
        )
        LumenMenuItem("App info", LumenIcons.Info, onClick = { vm.showAppInfo(entry); onDismiss() })
        LumenMenuItem("Uninstall", LumenIcons.Delete, danger = true, onClick = { vm.uninstall(entry); onDismiss() })
    }
}

// ---- Page indicator --------------------------------------------------------------------------

@Composable
private fun PageIndicator(count: Int, current: Int, onSelect: (Int) -> Unit, onAddPage: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .padding(horizontal = 6.dp)
                    .size(if (i == current) 9.dp else 7.dp)
                    .clip(CircleShape)
                    .background(if (i == current) LocalLumen.current.fg else LocalLumen.current.fg2.copy(alpha = 0.5f))
                    .clickable { onSelect(i) },
            )
        }
        Text(
            "+", color = LocalLumen.current.fg2, fontSize = 18.sp,
            modifier = Modifier.clickable(onClick = onAddPage).padding(horizontal = 10.dp),
        )
    }
}

/** The theme's text shadow is for text drawn over the wallpaper (the home page); overlays turn it off. */
@Composable
fun NoShadow(content: @Composable () -> Unit) {
    androidx.compose.material3.ProvideTextStyle(TextStyle(shadow = androidx.compose.ui.graphics.Shadow.None), content)
}

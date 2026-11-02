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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
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
    onPickWidget: (page: Int, info: AppWidgetProviderInfo) -> Unit,
    onReleaseWidget: (Int) -> Unit,
) {
    val pagerState = rememberPagerState { 1 + vm.pages.size }
    val scope = rememberCoroutineScope()
    var drawerOpen by remember { mutableStateOf(false) }
    var pickerForPage by remember { mutableStateOf<Int?>(null) }
    var editMode by remember { mutableStateOf(false) }

    LaunchedEffect(vm.homeSignal) {
        drawerOpen = false
        pickerForPage = null
        editMode = false
        pagerState.animateScrollToPage(0)
    }
    LaunchedEffect(pagerState.currentPage) { editMode = false }

    BackHandler {
        when {
            pickerForPage != null -> pickerForPage = null
            drawerOpen -> drawerOpen = false
            editMode -> editMode = false
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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize().nestedScroll(openDrawerOnSwipeUp)) {
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                if (page == 0) {
                    HomePage(vm)
                } else {
                    WidgetPage(
                        vm = vm,
                        pageIndex = page - 1,
                        widgetHost = widgetHost,
                        widgetManager = widgetManager,
                        editMode = editMode,
                        onToggleEdit = { editMode = !editMode },
                        onAddWidget = { pickerForPage = page - 1 },
                        onRemovePage = { idx ->
                            vm.removePage(idx).forEach(onReleaseWidget)
                            editMode = false
                        },
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
            AppDrawer(
                vm = vm,
                onLaunch = { vm.launch(it); drawerOpen = false },
                onClose = { drawerOpen = false },
            )
        }

        pickerForPage?.let { page ->
            WidgetPicker(
                onPick = { info -> pickerForPage = null; onPickWidget(page, info) },
                onDismiss = { pickerForPage = null },
            )
        }
    }
}

// ---- Home page (Niagara-style) ---------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomePage(vm: LauncherViewModel) {
    val pinnedApps = remember(vm.pinned, vm.apps) {
        val byKey = vm.apps.associateBy { it.key }
        vm.pinned.mapNotNull { byKey[it] }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullHeight = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(
                Modifier.height(fullHeight).statusBarsPadding().padding(horizontal = 32.dp, vertical = 24.dp),
            ) {
                Clock()
                Spacer(Modifier.weight(1f))
                if (pinnedApps.isEmpty()) {
                    Text(
                        "Swipe up for all apps.\nLong-press an app to pin it here.",
                        color = Color.Gray, fontSize = 15.sp,
                    )
                }
                pinnedApps.forEach { entry ->
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        Text(
                            entry.label,
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Light,
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { vm.launch(entry) },
                                    onLongClick = { menu = true },
                                )
                                .padding(vertical = 10.dp),
                        )
                        AppActionsMenu(menu, { menu = false }, true, entry, vm)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun Clock() {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    Column {
        Text(
            now.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
            color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.ExtraLight,
        )
        Text(
            now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
            color = Color(0xFFAAAAAA), fontSize = 16.sp,
        )
    }
}

@Composable
fun AppActionsMenu(expanded: Boolean, onDismiss: () -> Unit, pinned: Boolean, entry: AppEntry, vm: LauncherViewModel) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(if (pinned) "Unpin from home" else "Pin to home") },
            onClick = { vm.togglePin(entry); onDismiss() },
        )
        DropdownMenuItem(text = { Text("App info") }, onClick = { vm.showAppInfo(entry); onDismiss() })
        DropdownMenuItem(text = { Text("Uninstall") }, onClick = { vm.uninstall(entry); onDismiss() })
    }
}

// ---- Widget pages ----------------------------------------------------------------------------

@Composable
private fun WidgetPage(
    vm: LauncherViewModel,
    pageIndex: Int,
    widgetHost: AppWidgetHost,
    widgetManager: AppWidgetManager,
    editMode: Boolean,
    onToggleEdit: () -> Unit,
    onAddWidget: () -> Unit,
    onRemovePage: (Int) -> Unit,
) {
    val widgets = vm.pages.getOrNull(pageIndex) ?: return
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onToggleEdit() }) }
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        widgets.forEach { item ->
            val info = remember(item.id) { widgetManager.getAppWidgetInfo(item.id) }
            Box(Modifier.fillMaxWidth().height(item.heightDp.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))) {
                if (info != null) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx -> widgetHost.createView(ctx, item.id, info) },
                        update = { v ->
                            v.applySize(screenWidthDp - 24, item.heightDp)
                        },
                    )
                } else {
                    Text("Widget unavailable", color = Color.Gray, modifier = Modifier.padding(16.dp))
                }
                if (editMode) {
                    Box(Modifier.fillMaxSize().background(Color(0xCC000000)).clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                    ) {})
                    Row(
                        Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        EditAction("Shorter") { vm.resizeWidget(pageIndex, item.id, -40) }
                        EditAction("Taller") { vm.resizeWidget(pageIndex, item.id, 40) }
                        EditAction("Remove") {
                            vm.removeWidget(pageIndex, item.id)
                            widgetHost.deleteAppWidgetId(item.id)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            EditAction("+ Add widget", onAddWidget)
            EditAction(if (editMode) "Done" else "Edit", onToggleEdit)
            if (editMode) EditAction("Remove page") { onRemovePage(pageIndex) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EditAction(label: String, onClick: () -> Unit) {
    Text(
        label, color = Color.White, fontSize = 15.sp,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
    )
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
                    .background(if (i == current) Color.White else Color(0xFF555555))
                    .clickable { onSelect(i) },
            )
        }
        Text(
            "+", color = Color(0xFF888888), fontSize = 18.sp,
            modifier = Modifier.clickable(onClick = onAddPage).padding(horizontal = 10.dp),
        )
    }
}

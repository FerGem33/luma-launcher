package dev.luma.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Demo { None, SwipeUp, RightThird, Sideways, LongPress, Pause, Launcher }

private class TutorialPage(val demo: Demo, val title: String, val line: String)

private val tutorialPages = listOf(
    TutorialPage(Demo.None, "Luma", "A quick tour. Try each gesture."),
    TutorialPage(Demo.SwipeUp, "Swipe up", "All your apps."),
    TutorialPage(Demo.RightThird, "Search faster", "Swipe up from the right third."),
    TutorialPage(Demo.Sideways, "Swipe sideways", "Widgets live on pages beside home."),
    TutorialPage(Demo.LongPress, "Long-press an app", "Pin it, reorder it, see its info."),
    TutorialPage(Demo.Pause, "A pause", "Distracting apps make you wait."),
    TutorialPage(Demo.Launcher, "Make Luma home", "Then the Home button brings you here."),
)

/**
 * The first-run tour: a few pages in the theme's own look, each with a small phone you can try the gesture on.
 * Pages change with Next (swiping the pager would clash with the sideways demo). Skippable, and replayable
 * from Settings.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val t = LocalLuma.current
    val pager = rememberPagerState { tutorialPages.size }
    val scope = rememberCoroutineScope()
    val onLast = pager.currentPage == tutorialPages.lastIndex
    // Back steps through the pages, and leaves from the first.
    BackHandler {
        if (pager.currentPage > 0) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } else onFinish()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(t.overlay)
            // swallow touches so nothing leaks to the pages underneath
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        HorizontalPager(state = pager, userScrollEnabled = false, modifier = Modifier.weight(1f)) { index ->
            val page = tutorialPages[index]
            var done by remember { mutableStateOf(false) }
            var hint by remember { mutableStateOf<String?>(null) }
            Column(
                Modifier.fillMaxSize().padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // A fixed-height stage, so the mini phone stays put from page to page.
                if (page.demo != Demo.None && page.demo != Demo.Launcher) {
                    Box(Modifier.height(340.dp), contentAlignment = Alignment.Center) {
                        when (page.demo) {
                            Demo.SwipeUp -> DemoSwipeUp { done = true }
                            Demo.RightThird -> DemoRightThird(onDone = { done = true }, onHint = { hint = it })
                            Demo.Sideways -> DemoSideways { done = true }
                            Demo.LongPress -> DemoLongPress { done = true }
                            Demo.Pause -> DemoPause()
                            else -> {}
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
                Text(page.title, color = t.fg, fontSize = 28.sp, lineHeight = 36.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(
                    if (done) "Got it." else hint ?: page.line,
                    color = if (done) t.accent ?: t.fg else t.fg2,
                    fontSize = 17.sp, lineHeight = 24.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                if (page.demo == Demo.Launcher) {
                    Spacer(Modifier.height(24.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(t.surface)) { DefaultLauncherRow() }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (onLast) "" else "Skip", color = t.fg2, fontSize = 18.sp,
                modifier = Modifier.weight(1f).clickable(enabled = !onLast, onClick = onFinish).padding(vertical = 10.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(tutorialPages.size) { i ->
                    Box(
                        Modifier.size(if (i == pager.currentPage) 9.dp else 7.dp).clip(CircleShape)
                            .background(if (i == pager.currentPage) t.fg else t.fg2.copy(alpha = 0.4f)),
                    )
                }
            }
            Text(
                if (onLast) "Done" else "Next", color = t.fg, fontSize = 20.sp, textAlign = TextAlign.End,
                modifier = Modifier.weight(1f).clickable {
                    if (onLast) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }.padding(vertical = 10.dp),
            )
        }
    }
}

// ---- demos -----------------------------------------------------------------------------------

private const val SWIPE_PX = 80f

/** Reports where a drag started, how far it went, and the size of the area, when it ends. */
@Composable
private fun Modifier.swipeDetect(onSwipe: (start: Offset, delta: Offset, size: IntSize) -> Unit): Modifier {
    val latest = rememberUpdatedState(onSwipe)
    return pointerInput(Unit) {
        var start = Offset.Zero
        var total = Offset.Zero
        detectDragGestures(
            onDragStart = { start = it; total = Offset.Zero },
            onDrag = { change, delta -> change.consume(); total += delta },
            onDragEnd = { latest.value(start, total, size) },
        )
    }
}

/** A small phone to try a gesture on. */
@Composable
private fun PhoneMock(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val t = LocalLuma.current
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .size(190.dp, 330.dp)
            .clip(shape)
            .background(t.surface)
            .border(1.5.dp, t.fg2.copy(alpha = 0.4f), shape),
        content = content,
    )
}

private val mockPinned = listOf("phone", "notes", "camera")

/** The home page, miniature. */
@Composable
private fun MockHome(modifier: Modifier = Modifier, pinned: @Composable (String) -> Unit = { MockWord(it) }) {
    val t = LocalLuma.current
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("12:34", color = t.fg, fontSize = 34.sp)
        Text("fri 06 nov", color = t.accent ?: t.fg2, fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        mockPinned.forEach { pinned(it) }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun MockWord(text: String, modifier: Modifier = Modifier) {
    val t = LocalLuma.current
    Text(text, color = t.fg, fontSize = 15.sp, modifier = modifier.padding(vertical = 4.dp))
}

/** The app drawer, miniature: a short list, a search field, and optionally a keyboard. */
@Composable
private fun MockDrawer(typing: Boolean) {
    val t = LocalLuma.current
    Column(Modifier.fillMaxSize().background(t.menu).padding(16.dp)) {
        listOf("adobe", "amazon", "bank", "brave", "camera").forEach {
            Text(it, color = t.fg, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(14.dp)).background(t.surfaceHigh).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
            Text(if (typing) "br|" else "Search", color = if (typing) t.fg else t.fg2, fontSize = 12.sp)
        }
        if (typing) {
            Spacer(Modifier.height(8.dp))
            repeat(3) { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(if (row == 1) 9 else 10) {
                        Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(3.dp)).background(t.surfaceHigh))
                    }
                }
            }
        }
    }
}

/** A hint that bounces in the direction to swipe. */
@Composable
private fun BoxScope.SwipeHint(arrow: String, alignment: Alignment, horizontal: Boolean = false) {
    val t = LocalLuma.current
    val move by rememberInfiniteTransition(label = "hint").animateFloat(
        0f, -12f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "move",
    )
    Text(
        arrow, color = t.fg2, fontSize = 24.sp,
        modifier = Modifier.align(alignment).padding(18.dp).offset(x = if (horizontal) move.dp else 0.dp, y = if (horizontal) 0.dp else move.dp),
    )
}

@Composable
private fun DemoSwipeUp(onDone: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    PhoneMock(Modifier.swipeDetect { _, d, _ -> if (d.y < -SWIPE_PX && !open) { open = true; onDone() } }) {
        MockHome()
        if (!open) SwipeHint("↑", Alignment.BottomCenter)
        AnimatedVisibility(open, enter = slideInVertically { it } + fadeIn()) { MockDrawer(typing = false) }
    }
}

@Composable
private fun DemoRightThird(onDone: () -> Unit, onHint: (String?) -> Unit) {
    val t = LocalLuma.current
    // 0 closed, 1 opened from elsewhere (plain drawer), 2 opened from the right third (search)
    var state by remember { mutableIntStateOf(0) }
    PhoneMock(
        Modifier.swipeDetect { start, d, size ->
            if (d.y < -SWIPE_PX) {
                state = if (start.x > size.width * 2f / 3f) 2 else 1
                onHint(if (state == 1) "Start in the shaded third." else null)
                if (state == 2) onDone()
            } else if (d.y > SWIPE_PX) { state = 0; onHint(null) }
        },
    ) {
        MockHome()
        // the shaded right third
        Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(190.dp / 3).background((t.accent ?: t.fg2).copy(alpha = 0.16f)))
        if (state == 0) SwipeHint("↑", Alignment.BottomEnd)
        AnimatedVisibility(state != 0, enter = slideInVertically { it } + fadeIn()) { MockDrawer(typing = state == 2) }
    }
}

@Composable
private fun DemoSideways(onDone: () -> Unit) {
    val t = LocalLuma.current
    var page by remember { mutableIntStateOf(1) }
    PhoneMock(
        Modifier.swipeDetect { _, d, _ ->
            if (kotlin.math.abs(d.x) > SWIPE_PX && kotlin.math.abs(d.x) > kotlin.math.abs(d.y)) {
                page = if (d.x < 0) (page + 1).coerceAtMost(2) else (page - 1).coerceAtLeast(0)
                if (page != 1) onDone()
            }
        },
    ) {
        Crossfade(page, label = "page") { p ->
            if (p == 1) MockHome()
            else Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth().height(if (p == 0) 70.dp else 110.dp).clip(RoundedCornerShape(16.dp)).background(t.surfaceHigh))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f).height(70.dp).clip(RoundedCornerShape(16.dp)).background(t.surfaceHigh))
                    Box(Modifier.weight(1f).height(70.dp).clip(RoundedCornerShape(16.dp)).background(t.surfaceHigh))
                }
                Box(Modifier.fillMaxWidth().height(if (p == 0) 100.dp else 60.dp).clip(RoundedCornerShape(16.dp)).background(t.surfaceHigh))
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { i ->
                Box(Modifier.size(if (i == page) 8.dp else 6.dp).clip(CircleShape).background(if (i == page) t.fg else t.fg2.copy(alpha = 0.4f)))
            }
        }
        if (page == 1) SwipeHint("‹", Alignment.CenterEnd, horizontal = true)
    }
}

@Composable
private fun DemoLongPress(onDone: () -> Unit) {
    val t = LocalLuma.current
    var menuFor by remember { mutableStateOf<String?>(null) }
    PhoneMock {
        MockHome(pinned = { word ->
            Column {
                MockWord(
                    word,
                    Modifier.pointerInput(word) {
                        detectTapGestures(onLongPress = { menuFor = word; onDone() }, onTap = { menuFor = null })
                    },
                )
                if (menuFor == word) {
                    Column(
                        Modifier.padding(start = 10.dp, bottom = 4.dp).clip(RoundedCornerShape(10.dp)).background(t.menu).padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        listOf("unpin", "app info", "uninstall").forEach {
                            Text(it, color = if (it == "uninstall") t.fg2 else t.fg, fontSize = 11.sp, modifier = Modifier.padding(vertical = 3.dp))
                        }
                    }
                }
            }
        })
    }
}

/** The pause, miniature: counts down on its own, then offers the way through. */
@Composable
private fun DemoPause() {
    val t = LocalLuma.current
    var left by remember { mutableIntStateOf(3) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            left = if (left == 0) { delay(1500); 3 } else left - 1
        }
    }
    PhoneMock {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center) {
            Text("Open instagram?", color = t.fg, fontSize = 15.sp, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Text(if (left > 0) "Take a breath. $left" else "Still want to?", color = t.fg2, fontSize = 12.sp)
            Spacer(Modifier.height(26.dp))
            Text("Not now", color = t.fg, fontSize = 14.sp)
            Text(
                "Open for 5 min", fontSize = 12.sp,
                color = if (left == 0) t.fg2 else t.fg2.copy(alpha = 0f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

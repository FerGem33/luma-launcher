package dev.luma.launcher

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Seconds a distracting app waits before it can be opened. */
const val FRICTION_SECONDS = 10

/**
 * A quiet full-screen pause shown before a distracting app opens: a countdown, then a choice between
 * opening it anyway and walking away. "Not now" is always available.
 */
@Composable
fun FrictionScreen(entry: AppEntry, vm: LauncherViewModel) {
    val t = LocalLuma.current
    var left by remember(entry.key) { mutableIntStateOf(FRICTION_SECONDS) }
    // The pause can open straight after typing in the drawer's search: put the keyboard away.
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(entry.key) {
        focus.clearFocus()
        keyboard?.hide()
    }
    LaunchedEffect(entry.key) {
        while (left > 0) { delay(1000); left-- }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(t.overlay)
            // swallow touches so nothing leaks to the pages underneath
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = t.align.horizontal,
    ) {
        Text(stringResource(R.string.pause_kicker), color = t.fg2, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.open_app_question, entry.label), color = t.fg, fontSize = 28.sp, textAlign = t.align.text)
        Spacer(Modifier.height(12.dp))
        Text(
            if (left > 0) stringResource(R.string.take_a_breath, left) else stringResource(R.string.still_want),
            color = t.fg2, fontSize = 18.sp, textAlign = t.align.text,
        )
        Spacer(Modifier.height(48.dp))
        Text(
            stringResource(R.string.not_now), color = t.fg, fontSize = 22.sp,
            modifier = Modifier.clickable { vm.dismissFriction() }.padding(vertical = 12.dp),
        )
        if (left == 0) {
            Text(
                stringResource(R.string.open_for_minutes, vm.graceMinutes), color = t.fg2, fontSize = 18.sp,
                modifier = Modifier.clickable { vm.passFriction(entry) }.padding(vertical = 12.dp),
            )
        }
    }
}

package dev.luma.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * An app's icon, loaded off the main thread and cached; null until it is ready.
 * The state is created per app: a row that is reused for another app (after an uninstall, or as the search
 * list changes) must not keep showing the previous app's icon while the new one loads.
 */
@Composable
fun rememberAppIcon(vm: LauncherViewModel, entry: AppEntry): ImageBitmap? {
    val icon = remember(entry.key) { mutableStateOf(vm.cachedIcon(entry)) }
    LaunchedEffect(entry.key) {
        if (icon.value == null) icon.value = withContext(Dispatchers.IO) { vm.loadIcon(entry) }
    }
    return icon.value
}

/**
 * An app's name, with its icon beside it when the "App icons" setting is on. The icon sits on the side
 * the theme aligns its text to, so a right-aligned list keeps its right edge straight.
 */
@Composable
fun AppNameRow(
    entry: AppEntry,
    vm: LauncherViewModel,
    label: String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    align: HomeAlign,
    modifier: Modifier = Modifier,
) {
    val showIcon = vm.showIcons
    val icon = if (showIcon) rememberAppIcon(vm, entry) else null
    val iconSize = with(LocalDensity.current) { (fontSize * 1.3f).toDp() }
    val arrangement = when (align) {
        HomeAlign.Start -> Arrangement.Start
        HomeAlign.Center -> Arrangement.Center
        HomeAlign.End -> Arrangement.End
    }
    val iconContent = @Composable {
        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(iconSize))
        else Spacer(Modifier.size(iconSize))
    }
    Row(modifier, horizontalArrangement = arrangement, verticalAlignment = Alignment.CenterVertically) {
        if (showIcon && align != HomeAlign.End) { iconContent(); Spacer(Modifier.width(14.dp)) }
        Text(
            label,
            color = color, fontSize = fontSize, fontWeight = fontWeight,
            textAlign = if (align == HomeAlign.End) TextAlign.End else TextAlign.Start,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (showIcon && align == HomeAlign.End) { Spacer(Modifier.width(14.dp)); iconContent() }
    }
}

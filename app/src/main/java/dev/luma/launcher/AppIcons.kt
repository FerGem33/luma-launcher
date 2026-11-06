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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable

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

/** Corner radius of the common icon shape, as a fraction of the icon's size. */
private const val ICON_CORNER = 0.26f

/**
 * Draws any app icon as a rounded square of [px] pixels, so every icon has the same shape.
 * An adaptive icon keeps its two layers, redrawn inside this mask instead of the maker's own. An older icon
 * (a plain image) is shrunk a little and set on a backdrop of its own average colour.
 */
fun uniformIcon(icon: Drawable, px: Int): Bitmap {
    val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val r = px * ICON_CORNER
    canvas.clipPath(Path().apply { addRoundRect(0f, 0f, px.toFloat(), px.toFloat(), r, r, Path.Direction.CW) })
    if (icon is AdaptiveIconDrawable) {
        // The layers are drawn on a 108-unit canvas of which the middle 72 are shown: fit those 72 to the icon.
        val layer = (px * 108f / 72f).toInt()
        val offset = -(layer - px) / 2
        listOfNotNull(icon.background, icon.foreground).forEach {
            it.setBounds(offset, offset, offset + layer, offset + layer)
            it.draw(canvas)
        }
    } else {
        canvas.drawColor(averageColor(icon))
        val inset = (px * 0.16f).toInt()
        icon.setBounds(inset, inset, px - inset, px - inset)
        icon.draw(canvas)
    }
    return out
}

/** The average colour of an icon's visible (non-transparent) pixels; mid grey if it has none. */
private fun averageColor(icon: Drawable): Int {
    val size = 24
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    icon.setBounds(0, 0, size, size)
    icon.draw(Canvas(bmp))
    var r = 0L; var g = 0L; var b = 0L; var n = 0L
    for (y in 0 until size) for (x in 0 until size) {
        val c = bmp.getPixel(x, y)
        if ((c ushr 24) > 128) {
            r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
        }
    }
    bmp.recycle()
    return if (n == 0L) 0xFF808080.toInt()
    else (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
}

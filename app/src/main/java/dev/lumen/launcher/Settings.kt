package dev.lumen.launcher

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Full-screen settings. For now: pick one of the predefined themes. */
@Composable
fun SettingsScreen(vm: LauncherViewModel, onClose: () -> Unit) {
    val t = LocalLumen.current
    Column(
        Modifier
            .fillMaxSize()
            .background(t.overlay)
            // swallow touches so nothing leaks to the pages underneath
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", color = t.fg, fontSize = 22.sp, modifier = Modifier.weight(1f))
            Text("Close", color = t.fg2, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onClose))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Text("Theme", color = t.fg2, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LumenThemes.forEach { option ->
                    ThemeCard(option, selected = option.id == vm.themeId) { vm.setTheme(option.id) }
                }
            }
            if (vm.themeId == CustomTheme.id) {
                Spacer(Modifier.height(24.dp))
                WallpaperSection(vm)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ThemeCard(option: LumenTheme, selected: Boolean, onClick: () -> Unit) {
    val t = LocalLumen.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(t.surface)
            .border(2.dp, if (selected) t.accent ?: t.fg else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ThemeThumbnail(option)
        Column(Modifier.weight(1f)) {
            Text(option.name, color = t.fg, fontSize = 18.sp)
            Text(option.tagline, color = t.fg2, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** A tiny home page drawn with the theme's own fonts, colours and alignment. */
@Composable
private fun ThemeThumbnail(o: LumenTheme) {
    Box(
        Modifier
            .size(72.dp, 128.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(o.swatch),
    ) {
        if (o.background == null && o.scrim > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = o.scrim)))
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 10.dp), horizontalAlignment = o.align.horizontal) {
            Text(
                if (o.clock.use24h) "13:24" else "1:24",
                color = o.fg, fontSize = 16.sp, fontFamily = o.font,
                fontWeight = if (o.clock.weight.weight <= 300) FontWeight.Light else o.clock.weight,
            )
            Text("mon 02 nov", color = o.accent ?: o.fg2, fontSize = 6.sp, fontFamily = o.font)
            Spacer(Modifier.weight(1f))
            listOf("phone", "notes", "camera").forEach { label ->
                Text(label, color = o.fg, fontSize = 9.sp, fontFamily = o.font, modifier = Modifier.padding(vertical = 1.dp))
            }
        }
    }
}

/** Wallpaper choice for the Custom theme: one image, or a collection shown one per day. */
@Composable
private fun WallpaperSection(vm: LauncherViewModel) {
    val t = LocalLumen.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(12)) { uris ->
        vm.addWallpapers(uris)
    }
    Text("Wallpaper", color = t.fg2, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        vm.wallpapers.forEach { file -> WallpaperThumb(file) { vm.removeWallpaper(file) } }
        Box(
            Modifier
                .size(72.dp, 128.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, t.fg2.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(LumenIcons.Add, contentDescription = "Add images", tint = t.fg, modifier = Modifier.size(28.dp))
        }
    }
    Text(
        if (vm.wallpapers.isEmpty()) "No image chosen: your system wallpaper is used."
        else if (vm.wallpapers.size == 1) "Tap an image's cross to remove it, or add more."
        else "With several images, one is shown each day.",
        color = t.fg2, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun WallpaperThumb(file: java.io.File, onRemove: () -> Unit) {
    val t = LocalLumen.current
    val image = rememberImage(file, 400)
    Box(Modifier.size(72.dp, 128.dp).clip(RoundedCornerShape(14.dp)).background(t.surfaceHigh)) {
        if (image != null) {
            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(Color(0xCC000000))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(LumenIcons.Unpin, contentDescription = "Remove image", tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

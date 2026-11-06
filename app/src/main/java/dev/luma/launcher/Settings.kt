package dev.luma.launcher

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.window.Dialog
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Switch
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class SettingsPage { Menu, Appearance, Distracting }

/** Full-screen settings: a menu whose first entry leads to the appearance (theme) options. */
@Composable
fun SettingsScreen(vm: LauncherViewModel, onClose: () -> Unit) {
    val t = LocalLuma.current
    var page by remember { mutableStateOf(SettingsPage.Menu) }
    // Back steps out of a sub-page first; this handler is closer to the screen than the launcher's own.
    BackHandler(enabled = page != SettingsPage.Menu) { page = SettingsPage.Menu }

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
            if (page != SettingsPage.Menu) {
                Icon(
                    LumaIcons.Back, contentDescription = "Back", tint = t.fg,
                    modifier = Modifier.size(28.dp).clip(CircleShape).clickable { page = SettingsPage.Menu },
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(
                when (page) {
                    SettingsPage.Menu -> "Settings"
                    SettingsPage.Appearance -> "Appearance"
                    SettingsPage.Distracting -> "Distracting apps"
                },
                color = t.fg, fontSize = 22.sp, modifier = Modifier.weight(1f),
            )
            Text("Close", color = t.fg2, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onClose))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            when (page) {
                SettingsPage.Menu -> SettingsMenu(vm) { page = it }
                SettingsPage.Appearance -> AppearanceSettings(vm)
                SettingsPage.Distracting -> DistractingSettings(vm)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsMenu(vm: LauncherViewModel, open: (SettingsPage) -> Unit) {
    val t = LocalLuma.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(t.surface),
    ) {
        MenuRow(
            title = "Appearance",
            subtitle = if (vm.dayNight) "${themeById(vm.dayThemeId).name} by day, ${themeById(vm.nightThemeId).name} by night" else themeById(vm.themeId).name,
            onClick = { open(SettingsPage.Appearance) },
        ) {
            Icon(LumaIcons.Forward, contentDescription = null, tint = t.fg2, modifier = Modifier.size(24.dp))
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(t.fg2.copy(alpha = 0.2f)))
        MenuRow(
            title = "Distracting apps",
            subtitle = if (vm.distracting.isEmpty()) "None marked" else "${vm.distracting.size} wait behind a pause",
            onClick = { open(SettingsPage.Distracting) },
        ) {
            Icon(LumaIcons.Forward, contentDescription = null, tint = t.fg2, modifier = Modifier.size(24.dp))
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(t.fg2.copy(alpha = 0.2f)))
        DefaultLauncherRow()
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(t.fg2.copy(alpha = 0.2f)))
        MenuRow(
            title = "App icons",
            subtitle = if (vm.showIcons) "Shown next to app names" else "Hidden: names only",
            onClick = { vm.updateShowIcons(!vm.showIcons) },
        ) {
            Switch(
                checked = vm.showIcons,
                onCheckedChange = { vm.updateShowIcons(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = t.menu,
                    checkedTrackColor = t.accent ?: t.fg,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = t.fg2,
                    uncheckedTrackColor = t.surfaceHigh,
                    uncheckedBorderColor = t.fg2.copy(alpha = 0.5f),
                ),
            )
        }
    }
}

/** Whether Luma is the phone's home app. */
private fun isDefaultHome(c: android.content.Context): Boolean {
    val roles = c.getSystemService(android.app.role.RoleManager::class.java)
    return roles.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)
}

/** Shows whether Luma is the default launcher; tapping it asks Android to make it so. */
@Composable
private fun DefaultLauncherRow() {
    val t = LocalLuma.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var isDefault by remember { mutableStateOf(isDefaultHome(context)) }
    // Re-check on coming back from the system dialog or settings.
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) isDefault = isDefaultHome(context)
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = isDefaultHome(context)
    }
    MenuRow(
        title = "Default launcher",
        subtitle = if (isDefault) "Luma is your home app" else "Not set: tap to make Luma your home app",
        onClick = {
            if (!isDefault) {
                val roles = context.getSystemService(android.app.role.RoleManager::class.java)
                if (roles.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                    request.launch(roles.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME))
                } else {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_HOME_SETTINGS))
                }
            }
        },
    ) {
        if (isDefault) Text("On", color = t.fg2, fontSize = 16.sp)
        else Icon(LumaIcons.Forward, contentDescription = null, tint = t.fg2, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun MenuRow(title: String, subtitle: String, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    val t = LocalLuma.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = t.fg, fontSize = 18.sp)
            Text(subtitle, color = t.fg2, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
        }
        trailing()
    }
}

/** Theme choice (one theme, or a day and a night theme with switch times), and the Custom wallpaper picker. */
@Composable
private fun AppearanceSettings(vm: LauncherViewModel) {
    val t = LocalLuma.current
    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp).clip(RoundedCornerShape(20.dp)).background(t.surface)) {
        MenuRow(
            title = "Day / Night",
            subtitle = if (vm.dayNight) "Theme changes with the time of day" else "One theme all day",
            onClick = { vm.updateDayNight(!vm.dayNight) },
        ) {
            Switch(
                checked = vm.dayNight,
                onCheckedChange = { vm.updateDayNight(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = t.menu,
                    checkedTrackColor = t.accent ?: t.fg,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = t.fg2,
                    uncheckedTrackColor = t.surfaceHigh,
                    uncheckedBorderColor = t.fg2.copy(alpha = 0.5f),
                ),
            )
        }
    }
    if (vm.dayNight) {
        ThemeStrip("Day theme", vm.dayThemeId) { vm.setDayTheme(it) }
        Spacer(Modifier.height(20.dp))
        ThemeStrip("Night theme", vm.nightThemeId) { vm.setNightTheme(it) }
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(t.surface)) {
            TimeRow("Day starts", vm.dayStart) { vm.updateDayStart(it) }
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(t.fg2.copy(alpha = 0.2f)))
            TimeRow("Night starts", vm.nightStart) { vm.updateNightStart(it) }
        }
    } else {
        Text("Theme", color = t.fg2, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LumaThemes.forEach { option ->
                ThemeCard(option, selected = option.id == vm.themeId) { vm.setTheme(option.id) }
            }
        }
    }
    val customChosen = if (vm.dayNight) vm.dayThemeId == CustomTheme.id || vm.nightThemeId == CustomTheme.id else vm.themeId == CustomTheme.id
    if (customChosen) {
        Spacer(Modifier.height(24.dp))
        WallpaperSection(vm)
    }
}

/** A row of theme thumbnails to pick one from. */
@Composable
private fun ThemeStrip(title: String, selectedId: String, onSelect: (String) -> Unit) {
    val t = LocalLuma.current
    Text(title, color = t.fg2, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LumaThemes.forEach { option ->
            val shape = RoundedCornerShape(18.dp)
            Column(
                Modifier
                    .clip(shape)
                    .border(2.dp, if (option.id == selectedId) t.accent ?: t.fg else Color.Transparent, shape)
                    .clickable { onSelect(option.id) }
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ThemeThumbnail(option)
                Text(option.name, color = t.fg, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** A labelled time; tapping it opens the launcher's own time picker (24-hour). */
@Composable
private fun TimeRow(title: String, minute: Int, onChange: (Int) -> Unit) {
    val t = LocalLuma.current
    var picking by remember { mutableStateOf(false) }
    MenuRow(title = title, subtitle = "Tap to change", onClick = { picking = true }) {
        Text(ThemeSchedule.format(minute), color = t.fg, fontSize = 20.sp)
    }
    if (picking) {
        TimePickerSheet(title, minute, onDismiss = { picking = false }, onConfirm = { picking = false; onChange(it) })
    }
}

/** A themed time picker: an hour wheel and a minute wheel that snap to a value, then Cancel / Set. */
@Composable
private fun TimePickerSheet(title: String, initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val t = LocalLuma.current
    var hour by remember { mutableStateOf(initial / 60) }
    var minute by remember { mutableStateOf(initial % 60) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(t.menu).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, color = t.fg2, fontSize = 16.sp, fontFamily = t.font, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeWheel(24, hour) { hour = it }
                Text(":", color = t.fg, fontSize = 36.sp, fontFamily = t.font, modifier = Modifier.padding(horizontal = 8.dp))
                TimeWheel(60, minute) { minute = it }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Cancel", color = t.fg2, fontSize = 18.sp, fontFamily = t.font,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onDismiss).padding(horizontal = 16.dp, vertical = 10.dp),
                )
                Text(
                    "Set", color = t.fg, fontSize = 18.sp, fontFamily = t.font,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onConfirm(hour * 60 + minute) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** A vertical wheel of 0 until [count] showing three values; the one in the middle is selected. */
@Composable
private fun TimeWheel(count: Int, initial: Int, onSelect: (Int) -> Unit) {
    val t = LocalLuma.current
    val itemHeight = 56.dp
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initial)
    val selected by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - center) }?.index ?: initial
        }
    }
    LaunchedEffect(selected) { onSelect(selected) }
    Box(Modifier.size(width = 88.dp, height = itemHeight * 3), contentAlignment = Alignment.Center) {
        // Hairlines around the selected row.
        Column(Modifier.fillMaxWidth().height(itemHeight), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(t.fg2.copy(alpha = 0.35f)))
            Box(Modifier.fillMaxWidth().height(1.dp).background(t.fg2.copy(alpha = 0.35f)))
        }
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = PaddingValues(vertical = itemHeight),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(count) { i ->
                Box(Modifier.fillMaxWidth().height(itemHeight), contentAlignment = Alignment.Center) {
                    Text(
                        "%02d".format(i),
                        color = if (i == selected) t.fg else t.fg2.copy(alpha = 0.6f),
                        fontSize = if (i == selected) 36.sp else 28.sp,
                        fontFamily = t.font,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeCard(option: LumaTheme, selected: Boolean, onClick: () -> Unit) {
    val t = LocalLuma.current
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
private fun ThemeThumbnail(o: LumaTheme) {
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
    val t = LocalLuma.current
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
            Icon(LumaIcons.Add, contentDescription = "Add images", tint = t.fg, modifier = Modifier.size(28.dp))
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
    val t = LocalLuma.current
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
            Icon(LumaIcons.Unpin, contentDescription = "Remove image", tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

/** A checklist of every app; marked ones wait behind a pause screen before they open. */
@Composable
private fun DistractingSettings(vm: LauncherViewModel) {
    val t = LocalLuma.current
    var query by remember { mutableStateOf("") }
    BasicTextField(
        value = query,
        onValueChange = { query = it },
        singleLine = true,
        textStyle = TextStyle(color = t.fg, fontSize = 18.sp, fontFamily = t.font),
        cursorBrush = SolidColor(t.fg),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    .background(t.surface, RoundedCornerShape(24.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                if (query.isEmpty()) Text("Search apps", color = t.fg2, fontSize = 18.sp)
                inner()
            }
        },
    )
    // While searching, the explanation and time settings step aside so results sit right under the field.
    if (query.isEmpty()) {
        Text(
            "Marked apps show a short pause before they open, so opening them is a choice.",
            color = t.fg2, fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp),
        )
        TimeLimitSection(vm)
    }
    val shown = vm.apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    if (shown.isEmpty()) Text("No apps match.", color = t.fg2, fontSize = 14.sp)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(t.surface)) {
        shown.forEach { entry ->
            val marked = entry.key in vm.distracting
            Row(
                Modifier.fillMaxWidth().clickable { vm.toggleDistracting(entry) }.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(entry.label, color = t.fg, fontSize = 17.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = marked,
                    onCheckedChange = { vm.toggleDistracting(entry) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = t.menu,
                        checkedTrackColor = t.accent ?: t.fg,
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = t.fg2,
                        uncheckedTrackColor = t.surfaceHigh,
                        uncheckedBorderColor = t.fg2.copy(alpha = 0.5f),
                    ),
                )
            }
        }
    }
}

/** Minutes allowed per pass, and the switch that lets Luma enforce them. */
@Composable
private fun TimeLimitSection(vm: LauncherViewModel) {
    val t = LocalLuma.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var enabled by remember { mutableStateOf(TimeLimitService.isEnabled(context)) }
    // Re-check when coming back from the system accessibility screen.
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) enabled = TimeLimitService.isEnabled(context)
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val choices = TimeLimit.MINUTE_CHOICES
    val idx = choices.indexOf(vm.graceMinutes).coerceAtLeast(0)
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp).clip(RoundedCornerShape(20.dp)).background(t.surface)) {
        MenuRow(
            title = "Time per pass",
            subtitle = "Open and close freely for this long",
            onClick = {},
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "−", color = if (idx > 0) t.fg else t.fg2.copy(alpha = 0.4f), fontSize = 26.sp,
                    modifier = Modifier.clickable(enabled = idx > 0) { vm.updateGraceMinutes(choices[idx - 1]) }.padding(12.dp),
                )
                Text("${choices[idx]} min", color = t.fg, fontSize = 17.sp)
                Text(
                    "+", color = if (idx < choices.lastIndex) t.fg else t.fg2.copy(alpha = 0.4f), fontSize = 26.sp,
                    modifier = Modifier.clickable(enabled = idx < choices.lastIndex) { vm.updateGraceMinutes(choices[idx + 1]) }.padding(12.dp),
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(t.fg2.copy(alpha = 0.2f)))
        MenuRow(
            title = "Send me home when time is up",
            subtitle = if (enabled) "On: Luma's accessibility service is running" else "Off: tap to turn on in Accessibility settings",
            onClick = {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
        ) {
            Icon(LumaIcons.Forward, contentDescription = null, tint = t.fg2, modifier = Modifier.size(24.dp))
        }
    }
}

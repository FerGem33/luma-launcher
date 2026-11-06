package dev.luma.launcher

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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

private enum class SettingsPage { Menu, Appearance, Distracting, TimeLimit }

/** Full-screen settings: a menu of grouped entries (Look, Focus, Phone), each leading to its own page. */
@Composable
fun SettingsScreen(vm: LauncherViewModel, onClose: () -> Unit, onOpenTutorial: () -> Unit) {
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
                    LumaIcons.Back, contentDescription = stringResource(R.string.action_back), tint = t.fg,
                    modifier = Modifier.size(28.dp).clip(CircleShape).clickable { page = SettingsPage.Menu },
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(
                when (page) {
                    SettingsPage.Menu -> stringResource(R.string.settings_title)
                    SettingsPage.Appearance -> stringResource(R.string.settings_appearance)
                    SettingsPage.Distracting -> stringResource(R.string.settings_distracting)
                    SettingsPage.TimeLimit -> stringResource(R.string.settings_time_limit)
                },
                color = t.fg, fontSize = 22.sp, modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            Text(stringResource(R.string.action_close), color = t.fg2, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onClose))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            when (page) {
                SettingsPage.Menu -> SettingsMenu(vm, onOpenTutorial) { page = it }
                SettingsPage.Appearance -> AppearanceSettings(vm)
                SettingsPage.Distracting -> DistractingSettings(vm)
                SettingsPage.TimeLimit -> TimeLimitSettings(vm)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsMenu(vm: LauncherViewModel, onOpenTutorial: () -> Unit, open: (SettingsPage) -> Unit) {
    val t = LocalLuma.current
    val arrow = @Composable {
        Icon(LumaIcons.Forward, contentDescription = null, tint = t.fg2, modifier = Modifier.size(24.dp))
    }
    SectionHeader(stringResource(R.string.section_look))
    SettingsCard {
        MenuRow(
            title = stringResource(R.string.settings_appearance),
            subtitle = if (vm.dayNight) stringResource(R.string.appearance_day_night_summary, stringResource(themeById(vm.dayThemeId).nameRes), stringResource(themeById(vm.nightThemeId).nameRes)) else stringResource(themeById(vm.themeId).nameRes),
            onClick = { open(SettingsPage.Appearance) },
            trailing = arrow,
        )
    }
    SectionHeader(stringResource(R.string.section_focus))
    SettingsCard {
        MenuRow(
            title = stringResource(R.string.settings_distracting),
            subtitle = if (vm.distracting.isEmpty()) stringResource(R.string.distracting_none) else pluralStringResource(R.plurals.distracting_count, vm.distracting.size, vm.distracting.size),
            onClick = { open(SettingsPage.Distracting) },
            trailing = arrow,
        )
        Divider()
        MenuRow(
            title = stringResource(R.string.settings_time_limit),
            subtitle = pluralStringResource(R.plurals.time_limit_summary, vm.graceMinutes, vm.graceMinutes),
            onClick = { open(SettingsPage.TimeLimit) },
            trailing = arrow,
        )
    }
    SectionHeader(stringResource(R.string.section_phone))
    SettingsCard {
        DefaultLauncherRow()
        Divider()
        MenuRow(title = stringResource(R.string.how_it_works), subtitle = stringResource(R.string.how_it_works_sub), onClick = onOpenTutorial, trailing = arrow)
    }
}

/** A small heading above a group of settings. */
@Composable
private fun SectionHeader(title: String) {
    val t = LocalLuma.current
    Text(title, color = t.fg2, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp))
}

/** The rounded panel a group of rows sits on. */
@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    val t = LocalLuma.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(t.surface)) { content() }
}

@Composable
private fun Divider() {
    val t = LocalLuma.current
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(t.fg2.copy(alpha = 0.2f)))
}

@Composable
private fun lumaSwitchColors(): androidx.compose.material3.SwitchColors {
    val t = LocalLuma.current
    return SwitchDefaults.colors(
        checkedThumbColor = t.menu,
        checkedTrackColor = t.accent ?: t.fg,
        checkedBorderColor = Color.Transparent,
        uncheckedThumbColor = t.fg2,
        uncheckedTrackColor = t.surfaceHigh,
        uncheckedBorderColor = t.fg2.copy(alpha = 0.5f),
    )
}

/** Whether Luma is the phone's home app. */
private fun isDefaultHome(c: android.content.Context): Boolean {
    val roles = c.getSystemService(android.app.role.RoleManager::class.java)
    return roles.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)
}

/** Shows whether Luma is the default launcher; tapping it asks Android to make it so. */
@Composable
internal fun DefaultLauncherRow() {
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
        title = stringResource(R.string.default_launcher),
        subtitle = if (isDefault) stringResource(R.string.default_launcher_on) else stringResource(R.string.default_launcher_off),
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
        if (isDefault) Text(stringResource(R.string.action_on), color = t.fg2, fontSize = 16.sp)
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
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
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
    SettingsCard {
        MenuRow(
            title = stringResource(R.string.daynight_title),
            subtitle = if (vm.dayNight) stringResource(R.string.daynight_on) else stringResource(R.string.daynight_off),
            onClick = { vm.updateDayNight(!vm.dayNight) },
        ) {
            Switch(
                checked = vm.dayNight,
                onCheckedChange = { vm.updateDayNight(it) },
                colors = lumaSwitchColors(),
            )
        }
        Divider()
        MenuRow(
            title = stringResource(R.string.app_icons),
            subtitle = if (vm.showIcons) stringResource(R.string.app_icons_on) else stringResource(R.string.app_icons_off),
            onClick = { vm.updateShowIcons(!vm.showIcons) },
        ) {
            Switch(checked = vm.showIcons, onCheckedChange = { vm.updateShowIcons(it) }, colors = lumaSwitchColors())
        }
    }
    Spacer(Modifier.height(20.dp))
    if (vm.dayNight) {
        ThemeStrip(stringResource(R.string.day_theme), vm.dayThemeId) { vm.setDayTheme(it) }
        Spacer(Modifier.height(20.dp))
        ThemeStrip(stringResource(R.string.night_theme), vm.nightThemeId) { vm.setNightTheme(it) }
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(t.surface)) {
            TimeRow(stringResource(R.string.day_starts), vm.dayStart) { vm.updateDayStart(it) }
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(t.fg2.copy(alpha = 0.2f)))
            TimeRow(stringResource(R.string.night_starts), vm.nightStart) { vm.updateNightStart(it) }
        }
    } else {
        Text(stringResource(R.string.theme_label), color = t.fg2, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
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
                Text(stringResource(option.nameRes), color = t.fg, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** A labelled time; tapping it opens the launcher's own time picker (24-hour). */
@Composable
private fun TimeRow(title: String, minute: Int, onChange: (Int) -> Unit) {
    val t = LocalLuma.current
    var picking by remember { mutableStateOf(false) }
    MenuRow(title = title, subtitle = stringResource(R.string.tap_to_change), onClick = { picking = true }) {
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
                    stringResource(R.string.action_cancel), color = t.fg2, fontSize = 18.sp, fontFamily = t.font,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onDismiss).padding(horizontal = 16.dp, vertical = 10.dp),
                )
                Text(
                    stringResource(R.string.action_set), color = t.fg, fontSize = 18.sp, fontFamily = t.font,
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
            Text(stringResource(option.nameRes), color = t.fg, fontSize = 18.sp)
            Text(stringResource(option.taglineRes), color = t.fg2, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
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
            Text(stringResource(R.string.sample_date), color = o.accent ?: o.fg2, fontSize = 6.sp, fontFamily = o.font)
            Spacer(Modifier.weight(1f))
            listOf(R.string.sample_phone, R.string.sample_notes, R.string.sample_camera).forEach { labelRes ->
                val label = stringResource(labelRes)
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
    Text(stringResource(R.string.wallpaper), color = t.fg2, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
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
            Icon(LumaIcons.Add, contentDescription = stringResource(R.string.add_images), tint = t.fg, modifier = Modifier.size(28.dp))
        }
    }
    Text(
        if (vm.wallpapers.isEmpty()) stringResource(R.string.wallpaper_none)
        else if (vm.wallpapers.size == 1) stringResource(R.string.wallpaper_one)
        else stringResource(R.string.wallpaper_many),
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
            Icon(LumaIcons.Unpin, contentDescription = stringResource(R.string.remove_image), tint = Color.White, modifier = Modifier.size(16.dp))
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
                if (query.isEmpty()) Text(stringResource(R.string.search_apps), color = t.fg2, fontSize = 18.sp)
                inner()
            }
        },
    )
    // While searching, the explanation steps aside so results sit right under the field.
    if (query.isEmpty()) {
        Text(
            stringResource(R.string.distracting_explainer),
            color = t.fg2, fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp),
        )
    }
    val shown = vm.apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    if (shown.isEmpty()) Text(stringResource(R.string.no_apps_match), color = t.fg2, fontSize = 14.sp)
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
                    colors = lumaSwitchColors(),
                )
            }
        }
    }
}

/** The time limit page: how long a pause lets a distracting app stay open, and enforcing it. */
@Composable
private fun TimeLimitSettings(vm: LauncherViewModel) {
    val t = LocalLuma.current
    Text(
        stringResource(R.string.time_limit_explainer),
        color = t.fg2, fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp),
    )
    TimeLimitSection(vm)
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
            title = stringResource(R.string.time_per_pass),
            subtitle = stringResource(R.string.time_per_pass_sub),
            onClick = {},
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "−", color = if (idx > 0) t.fg else t.fg2.copy(alpha = 0.4f), fontSize = 26.sp,
                    modifier = Modifier.clickable(enabled = idx > 0) { vm.updateGraceMinutes(choices[idx - 1]) }.padding(12.dp),
                )
                Text(stringResource(R.string.minutes_short, choices[idx]), color = t.fg, fontSize = 17.sp)
                Text(
                    "+", color = if (idx < choices.lastIndex) t.fg else t.fg2.copy(alpha = 0.4f), fontSize = 26.sp,
                    modifier = Modifier.clickable(enabled = idx < choices.lastIndex) { vm.updateGraceMinutes(choices[idx + 1]) }.padding(12.dp),
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(t.fg2.copy(alpha = 0.2f)))
        MenuRow(
            title = stringResource(R.string.send_home_title),
            subtitle = if (enabled) stringResource(R.string.send_home_on) else stringResource(R.string.send_home_off),
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

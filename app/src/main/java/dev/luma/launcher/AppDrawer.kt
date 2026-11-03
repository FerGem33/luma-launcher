package dev.luma.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private sealed interface Row2 {
    data class Header(val letter: Char) : Row2
    data class App(val entry: AppEntry) : Row2
}

private fun letterOf(label: String): Char =
    label.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#'

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawer(vm: LauncherViewModel, onLaunch: (AppEntry) -> Unit, onClose: () -> Unit) {
    val t = LocalLuma.current
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val searching = query.isNotBlank()
    // A new query starts from its best match (nearest the search field), not wherever the old list was scrolled.
    LaunchedEffect(query) { listState.scrollToItem(0) }

    val rows: List<Row2> = remember(vm.apps, query) {
        if (searching) {
            // Names that start with the query come first, then the rest, each in alphabetical order.
            val q = query.trim()
            vm.apps.filter { it.label.contains(q, ignoreCase = true) }
                .sortedBy { !it.label.startsWith(q, ignoreCase = true) }
                .map { Row2.App(it) }
        } else {
            buildList<Row2> {
                var last: Char? = null
                vm.apps.forEach { a ->
                    val l = letterOf(a.label)
                    if (l != last) { add(Row2.Header(l)); last = l }
                    add(Row2.App(a))
                }
            }
        }
    }
    val letterIndex = remember(rows) {
        rows.withIndex().filter { it.value is Row2.Header }.associate { (it.value as Row2.Header).letter to it.index }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(t.overlay)
            // swallow touches so nothing leaks to the pages underneath
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                // While searching, results are anchored just above the search field, best match nearest it.
                reverseLayout = searching,
            ) {
                // Keyed by app, so a row stays with its own app when the list shifts (uninstalls, search).
                items(rows.size, key = { i ->
                    when (val r = rows[i]) {
                        is Row2.Header -> "h:${r.letter}"
                        is Row2.App -> "a:${r.entry.key}"
                    }
                }) { i ->
                    when (val r = rows[i]) {
                        is Row2.Header -> Text(
                            r.letter.toString(), color = t.accent ?: t.fg2, fontSize = 14.sp,
                            fontWeight = FontWeight.Medium, textAlign = t.align.text,
                            modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 12.dp, top = 18.dp, bottom = 2.dp),
                        )
                        is Row2.App -> DrawerItem(r.entry, vm, onLaunch)
                    }
                }
            }
            if (!searching) {
                AlphabetBar(letterIndex.keys.sorted()) { letter ->
                    letterIndex[letter]?.let { idx -> scope.launch { listState.scrollToItem(idx) } }
                }
            }
        }
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = t.fg, fontSize = 18.sp, fontFamily = t.font),
            cursorBrush = SolidColor(t.fg),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                (rows.firstOrNull() as? Row2.App)?.let { onLaunch(it.entry) }
            }),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)
                        .background(t.surface, androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    if (query.isEmpty()) Text("Search apps", color = t.fg2, fontSize = 18.sp)
                    inner()
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerItem(entry: AppEntry, vm: LauncherViewModel, onLaunch: (AppEntry) -> Unit) {
    val t = LocalLuma.current
    var menu by remember { mutableStateOf(false) }
    Box {
        AppNameRow(
            entry, vm,
            label = if (t.list.lowercase) entry.label.lowercase() else entry.label,
            color = t.fg, fontSize = 22.sp, fontWeight = t.list.weight, align = t.align,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { onLaunch(entry) }, onLongClick = { menu = true })
                .padding(start = 32.dp, top = 9.dp, bottom = 9.dp, end = 12.dp),
        )
        AppActionsMenu(menu, { menu = false }, entry.key in vm.pinned, entry, vm)
    }
}

@Composable
private fun AlphabetBar(letters: List<Char>, onLetter: (Char) -> Unit) {
    if (letters.isEmpty()) return
    var heightPx by remember { mutableStateOf(1) }
    fun pick(y: Float) {
        val i = ((y / heightPx) * letters.size).toInt().coerceIn(0, letters.size - 1)
        onLetter(letters[i])
    }
    Column(
        Modifier
            .width(32.dp)
            .fillMaxHeight()
            .pointerInput(letters) {
                detectDragGestures(
                    onDragStart = { pick(it.y) },
                    onDrag = { change, _ -> change.consume(); pick(change.position.y) },
                )
            }
            .pointerInput(letters) {
                detectTapGestures { pick(it.y) }
            }
            .onSizeChanged { heightPx = it.height },
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { Text(it.toString(), color = LocalLuma.current.fg2, fontSize = 11.sp) }
    }
}

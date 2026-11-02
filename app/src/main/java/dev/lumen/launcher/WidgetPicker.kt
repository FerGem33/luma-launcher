package dev.lumen.launcher

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

@Composable
fun WidgetPicker(onPick: (AppWidgetProviderInfo) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val groups = remember {
        val pm = ctx.packageManager
        AppWidgetManager.getInstance(ctx).installedProviders
            .groupBy { pm.getApplicationLabel(pm.getApplicationInfo(it.provider.packageName, 0)).toString() }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }
    val density = ctx.resources.displayMetrics.density
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val filtered = remember(groups, q) {
        if (q.isEmpty()) groups
        else groups.filterKeys { it.contains(q, ignoreCase = true) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xF2000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(24.dp)) {
            Text("Add widget", color = Color.White, fontSize = 22.sp, modifier = Modifier.weight(1f))
            Text("Cancel", color = Color(0xFF999999), fontSize = 16.sp, modifier = Modifier.clickable(onClick = onDismiss))
        }
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
            cursorBrush = SolidColor(Color.White),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                        .background(Color(0xFF1C1C1E), RoundedCornerShape(24.dp))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    if (query.isEmpty()) Text("Search apps", color = Color(0xFF777777), fontSize = 18.sp)
                    inner()
                }
            },
        )
        LazyColumn(Modifier.weight(1f)) {
            filtered.forEach { (app, providers) ->
                item {
                    Text(app, color = Color(0xFF999999), fontSize = 14.sp,
                        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 6.dp))
                }
                items(providers) { info ->
                    val preview = remember(info) {
                        runCatching { info.loadPreviewImage(ctx, 0)?.toBitmap()?.asImageBitmap() }.getOrNull()
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(info) }.padding(horizontal = 24.dp, vertical = 8.dp),
                    ) {
                        if (preview != null) {
                            Image(
                                preview, null,
                                modifier = Modifier.width(120.dp).heightIn(max = 90.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }
                        Column(Modifier.padding(start = if (preview != null) 16.dp else 0.dp)) {
                            Text(info.loadLabel(ctx.packageManager), color = Color.White, fontSize = 17.sp)
                            Text(
                                "${(info.minWidth / density).toInt()} × ${(info.minHeight / density).toInt()} dp",
                                color = Color(0xFF777777), fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

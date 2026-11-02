package dev.lumen.launcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Lumen's popup menu: flat (no shadow), a thin outline, the theme's own surface and font, and an icon
 * on every row.
 */
@Composable
fun LumenMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    offset: DpOffset = DpOffset.Zero,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalLumen.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = offset,
        shape = RoundedCornerShape(14.dp),
        containerColor = t.menu,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, t.fg2.copy(alpha = 0.3f)),
        content = { NoShadow { Column { content() } } },
    )
}

@Composable
fun LumenMenuItem(label: String, icon: ImageVector, onClick: () -> Unit, danger: Boolean = false) {
    val t = LocalLumen.current
    val color = when {
        danger && t.isLight -> Color(0xFFB3463C)
        danger -> Color(0xFFE48A80)
        else -> t.fg
    }
    DropdownMenuItem(
        text = { Text(if (t.list.lowercase) label.lowercase() else label, color = color, fontSize = 16.sp) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp)) },
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    )
}

/** Icons used by menus and widget actions, in one place. */
object LumenIcons {
    val Add = Icons.Outlined.Add
    val Unpin = Icons.Outlined.Clear
    val Delete = Icons.Outlined.Delete
    val Info = Icons.Outlined.Info
    val Settings = Icons.Outlined.Settings
}

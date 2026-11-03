package dev.luma.launcher

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** Where a widget page's grid is on screen, so a drag that starts elsewhere (the widget picker) can land on it. */
class GridGeometry(val bounds: Rect, val cellPx: Float)

/** The cells a widget being dragged would take, and whether that spot is free. */
class DropPreview(val col: Int, val row: Int, val cols: Int, val rows: Int, val valid: Boolean)

/** A widget being dragged out of the picker, with the pointer's position in window coordinates. */
class PickerDrag(val info: AppWidgetProviderInfo, val variant: SizeVariant?, val cols: Int, val rows: Int, val pointer: Offset)

package dev.lumen.launcher

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.SizeF

/**
 * Widget size options. Modern widgets (notably Jetpack Glance ones) read
 * [AppWidgetManager.OPTION_APPWIDGET_SIZES]; leaving it empty makes them show "Can't show content".
 */
fun widgetOptions(widthDp: Int, heightDp: Int): Bundle = Bundle().apply {
    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        putParcelableArrayList(
            AppWidgetManager.OPTION_APPWIDGET_SIZES,
            arrayListOf(SizeF(widthDp.toFloat(), heightDp.toFloat())),
        )
    }
}

fun AppWidgetHostView.applySize(widthDp: Int, heightDp: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp.toFloat(), heightDp.toFloat())))
    } else {
        updateAppWidgetSize(Bundle(), widthDp, heightDp, widthDp, heightDp)
    }
}

/** Grid cells a widget wants by default (Samsung/AOSP rule of thumb: a cell is ~70dp). */
fun defaultSpan(ctx: Context, info: AppWidgetProviderInfo): Pair<Int, Int> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
        return info.targetCellWidth.coerceIn(1, GRID_COLS) to info.targetCellHeight
    }
    val d = ctx.resources.displayMetrics.density
    val cols = Math.ceil(((info.minWidth / d) + 30) / 70.0).toInt()
    val rows = Math.ceil(((info.minHeight / d) + 30) / 70.0).toInt()
    return cols.coerceIn(1, GRID_COLS) to rows.coerceAtLeast(1)
}

/** Edge length of one widget-grid cell in px, matching [WidgetPage]'s layout (8dp page margins). */
fun gridCellPx(ctx: Context): Float {
    val m = ctx.resources.displayMetrics
    return (m.widthPixels - 16 * m.density) / GRID_COLS
}

/** One of the four widget sizes One UI offers, with the bit Samsung uses for it and its preview attribute. */
class SamsungSize(val bit: Int, val attr: String, val cols: Int, val rows: Int)

/**
 * Inferred from Samsung's own widget XML: `widgetSize` is a bitmask over these sizes, and each size has a
 * `previewLayout…` attribute (e.g. Analog Clock: 0xa = Small+Medium; TV remote: 0x18 = Medium+Large).
 */
val SAMSUNG_SIZES = listOf(
    SamsungSize(2, "previewLayoutSmall", 2, 1),
    SamsungSize(4, "previewLayoutWideSmall", 4, 1),
    SamsungSize(8, "previewLayoutMedium", 2, 2),
    SamsungSize(16, "previewLayoutLarge", 4, 2),
)

/** A size a (Samsung) widget explicitly supports, with a preview layout made for exactly that size. */
data class SizeVariant(val cols: Int, val rows: Int, val previewLayout: Int)

/**
 * Reads the size variants One UI offers for a widget from its (private) provider XML attributes.
 * Empty for widgets that don't declare any, which is every non-Samsung widget.
 */
fun samsungVariants(ctx: Context, info: AppWidgetProviderInfo): List<SizeVariant> = runCatching {
    val pm = ctx.packageManager
    val ri = pm.getReceiverInfo(info.provider, PackageManager.GET_META_DATA)
    val xmlId = ri.metaData?.getInt(AppWidgetManager.META_DATA_APPWIDGET_PROVIDER) ?: 0
    if (xmlId == 0) return emptyList()
    val xp = pm.getXml(info.provider.packageName, xmlId, ri.applicationInfo) ?: return emptyList()
    var mask = 0
    val layouts = mutableMapOf<String, Int>()
    try {
        var ev = xp.eventType
        while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (ev == org.xmlpull.v1.XmlPullParser.START_TAG && xp.name == "appwidget-provider") {
                for (i in 0 until xp.attributeCount) {
                    val name = xp.getAttributeName(i)
                    if (name == "widgetSize") mask = xp.getAttributeIntValue(i, 0)
                    else if (SAMSUNG_SIZES.any { it.attr == name }) layouts[name] = xp.getAttributeResourceValue(i, 0)
                }
                break
            }
            ev = xp.next()
        }
    } finally {
        xp.close()
    }
    SAMSUNG_SIZES
        .filter { mask and it.bit != 0 && (layouts[it.attr] ?: 0) != 0 }
        .map { SizeVariant(it.cols, it.rows, layouts.getValue(it.attr)) }
        .sortedWith(compareBy({ it.cols }, { it.rows }))
}.getOrDefault(emptyList())

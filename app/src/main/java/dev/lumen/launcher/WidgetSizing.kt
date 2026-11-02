package dev.lumen.launcher

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
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

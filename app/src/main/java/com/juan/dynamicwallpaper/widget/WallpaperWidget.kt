package com.juan.dynamicwallpaper.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.juan.dynamicwallpaper.R
import com.juan.dynamicwallpaper.worker.WallpaperWorker

class WallpaperWidget1x1 : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it, R.layout.widget_wallpaper_1x1) }
    }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_NEXT) triggerNext(context)
    }
}

class WallpaperWidget1x2 : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it, R.layout.widget_wallpaper_1x2) }
    }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_NEXT) triggerNext(context)
    }
}

const val ACTION_NEXT = "com.juan.dynamicwallpaper.NEXT_WALLPAPER"

fun triggerNext(context: Context) {
    WorkManager.getInstance(context).enqueue(
        OneTimeWorkRequestBuilder<WallpaperWorker>().build()
    )
}

fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int, layoutRes: Int) {
    val views = RemoteViews(context.packageName, layoutRes)
    val intent1x1 = Intent(context, WallpaperWidget1x1::class.java).apply { action = ACTION_NEXT }
    val intent1x2 = Intent(context, WallpaperWidget1x2::class.java).apply { action = ACTION_NEXT }
    val pi1x1 = PendingIntent.getBroadcast(context, 1, intent1x1, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val pi1x2 = PendingIntent.getBroadcast(context, 2, intent1x2, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val pi = if (layoutRes == R.layout.widget_wallpaper_1x1) pi1x1 else pi1x2
    views.setOnClickPendingIntent(R.id.widget_btn_next, pi)
    appWidgetManager.updateAppWidget(widgetId, views)
}

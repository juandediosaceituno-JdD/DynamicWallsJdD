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

class WallpaperWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_NEXT) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<WallpaperWorker>().build()
            )
        }
    }

    companion object {
        const val ACTION_NEXT = "com.juan.dynamicwallpaper.NEXT_WALLPAPER"

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_wallpaper)
            val intent = Intent(context, WallpaperWidget::class.java).apply {
                action = ACTION_NEXT
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_next, pendingIntent)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}

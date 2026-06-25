package com.juan.dynamicwallpaper.worker

import android.content.Context
import android.content.Intent
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.juan.dynamicwallpaper.data.PreferencesManager

class ScreenOffLauncherWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val prefs = PreferencesManager(applicationContext)
        if (prefs.getIsRunning() && prefs.getInterval() == 0) {
            applicationContext.startForegroundService(
                Intent(applicationContext, ScreenOffService::class.java)
            )
        }
        return Result.success()
    }
}

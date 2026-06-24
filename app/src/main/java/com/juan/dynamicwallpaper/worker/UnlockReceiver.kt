package com.juan.dynamicwallpaper.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.juan.dynamicwallpaper.data.PreferencesManager

class UnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_USER_PRESENT) {
            val prefs = PreferencesManager(context)
            if (prefs.getIsRunning() && prefs.getInterval() == 0) {
                WorkManager.getInstance(context)
                    .enqueue(OneTimeWorkRequestBuilder<WallpaperWorker>().build())
            }
        }
    }
}

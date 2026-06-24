package com.juan.dynamicwallpaper.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import com.juan.dynamicwallpaper.data.PreferencesManager
import com.juan.dynamicwallpaper.ui.scheduleWallpaperWorker
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = PreferencesManager(context)
        if (!prefs.getIsRunning()) return

        val hasSource = when (prefs.getPickerMode()) {
            "photos" -> prefs.getSelectedPhotos().isNotEmpty()
            "album"  -> prefs.getSelectedBucketId() != null
            else     -> prefs.getFolderUri() != null
        }
        if (!hasSource) return

        if (prefs.getInterval() == 0) {
            // Lanzar ScreenOffService con delay via WorkManager para sobrevivir boot
            val request = OneTimeWorkRequestBuilder<ScreenOffLauncherWorker>()
                .setInitialDelay(10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        } else {
            scheduleWallpaperWorker(context, prefs.getInterval())
        }
    }
}

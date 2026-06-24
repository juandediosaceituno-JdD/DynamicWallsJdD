package com.juan.dynamicwallpaper.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.juan.dynamicwallpaper.data.PreferencesManager
import com.juan.dynamicwallpaper.ui.scheduleWallpaperWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = PreferencesManager(context)
            if (!prefs.getIsRunning()) return

            // Verificar que haya alguna fuente configurada
            val hasSource = when (prefs.getPickerMode()) {
                "photos" -> prefs.getSelectedPhotos().isNotEmpty()
                "album"  -> prefs.getSelectedBucketId() != null
                else     -> prefs.getFolderUri() != null
            }
            if (!hasSource) return

            if (prefs.getInterval() == 0) {
                // Modo "al apagar pantalla": lanzar ScreenOffService
                context.startForegroundService(
                    Intent(context, ScreenOffService::class.java)
                )
            } else {
                // Modo periódico: reprogramar WorkManager
                scheduleWallpaperWorker(context, prefs.getInterval())
            }
        }
    }
}

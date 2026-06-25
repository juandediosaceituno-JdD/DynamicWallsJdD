package com.juan.dynamicwallpaper.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.juan.dynamicwallpaper.data.PreferencesManager

class UnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = PreferencesManager(context)
        if (!prefs.getIsRunning() || prefs.getInterval() != 0) return

        when (intent.action) {
            // Al apagar: cambiar solo pantalla de inicio (siempre funciona)
            Intent.ACTION_SCREEN_OFF -> {
                if (prefs.getApplyHome()) {
                    WorkManager.getInstance(context)
                        .enqueue(OneTimeWorkRequestBuilder<WallpaperWorker>().build())
                }
            }
            // Al encender: cambiar pantalla de bloqueo (necesita pantalla activa)
            Intent.ACTION_SCREEN_ON -> {
                if (prefs.getApplyLock()) {
                    WorkManager.getInstance(context)
                        .enqueue(OneTimeWorkRequestBuilder<WallpaperWorker>().build())
                }
            }
        }
    }
}

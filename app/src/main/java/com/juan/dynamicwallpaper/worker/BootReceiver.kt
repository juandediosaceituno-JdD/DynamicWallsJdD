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
            if (prefs.getIsRunning() && prefs.getFolderUri() != null) {
                scheduleWallpaperWorker(context, prefs.getInterval())
            }
        }
    }
}

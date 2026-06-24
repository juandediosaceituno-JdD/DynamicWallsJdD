package com.juan.dynamicwallpaper.worker

import android.app.*
import android.content.*
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.juan.dynamicwallpaper.data.PreferencesManager

class ScreenOffService : Service() {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                val prefs = PreferencesManager(context)
                if (prefs.getIsRunning() && prefs.getInterval() == 0) {
                    WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<WallpaperWorker>().build())
                }
            }
        }
    }
    override fun onCreate() {
        super.onCreate()
        registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        val channelId = "dw_channel"
        val channel = NotificationChannel(channelId, "DynamicWalls", NotificationManager.IMPORTANCE_MIN)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("DynamicWalls activo")
            .setContentText("Cambia foto al apagar pantalla")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setSilent(true).build()
        startForeground(1, notif)
    }
    override fun onDestroy() { super.onDestroy(); try { unregisterReceiver(receiver) } catch (e: Exception) {} }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
}

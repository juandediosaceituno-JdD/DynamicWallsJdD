package com.juan.dynamicwallpaper.worker

import android.app.*
import android.content.*
import android.graphics.*
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.juan.dynamicwallpaper.data.MediaStoreHelper
import com.juan.dynamicwallpaper.data.PreferencesManager
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.*

class ScreenOffService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                val prefs = PreferencesManager(context)
                if (prefs.getIsRunning() && prefs.getInterval() == 0) {
                    scope.launch { applyRandomWallpaper(context, prefs) }
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

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        try { unregisterReceiver(receiver) } catch (e: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun applyRandomWallpaper(context: Context, prefs: PreferencesManager) {
        try {
            val uri: Uri? = when (prefs.getPickerMode()) {
                "photos" -> prefs.getSelectedPhotos().randomOrNull()?.let { Uri.parse(it) }
                "album"  -> prefs.getSelectedBucketId()?.let {
                    MediaStoreHelper.getPhotosFromAlbum(context, it).randomOrNull()
                }
                else -> {
                    val folderUri = prefs.getFolderUri()?.let { Uri.parse(it) } ?: return
                    DocumentFile.fromTreeUri(context, folderUri)
                        ?.listFiles()?.filter { it.isFile && it.type?.startsWith("image/") == true }
                        ?.randomOrNull()?.uri
                }
            }
            uri ?: return
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return
            val wm = android.app.WallpaperManager.getInstance(context)
            val sw = context.resources.displayMetrics.widthPixels
            val sh = context.resources.displayMetrics.heightPixels
            val scale = maxOf(sw.toFloat() / bitmap.width, sh.toFloat() / bitmap.height)
            val scaledW = (bitmap.width * scale).toInt()
            val scaledH = (bitmap.height * scale).toInt()
            val scaled = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
            val result = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(result)
            canvas.drawBitmap(scaled, (sw - scaledW) / 2f, (sh - scaledH) / 2f, null)
            val applyHome = prefs.getApplyHome()
            val applyLock = prefs.getApplyLock()
            // Pequeño delay para transición menos brusca
            kotlinx.coroutines.delay(300)
            when {
                applyHome && applyLock -> wm.setBitmap(result, null, true,
                    android.app.WallpaperManager.FLAG_SYSTEM or android.app.WallpaperManager.FLAG_LOCK)
                applyHome -> wm.setBitmap(result, null, true, android.app.WallpaperManager.FLAG_SYSTEM)
                applyLock -> wm.setBitmap(result, null, true, android.app.WallpaperManager.FLAG_LOCK)
            }
            prefs.saveLastChangedTime(System.currentTimeMillis())
        } catch (e: Exception) {
            android.util.Log.e("ScreenOffService", "Error aplicando wallpaper", e)
        }
    }
}

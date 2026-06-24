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
                if (prefs.getIsRunning()) {
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
            val rawBitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return
            val rotation = context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = androidx.exifinterface.media.ExifInterface(stream)
                when (exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)) {
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90  -> 90f
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            val bitmap = if (rotation != 0f) {
                android.graphics.Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height,
                    android.graphics.Matrix().apply { postRotate(rotation) }, true)
            } else rawBitmap
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
            // Sin delay: el cambio ocurre mientras la pantalla está apagada
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

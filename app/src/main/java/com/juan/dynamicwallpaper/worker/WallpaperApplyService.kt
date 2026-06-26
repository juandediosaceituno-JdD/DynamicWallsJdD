package com.juan.dynamicwallpaper.worker

import android.app.*
import android.content.*
import android.graphics.*
import android.net.Uri
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import com.juan.dynamicwallpaper.data.MediaStoreHelper
import com.juan.dynamicwallpaper.data.PreferencesManager
import kotlinx.coroutines.*

class WallpaperApplyService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Iniciar como foreground para poder cambiar lock screen en Samsung
        val channelId = "dw_apply_channel"
        val channel = NotificationChannel(channelId, "DynamicWalls Apply", NotificationManager.IMPORTANCE_MIN)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Cambiando fondo...")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setSilent(true).build()
        startForeground(2, notif)

        scope.launch {
            try {
                applyWallpapers()
            } finally {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun applyWallpapers() {
        val prefs = PreferencesManager(this)
        val applyHome   = prefs.getApplyHome()
        val applyLock   = prefs.getApplyLock()
        val scalingMode = prefs.getScalingMode()
        val autoAdjust  = prefs.getAutoAdjust()

        if (!applyHome && !applyLock) return

        val wm = WallpaperManager.getInstance(this)
        val sw = resources.displayMetrics.widthPixels
        val sh = resources.displayMetrics.heightPixels

        if (applyHome) {
            val uri = pickImage(prefs, "home") ?: return
            val bmp = loadAndRotateBitmap(uri) ?: return
            val scaled = scaleBitmap(bmp, sw, sh, scalingMode, autoAdjust)
            wm.setBitmap(scaled, null, true, WallpaperManager.FLAG_SYSTEM)
            prefs.saveLastHomeUri(uri.toString())
            Log.d("WallpaperApplyService", "Home aplicado: $uri")
        }

        if (applyHome && applyLock) delay(1000)

        if (applyLock) {
            val uri = pickImage(prefs, "lock") ?: return
            val bmp = loadAndRotateBitmap(uri) ?: return
            val scaled = scaleBitmap(bmp, sw, sh, scalingMode, autoAdjust)
            wm.setBitmap(scaled, null, true, WallpaperManager.FLAG_LOCK)
            prefs.saveLastLockUri(uri.toString())
            Log.d("WallpaperApplyService", "Lock aplicado: $uri")
        }

        val now = System.currentTimeMillis()
        prefs.saveLastChangedTime(now)
        prefs.saveNextChangeTime(now + prefs.getInterval() * 60 * 1000L)
    }

    private fun pickImage(prefs: PreferencesManager, screen: String): Uri? {
        val useIndependent = if (screen == "lock") prefs.getLockIndependent() else false
        val pickerMode = if (useIndependent) prefs.getPickerModeLock() else prefs.getPickerMode()
        return when (pickerMode) {
            "photos" -> {
                val photos = if (useIndependent) prefs.getSelectedPhotosLock() else prefs.getSelectedPhotos()
                if (photos.isEmpty()) null else Uri.parse(photos.random())
            }
            "album" -> {
                val bucketId = (if (useIndependent) prefs.getSelectedBucketIdLock() else prefs.getSelectedBucketId()) ?: return null
                val uris = MediaStoreHelper.getPhotosFromAlbum(this, bucketId)
                if (uris.isEmpty()) null else uris.random()
            }
            else -> {
                val folderUriStr = (if (useIndependent) prefs.getFolderUriLock() else prefs.getFolderUri()) ?: return null
                val docFolder = DocumentFile.fromTreeUri(this, Uri.parse(folderUriStr)) ?: return null
                val images = docFolder.listFiles().filter { it.isFile && it.type?.startsWith("image/") == true }
                if (images.isEmpty()) null else images.random().uri
            }
        }
    }

    private fun loadAndRotateBitmap(uri: Uri): Bitmap? = try {
        val rotation = contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90  -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        if (rotation != 0f) Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation) }, true)
        else bitmap
    } catch (e: Exception) { null }

    private fun scaleBitmap(src: Bitmap, tw: Int, th: Int, mode: String, autoAdjust: Boolean): Bitmap {
        val source = if (autoAdjust) autoAdjustBitmap(src) else src
        val result = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        when (mode) {
            "FILL" -> {
                val scale = maxOf(tw.toFloat() / source.width, th.toFloat() / source.height)
                val sw = (source.width * scale).toInt(); val sh = (source.height * scale).toInt()
                canvas.drawBitmap(Bitmap.createScaledBitmap(source, sw, sh, true), (tw - sw) / 2f, (th - sh) / 2f, paint)
            }
            "FIT" -> {
                val scale = minOf(tw.toFloat() / source.width, th.toFloat() / source.height)
                val sw = (source.width * scale).toInt(); val sh = (source.height * scale).toInt()
                canvas.drawBitmap(Bitmap.createScaledBitmap(source, sw, sh, true), (tw - sw) / 2f, (th - sh) / 2f, paint)
            }
            "STRETCH" -> canvas.drawBitmap(Bitmap.createScaledBitmap(source, tw, th, true), 0f, 0f, paint)
            "NONE" -> canvas.drawBitmap(source, (tw - source.width) / 2f, (th - source.height) / 2f, paint)
        }
        return result
    }

    private fun autoAdjustBitmap(src: Bitmap): Bitmap {
        val pixels = IntArray(src.width * src.height)
        src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
        var totalBrightness = 0L
        for (pixel in pixels) totalBrightness += (0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel)).toLong()
        val avgBrightness = totalBrightness / pixels.size
        if (avgBrightness in 80..180) return src
        val factor = (128.toFloat() / avgBrightness.coerceAtLeast(1).toFloat()).coerceIn(0.5f, 2.0f)
        val adjusted = IntArray(pixels.size)
        for (i in pixels.indices) {
            adjusted[i] = Color.argb(Color.alpha(pixels[i]),
                (Color.red(pixels[i]) * factor).toInt().coerceIn(0, 255),
                (Color.green(pixels[i]) * factor).toInt().coerceIn(0, 255),
                (Color.blue(pixels[i]) * factor).toInt().coerceIn(0, 255))
        }
        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        result.setPixels(adjusted, 0, src.width, 0, 0, src.width, src.height)
        return result
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
    override fun onBind(intent: Intent?): IBinder? = null
}

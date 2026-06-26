package com.juan.dynamicwallpaper.worker

import android.app.WallpaperManager
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.juan.dynamicwallpaper.data.MediaStoreHelper
import com.juan.dynamicwallpaper.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class WallpaperWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            applyWallpapers(context)
            Result.success()
        } catch (e: Exception) {
            Log.e("WallpaperWorker", "Error", e)
            Result.retry()
        }
    }

    companion object {
        suspend fun applyWallpapers(context: Context) {
            val prefs       = PreferencesManager(context)
            val applyHome   = prefs.getApplyHome()
            val applyLock   = prefs.getApplyLock()
            val scalingMode = prefs.getScalingMode()
            val autoAdjust  = prefs.getAutoAdjust()

            if (!applyHome && !applyLock) return

            val wm = WallpaperManager.getInstance(context)
            val sw = context.resources.displayMetrics.widthPixels
            val sh = context.resources.displayMetrics.heightPixels

            if (applyHome) {
                val uri = pickImage(context, prefs, "home") ?: return
                val bmp = loadAndRotateBitmap(context, uri) ?: return
                val scaled = scaleBitmap(bmp, sw, sh, scalingMode, autoAdjust)
                wm.setBitmap(scaled, null, true, WallpaperManager.FLAG_SYSTEM)
                prefs.saveLastHomeUri(uri.toString())
                Log.d("WallpaperWorker", "Home OK: $uri")
            }

            if (applyHome && applyLock) delay(500)

            if (applyLock) {
                val uri = pickImage(context, prefs, "lock") ?: return
                val bmp = loadAndRotateBitmap(context, uri) ?: return
                val scaled = scaleBitmap(bmp, sw, sh, scalingMode, autoAdjust)
                wm.setBitmap(scaled, null, true, WallpaperManager.FLAG_LOCK)
                prefs.saveLastLockUri(uri.toString())
                Log.d("WallpaperWorker", "Lock OK: $uri")
            }

            val now = System.currentTimeMillis()
            prefs.saveLastChangedTime(now)
            prefs.saveNextChangeTime(now + prefs.getInterval() * 60 * 1000L)
        }

        fun pickImage(context: Context, prefs: PreferencesManager, screen: String): Uri? {
            val useIndependent = if (screen == "lock") prefs.getLockIndependent() else false
            val pickerMode = if (useIndependent) prefs.getPickerModeLock() else prefs.getPickerMode()
            return when (pickerMode) {
                "photos" -> {
                    val photos = if (useIndependent) prefs.getSelectedPhotosLock() else prefs.getSelectedPhotos()
                    if (photos.isEmpty()) null else Uri.parse(photos.random())
                }
                "album" -> {
                    val bucketId = (if (useIndependent) prefs.getSelectedBucketIdLock() else prefs.getSelectedBucketId()) ?: return null
                    val uris = MediaStoreHelper.getPhotosFromAlbum(context, bucketId)
                    if (uris.isEmpty()) null else uris.random()
                }
                else -> {
                    val folderUriStr = (if (useIndependent) prefs.getFolderUriLock() else prefs.getFolderUri()) ?: return null
                    val docFolder = DocumentFile.fromTreeUri(context, Uri.parse(folderUriStr)) ?: return null
                    val images = docFolder.listFiles().filter { it.isFile && it.type?.startsWith("image/") == true }
                    if (images.isEmpty()) null else images.random().uri
                }
            }
        }

        fun loadAndRotateBitmap(context: Context, uri: Uri): Bitmap? {
            return try {
                val rotation = context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90  -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
                val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
                if (rotation != 0f) Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation) }, true)
                else bitmap
            } catch (e: Exception) { null }
        }

        fun scaleBitmap(src: Bitmap, tw: Int, th: Int, mode: String, autoAdjust: Boolean): Bitmap {
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

        fun autoAdjustBitmap(src: Bitmap): Bitmap {
            val pixels = IntArray(src.width * src.height)
            src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
            var totalBrightness = 0L
            for (pixel in pixels) totalBrightness += (0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel)).toLong()
            val avgBrightness = totalBrightness / pixels.size
            if (avgBrightness in 80..180) return src
            val factor = (128f / avgBrightness.coerceAtLeast(1).toFloat()).coerceIn(0.5f, 2.0f)
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
    }
}

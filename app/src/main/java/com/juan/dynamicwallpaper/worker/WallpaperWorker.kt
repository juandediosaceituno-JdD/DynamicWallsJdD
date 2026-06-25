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
import kotlinx.coroutines.withContext

class WallpaperWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val prefs       = PreferencesManager(context)
            val applyHome   = prefs.getApplyHome()
            val applyLock   = prefs.getApplyLock()
            val scalingMode = prefs.getScalingMode()
            val pickerMode  = prefs.getPickerMode()

            if (!applyHome && !applyLock) return@withContext Result.success()

            val imageUri: Uri? = when (pickerMode) {
                "photos" -> {
                    val photos = prefs.getSelectedPhotos()
                    if (photos.isEmpty()) return@withContext Result.failure()
                    Uri.parse(photos.random())
                }
                "album" -> {
                    val bucketId = prefs.getSelectedBucketId() ?: return@withContext Result.failure()
                    val uris = MediaStoreHelper.getPhotosFromAlbum(context, bucketId)
                    if (uris.isEmpty()) return@withContext Result.failure()
                    uris.random()
                }
                else -> { // "folder"
                    val folderUriStr = prefs.getFolderUri() ?: return@withContext Result.failure()
                    val folderUri = Uri.parse(folderUriStr)
                    val docFolder = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext Result.failure()
                    val images = docFolder.listFiles().filter { it.isFile && it.type?.startsWith("image/") == true }
                    if (images.isEmpty()) return@withContext Result.failure()
                    images.random().uri
                }
            }

            val bitmap = loadAndRotateBitmap(imageUri ?: return@withContext Result.failure()) ?: return@withContext Result.failure()
            val wm = WallpaperManager.getInstance(context)
            val sw = context.resources.displayMetrics.widthPixels
            val sh = context.resources.displayMetrics.heightPixels
            val final = scaleBitmap(bitmap, sw, sh, scalingMode)

            when {
                applyHome && applyLock -> wm.setBitmap(final)
                applyHome -> wm.setBitmap(final, null, true, WallpaperManager.FLAG_SYSTEM)
                applyLock -> wm.setBitmap(final, null, true, WallpaperManager.FLAG_LOCK)
            }

            val now = System.currentTimeMillis()
            prefs.saveLastChangedTime(now)
            prefs.saveNextChangeTime(now + prefs.getInterval() * 60 * 1000L)
            Log.d("WallpaperWorker", "Wallpaper cambiado OK - modo: $pickerMode")
            Result.success()
        } catch (e: Exception) {
            Log.e("WallpaperWorker", "Error", e)
            Result.retry()
        }
    }

    private fun loadAndRotateBitmap(uri: Uri): Bitmap? {
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
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return null
            if (rotation != 0f) {
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation) }, true)
            } else bitmap
        } catch (e: Exception) { null }
    }

    private fun scaleBitmap(src: Bitmap, tw: Int, th: Int, mode: String): Bitmap {
        val result = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        when (mode) {
            "FILL" -> {
                val scale = maxOf(tw.toFloat() / src.width, th.toFloat() / src.height)
                val sw = (src.width * scale).toInt(); val sh = (src.height * scale).toInt()
                canvas.drawBitmap(Bitmap.createScaledBitmap(src, sw, sh, true), (tw - sw) / 2f, (th - sh) / 2f, paint)
            }
            "FIT" -> {
                val scale = minOf(tw.toFloat() / src.width, th.toFloat() / src.height)
                val sw = (src.width * scale).toInt(); val sh = (src.height * scale).toInt()
                canvas.drawBitmap(Bitmap.createScaledBitmap(src, sw, sh, true), (tw - sw) / 2f, (th - sh) / 2f, paint)
            }
            "STRETCH" -> canvas.drawBitmap(Bitmap.createScaledBitmap(src, tw, th, true), 0f, 0f, paint)
            "NONE"    -> canvas.drawBitmap(src, (tw - src.width) / 2f, (th - src.height) / 2f, paint)
        }
        return result
    }
}

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
            val autoAdjust  = prefs.getAutoAdjust()

            if (!applyHome && !applyLock) return@withContext Result.success()

            val wm = WallpaperManager.getInstance(context)
            val sw = context.resources.displayMetrics.widthPixels
            val sh = context.resources.displayMetrics.heightPixels

            // ── Pantalla de inicio ──────────────────────────────────────────
            if (applyHome) {
                val uri = pickImage(prefs, "home") ?: return@withContext Result.failure()
                val bmp = loadAndRotateBitmap(uri) ?: return@withContext Result.failure()
                val final = scaleBitmap(bmp, sw, sh, scalingMode, autoAdjust)
                wm.setBitmap(final, null, true, WallpaperManager.FLAG_SYSTEM)
            }

            // ── Pantalla de bloqueo ─────────────────────────────────────────
            if (applyLock) {
                val uri = pickImage(prefs, "lock") ?: return@withContext Result.failure()
                val bmp = loadAndRotateBitmap(uri) ?: return@withContext Result.failure()
                val final = scaleBitmap(bmp, sw, sh, scalingMode, autoAdjust)
                wm.setBitmap(final, null, true, WallpaperManager.FLAG_LOCK)
            }

            val now = System.currentTimeMillis()
            prefs.saveLastChangedTime(now)
            prefs.saveNextChangeTime(now + prefs.getInterval() * 60 * 1000L)
            Log.d("WallpaperWorker", "Wallpaper cambiado OK")
            Result.success()
        } catch (e: Exception) {
            Log.e("WallpaperWorker", "Error", e)
            Result.retry()
        }
    }

    private fun pickImage(prefs: PreferencesManager, screen: String): Uri? {
        // Si la pantalla no tiene fuente independiente, usa la fuente principal
        val useIndependent = if (screen == "lock") prefs.getLockIndependent() else false
        val pickerMode = if (useIndependent) prefs.getPickerModeLock() else prefs.getPickerMode()

        return when (pickerMode) {
            "photos" -> {
                val photos = if (useIndependent) prefs.getSelectedPhotosLock() else prefs.getSelectedPhotos()
                if (photos.isEmpty()) null else Uri.parse(photos.random())
            }
            "album" -> {
                val bucketId = (if (useIndependent) prefs.getSelectedBucketIdLock() else prefs.getSelectedBucketId())
                    ?: return null
                val uris = MediaStoreHelper.getPhotosFromAlbum(context, bucketId)
                if (uris.isEmpty()) null else uris.random()
            }
            else -> { // folder
                val folderUriStr = (if (useIndependent) prefs.getFolderUriLock() else prefs.getFolderUri())
                    ?: return null
                val folderUri = Uri.parse(folderUriStr)
                val docFolder = DocumentFile.fromTreeUri(context, folderUri) ?: return null
                val images = docFolder.listFiles().filter { it.isFile && it.type?.startsWith("image/") == true }
                if (images.isEmpty()) null else images.random().uri
            }
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

    private fun scaleBitmap(src: Bitmap, tw: Int, th: Int, mode: String, autoAdjust: Boolean): Bitmap {
        val source = if (autoAdjust) autoAdjustBitmap(src) else src
        val result = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        when (mode) {
            "FILL" -> {
                val scale = maxOf(tw.toFloat() / source.width, th.toFloat() / source.height)
                val sw = (source.width * scale).toInt()
                val sh = (source.height * scale).toInt()
                // Smart crop: centrar en zona de interés
                val focusX = if (autoAdjust) findFocusX(source, sw, tw) else (tw - sw) / 2f
                val focusY = if (autoAdjust) findFocusY(source, sh, th) else (th - sh) / 2f
                canvas.drawBitmap(Bitmap.createScaledBitmap(source, sw, sh, true), focusX, focusY, paint)
            }
            "FIT" -> {
                val scale = minOf(tw.toFloat() / source.width, th.toFloat() / source.height)
                val sw = (source.width * scale).toInt(); val sh = (source.height * scale).toInt()
                canvas.drawBitmap(Bitmap.createScaledBitmap(source, sw, sh, true), (tw - sw) / 2f, (th - sh) / 2f, paint)
            }
            "STRETCH" -> canvas.drawBitmap(Bitmap.createScaledBitmap(source, tw, th, true), 0f, 0f, paint)
            "NONE"    -> canvas.drawBitmap(source, (tw - source.width) / 2f, (th - source.height) / 2f, paint)
        }
        return result
    }

    // ── Autoajuste de brillo ──────────────────────────────────────────────────
    private fun autoAdjustBitmap(src: Bitmap): Bitmap {
        val pixels = IntArray(src.width * src.height)
        src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)

        // Calcular brillo promedio
        var totalBrightness = 0L
        for (pixel in pixels) {
            val r = Color.red(pixel); val g = Color.green(pixel); val b = Color.blue(pixel)
            totalBrightness += (0.299 * r + 0.587 * g + 0.114 * b).toLong()
        }
        val avgBrightness = totalBrightness / pixels.size

        // Solo ajustar si está muy oscura (<80) o muy clara (>180)
        val targetBrightness = 128
        if (avgBrightness in 80..180) return src

        val factor = targetBrightness.toFloat() / avgBrightness.coerceAtLeast(1).toFloat()
        val clampedFactor = factor.coerceIn(0.5f, 2.0f)

        val adjusted = IntArray(pixels.size)
        for (i in pixels.indices) {
            val r = (Color.red(pixels[i]) * clampedFactor).toInt().coerceIn(0, 255)
            val g = (Color.green(pixels[i]) * clampedFactor).toInt().coerceIn(0, 255)
            val b = (Color.blue(pixels[i]) * clampedFactor).toInt().coerceIn(0, 255)
            adjusted[i] = Color.argb(Color.alpha(pixels[i]), r, g, b)
        }

        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        result.setPixels(adjusted, 0, src.width, 0, 0, src.width, src.height)
        return result
    }

    // ── Smart crop: detectar zona más brillante (punto de interés) ────────────
    private fun findFocusX(src: Bitmap, scaledW: Int, targetW: Int): Float {
        if (scaledW <= targetW) return (targetW - scaledW) / 2f
        val sampleW = minOf(src.width, 32)
        val sampleH = minOf(src.height, 32)
        val small = Bitmap.createScaledBitmap(src, sampleW, sampleH, true)
        var maxBrightness = 0f; var focusCol = sampleW / 2
        for (x in 0 until sampleW) {
            var colBrightness = 0f
            for (y in 0 until sampleH) {
                val p = small.getPixel(x, y)
                colBrightness += 0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)
            }
            if (colBrightness > maxBrightness) { maxBrightness = colBrightness; focusCol = x }
        }
        val focusRatio = focusCol.toFloat() / sampleW
        val idealOffset = -(focusRatio * scaledW - targetW / 2f)
        return idealOffset.coerceIn((targetW - scaledW).toFloat(), 0f)
    }

    private fun findFocusY(src: Bitmap, scaledH: Int, targetH: Int): Float {
        if (scaledH <= targetH) return (targetH - scaledH) / 2f
        val sampleW = minOf(src.width, 32)
        val sampleH = minOf(src.height, 32)
        val small = Bitmap.createScaledBitmap(src, sampleW, sampleH, true)
        var maxBrightness = 0f; var focusRow = sampleH / 3 // bias hacia arriba (retratos)
        for (y in 0 until sampleH) {
            var rowBrightness = 0f
            for (x in 0 until sampleW) {
                val p = small.getPixel(x, y)
                rowBrightness += 0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)
            }
            if (rowBrightness > maxBrightness) { maxBrightness = rowBrightness; focusRow = y }
        }
        val focusRatio = focusRow.toFloat() / sampleH
        val idealOffset = -(focusRatio * scaledH - targetH / 2f)
        return idealOffset.coerceIn((targetH - scaledH).toFloat(), 0f)
    }
}

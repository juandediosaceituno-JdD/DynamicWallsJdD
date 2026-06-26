package com.juan.dynamicwallpaper.worker

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class WallpaperWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            Log.d("WallpaperWorker", "Disparando WallpaperApplyService")
            context.startForegroundService(Intent(context, WallpaperApplyService::class.java))
            Result.success()
        } catch (e: Exception) {
            Log.e("WallpaperWorker", "Error iniciando service", e)
            Result.retry()
        }
    }
}

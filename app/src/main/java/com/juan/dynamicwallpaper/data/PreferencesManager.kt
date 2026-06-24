package com.juan.dynamicwallpaper.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("dynamic_walls_prefs", Context.MODE_PRIVATE)

    // Carpeta (DocumentFile)
    fun saveFolderUri(uri: String)    = prefs.edit().putString("folder_uri", uri).apply()
    fun getFolderUri(): String?        = prefs.getString("folder_uri", null)
    fun saveFolderName(name: String)  = prefs.edit().putString("folder_name", name).apply()
    fun getFolderName(): String        = prefs.getString("folder_name", "") ?: ""
    fun savePhotoCount(count: Int)    = prefs.edit().putInt("photo_count", count).apply()
    fun getPhotoCount(): Int           = prefs.getInt("photo_count", 0)

    // Fotos sueltas (URIs separadas por |)
    fun saveSelectedPhotos(uris: List<String>) =
        prefs.edit().putString("selected_photos", uris.joinToString("|")).apply()
    fun getSelectedPhotos(): List<String> =
        prefs.getString("selected_photos", "")?.split("|")?.filter { it.isNotBlank() } ?: emptyList()

    // Álbum MediaStore (Favoritos Samsung y otros álbumes del sistema)
    fun saveSelectedBucketId(id: String)   = prefs.edit().putString("selected_bucket_id", id).apply()
    fun getSelectedBucketId(): String?      = prefs.getString("selected_bucket_id", null)

    // Modo: "folder" | "photos" | "album"
    fun savePickerMode(mode: String)  = prefs.edit().putString("picker_mode", mode).apply()
    fun getPickerMode(): String        = prefs.getString("picker_mode", "folder") ?: "folder"

    // Compat con código anterior
    fun saveUsePhotoPicker(value: Boolean) = savePickerMode(if (value) "photos" else "folder")
    fun getUsePhotoPicker(): Boolean        = getPickerMode() == "photos"

    fun saveInterval(minutes: Int)    = prefs.edit().putInt("interval_minutes", minutes).apply()
    fun getInterval(): Int             = prefs.getInt("interval_minutes", 30)
    fun saveApplyHome(v: Boolean)     = prefs.edit().putBoolean("apply_home", v).apply()
    fun getApplyHome(): Boolean        = prefs.getBoolean("apply_home", true)
    fun saveApplyLock(v: Boolean)     = prefs.edit().putBoolean("apply_lock", v).apply()
    fun getApplyLock(): Boolean        = prefs.getBoolean("apply_lock", false)
    fun saveScalingMode(mode: String) = prefs.edit().putString("scaling_mode", mode).apply()
    fun getScalingMode(): String       = prefs.getString("scaling_mode", "FILL") ?: "FILL"
    fun saveIsRunning(v: Boolean)     = prefs.edit().putBoolean("is_running", v).apply()
    fun getIsRunning(): Boolean        = prefs.getBoolean("is_running", false)
    fun saveLastChangedTime(t: Long)  = prefs.edit().putLong("last_changed_time", t).apply()
    fun getLastChangedTime(): Long     = prefs.getLong("last_changed_time", 0L)
    fun saveNextChangeTime(t: Long)   = prefs.edit().putLong("next_change_time", t).apply()
    fun getNextChangeTime(): Long      = prefs.getLong("next_change_time", 0L)
}

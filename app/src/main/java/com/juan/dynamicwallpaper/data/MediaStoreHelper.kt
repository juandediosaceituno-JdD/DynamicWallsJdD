package com.juan.dynamicwallpaper.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

data class MediaAlbum(
    val bucketId: String,
    val name: String,
    val coverUri: Uri,
    val photoCount: Int,
    val isFavorites: Boolean = false
)

object MediaStoreHelper {

    fun getAlbums(context: Context): List<MediaAlbum> {
        val albums = mutableMapOf<String, MediaAlbum>()

        // Columnas a consultar
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol        = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val bucketIdCol  = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id         = cursor.getLong(idCol)
                    val bucketId   = cursor.getString(bucketIdCol) ?: continue
                    val bucketName = cursor.getString(bucketNameCol) ?: "Sin nombre"
                    val imageUri   = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()
                    )

                    if (!albums.containsKey(bucketId)) {
                        albums[bucketId] = MediaAlbum(
                            bucketId    = bucketId,
                            name        = bucketName,
                            coverUri    = imageUri,
                            photoCount  = 1
                        )
                    } else {
                        val existing = albums[bucketId]!!
                        albums[bucketId] = existing.copy(photoCount = existing.photoCount + 1)
                    }
                }
            }

            // Álbum de Favoritos Samsung (is_favorite = 1)
            // Intentar agregar si el dispositivo lo soporta
            try {
                val favProjection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.BUCKET_ID
                )
                val favSelection = "is_favorite = 1"
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    favProjection,
                    favSelection,
                    null,
                    "${MediaStore.Images.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    var favCount = 0
                    var firstUri: Uri? = null
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                        if (firstUri == null) {
                            firstUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                        }
                        favCount++
                    }
                    if (favCount > 0 && firstUri != null) {
                        // Agregar o reemplazar el álbum de favoritos con flag especial
                        albums["__favorites__"] = MediaAlbum(
                            bucketId   = "__favorites__",
                            name       = "★ Favoritos",
                            coverUri   = firstUri!!,
                            photoCount = favCount,
                            isFavorites = true
                        )
                    }
                }
            } catch (e: Exception) {
                Log.d("MediaStoreHelper", "is_favorite no soportado en este dispositivo")
            }

        } catch (e: Exception) {
            Log.e("MediaStoreHelper", "Error consultando álbumes", e)
        }

        // Ordenar: Favoritos primero, luego por cantidad de fotos
        return albums.values.sortedWith(
            compareByDescending<MediaAlbum> { it.isFavorites }
                .thenByDescending { it.photoCount }
        )
    }

    fun getPhotosFromAlbum(context: Context, bucketId: String): List<Uri> {
        val uris = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Images.Media._ID)

        val (selection, selectionArgs) = if (bucketId == "__favorites__") {
            "is_favorite = 1" to null
        } else {
            "${MediaStore.Images.Media.BUCKET_ID} = ?" to arrayOf(bucketId)
        }

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    uris.add(Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()))
                }
            }
        } catch (e: Exception) {
            Log.e("MediaStoreHelper", "Error obteniendo fotos del álbum $bucketId", e)
        }

        return uris
    }
}

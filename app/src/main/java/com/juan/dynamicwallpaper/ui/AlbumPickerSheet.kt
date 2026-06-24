package com.juan.dynamicwallpaper.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juan.dynamicwallpaper.data.MediaAlbum
import com.juan.dynamicwallpaper.data.MediaStoreHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumPickerSheet(
    onDismiss: () -> Unit,
    onAlbumSelected: (MediaAlbum) -> Unit
) {
    val context = LocalContext.current
    var albums by remember { mutableStateOf<List<MediaAlbum>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val result = MediaStoreHelper.getAlbums(context)
            withContext(Dispatchers.Main) {
                albums = result
                loading = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF555555))
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Seleccionar álbum", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color(0xFF9E9E9E))
                }
            }

            if (loading) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF90CAF9))
                }
            } else if (albums.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("No se encontraron álbumes", color = Color(0xFF9E9E9E))
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(albums) { album ->
                        AlbumThumbnail(
                            album = album,
                            context = context,
                            onClick = { onAlbumSelected(album) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumThumbnail(album: MediaAlbum, context: Context, onClick: () -> Unit) {
    var bitmap by remember(album.bucketId) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(album.coverUri) {
        withContext(Dispatchers.IO) {
            bitmap = loadThumbFromUri(context, album.coverUri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF2C2C2C))
        ) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Badge de favoritos
            if (album.isFavorites) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFFFD700).copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
            // Badge con cantidad de fotos
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text("${album.photoCount}", color = Color.White, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = album.name,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun loadThumbFromUri(context: Context, uri: Uri): Bitmap? = try {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = 4 })
    }
} catch (e: Exception) { null }

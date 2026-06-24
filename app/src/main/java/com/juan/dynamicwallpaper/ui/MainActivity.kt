package com.juan.dynamicwallpaper.ui

import android.app.WallpaperManager
import android.content.*
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.*
import com.juan.dynamicwallpaper.data.MediaAlbum
import com.juan.dynamicwallpaper.data.PreferencesManager
import com.juan.dynamicwallpaper.worker.WallpaperWorker
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DynamicWallsTheme { WallpaperScreen() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperScreen() {
    val context = LocalContext.current
    val prefs   = remember { PreferencesManager(context) }
    val scope   = rememberCoroutineScope()

    var pickerMode       by remember { mutableStateOf(prefs.getPickerMode()) } // "folder"|"photos"|"album"
    var selectedFolderUri by remember { mutableStateOf(prefs.getFolderUri()) }
    var folderName       by remember { mutableStateOf(prefs.getFolderName()) }
    var photoCount       by remember { mutableStateOf(prefs.getPhotoCount()) }
    var selectedPhotos   by remember { mutableStateOf(prefs.getSelectedPhotos()) }
    var selectedBucketId by remember { mutableStateOf(prefs.getSelectedBucketId()) }
    var intervalMinutes  by remember { mutableStateOf(prefs.getInterval()) }
    var applyHome        by remember { mutableStateOf(prefs.getApplyHome()) }
    var applyLock        by remember { mutableStateOf(prefs.getApplyLock()) }
    var scalingMode      by remember { mutableStateOf(prefs.getScalingMode()) }
    var isRunning        by remember { mutableStateOf(prefs.getIsRunning()) }
    var lastChangedTime  by remember { mutableStateOf(prefs.getLastChangedTime()) }
    var nextChangeTime   by remember { mutableStateOf(prefs.getNextChangeTime()) }
    var thumbnailBitmap  by remember { mutableStateOf<ImageBitmap?>(null) }
    var homeBitmap       by remember { mutableStateOf<ImageBitmap?>(null) }
    var lockBitmap       by remember { mutableStateOf<ImageBitmap?>(null) }
    var showAlbumPicker  by remember { mutableStateOf(false) }
    var showSettings     by remember { mutableStateOf(false) }

    // Arrancar servicio si ya estaba configurado en modo apagar pantalla
    LaunchedEffect(Unit) {
        if (prefs.getIsRunning() && prefs.getInterval() == 0) {
            context.startForegroundService(android.content.Intent(context, com.juan.dynamicwallpaper.worker.ScreenOffService::class.java))
        }
    }

    // Cargar wallpapers actuales al iniciar
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            val wm = WallpaperManager.getInstance(context)
            val homeDrawable = try { wm.drawable } catch (e: Exception) { null }
            val homeBmp = (homeDrawable as? BitmapDrawable)?.bitmap ?: drawableToBitmap(homeDrawable)
            val lockDrawable = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
                    wm.getWallpaperFile(WallpaperManager.FLAG_LOCK)?.let { pfd ->
                        android.graphics.drawable.Drawable.createFromStream(
                            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd), null
                        )
                    } else null
            } catch (e: Exception) { null }
            val lockBmp = (lockDrawable as? BitmapDrawable)?.bitmap ?: drawableToBitmap(lockDrawable) ?: homeBmp
            withContext(Dispatchers.Main) {
                homeBitmap = homeBmp?.asImageBitmap()
                lockBitmap = lockBmp?.asImageBitmap()
            }
        }
        scope.launch(Dispatchers.IO) {
            val bmp = when (pickerMode) {
                "photos" -> selectedPhotos.firstOrNull()?.let { loadBitmapFromUri(context, Uri.parse(it)) }
                "album"  -> null // se carga al seleccionar álbum
                else     -> selectedFolderUri?.let { loadFolderThumbnail(context, it) }
            }
            withContext(Dispatchers.Main) { thumbnailBitmap = bmp?.asImageBitmap() }
        }
    }

    // Pickers
    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val name = getFolderDisplayName(context, it)
            val count = countPhotosInFolder(context, it)
            selectedFolderUri = it.toString(); folderName = name; photoCount = count
            prefs.saveFolderUri(it.toString()); prefs.saveFolderName(name); prefs.savePhotoCount(count)
            scope.launch(Dispatchers.IO) {
                val bmp = loadFolderThumbnail(context, it.toString())
                withContext(Dispatchers.Main) { thumbnailBitmap = bmp?.asImageBitmap() }
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                catch (e: Exception) { }
            }
            val uriStrings = uris.map { it.toString() }
            selectedPhotos = uriStrings; photoCount = uris.size
            folderName = "${uris.size} fotos seleccionadas"
            prefs.saveSelectedPhotos(uriStrings); prefs.savePhotoCount(uris.size); prefs.saveFolderName(folderName)
            scope.launch(Dispatchers.IO) {
                val bmp = loadBitmapFromUri(context, uris.first())
                withContext(Dispatchers.Main) { thumbnailBitmap = bmp?.asImageBitmap() }
            }
        }
    }

    val dateFormat = SimpleDateFormat("d/M/yyyy\nhh:mm a", Locale.getDefault())

    // Pantalla de ajustes
    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
        return
    }

    // Album picker bottom sheet
    if (showAlbumPicker) {
        AlbumPickerSheet(
            onDismiss = { showAlbumPicker = false },
            onAlbumSelected = { album: MediaAlbum ->
                showAlbumPicker = false
                selectedBucketId = album.bucketId
                folderName = album.name
                photoCount = album.photoCount
                prefs.saveSelectedBucketId(album.bucketId)
                prefs.saveFolderName(album.name)
                prefs.savePhotoCount(album.photoCount)
                scope.launch(Dispatchers.IO) {
                    val bmp = loadBitmapFromUri(context, album.coverUri)
                    withContext(Dispatchers.Main) { thumbnailBitmap = bmp?.asImageBitmap() }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DynamicWalls", fontWeight = FontWeight.Medium) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212), titleContentColor = Color.White),
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes", tint = Color.White)
                    }
                }
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── SELECTOR DE MODO ──────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF282828)), shape = RoundedCornerShape(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                    listOf("folder" to "Carpeta", "photos" to "Fotos", "album" to "Álbum").forEach { (mode, label) ->
                        Box(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (pickerMode == mode) Color(0xFF404040) else Color.Transparent)
                                .clickable { pickerMode = mode; prefs.savePickerMode(mode) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label,
                                color = if (pickerMode == mode) Color.White else Color(0xFF9E9E9E),
                                fontWeight = if (pickerMode == mode) FontWeight.Medium else FontWeight.Normal,
                                fontSize = 14.sp)
                        }
                    }
                }
            }

            // ── CARD FUENTE ACTIVA ────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF282828)), shape = RoundedCornerShape(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Photo, contentDescription = null, tint = Color(0xFF555555), modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = folderName.ifEmpty {
                                    when (pickerMode) {
                                        "photos" -> "Sin fotos seleccionadas"
                                        "album"  -> "Sin álbum seleccionado"
                                        else     -> "Sin carpeta"
                                    }
                                },
                                color = Color.White, fontWeight = FontWeight.Medium, fontSize = 15.sp
                            )
                            Text(
                                text = if (photoCount > 0) "$photoCount fotos" else "Toca ▾ para seleccionar",
                                color = Color(0xFF9E9E9E), fontSize = 13.sp
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = isRunning,
                            onCheckedChange = { running ->
                                isRunning = running; prefs.saveIsRunning(running)
                                if (running) {
                                    scheduleWallpaperWorker(context, intervalMinutes)
                                    val next = System.currentTimeMillis() + intervalMinutes * 60 * 1000L
                                    nextChangeTime = next; prefs.saveNextChangeTime(next)
                                    if (intervalMinutes == 0) {
                                        context.startForegroundService(android.content.Intent(context, com.juan.dynamicwallpaper.worker.ScreenOffService::class.java))
                                    }
                                } else {
                                    WorkManager.getInstance(context).cancelUniqueWork("wallpaper_rotation")
                                    context.stopService(android.content.Intent(context, com.juan.dynamicwallpaper.worker.ScreenOffService::class.java))
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF4A9EE8))
                        )
                        IconButton(onClick = {
                            when (pickerMode) {
                                "photos" -> photoPickerLauncher.launch(arrayOf("image/*"))
                                "album"  -> showAlbumPicker = true
                                else     -> folderPickerLauncher.launch(null)
                            }
                        }) {
                            Icon(Icons.Default.ExpandMore, contentDescription = "Seleccionar", tint = Color(0xFF9E9E9E))
                        }
                    }
                }
            }

            // ── PANTALLAS ────────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF282828)), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color(0xFF4A9EE8), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Pantalla de bloqueo", color = Color.White, fontSize = 14.sp)
                        }
                        Switch(checked = applyLock, onCheckedChange = { applyLock = it; prefs.saveApplyLock(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF4A9EE8)))
                    }
                    Divider(color = Color(0xFF3C3C3C), thickness = 0.5.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Home, contentDescription = null, tint = Color(0xFF4A9EE8), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Pantalla de inicio", color = Color.White, fontSize = 14.sp)
                        }
                        Switch(checked = applyHome, onCheckedChange = { applyHome = it; prefs.saveApplyHome(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF4A9EE8)))
                    }
                    Divider(color = Color(0xFF3C3C3C), thickness = 0.5.dp)
                    // Scaling modes
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Ajuste", color = Color(0xFF9E9E9E), fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("FILL" to "Llenar", "FIT" to "Adaptar", "STRETCH" to "Estirar", "NONE" to "Ninguno").forEach { (key, label) ->
                                val sel = scalingMode == key
                                Box(
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                        .background(if (sel) Color(0xFF4A9EE8) else Color(0xFF3C3C3C))
                                        .clickable { scalingMode = key; prefs.saveScalingMode(key) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) { Text(label, color = if (sel) Color.White else Color(0xFF9E9E9E), fontSize = 11.sp) }
                            }
                        }
                    }
                }
            }

            // ── INTERVALO ─────────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF282828)), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Cambiar cada", color = Color.White, fontSize = 15.sp)
                        Text(formatInterval(intervalMinutes), color = Color(0xFF4A9EE8), fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    val intervals = listOf(0, 15, 30, 60, 120, 240, 480)
                    val sliderIndex = intervals.indexOf(intervalMinutes).takeIf { it >= 0 } ?: 3
                    Slider(
                        value = sliderIndex.toFloat(),
                        onValueChange = {
                            val idx = it.toInt().coerceIn(0, intervals.size - 1)
                            intervalMinutes = intervals[idx]; prefs.saveInterval(intervals[idx])
                            if (isRunning) {
                                if (intervals[idx] == 0) {
                                    context.startForegroundService(android.content.Intent(context, com.juan.dynamicwallpaper.worker.ScreenOffService::class.java))
                                } else {
                                    context.stopService(android.content.Intent(context, com.juan.dynamicwallpaper.worker.ScreenOffService::class.java))
                                }
                                scheduleWallpaperWorker(context, intervals[idx])
                                val next = System.currentTimeMillis() + intervals[idx] * 60 * 1000L
                                nextChangeTime = next; prefs.saveNextChangeTime(next)
                            }
                        },
                        valueRange = 0f..(intervals.size - 1).toFloat(),
                        steps = intervals.size - 2,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF4A9EE8), activeTrackColor = Color(0xFF4A9EE8))
                    )
                }
            }

            // ── CAMBIAR AHORA ─────────────────────────────────────────────
            Button(
                onClick = {
                    val now = System.currentTimeMillis()
                    lastChangedTime = now; nextChangeTime = now + intervalMinutes * 60 * 1000L
                    prefs.saveLastChangedTime(now); prefs.saveNextChangeTime(nextChangeTime)
                    WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<WallpaperWorker>().build())
                    scope.launch {
                        delay(2500)
                        withContext(Dispatchers.IO) {
                            val wm = WallpaperManager.getInstance(context)
                            val bmp = drawableToBitmap(wm.drawable)
                            withContext(Dispatchers.Main) { homeBitmap = bmp?.asImageBitmap(); lockBitmap = bmp?.asImageBitmap() }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A9EE8))
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF121212))
                Spacer(Modifier.width(8.dp))
                Text("Cambiar ahora", color = Color(0xFF121212), fontWeight = FontWeight.Medium)
            }

            // ── TIMESTAMPS ────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TimestampCard(icon = "⬅️", label = "Último cambio",
                    time = if (lastChangedTime > 0) dateFormat.format(Date(lastChangedTime)) else "—", modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                TimestampCard(icon = "➡️", label = "Próximo cambio",
                    time = if (nextChangeTime > 0) dateFormat.format(Date(nextChangeTime)) else "—", modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── COMPONENTES ───────────────────────────────────────────────────────────────

@Composable
fun WallpaperPreview(bitmap: ImageBitmap?, isActive: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(9f / 19.5f)
            .clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A1A1A))
            .then(if (isActive) Modifier.border(1.dp, Color(0xFF4A9EE8).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                  else Modifier.border(1.dp, Color(0xFF404040), RoundedCornerShape(12.dp)))
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(), alpha = if (isActive) 1f else 0.4f)
        } else {
            Icon(Icons.Default.Wallpaper, contentDescription = null, tint = Color(0xFF404040),
                modifier = Modifier.size(32.dp).align(Alignment.Center))
        }
    }
}

@Composable
fun TimestampCard(icon: String, label: String, time: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFF282828)), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 20.sp); Spacer(Modifier.width(8.dp))
            Column { Text(label, color = Color(0xFF9E9E9E), fontSize = 11.sp); Text(time, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

@Composable
fun DynamicWallsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF121212), surface = Color(0xFF282828), primary = Color(0xFF4A9EE8)), content = content)
}

// ── HELPERS ───────────────────────────────────────────────────────────────────

fun formatInterval(minutes: Int) = when { minutes == 0 -> "Al apagar pantalla"; minutes < 60 -> "$minutes min"; minutes == 60 -> "1 hora"; else -> "${minutes / 60} horas" }
fun getScalingIcon(mode: String) = when (mode) { "FILL" -> "⬛"; "FIT" -> "🔲"; "STRETCH" -> "↔️"; else -> "✖️" }

fun scheduleWallpaperWorker(context: Context, intervalMinutes: Int) {
    if (intervalMinutes == 0) {
        WorkManager.getInstance(context).cancelUniqueWork("wallpaper_rotation")
        return
    }
    val safeInterval = maxOf(intervalMinutes.toLong(), 15L)
    val request = PeriodicWorkRequestBuilder<WallpaperWorker>(safeInterval, TimeUnit.MINUTES)
        .setConstraints(Constraints.Builder().build())
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("wallpaper_rotation", ExistingPeriodicWorkPolicy.REPLACE, request)
}

fun getFolderDisplayName(context: Context, uri: Uri): String =
    uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "Carpeta"

fun countPhotosInFolder(context: Context, uri: Uri): Int = try {
    androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
        ?.listFiles()?.count { it.isFile && it.type?.startsWith("image/") == true } ?: 0
} catch (e: Exception) { 0 }

fun loadFolderThumbnail(context: Context, folderUriString: String): Bitmap? = try {
    val uri = Uri.parse(folderUriString)
    val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
    val firstImage = docFile?.listFiles()?.firstOrNull { it.isFile && it.type?.startsWith("image/") == true }
    firstImage?.uri?.let { loadBitmapFromUri(context, it) }
} catch (e: Exception) { null }

fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? = try {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = 4 })
    }
} catch (e: Exception) { null }

fun drawableToBitmap(drawable: android.graphics.drawable.Drawable?): Bitmap? {
    drawable ?: return null
    if (drawable is BitmapDrawable) return drawable.bitmap
    val w = drawable.intrinsicWidth.coerceAtLeast(1); val h = drawable.intrinsicHeight.coerceAtLeast(1)
    return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
        val canvas = android.graphics.Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height); drawable.draw(canvas)
    }
}

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
import java.util.concurrent.TimeUnit

// ── PALETA ────────────────────────────────────────────────────────────────────
val BgApp      = Color(0xFF060F0D)
val BgCard     = Color(0xFF102220)
val BgSegment  = Color(0xFF0C1A18)
val Accent     = Color(0xFF2FBFA6)
val TxtPrimary = Color(0xFFF2F4F7)
val TxtSecond  = Color(0xFF7A808A)
val TxtLabel   = Color(0xFF5E646C)
val Hairline   = Color(0x0FFFFFFF)

class MainActivity : ComponentActivity() {
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val perms = mutableListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
            if (checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                perms.add(android.Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                perms.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (perms.isNotEmpty()) permLauncher.launch(perms.toTypedArray())
        setContent { DynamicWallsTheme { WallpaperScreen() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperScreen() {
    val context = LocalContext.current
    val prefs   = remember { PreferencesManager(context) }
    val scope   = rememberCoroutineScope()

    var pickerMode         by remember { mutableStateOf(prefs.getPickerMode()) }
    var selectedFolderUri  by remember { mutableStateOf(prefs.getFolderUri()) }
    var folderName         by remember { mutableStateOf(prefs.getFolderName()) }
    var photoCount         by remember { mutableStateOf(prefs.getPhotoCount()) }
    var selectedPhotos     by remember { mutableStateOf(prefs.getSelectedPhotos()) }
    var selectedBucketId   by remember { mutableStateOf(prefs.getSelectedBucketId()) }
    var intervalMinutes    by remember { mutableStateOf(prefs.getInterval()) }
    var applyHome          by remember { mutableStateOf(prefs.getApplyHome()) }
    var applyLock          by remember { mutableStateOf(prefs.getApplyLock()) }
    var scalingMode        by remember { mutableStateOf(prefs.getScalingMode()) }
    var isRunning          by remember { mutableStateOf(prefs.getIsRunning()) }
    var homeBitmap         by remember { mutableStateOf<ImageBitmap?>(null) }
    var lockBitmap         by remember { mutableStateOf<ImageBitmap?>(null) }
    var showAlbumPicker    by remember { mutableStateOf(false) }
    var lockIndependent    by remember { mutableStateOf(prefs.getLockIndependent()) }
    var lockPickerMode     by remember { mutableStateOf(prefs.getPickerModeLock()) }
    var lockFolderUri      by remember { mutableStateOf(prefs.getFolderUriLock()) }
    var lockFolderName     by remember { mutableStateOf(prefs.getFolderNameLock()) }
    var lockPhotoCount     by remember { mutableStateOf(prefs.getPhotoCountLock()) }
    var lockSelectedPhotos by remember { mutableStateOf(prefs.getSelectedPhotosLock()) }
    var lockBucketId       by remember { mutableStateOf(prefs.getSelectedBucketIdLock()) }
    var autoAdjust         by remember { mutableStateOf(prefs.getAutoAdjust()) }
    var showAlbumPickerLock by remember { mutableStateOf(false) }
    var showSettings       by remember { mutableStateOf(false) }

    // Cargar wallpapers actuales
    LaunchedEffect(Unit) {
        if (prefs.getIsRunning() && prefs.getInterval() == 0)
            context.startForegroundService(Intent(context, com.juan.dynamicwallpaper.worker.ScreenOffService::class.java))
        scope.launch(Dispatchers.IO) {
            // Intentar cargar desde última URI guardada (más confiable en Samsung)
            val homeUri = prefs.getLastHomeUri()
            val lockUri = prefs.getLastLockUri()
            val homeBmp = homeUri?.let { loadBitmapFromUri(context, Uri.parse(it)) }
                ?: run {
                    // Fallback: wallpaper actual del sistema
                    val wm = WallpaperManager.getInstance(context)
                    val drawable = try { wm.drawable } catch (e: Exception) { null }
                    (drawable as? BitmapDrawable)?.bitmap ?: drawableToBitmap(drawable)
                }
            val lockBmp = lockUri?.let { loadBitmapFromUri(context, Uri.parse(it)) } ?: homeBmp
            withContext(Dispatchers.Main) {
                homeBitmap = homeBmp?.asImageBitmap()
                lockBitmap = lockBmp?.asImageBitmap()
            }
        }
    }

    // Pickers
    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val name = getFolderDisplayName(context, it); val count = countPhotosInFolder(context, it)
            selectedFolderUri = it.toString(); folderName = name; photoCount = count
            prefs.saveFolderUri(it.toString()); prefs.saveFolderName(name); prefs.savePhotoCount(count)
        }
    }
    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri -> try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {} }
            val uriStrings = uris.map { it.toString() }
            selectedPhotos = uriStrings; photoCount = uris.size; folderName = "${uris.size} fotos seleccionadas"
            prefs.saveSelectedPhotos(uriStrings); prefs.savePhotoCount(uris.size); prefs.saveFolderName(folderName)
        }
    }
    val folderPickerLauncherLock = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val name = getFolderDisplayName(context, it); val count = countPhotosInFolder(context, it)
            lockFolderUri = it.toString(); lockFolderName = name; lockPhotoCount = count
            prefs.saveFolderUriLock(it.toString()); prefs.saveFolderNameLock(name); prefs.savePhotoCountLock(count)
        }
    }
    val photoPickerLauncherLock = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri -> try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {} }
            val uriStrings = uris.map { it.toString() }
            lockSelectedPhotos = uriStrings; lockPhotoCount = uris.size; lockFolderName = "${uris.size} fotos"
            prefs.saveSelectedPhotosLock(uriStrings); prefs.savePhotoCountLock(uris.size); prefs.saveFolderNameLock(lockFolderName)
        }
    }

    if (showSettings) { SettingsScreen(onBack = { showSettings = false }); return }

    if (showAlbumPickerLock) {
        AlbumPickerSheet(onDismiss = { showAlbumPickerLock = false }, onAlbumSelected = { album: MediaAlbum ->
            showAlbumPickerLock = false; lockBucketId = album.bucketId; lockFolderName = album.name; lockPhotoCount = album.photoCount
            prefs.saveSelectedBucketIdLock(album.bucketId); prefs.saveFolderNameLock(album.name); prefs.savePhotoCountLock(album.photoCount)
        })
    }
    if (showAlbumPicker) {
        AlbumPickerSheet(onDismiss = { showAlbumPicker = false }, onAlbumSelected = { album: MediaAlbum ->
            showAlbumPicker = false; selectedBucketId = album.bucketId; folderName = album.name; photoCount = album.photoCount
            prefs.saveSelectedBucketId(album.bucketId); prefs.saveFolderName(album.name); prefs.savePhotoCount(album.photoCount)
            scope.launch(Dispatchers.IO) {
                val bmp = loadBitmapFromUri(context, album.coverUri)
                withContext(Dispatchers.Main) { }
            }
        })
    }

    Box(modifier = Modifier.fillMaxSize().background(BgApp)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 88.dp)
        ) {
            // ── HEADER ────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("DynamicWalls", color = TxtPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Fondos que cambian solos", color = TxtSecond, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(BgCard).clickable { showSettings = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Ajustes", tint = TxtSecond, modifier = Modifier.size(20.dp))
                }
            }

            // ── PREVIEWS ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PhonePreview(label = "Bloqueo", bitmap = lockBitmap, isActive = applyLock, modifier = Modifier.weight(1f))
                PhonePreview(label = "Inicio",  bitmap = homeBitmap, isActive = applyHome, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(24.dp))

            // ── SECCIÓN FUENTE ────────────────────────────────────────────
            SectionLabel("FUENTE")
            DwCard {
                // Segmentado Carpeta/Fotos/Álbum
                Row(
                    modifier = Modifier.fillMaxWidth().padding(4.dp).clip(RoundedCornerShape(10.dp)).background(BgSegment)
                ) {
                    listOf("folder" to "Carpeta", "photos" to "Fotos", "album" to "Álbum").forEach { (mode, label) ->
                        Box(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (pickerMode == mode) Accent else Color.Transparent)
                                .clickable { pickerMode = mode; prefs.savePickerMode(mode) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label,
                                color = if (pickerMode == mode) Color.Black else TxtSecond,
                                fontWeight = if (pickerMode == mode) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                // Fuente activa
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(BgSegment), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Photo, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (pickerMode == "album" && folderName.isNotEmpty())
                                Text("★ ", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = folderName.ifEmpty { when (pickerMode) { "photos" -> "Sin fotos"; "album" -> "Sin álbum"; else -> "Sin carpeta" } },
                                color = TxtPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                            )
                        }
                        Text(
                            text = if (photoCount > 0) "$photoCount fotos" else "Toca para seleccionar",
                            color = TxtSecond, fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = isRunning,
                        onCheckedChange = { running ->
                            isRunning = running; prefs.saveIsRunning(running)
                            if (running) {
                                scheduleWallpaperWorker(context, intervalMinutes)
                                if (intervalMinutes == 0) context.startForegroundService(Intent(context, com.juan.dynamicwallpaper.worker.ScreenOffService::class.java))
                            } else {
                                WorkManager.getInstance(context).cancelUniqueWork("wallpaper_rotation")
                                context.stopService(Intent(context, com.juan.dynamicwallpaper.worker.ScreenOffService::class.java))
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Accent, uncheckedTrackColor = BgSegment)
                    )
                    IconButton(onClick = {
                        when (pickerMode) { "photos" -> photoPickerLauncher.launch(arrayOf("image/*")); "album" -> showAlbumPicker = true; else -> folderPickerLauncher.launch(null) }
                    }) {
                        Icon(Icons.Default.ExpandMore, contentDescription = "Seleccionar", tint = TxtSecond)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── SECCIÓN APLICAR EN ────────────────────────────────────────
            SectionLabel("APLICAR EN")
            DwCard {
                DwRow(icon = Icons.Default.LockOpen, label = "Pantalla de bloqueo") {
                    Switch(checked = applyLock, onCheckedChange = { applyLock = it; prefs.saveApplyLock(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Accent, uncheckedTrackColor = BgSegment))
                }
                DwDivider()
                DwRow(icon = Icons.Default.Home, label = "Pantalla de inicio") {
                    Switch(checked = applyHome, onCheckedChange = { applyHome = it; prefs.saveApplyHome(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Accent, uncheckedTrackColor = BgSegment))
                }
                if (applyLock) {
                    DwDivider()
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Fuente independiente", color = TxtPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Fotos distintas en bloqueo", color = TxtSecond, fontSize = 12.sp)
                        }
                        Switch(checked = lockIndependent, onCheckedChange = { lockIndependent = it; prefs.saveLockIndependent(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Accent, uncheckedTrackColor = BgSegment))
                    }
                    if (lockIndependent) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(BgSegment)) {
                                listOf("folder" to "Carpeta", "photos" to "Fotos", "album" to "Álbum").forEach { (mode, label) ->
                                    Box(
                                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                            .background(if (lockPickerMode == mode) Accent else Color.Transparent)
                                            .clickable { lockPickerMode = mode; prefs.savePickerModeLock(mode) }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(label, color = if (lockPickerMode == mode) Color.Black else TxtSecond,
                                            fontSize = 12.sp, fontWeight = if (lockPickerMode == mode) FontWeight.Bold else FontWeight.Medium)
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = lockFolderName.ifEmpty { "Sin fuente seleccionada" }, color = TxtSecond, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    when (lockPickerMode) { "photos" -> photoPickerLauncherLock.launch(arrayOf("image/*")); "album" -> showAlbumPickerLock = true; else -> folderPickerLauncherLock.launch(null) }
                                }) { Icon(Icons.Default.ExpandMore, contentDescription = null, tint = TxtSecond) }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── SECCIÓN AJUSTE ────────────────────────────────────────────
            SectionLabel("AJUSTE")
            DwCard {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("FILL" to "Llenar", "FIT" to "Adaptar", "STRETCH" to "Estirar", "NONE" to "Ninguno").forEach { (key, label) ->
                        val sel = scalingMode == key
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                .background(if (sel) Accent else BgSegment)
                                .clickable { scalingMode = key; prefs.saveScalingMode(key) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) { Text(label, color = if (sel) Color.Black else TxtSecond, fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium) }
                    }
                }
                DwDivider()
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(BgSegment), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Autoajuste", color = TxtPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Brillo y encuadre automático", color = TxtSecond, fontSize = 12.sp)
                        }
                    }
                    Switch(checked = autoAdjust, onCheckedChange = { autoAdjust = it; prefs.saveAutoAdjust(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Accent, uncheckedTrackColor = BgSegment))
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── SECCIÓN FRECUENCIA ────────────────────────────────────────
            SectionLabel("FRECUENCIA")
            DwCard {
                val intervals = listOf(0, 15, 30, 60, 180, 360, 720, 1440)
                val sliderIndex = intervals.indexOf(intervalMinutes).takeIf { it >= 0 } ?: 2
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Cambiar cada", color = TxtSecond, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(formatInterval(intervalMinutes), color = Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Slider(
                        value = sliderIndex.toFloat(),
                        onValueChange = {
                            val idx = it.toInt().coerceIn(0, intervals.size - 1)
                            intervalMinutes = intervals[idx]; prefs.saveInterval(intervals[idx])
                            if (isRunning) {
                                if (intervals[idx] == 0) context.startForegroundService(Intent(context, com.juan.dynamicwallpaper.worker.ScreenOffService::class.java))
                                else context.stopService(Intent(context, com.juan.dynamicwallpaper.worker.ScreenOffService::class.java))
                                scheduleWallpaperWorker(context, intervals[idx])
                            }
                        },
                        valueRange = 0f..(intervals.size - 1).toFloat(),
                        steps = intervals.size - 2,
                        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = BgSegment)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        // ── CTA FIJO ──────────────────────────────────────────────────────
        Box(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(BgApp.copy(alpha = 0.95f)).padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        WallpaperWorker.applyWallpapers(context)
                        // Recargar previews desde última URI guardada
                        val prefs2 = PreferencesManager(context)
                        val homeUri = prefs2.getLastHomeUri()
                        val lockUri = prefs2.getLastLockUri()
                        val homeBmp = homeUri?.let { loadBitmapFromUri(context, Uri.parse(it)) }
                        val lockBmp = lockUri?.let { loadBitmapFromUri(context, Uri.parse(it)) }
                        withContext(Dispatchers.Main) {
                            homeBmp?.let { homeBitmap = it.asImageBitmap() }
                            lockBmp?.let { lockBitmap = it.asImageBitmap() }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("Cambiar ahora", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
        }
    }
}

// ── COMPONENTES ───────────────────────────────────────────────────────────────

@Composable
fun PhonePreview(label: String, bitmap: ImageBitmap?, isActive: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(9f / 19.5f)
                .clip(RoundedCornerShape(16.dp))
                .background(BgCard)
                .then(
                    if (isActive) Modifier.border(1.5.dp, Accent.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                    else Modifier.border(1.dp, Hairline, RoundedCornerShape(16.dp))
                )
        ) {
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(), alpha = if (isActive) 1f else 0.5f)
            } else {
                Icon(Icons.Default.Wallpaper, contentDescription = null, tint = BgSegment,
                    modifier = Modifier.size(28.dp).align(Alignment.Center))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = if (isActive) TxtPrimary else TxtSecond, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, color = TxtLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
}

@Composable
fun DwCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        shape = RoundedCornerShape(16.dp)
    ) { Column(modifier = Modifier.padding(8.dp), content = content) }
}

@Composable
fun DwRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, action: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(BgSegment), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(label, color = TxtPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        action()
    }
}

@Composable
fun DwDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = Hairline, thickness = 0.5.dp)
}

@Composable
fun DynamicWallsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(background = BgApp, surface = BgCard, primary = Accent), content = content)
}

// ── HELPERS ───────────────────────────────────────────────────────────────────

fun formatInterval(minutes: Int) = when {
    minutes == 0    -> "Al apagar pantalla"
    minutes < 60    -> "$minutes min"
    minutes == 60   -> "1 hora"
    minutes == 1440 -> "1 día"
    else            -> "${minutes / 60} horas"
}

fun scheduleWallpaperWorker(context: Context, intervalMinutes: Int) {
    if (intervalMinutes == 0) { WorkManager.getInstance(context).cancelUniqueWork("wallpaper_rotation"); return }
    val safeInterval = maxOf(intervalMinutes.toLong(), 15L)
    val request = PeriodicWorkRequestBuilder<WallpaperWorker>(safeInterval, TimeUnit.MINUTES).setConstraints(Constraints.Builder().build()).build()
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
    val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = 4 })
    } ?: return null
    if (rotation != 0f)
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation) }, true)
    else bitmap
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

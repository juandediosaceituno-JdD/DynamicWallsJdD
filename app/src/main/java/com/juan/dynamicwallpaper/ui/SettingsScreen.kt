package com.juan.dynamicwallpaper.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    val hasImages = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED
        else
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val hasWallpaper = remember {
        context.checkSelfPermission(android.Manifest.permission.SET_WALLPAPER) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val hasBattery = remember {
        BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212), titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── PERMISOS ──────────────────────────────────────────────────
            Text("Permisos", color = Color(0xFF9E9E9E), fontSize = 12.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp))

            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF282828)), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(4.dp)) {
                    PermissionRow(
                        icon = Icons.Default.Image,
                        label = "Acceso a fotos",
                        granted = hasImages,
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            })
                        }
                    )
                    Divider(color = Color(0xFF3C3C3C), thickness = 0.5.dp)
                    PermissionRow(
                        icon = Icons.Default.Wallpaper,
                        label = "Establecer wallpaper",
                        granted = hasWallpaper,
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            })
                        }
                    )
                    Divider(color = Color(0xFF3C3C3C), thickness = 0.5.dp)
                    PermissionRow(
                        icon = Icons.Default.BatteryFull,
                        label = "Sin restricción de batería",
                        granted = hasBattery,
                        onClick = { BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── ACERCA DE ─────────────────────────────────────────────────
            Text("Acerca de", color = Color(0xFF9E9E9E), fontSize = 12.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp))

            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF282828)), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Versión", color = Color(0xFF9E9E9E), fontSize = 14.sp)
                        Text("4.0", color = Color.White, fontSize = 14.sp)
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Desarrollador", color = Color(0xFF9E9E9E), fontSize = 14.sp)
                        Text("JdD", color = Color.White, fontSize = 14.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun PermissionRow(icon: ImageVector, label: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color(0xFF1B5FA8), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, color = Color.White, fontSize = 14.sp)
        }
        if (granted) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
        } else {
            TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text("Activar", color = Color(0xFF1B5FA8), fontSize = 13.sp)
            }
        }
    }
}

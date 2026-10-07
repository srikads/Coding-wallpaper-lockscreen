package com.srikads.codewall.ui

import android.Manifest
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.srikads.codewall.data.Permissions
import com.srikads.codewall.render.PreviewView
import com.srikads.codewall.wallpaper.CodeWallpaperService

@Composable
fun PreviewScreen(modifier: Modifier) {
    val context = LocalContext.current
    var lockedPreview by remember { mutableStateOf(false) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    // Re-check permissions whenever we come back to the app (e.g. from system settings).
    var permTick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) permTick++ }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permTick++ }

    Column(modifier.verticalScroll(rememberScrollState())) {
        AndroidView(
            factory = { PreviewView(it).also { v -> previewView = v } },
            update = { it.previewLocked = lockedPreview },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(20.dp)),
        )
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = !lockedPreview, onClick = { lockedPreview = false }, label = { Text("Home") })
            FilterChip(selected = lockedPreview, onClick = { lockedPreview = true }, label = { Text("Lock screen") })
            TextButton(onClick = { previewView?.replayTyping() }) { Text("Replay") }
        }
        Button(onClick = { setWallpaper(context) }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Set as live wallpaper")
        }
        Text(
            "In the system picker choose \"Home and lock screen\" to get the live view on your lock screen too.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Section("permissions") {
            key(permTick) {
                PermissionRow("Location (approximate)", "Weather. Rounded to ~11 km before it leaves the phone.", Permissions.location(context)) {
                    launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))
                }
                PermissionRow("Calendar", "Today's events and next-event countdown. Read only.", Permissions.calendar(context)) {
                    launcher.launch(arrayOf(Manifest.permission.READ_CALENDAR))
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    PermissionRow("Physical activity", "Step counter.", Permissions.activity(context)) {
                        launcher.launch(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION))
                    }
                }
                PermissionRow("Notification access", "Only used to read the current song title. Notifications are ignored.", Permissions.notificationListener(context)) {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            }
        }

        Section("privacy") {
            Text(
                "Everything stays on this phone: no accounts, no analytics, no ads, no backups. " +
                    "The only network request is the weather forecast to api.open-meteo.com with your rounded " +
                    "coordinates. Turn weather off (Fields tab) and CodeWall never touches the network.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PermissionRow(title: String, why: String, granted: Boolean, onGrant: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (granted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (granted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title)
            Text(why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        if (!granted) OutlinedButton(onClick = onGrant) { Text("Grant") }
    }
}

private fun setWallpaper(context: Context) {
    val component = ComponentName(context, CodeWallpaperService::class.java)
    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
    }
}

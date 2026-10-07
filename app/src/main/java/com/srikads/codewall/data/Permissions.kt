package com.srikads.codewall.data

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

object Permissions {
    fun has(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun location(context: Context) = has(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    fun calendar(context: Context) = has(context, Manifest.permission.READ_CALENDAR)
    fun activity(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || has(context, Manifest.permission.ACTIVITY_RECOGNITION)

    fun notificationListener(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(context, MediaListenerService::class.java)
        return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}

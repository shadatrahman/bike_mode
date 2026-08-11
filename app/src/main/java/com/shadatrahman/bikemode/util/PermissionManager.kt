package com.shadatrahman.bikemode.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/**
 * WRITE_SETTINGS is a special permission: it cannot be requested at runtime, only granted by the
 * user on the system "Modify system settings" screen. The app never tries to work around that.
 */
object PermissionManager {

    fun canWriteSettings(context: Context): Boolean = Settings.System.canWrite(context)

    /** BLUETOOTH_CONNECT gates both reading the paired list and offering the "turn it on" dialog. */
    fun canUseBluetooth(context: Context): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_CONNECT,
    ) == PackageManager.PERMISSION_GRANTED

    fun writeSettingsIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_WRITE_SETTINGS,
        "package:${context.packageName}".toUri(),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

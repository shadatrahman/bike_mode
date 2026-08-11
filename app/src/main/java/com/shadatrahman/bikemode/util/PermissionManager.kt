package com.shadatrahman.bikemode.util

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

/**
 * WRITE_SETTINGS is a special permission: it cannot be requested at runtime, only granted by the
 * user on the system "Modify system settings" screen. The app never tries to work around that.
 */
object PermissionManager {

    fun canWriteSettings(context: Context): Boolean = Settings.System.canWrite(context)

    fun writeSettingsIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_WRITE_SETTINGS,
        "package:${context.packageName}".toUri(),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

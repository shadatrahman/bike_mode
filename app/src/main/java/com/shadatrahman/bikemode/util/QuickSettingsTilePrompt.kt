package com.shadatrahman.bikemode.util

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import androidx.core.content.getSystemService
import com.shadatrahman.bikemode.R
import com.shadatrahman.bikemode.quicksettings.BikeModeTileService

/**
 * Asks the system to show the "add this tile?" dialog, so the rider never has to hunt through the
 * Quick Settings edit screen. Available since Android 13, which is our minSdk.
 */
object QuickSettingsTilePrompt {

    fun request(context: Context) {
        val statusBarManager = context.getSystemService<StatusBarManager>() ?: return
        statusBarManager.requestAddTileService(
            ComponentName(context, BikeModeTileService::class.java),
            context.getString(R.string.tile_label),
            Icon.createWithResource(context, R.drawable.ic_bike_mode),
            context.mainExecutor,
        ) { /* Result is informational; the system already tells the user what happened. */ }
    }
}

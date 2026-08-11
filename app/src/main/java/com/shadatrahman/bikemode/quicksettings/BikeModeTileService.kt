package com.shadatrahman.bikemode.quicksettings

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.shadatrahman.bikemode.MainActivity
import com.shadatrahman.bikemode.R
import com.shadatrahman.bikemode.rotation.BikeModeManager
import com.shadatrahman.bikemode.util.PermissionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The rider's primary interface: one tap in Quick Settings, no app UI opened.
 *
 * Reads and writes go through [BikeModeManager] on a service-scoped coroutine; the service stays
 * bound while the Quick Settings panel is open, which covers the click.
 */
class BikeModeTileService : TileService() {

    private lateinit var manager: BikeModeManager
    private lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        manager = BikeModeManager(applicationContext)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        if (!PermissionManager.canWriteSettings(this)) {
            openApp()
            return
        }
        scope.launch {
            manager.toggle()
                .onFailure { Toast.makeText(this@BikeModeTileService, R.string.error_system_rejected, Toast.LENGTH_LONG).show() }
            refreshTile()
        }
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        scope.launch {
            val hasPermission = PermissionManager.canWriteSettings(this@BikeModeTileService)
            val active = hasPermission && manager.isActive()
            tile.state = when {
                !hasPermission -> Tile.STATE_UNAVAILABLE
                active -> Tile.STATE_ACTIVE
                else -> Tile.STATE_INACTIVE
            }
            tile.label = getString(R.string.tile_label)
            tile.subtitle = getString(
                when {
                    !hasPermission -> R.string.tile_subtitle_permission_needed
                    active -> R.string.tile_subtitle_locked
                    else -> R.string.tile_subtitle_off
                }
            )
            tile.icon = Icon.createWithResource(this@BikeModeTileService, R.drawable.ic_bike_mode)
            tile.updateTile()
        }
    }

    /** Sends the rider to the app to grant WRITE_SETTINGS, since a tile cannot request it. */
    @Suppress("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

package com.shadatrahman.bikemode.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shadatrahman.bikemode.rotation.BikeModeManager
import com.shadatrahman.bikemode.util.PermissionManager
import com.shadatrahman.bikemode.widget.BikeModeWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Puts the watchdog back after a reboot or an app update.
 *
 * The rotation settings themselves survive a reboot, so Bike Mode comes back on looking correct
 * while nothing is left watching it — the content-trigger job cannot be persisted, and the service
 * died with the last boot. Without this, the first portrait-locked app after a restart would knock
 * the lock loose for good.
 *
 * BOOT_COMPLETED and MY_PACKAGE_REPLACED are both exempt from the background foreground-service
 * start restriction, so re-arming from here is allowed.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }

        // The broadcast ends as soon as onReceive returns, so hold it open for the DataStore read.
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                val manager = BikeModeManager(appContext)
                // enable() is the re-arm path: it re-applies the pinned direction and starts both
                // watchdogs, and it keeps the saved pre-Bike-Mode state rather than overwriting it.
                if (PermissionManager.canWriteSettings(appContext) && manager.isActive()) {
                    manager.enable(requestBluetooth = false)
                }
                BikeModeWidgetProvider.refresh(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

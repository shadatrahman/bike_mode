package com.shadatrahman.bikemode.companion

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import com.shadatrahman.bikemode.rotation.BikeModeManager
import com.shadatrahman.bikemode.util.PermissionManager
import com.shadatrahman.bikemode.widget.BikeModeWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Starts and stops Bike Mode with the helmet, so the rider never taps anything.
 *
 * This is the only mechanism that can do it. A manifest-registered ACL_CONNECTED receiver would
 * not fire while the app is not running — that broadcast is not on the implicit-broadcast
 * exemption list — whereas the system binds a CompanionDeviceService for an associated device
 * whether the app is running or not. It is also what makes starting the watchdog's foreground
 * service legal from here, via REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND.
 *
 * Both callbacks are no-ops unless the rider opted in, so an association left over from a
 * disabled feature cannot quietly keep driving the phone.
 */
class HelmetPresenceService : CompanionDeviceService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        act { manager -> manager.enable() }
    }

    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        // The helmet coming off ends the ride: rotation restored, and media paused with it.
        act { manager -> manager.disable() }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun act(block: suspend (BikeModeManager) -> Unit) {
        val appContext = applicationContext
        scope.launch {
            val manager = BikeModeManager(appContext)
            if (!manager.preferences().autoStartWithHelmet) return@launch
            if (!PermissionManager.canWriteSettings(appContext)) return@launch
            block(manager)
            BikeModeWidgetProvider.refresh(appContext)
        }
    }
}

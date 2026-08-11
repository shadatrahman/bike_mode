package com.shadatrahman.bikemode.companion

import android.app.Activity
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.getSystemService
import com.shadatrahman.bikemode.data.PairedDevice

/**
 * Managing the system association that [HelmetPresenceService] is woken for.
 *
 * Association is the rider's consent, granted once in a system dialog, and it is separate from the
 * device they picked in the app: the picker says which helmet to watch for during a ride, this
 * says the system may wake Bike Mode for it. Filtering by address means the dialog offers exactly
 * the one they already chose rather than making them choose twice.
 */
class HelmetAssociation(context: Context) {

    private val appContext = context.applicationContext

    private val manager get() = appContext.getSystemService<CompanionDeviceManager>()

    /** Whether [device] is already associated, so the UI can offer the right action. */
    fun isAssociated(device: PairedDevice): Boolean = runCatching {
        manager?.myAssociations.orEmpty().any {
            it.deviceMacAddress?.toString().equals(device.address, ignoreCase = true)
        }
    }.getOrDefault(false)

    /**
     * Asks the system to show its association dialog. The result arrives on [onPending] as an
     * [IntentSender] the caller launches, because only an activity may show it.
     *
     * [activity] must be one: the system ties the dialog to the calling activity, and asking
     * through the application context gets nothing back at all. Every failure path reports why,
     * because the alternative — a switch that silently slides back — tells the rider nothing about
     * a helmet that needs waking, a permission that was refused, or a phone that lacks the feature.
     */
    fun requestAssociation(
        activity: Activity,
        device: PairedDevice,
        onPending: (IntentSender) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val companions = activity.getSystemService<CompanionDeviceManager>()
        if (companions == null || !activity.packageManager
                .hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)
        ) {
            report(onFailure, "This phone does not support companion devices")
            return
        }
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(device.address).build())
            .setSingleDevice(true)
            .setDisplayName(device.name)
            .build()
        val systemCallback = object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) = onPending(intentSender)

            override fun onFailure(error: CharSequence?) =
                report(onFailure, error?.toString() ?: "The system could not find the device")
        }
        runCatching { companions.associate(request, activity.mainExecutor, systemCallback) }
            .onFailure { report(onFailure, it.message ?: it::class.java.simpleName) }
    }

    private fun report(onFailure: (String) -> Unit, reason: String) {
        Log.w(TAG, "Companion association failed: $reason")
        onFailure(reason)
    }

    /** Begins watching, so the system starts binding [HelmetPresenceService] on arrival. */
    fun startObserving(device: PairedDevice) {
        runCatching { manager?.startObservingDevicePresence(device.address) }
    }

    fun stopObserving(device: PairedDevice) {
        runCatching { manager?.stopObservingDevicePresence(device.address) }
    }

    /** Dropping the choice should drop the system's permission to wake us for it as well. */
    fun forget(device: PairedDevice) {
        runCatching {
            manager?.myAssociations.orEmpty()
                .filter { it.deviceMacAddress?.toString().equals(device.address, ignoreCase = true) }
                .forEach { manager?.disassociate(it.id) }
        }
    }

    private companion object {
        const val TAG = "HelmetAssociation"
    }
}

package com.shadatrahman.bikemode.companion

import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
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
     * Asks the system to show its association dialog. The result arrives on [callback] as an
     * [IntentSender] the caller launches, because only an activity may show it.
     */
    fun requestAssociation(device: PairedDevice, callback: (IntentSender) -> Unit, onFailure: () -> Unit) {
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(device.address).build())
            .setSingleDevice(true)
            .setDisplayName(device.name)
            .build()
        val systemCallback = object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) = callback(intentSender)

            override fun onFailure(error: CharSequence?) = onFailure()
        }
        runCatching {
            manager?.associate(request, appContext.mainExecutor, systemCallback)
        }.onFailure { onFailure() }
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
}

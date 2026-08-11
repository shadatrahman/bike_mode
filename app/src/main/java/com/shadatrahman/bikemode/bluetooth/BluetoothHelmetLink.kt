package com.shadatrahman.bikemode.bluetooth

import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import androidx.core.content.getSystemService
import com.shadatrahman.bikemode.data.PairedDevice
import com.shadatrahman.bikemode.util.PermissionManager
import com.shadatrahman.bikemode.util.reportingFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/**
 * [HelmetLink] over the real adapter.
 *
 * Every call is wrapped: BLUETOOTH_CONNECT may not be granted, the device may have no adapter, and
 * the profile proxies bind asynchronously, so any of these can legitimately come back empty. None
 * of that is worth failing a ride over — the worst case is the notification saying the helmet is
 * not connected when it is.
 *
 * But none of it happens quietly either. Every one of those causes reaches the rider as the same
 * "helmet not connected", so each says which it was in logcat under this class's tag. Expected
 * outcomes go in at info, genuine faults at warning.
 */
class BluetoothHelmetLink(context: Context) : HelmetLink {

    private val appContext = context.applicationContext

    private val adapter get() = appContext.getSystemService<BluetoothManager>()?.adapter

    /** Profile proxies arrive on a callback, so they stay null for a moment after construction. */
    private var a2dp: BluetoothProfile? = null
    private var headset: BluetoothProfile? = null

    /** Keeps a once-a-second poll from repeating the same complaint for a whole ride. */
    private var reportedConnectionFailure = false

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dp = proxy
                BluetoothProfile.HEADSET -> headset = proxy
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dp = null
                BluetoothProfile.HEADSET -> headset = null
            }
        }
    }

    init {
        // Worth shouting about: without these proxies isConnected can only ever say false, so the
        // rider would be told the helmet is missing for the whole ride with no other clue why.
        reportingFailure(TAG, "Binding audio profile proxies", Unit) {
            adapter?.getProfileProxy(appContext, serviceListener, BluetoothProfile.A2DP)
            adapter?.getProfileProxy(appContext, serviceListener, BluetoothProfile.HEADSET)
        }
    }

    override fun bondedDevices(): List<PairedDevice> {
        if (!PermissionManager.canUseBluetooth(appContext)) {
            Log.i(TAG, "Not listing paired devices: BLUETOOTH_CONNECT not granted")
            return emptyList()
        }
        // Caught here rather than through reportingFailure because lint will not follow a
        // SecurityException handler through an inline helper, and it is right to insist.
        return try {
            adapter?.bondedDevices.orEmpty()
                .map { PairedDevice(address = it.address, name = it.name ?: it.address) }
                .sortedBy { it.name.lowercase() }
        } catch (e: SecurityException) {
            Log.w(TAG, "Listing paired devices failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Polled once a second while the helmet is being waited for, so a failure here would otherwise
     * fill logcat. It reports the first one and then holds its tongue — the cause never changes
     * between calls anyway.
     */
    override fun isConnected(address: String): Boolean {
        if (!PermissionManager.canUseBluetooth(appContext)) return false
        return try {
            listOfNotNull(a2dp, headset).any { profile ->
                profile.connectedDevices.any { it.address.equals(address, ignoreCase = true) }
            }
        } catch (e: Exception) {
            if (!reportedConnectionFailure) {
                reportedConnectionFailure = true
                Log.w(TAG, "Reading connection state failed: ${e.javaClass.simpleName}: ${e.message}")
            }
            false
        }
    }

    /**
     * Opens an SPP socket and closes it straight away. Holding it open is not the point — the
     * connection attempt itself is what tends to make an intercom bring up its audio profiles.
     *
     * `connect()` blocks for up to about twelve seconds on failure, hence the IO dispatcher.
     */
    override suspend fun nudge(address: String): Boolean = withContext(Dispatchers.IO) {
        if (!PermissionManager.canUseBluetooth(appContext)) return@withContext false
        val device = reportingFailure(TAG, "Resolving $address", null) { adapter?.getRemoteDevice(address) }
            ?: return@withContext false
        try {
            device.createInsecureRfcommSocketToServiceRecord(SPP_UUID).use { it.connect() }
            Log.i(TAG, "Nudge opened an RFCOMM link to $address")
            true
        } catch (e: IOException) {
            // Ordinary and expected — no SPP service, or the helmet is simply off — so this is
            // information, not a warning. It is still the answer to "why did the nudge do nothing".
            Log.i(TAG, "Nudge did not take: ${e.message}")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Nudge failed: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    override fun close() {
        reportingFailure(TAG, "Releasing audio profile proxies", Unit) {
            a2dp?.let { adapter?.closeProfileProxy(BluetoothProfile.A2DP, it) }
            headset?.let { adapter?.closeProfileProxy(BluetoothProfile.HEADSET, it) }
        }
        a2dp = null
        headset = null
    }

    private companion object {
        const val TAG = "BluetoothHelmetLink"

        /** The standard Serial Port Profile UUID, which nearly every intercom advertises. */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}

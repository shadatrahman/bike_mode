package com.shadatrahman.bikemode.bluetooth

import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import androidx.core.content.getSystemService
import com.shadatrahman.bikemode.data.PairedDevice
import com.shadatrahman.bikemode.util.PermissionManager
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
 */
class BluetoothHelmetLink(context: Context) : HelmetLink {

    private val appContext = context.applicationContext

    private val adapter get() = appContext.getSystemService<BluetoothManager>()?.adapter

    /** Profile proxies arrive on a callback, so they stay null for a moment after [bind]. */
    private var a2dp: BluetoothProfile? = null
    private var headset: BluetoothProfile? = null

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
        runCatching {
            adapter?.getProfileProxy(appContext, serviceListener, BluetoothProfile.A2DP)
            adapter?.getProfileProxy(appContext, serviceListener, BluetoothProfile.HEADSET)
        }
    }

    override fun bondedDevices(): List<PairedDevice> {
        if (!PermissionManager.canUseBluetooth(appContext)) return emptyList()
        return try {
            adapter?.bondedDevices.orEmpty()
                .map { PairedDevice(address = it.address, name = it.name ?: it.address) }
                .sortedBy { it.name.lowercase() }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    override fun isConnected(address: String): Boolean {
        if (!PermissionManager.canUseBluetooth(appContext)) return false
        return try {
            listOfNotNull(a2dp, headset).any { profile ->
                profile.connectedDevices.any { it.address.equals(address, ignoreCase = true) }
            }
        } catch (_: SecurityException) {
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
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
            ?: return@withContext false
        try {
            device.createInsecureRfcommSocketToServiceRecord(SPP_UUID).use { it.connect() }
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IOException) {
            // The overwhelmingly common outcome: no SPP service, or the helmet is simply off.
            false
        }
    }

    override fun close() {
        runCatching {
            a2dp?.let { adapter?.closeProfileProxy(BluetoothProfile.A2DP, it) }
            headset?.let { adapter?.closeProfileProxy(BluetoothProfile.HEADSET, it) }
        }
        a2dp = null
        headset = null
    }

    private companion object {
        /** The standard Serial Port Profile UUID, which nearly every intercom advertises. */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}

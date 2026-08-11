package com.shadatrahman.bikemode.bluetooth

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService

/** [BluetoothRequester] backed by the real adapter. */
class BluetoothController(context: Context) : BluetoothRequester {

    private val appContext = context.applicationContext

    private val adapter get() = appContext.getSystemService<BluetoothManager>()?.adapter

    /**
     * Reading the adapter state needs BLUETOOTH_CONNECT, which the rider may not have granted yet.
     * Treating that as "off" is the useful answer: it sends us to [requestEnable], which asks for
     * the permission and then quietly finishes if Bluetooth turns out to be on already.
     */
    override fun isEnabled(): Boolean = runCatching { adapter?.isEnabled == true }.getOrDefault(false)

    /**
     * Routed through [BluetoothRequestActivity] because the request is an activity and most taps
     * that start Bike Mode come from the tile or the widget, where there is no activity to host it.
     * A background-start refusal is survivable — Bike Mode itself is already on either way.
     */
    override fun requestEnable() {
        if (adapter == null) return
        runCatching {
            appContext.startActivity(
                Intent(appContext, BluetoothRequestActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

package com.shadatrahman.bikemode.bluetooth

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.getSystemService
import com.shadatrahman.bikemode.util.reportingFailure

/** [BluetoothRequester] backed by the real adapter. */
class BluetoothController(context: Context) : BluetoothRequester {

    private val appContext = context.applicationContext

    private val adapter get() = appContext.getSystemService<BluetoothManager>()?.adapter

    /**
     * Reading the adapter state needs BLUETOOTH_CONNECT, which the rider may not have granted yet.
     * Treating that as "off" is the useful answer: it sends us to [requestEnable], which asks for
     * the permission and then quietly finishes if Bluetooth turns out to be on already.
     */
    override fun isEnabled(): Boolean =
        reportingFailure(TAG, "Reading whether Bluetooth is on", false) { adapter?.isEnabled == true }

    /**
     * Routed through [BluetoothRequestActivity] because the request is an activity and most taps
     * that start Bike Mode come from the tile or the widget, where there is no activity to host it.
     * A background-start refusal is survivable — Bike Mode itself is already on either way.
     */
    override fun requestEnable() {
        if (adapter == null) {
            Log.i(TAG, "Not asking to turn Bluetooth on: this device has no adapter")
            return
        }
        // A background-start refusal is the expected failure and is survivable — Bike Mode itself
        // is on either way — but it is also exactly why the dialog would fail to appear, so say so.
        reportingFailure(TAG, "Showing the turn-on dialog", Unit) {
            appContext.startActivity(
                Intent(appContext, BluetoothRequestActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private companion object {
        const val TAG = "BluetoothController"
    }
}

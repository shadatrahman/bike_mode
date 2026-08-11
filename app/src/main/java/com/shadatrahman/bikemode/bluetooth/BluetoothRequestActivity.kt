package com.shadatrahman.bikemode.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.getSystemService
import com.shadatrahman.bikemode.util.reportingFailure

/**
 * Invisible trampoline that hosts the system "turn on Bluetooth?" dialog.
 *
 * It draws nothing of its own. It exists because `ACTION_REQUEST_ENABLE` and the BLUETOOTH_CONNECT
 * runtime prompt both need an activity, while Bike Mode is usually started from the Quick Settings
 * tile or the home-screen widget, neither of which has one.
 *
 * Every path out of here calls [finish]: the rider sees one dialog at most and lands back where
 * they were.
 */
class BluetoothRequestActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) askToEnable() else finish()
        }

    private val enableLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val granted = checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) askToEnable() else permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
    }

    /** No adapter, or Bluetooth already on, means there is nothing to ask and nothing to show. */
    private fun askToEnable() {
        val adapter = getSystemService<BluetoothManager>()?.adapter
        // Defaulting to "already on" when the state cannot be read keeps a phone that refuses the
        // question from being asked to turn on something that may well be on.
        val alreadyOn = reportingFailure(TAG, "Reading whether Bluetooth is on", true) {
            adapter?.isEnabled == true
        }
        if (adapter == null || alreadyOn) {
            finish()
            return
        }
        try {
            enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } catch (e: Exception) {
            Log.w(TAG, "Could not show the turn-on dialog: ${e.javaClass.simpleName}: ${e.message}")
            finish()
        }
    }

    private companion object {
        const val TAG = "BluetoothRequest"
    }
}

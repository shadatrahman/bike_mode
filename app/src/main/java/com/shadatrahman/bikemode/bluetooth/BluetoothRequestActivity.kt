package com.shadatrahman.bikemode.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.getSystemService

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
        val alreadyOn = runCatching { adapter?.isEnabled == true }.getOrDefault(true)
        if (adapter == null || alreadyOn) {
            finish()
            return
        }
        runCatching { enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
            .onFailure { finish() }
    }
}

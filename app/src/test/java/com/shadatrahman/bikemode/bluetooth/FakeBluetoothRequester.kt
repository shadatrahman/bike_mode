package com.shadatrahman.bikemode.bluetooth

/** In-memory [BluetoothRequester]. Records asks rather than performing them. */
class FakeBluetoothRequester(private var enabled: Boolean = false) : BluetoothRequester {

    var requests = 0
        private set

    override fun isEnabled(): Boolean = enabled

    /** Stands in for the rider accepting the system dialog. */
    override fun requestEnable() {
        requests++
        enabled = true
    }
}

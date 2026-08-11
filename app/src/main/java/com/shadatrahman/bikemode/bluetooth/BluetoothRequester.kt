package com.shadatrahman.bikemode.bluetooth

/**
 * Turning Bluetooth on when the ride starts, so a helmet intercom or earbuds connect without the
 * rider digging through Quick Settings.
 *
 * Deliberately one-way. `BluetoothAdapter.enable()` is a no-op for apps targeting API 33+, and the
 * only sanctioned replacement is `ACTION_REQUEST_ENABLE`, a system dialog the user confirms. There
 * is no matching public "request disable", so Bike Mode can raise Bluetooth and never lowers it —
 * see [com.shadatrahman.bikemode.rotation.BikeModeManager.disable], which leaves it alone.
 *
 * A seam rather than a direct call, so the toggle rules stay testable without Android.
 */
interface BluetoothRequester {

    /** Whether Bluetooth is already on. False when there is no adapter, or no permission to ask. */
    fun isEnabled(): Boolean

    /** Asks the system to turn Bluetooth on. The rider always gets the final say in a dialog. */
    fun requestEnable()
}

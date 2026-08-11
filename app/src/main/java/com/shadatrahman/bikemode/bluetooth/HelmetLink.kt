package com.shadatrahman.bikemode.bluetooth

import com.shadatrahman.bikemode.data.PairedDevice

/** Where the rider's chosen device stands right now. */
enum class HelmetState {
    /** No device chosen, so there is nothing to report. */
    NONE,

    /** Chosen, not connected yet, still inside the window where that is expected. */
    WAITING,

    CONNECTED,

    /** Chosen, and still not connected after the wait and the nudge. */
    MISSING,
}

/**
 * Everything Bike Mode is allowed to do about the rider's helmet intercom.
 *
 * Which is less than you would hope. `BluetoothA2dp` and `BluetoothHeadset` expose only
 * `getConnectedDevices`, `getConnectionState` and `isAudioConnected` — the matching `connect()`
 * methods are hidden behind BLUETOOTH_PRIVILEGED, a signature permission. So an ordinary app can
 * *observe* an audio connection but never *command* one.
 *
 * [nudge] is the one lever left, and it is indirect: opening an RFCOMM link to a bonded device
 * often prompts an intercom to bring up its own audio profiles in response. It works on many
 * headsets and does nothing on others, so every caller must treat it as best effort.
 */
interface HelmetLink {

    /** Devices already paired with the phone, for the rider to pick from. Empty without permission. */
    fun bondedDevices(): List<PairedDevice>

    /** Whether [address] is connected on A2DP or hands-free right now. */
    fun isConnected(address: String): Boolean

    /** Best-effort attempt to wake the link. Returns whether the socket opened, not whether audio came up. */
    suspend fun nudge(address: String): Boolean

    /** Releases the profile proxies. Safe to call more than once. */
    fun close()
}

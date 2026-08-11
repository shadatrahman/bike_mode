package com.shadatrahman.bikemode.data

import android.view.Surface

/** The two landscape orientations a rider can mount the phone in. */
enum class LandscapeDirection(val surfaceRotation: Int) {
    LEFT(Surface.ROTATION_90),
    RIGHT(Surface.ROTATION_270);

    companion object {
        fun fromSurfaceRotation(rotation: Int): LandscapeDirection =
            entries.firstOrNull { it.surfaceRotation == rotation } ?: RIGHT
    }
}

/**
 * A Bluetooth device the rider has singled out — the helmet intercom, typically.
 *
 * Held by address rather than by name because names are not unique and change when a device is
 * renamed; the name is carried alongside only so the UI has something readable to show.
 */
data class PairedDevice(val address: String, val name: String)

/** The Android rotation settings as they were before Bike Mode touched them. */
data class SavedRotationState(
    val accelerometerRotation: Int,
    val userRotation: Int,
)

data class BikeModePreferences(
    val direction: LandscapeDirection = LandscapeDirection.RIGHT,
    val bikeModeActive: Boolean = false,
    val previous: SavedRotationState? = null,
    val firstLaunchCompleted: Boolean = false,
    /** Ask to turn Bluetooth on when the ride starts. Opt-out, since a helmet intercom is the norm. */
    val bluetoothOnEnable: Boolean = true,
    /** The device Bike Mode watches for once the ride starts. Null means it watches nothing. */
    val helmet: PairedDevice? = null,
    /** Pause whatever is playing when the ride ends. Opt-out, like the Bluetooth prompt. */
    val pauseMediaOnDisable: Boolean = true,
)

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

/**
 * The display settings as they were before Bike Mode touched them.
 *
 * Each field is null when Bike Mode never changed it, so restoring can put back exactly what it
 * took and nothing else — a rider who dimmed the screen by hand mid-ride keeps that.
 */
data class SavedDisplayState(
    val screenOffTimeout: Int? = null,
    val brightness: Int? = null,
    val brightnessMode: Int? = null,
) {
    val isEmpty: Boolean get() = screenOffTimeout == null && brightness == null && brightnessMode == null
}

data class BikeModePreferences(
    val direction: LandscapeDirection = LandscapeDirection.RIGHT,
    val bikeModeActive: Boolean = false,
    val previous: SavedRotationState? = null,
    val previousDisplay: SavedDisplayState? = null,
    val firstLaunchCompleted: Boolean = false,
    /** Ask to turn Bluetooth on when the ride starts. Opt-out, since a helmet intercom is the norm. */
    val bluetoothOnEnable: Boolean = true,
    /** The device Bike Mode watches for once the ride starts. Null means it watches nothing. */
    val helmet: PairedDevice? = null,
    /** Pause whatever is playing when the ride ends. Opt-out, like the Bluetooth prompt. */
    val pauseMediaOnDisable: Boolean = true,
    /** Hold the screen awake while riding. Opt-out: a lock that sleeps at a red light is no use. */
    val keepScreenOn: Boolean = true,
    /** Force full brightness for sunlight. Opt-in, because it costs battery and can dazzle at night. */
    val boostBrightness: Boolean = false,
    /** Start and stop Bike Mode with the helmet. Opt-in: it changes when the app acts on its own. */
    val autoStartWithHelmet: Boolean = false,
)

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
)

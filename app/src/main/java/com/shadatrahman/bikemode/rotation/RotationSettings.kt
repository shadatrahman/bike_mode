package com.shadatrahman.bikemode.rotation

import com.shadatrahman.bikemode.data.LandscapeDirection
import com.shadatrahman.bikemode.data.SavedRotationState

/**
 * System-rotation seam. Writes return [Result] because Settings.System writes fail (or throw
 * SecurityException) when WRITE_SETTINGS is missing or the device refuses the change.
 */
interface RotationSettings {

    fun readState(): SavedRotationState

    fun isAutoRotateEnabled(): Boolean

    /** Disables sensor rotation, then pins the display to [direction]. */
    fun applyBikeMode(direction: LandscapeDirection): Result<Unit>

    /** Puts back [previous], or plain auto-rotate when nothing was recorded. */
    fun restore(previous: SavedRotationState?): Result<Unit>

    companion object {
        const val AUTO_ROTATE_OFF = 0
        const val AUTO_ROTATE_ON = 1
    }
}

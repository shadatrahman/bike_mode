package com.shadatrahman.bikemode.rotation

import android.view.Surface
import com.shadatrahman.bikemode.data.LandscapeDirection
import com.shadatrahman.bikemode.data.SavedRotationState
import com.shadatrahman.bikemode.rotation.RotationSettings.Companion.AUTO_ROTATE_OFF
import com.shadatrahman.bikemode.rotation.RotationSettings.Companion.AUTO_ROTATE_ON

/**
 * In-memory stand-in for Android's rotation settings. Mirrors what `Settings.System` would hold,
 * so a test can assert on the values a rider's device would actually be left with.
 */
class FakeRotationSettings(
    accelerometerRotation: Int = AUTO_ROTATE_ON,
    userRotation: Int = Surface.ROTATION_0,
) : RotationSettings {

    var state = SavedRotationState(accelerometerRotation, userRotation)
        private set

    /** Simulates a device that refuses the write, e.g. revoked WRITE_SETTINGS. */
    var failWrites = false

    var applyCount = 0
        private set

    /** Stands in for the system or another app rewriting the settings behind Bike Mode's back. */
    fun rewrittenExternallyTo(newState: SavedRotationState) {
        state = newState
    }

    override fun readState(): SavedRotationState = state

    override fun isAutoRotateEnabled(): Boolean = state.accelerometerRotation == AUTO_ROTATE_ON

    override fun applyBikeMode(direction: LandscapeDirection): Result<Unit> = write {
        applyCount++
        state = SavedRotationState(AUTO_ROTATE_OFF, direction.surfaceRotation)
    }

    override fun restore(previous: SavedRotationState?): Result<Unit> = write {
        state = previous ?: state.copy(accelerometerRotation = AUTO_ROTATE_ON)
    }

    private fun write(block: () -> Unit): Result<Unit> =
        if (failWrites) {
            Result.failure(IllegalStateException("System rejected write"))
        } else {
            Result.success(block())
        }
}

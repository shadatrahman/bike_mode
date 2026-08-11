package com.shadatrahman.bikemode.rotation

import android.content.Context
import android.provider.Settings
import android.view.Surface
import com.shadatrahman.bikemode.data.LandscapeDirection
import com.shadatrahman.bikemode.data.SavedRotationState

/**
 * Reads and writes Android's system rotation settings.
 *
 * Every write needs the WRITE_SETTINGS permission and throws [SecurityException] without it, so
 * writes are wrapped in [Result] and callers surface the failure instead of crashing.
 */
class RotationController(context: Context) {

    private val resolver = context.applicationContext.contentResolver

    fun readState(): SavedRotationState = SavedRotationState(
        accelerometerRotation = accelerometerRotation(),
        userRotation = userRotation(),
    )

    fun accelerometerRotation(): Int = Settings.System.getInt(
        resolver,
        Settings.System.ACCELEROMETER_ROTATION,
        AUTO_ROTATE_ON,
    )

    fun userRotation(): Int = Settings.System.getInt(
        resolver,
        Settings.System.USER_ROTATION,
        Surface.ROTATION_0,
    )

    fun isAutoRotateEnabled(): Boolean = accelerometerRotation() == AUTO_ROTATE_ON

    /**
     * Disables sensor rotation, then pins the display to [direction]. Order matters: while
     * auto-rotate is still on, the sensor immediately overrides USER_ROTATION.
     */
    fun applyBikeMode(direction: LandscapeDirection): Result<Unit> = runCatching {
        writeInt(Settings.System.ACCELEROMETER_ROTATION, AUTO_ROTATE_OFF)
        writeInt(Settings.System.USER_ROTATION, direction.surfaceRotation)
    }

    /**
     * Puts back what the user had before Bike Mode. Falls back to plain auto-rotate when no
     * previous state was recorded.
     */
    fun restore(previous: SavedRotationState?): Result<Unit> = runCatching {
        if (previous == null) {
            writeInt(Settings.System.ACCELEROMETER_ROTATION, AUTO_ROTATE_ON)
            return@runCatching
        }
        writeInt(Settings.System.USER_ROTATION, previous.userRotation)
        writeInt(Settings.System.ACCELEROMETER_ROTATION, previous.accelerometerRotation)
    }

    private fun writeInt(key: String, value: Int) {
        check(Settings.System.putInt(resolver, key, value)) { "System rejected write to $key" }
    }

    private companion object {
        const val AUTO_ROTATE_OFF = 0
        const val AUTO_ROTATE_ON = 1
    }
}

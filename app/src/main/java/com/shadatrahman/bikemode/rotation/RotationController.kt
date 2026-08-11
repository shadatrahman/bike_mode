package com.shadatrahman.bikemode.rotation

import android.content.Context
import android.provider.Settings
import android.view.Surface
import com.shadatrahman.bikemode.data.LandscapeDirection
import com.shadatrahman.bikemode.data.SavedRotationState
import com.shadatrahman.bikemode.rotation.RotationSettings.Companion.AUTO_ROTATE_OFF
import com.shadatrahman.bikemode.rotation.RotationSettings.Companion.AUTO_ROTATE_ON

/** [RotationSettings] backed by Android's real `Settings.System` values. */
class RotationController(context: Context) : RotationSettings {

    private val resolver = context.applicationContext.contentResolver

    override fun readState(): SavedRotationState = SavedRotationState(
        accelerometerRotation = accelerometerRotation(),
        userRotation = userRotation(),
    )

    override fun isAutoRotateEnabled(): Boolean = accelerometerRotation() == AUTO_ROTATE_ON

    /**
     * Order matters: while auto-rotate is still on, the sensor immediately overrides
     * USER_ROTATION, so auto-rotate has to go off first.
     */
    override fun applyBikeMode(direction: LandscapeDirection): Result<Unit> = runCatching {
        writeInt(Settings.System.ACCELEROMETER_ROTATION, AUTO_ROTATE_OFF)
        writeInt(Settings.System.USER_ROTATION, direction.surfaceRotation)
    }

    override fun restore(previous: SavedRotationState?): Result<Unit> = runCatching {
        if (previous == null) {
            writeInt(Settings.System.ACCELEROMETER_ROTATION, AUTO_ROTATE_ON)
            return@runCatching
        }
        writeInt(Settings.System.USER_ROTATION, previous.userRotation)
        writeInt(Settings.System.ACCELEROMETER_ROTATION, previous.accelerometerRotation)
    }

    private fun accelerometerRotation(): Int = Settings.System.getInt(
        resolver,
        Settings.System.ACCELEROMETER_ROTATION,
        AUTO_ROTATE_ON,
    )

    private fun userRotation(): Int = Settings.System.getInt(
        resolver,
        Settings.System.USER_ROTATION,
        Surface.ROTATION_0,
    )

    private fun writeInt(key: String, value: Int) {
        check(Settings.System.putInt(resolver, key, value)) { "System rejected write to $key" }
    }
}

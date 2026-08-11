package com.shadatrahman.bikemode.display

import com.shadatrahman.bikemode.data.SavedDisplayState

/** In-memory [DisplaySettings] holding the three values Bike Mode can change. */
class FakeDisplaySettings(
    var timeout: Int = DEFAULT_TIMEOUT,
    var brightness: Int = 120,
    var brightnessMode: Int = AUTOMATIC,
) : DisplaySettings {

    var failWrites = false

    override fun screenOffTimeout(): Int = timeout

    override fun brightness(): Int = brightness

    override fun brightnessMode(): Int = brightnessMode

    override fun applyKeepAwake(): Result<Unit> = write { timeout = RIDE_TIMEOUT }

    override fun applyBrightnessBoost(): Result<Unit> = write {
        brightnessMode = MANUAL
        brightness = MAX
    }

    override fun restore(previous: SavedDisplayState?): Result<Unit> = write {
        previous?.screenOffTimeout?.let { timeout = it }
        previous?.brightness?.let { brightness = it }
        previous?.brightnessMode?.let { brightnessMode = it }
    }

    private fun write(block: () -> Unit): Result<Unit> =
        if (failWrites) Result.failure(IllegalStateException("System rejected write"))
        else Result.success(block())

    companion object {
        const val DEFAULT_TIMEOUT = 30_000
        const val RIDE_TIMEOUT = 30 * 60 * 1000
        const val AUTOMATIC = 1
        const val MANUAL = 0
        const val MAX = 255
    }
}

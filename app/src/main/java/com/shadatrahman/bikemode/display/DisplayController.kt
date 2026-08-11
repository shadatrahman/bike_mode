package com.shadatrahman.bikemode.display

import android.content.Context
import android.provider.Settings
import com.shadatrahman.bikemode.data.SavedDisplayState

/** [DisplaySettings] backed by Android's real `Settings.System` values. */
class DisplayController(context: Context) : DisplaySettings {

    private val resolver = context.applicationContext.contentResolver

    override fun screenOffTimeout(): Int =
        read(Settings.System.SCREEN_OFF_TIMEOUT, DEFAULT_TIMEOUT_MS)

    override fun brightness(): Int = read(Settings.System.SCREEN_BRIGHTNESS, MAX_BRIGHTNESS)

    override fun brightnessMode(): Int = read(
        Settings.System.SCREEN_BRIGHTNESS_MODE,
        Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
    )

    override fun applyKeepAwake(): Result<Unit> = runCatching {
        write(Settings.System.SCREEN_OFF_TIMEOUT, RIDE_TIMEOUT_MS)
    }

    /**
     * Order matters: automatic brightness overrides whatever we write, so the mode has to go
     * manual first — the same trap as auto-rotate overriding USER_ROTATION.
     */
    override fun applyBrightnessBoost(): Result<Unit> = runCatching {
        write(
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
        write(Settings.System.SCREEN_BRIGHTNESS, MAX_BRIGHTNESS)
    }

    override fun restore(previous: SavedDisplayState?): Result<Unit> = runCatching {
        if (previous == null) return@runCatching
        previous.screenOffTimeout?.let { write(Settings.System.SCREEN_OFF_TIMEOUT, it) }
        // Brightness before mode: going back to automatic recomputes the level anyway, and this
        // way a rider who was on manual gets their exact level back.
        previous.brightness?.let { write(Settings.System.SCREEN_BRIGHTNESS, it) }
        previous.brightnessMode?.let { write(Settings.System.SCREEN_BRIGHTNESS_MODE, it) }
    }

    private fun read(key: String, fallback: Int): Int =
        Settings.System.getInt(resolver, key, fallback)

    private fun write(key: String, value: Int) {
        check(Settings.System.putInt(resolver, key, value)) { "System rejected write to $key" }
    }

    private companion object {
        /**
         * Thirty minutes rather than "never": long enough to outlast any ride between stops, but
         * still a backstop if Bike Mode is somehow left on with the phone in a pocket.
         */
        const val RIDE_TIMEOUT_MS = 30 * 60 * 1000

        const val DEFAULT_TIMEOUT_MS = 60 * 1000
        const val MAX_BRIGHTNESS = 255
    }
}

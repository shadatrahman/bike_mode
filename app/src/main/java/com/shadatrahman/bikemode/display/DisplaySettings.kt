package com.shadatrahman.bikemode.display

import com.shadatrahman.bikemode.data.SavedDisplayState

/**
 * The two display settings a ride wants changed, and putting them back afterwards.
 *
 * Both live in `Settings.System`, so WRITE_SETTINGS — the permission Bike Mode already holds for
 * rotation — covers them. No new permission, no new prompt.
 *
 * A seam rather than direct calls, so the save-and-restore rules stay testable without Android.
 */
interface DisplaySettings {

    fun screenOffTimeout(): Int

    fun brightness(): Int

    fun brightnessMode(): Int

    /** Stops the screen sleeping at a red light, which would strand the rider mid-navigation. */
    fun applyKeepAwake(): Result<Unit>

    /** Full manual brightness, for a screen being read in direct sun. */
    fun applyBrightnessBoost(): Result<Unit>

    /** Writes back only the fields [previous] actually holds; nulls are left alone. */
    fun restore(previous: SavedDisplayState?): Result<Unit>
}

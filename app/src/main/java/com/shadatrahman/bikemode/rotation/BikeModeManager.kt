package com.shadatrahman.bikemode.rotation

import android.content.Context
import com.shadatrahman.bikemode.bluetooth.BluetoothController
import com.shadatrahman.bikemode.bluetooth.BluetoothRequester
import com.shadatrahman.bikemode.data.BikeModePreferences
import com.shadatrahman.bikemode.data.BikeModeStore
import com.shadatrahman.bikemode.data.LandscapeDirection
import com.shadatrahman.bikemode.data.PairedDevice
import com.shadatrahman.bikemode.data.PreferencesRepository
import com.shadatrahman.bikemode.data.SavedDisplayState
import com.shadatrahman.bikemode.display.AmbientLight
import com.shadatrahman.bikemode.display.DaylightGate
import com.shadatrahman.bikemode.display.DisplayController
import com.shadatrahman.bikemode.display.DisplaySettings
import com.shadatrahman.bikemode.display.SensorAmbientLight
import com.shadatrahman.bikemode.media.MediaPauseController
import com.shadatrahman.bikemode.media.MediaPauser
import com.shadatrahman.bikemode.notifications.InterruptionController
import com.shadatrahman.bikemode.notifications.InterruptionSettings

/**
 * Orchestrates the Bike Mode toggle: saves the rider's rotation state before locking landscape,
 * and restores that exact state when Bike Mode goes off.
 *
 * Shared by the app UI and the Quick Settings tile so both take the same path.
 */
class BikeModeManager(
    private val store: BikeModeStore,
    private val settings: RotationSettings,
    private val watchdog: RotationWatchdog,
    private val bluetooth: BluetoothRequester,
    private val media: MediaPauser,
    private val display: DisplaySettings,
    private val ambientLight: AmbientLight,
    private val interruptions: InterruptionSettings,
) {

    constructor(context: Context) : this(
        store = PreferencesRepository(context),
        settings = RotationController(context),
        watchdog = ServiceRotationWatchdog(context),
        bluetooth = BluetoothController(context),
        media = MediaPauseController(context),
        display = DisplayController(context),
        ambientLight = SensorAmbientLight(context),
        interruptions = InterruptionController(context),
    )

    /**
     * Bike Mode is only really on if we flagged it on *and* auto-rotate is still off. The user can
     * re-enable auto-rotate from the system Quick Settings behind our back, which silently ends
     * Bike Mode; treat that as off so the next tap re-locks instead of "restoring" stale state.
     */
    suspend fun isActive(): Boolean {
        val prefs = store.current()
        if (!prefs.bikeModeActive) return false
        if (settings.isAutoRotateEnabled()) {
            store.markInactive()
            watchdog.stop()
            return false
        }
        return true
    }

    suspend fun preferences(): BikeModePreferences = store.current()

    /**
     * [requestBluetooth] is off for the boot path: an activity cannot launch from a boot broadcast,
     * and waking a rider's phone with a dialog after a restart would be wrong even if it could.
     */
    suspend fun enable(requestBluetooth: Boolean = true): Result<Unit> {
        val prefs = store.current()
        // Only capture the previous state on a genuine off -> on transition, otherwise a re-apply
        // would overwrite it with Bike Mode's own values and lose what we owe the user.
        val previous = prefs.previous.takeIf { prefs.bikeModeActive } ?: settings.readState()
        val previousDisplay = prefs.previousDisplay.takeIf { prefs.bikeModeActive } ?: captureDisplay(prefs)
        val previousInterruption = prefs.previousInterruptionFilter.takeIf { prefs.bikeModeActive }
            ?: captureInterruption(prefs)
        return settings.applyBikeMode(prefs.direction)
            .onSuccess {
                store.markActive(previous, previousDisplay, previousInterruption)
                applyDisplay(prefs)
                if (prefs.silenceNotifications) interruptions.apply()
                watchdog.start()
                if (requestBluetooth && prefs.bluetoothOnEnable) raiseBluetooth()
            }
    }

    /**
     * Only the settings Bike Mode is about to change are captured; the rest stay null so [disable]
     * knows it owes the rider nothing for them.
     */
    /**
     * Null unless Bike Mode is actually going to silence anything, so [disable] knows it owes the
     * rider nothing and leaves a filter they set themselves alone.
     */
    private fun captureInterruption(prefs: BikeModePreferences): Int? =
        interruptions.current().takeIf { prefs.silenceNotifications && interruptions.canControl }

    private fun captureDisplay(prefs: BikeModePreferences) = SavedDisplayState(
        screenOffTimeout = display.screenOffTimeout().takeIf { prefs.keepScreenOn },
        brightness = display.brightness().takeIf { prefs.boostBrightness },
        brightnessMode = display.brightnessMode().takeIf { prefs.boostBrightness },
    )

    /**
     * Best effort on purpose. A device that refuses a brightness write should still get its
     * landscape lock — the rotation is the feature, the display settings are comfort on top.
     */
    private suspend fun applyDisplay(prefs: BikeModePreferences) {
        if (prefs.keepScreenOn) display.applyKeepAwake()
        if (prefs.boostBrightness && isDaylight()) display.applyBrightnessBoost()
    }

    /**
     * The switch says the rider wants a bright screen in sun; it does not say the sun is out. A
     * ride starting after dark must not be met with full brightness, so the sensor has the final
     * say — and where there is no sensor to ask, the switch stands on its own.
     *
     * [RotationWatchdogService] keeps watching the same reading for the rest of the ride, so a
     * commute that ends after sunset gives the brightness back on the way.
     */
    private suspend fun isDaylight(): Boolean {
        if (!ambientLight.isAvailable) return true
        val lux = ambientLight.currentLux() ?: return true
        return DaylightGate().update(lux) == true
    }

    suspend fun disable(): Result<Unit> {
        val prefs = store.current()
        return settings.restore(prefs.previous)
            .onSuccess {
                // Before markInactive clears what we owe: the rider gets their screen back even if
                // the display writes fail, since a stuck-awake screen is worse than a failed lock.
                display.restore(prefs.previousDisplay)
                interruptions.restore(prefs.previousInterruptionFilter)
                store.markInactive()
                watchdog.stop()
                // Every off switch — app, tile, widget, notification — lands here, so the media
                // pause belongs here too rather than at each of them.
                if (prefs.pauseMediaOnDisable) media.pause()
            }
    }

    /**
     * Re-applies the pinned rotation if something knocked it loose — the system rewrites
     * USER_ROTATION when a portrait-locked app takes the foreground, which would otherwise leave
     * Bike Mode claiming to be on while the screen no longer holds landscape.
     *
     * Returns whether Bike Mode is still active, so callers know whether to keep watching.
     */
    suspend fun reassert(): Boolean {
        if (!isActive()) return false
        val prefs = store.current()
        if (settings.readState().userRotation != prefs.direction.surfaceRotation) {
            settings.applyBikeMode(prefs.direction)
        }
        return true
    }

    /** Returns the resulting active state, or the failure that stopped the toggle. */
    suspend fun toggle(): Result<Boolean> =
        if (isActive()) disable().map { false } else enable().map { true }

    /** Changing direction while riding should take effect immediately. */
    suspend fun setDirection(direction: LandscapeDirection): Result<Unit> {
        store.setDirection(direction)
        return if (isActive()) settings.applyBikeMode(direction) else Result.success(Unit)
    }

    /**
     * The watchdog service reads the choice when it next comes up, so changing it mid-ride takes
     * effect on the following toggle rather than restarting the watch under the rider.
     */
    suspend fun setHelmet(device: PairedDevice?) = store.setHelmet(device)

    /** Switching this on mid-ride acts at once, so the rider sees the setting do something. */
    suspend fun setBluetoothOnEnable(enabled: Boolean) {
        store.setBluetoothOnEnable(enabled)
        if (enabled && isActive()) raiseBluetooth()
    }

    /** Unlike the Bluetooth one, this has no immediate effect to show: it acts when the ride ends. */
    suspend fun setPauseMediaOnDisable(enabled: Boolean) = store.setPauseMediaOnDisable(enabled)

    /**
     * Display settings changed mid-ride take effect at once, and switching one off hands that
     * setting straight back rather than holding it hostage until the ride ends.
     */
    suspend fun setKeepScreenOn(enabled: Boolean) {
        store.setKeepScreenOn(enabled)
        if (!isActive()) return
        if (enabled) {
            rememberDisplay { it.copy(screenOffTimeout = display.screenOffTimeout()) }
            display.applyKeepAwake()
        } else {
            val owed = store.current().previousDisplay
            display.restore(SavedDisplayState(screenOffTimeout = owed?.screenOffTimeout))
            rememberDisplay { it.copy(screenOffTimeout = null) }
        }
    }

    suspend fun setBoostBrightness(enabled: Boolean) {
        store.setBoostBrightness(enabled)
        if (!isActive()) return
        if (enabled) {
            rememberDisplay {
                it.copy(brightness = display.brightness(), brightnessMode = display.brightnessMode())
            }
            if (isDaylight()) display.applyBrightnessBoost()
        } else {
            val owed = store.current().previousDisplay
            display.restore(
                SavedDisplayState(brightness = owed?.brightness, brightnessMode = owed?.brightnessMode)
            )
            rememberDisplay { it.copy(brightness = null, brightnessMode = null) }
        }
    }

    suspend fun setAutoStartWithHelmet(enabled: Boolean) = store.setAutoStartWithHelmet(enabled)

    /** Like the display settings, this takes effect at once and hands back at once. */
    suspend fun setSilenceNotifications(enabled: Boolean) {
        store.setSilenceNotifications(enabled)
        if (!isActive() || !interruptions.canControl) return
        val prefs = store.current()
        if (enabled) {
            rememberRide(prefs.previousDisplay, interruptions.current())
            interruptions.apply()
        } else {
            interruptions.restore(prefs.previousInterruptionFilter)
            rememberRide(prefs.previousDisplay, null)
        }
    }

    /** Rewrites what Bike Mode owes the rider without disturbing the rotation half of it. */
    private suspend fun rememberDisplay(edit: (SavedDisplayState) -> SavedDisplayState) {
        val prefs = store.current()
        rememberRide(edit(prefs.previousDisplay ?: SavedDisplayState()), prefs.previousInterruptionFilter)
    }

    private suspend fun rememberRide(display: SavedDisplayState?, interruption: Int?) {
        val prefs = store.current()
        val rotation = prefs.previous ?: settings.readState()
        store.markActive(rotation, display ?: SavedDisplayState(), interruption)
    }

    /**
     * Never lowers Bluetooth, only raises it, and only when it is actually down — so a rider who
     * already has an intercom paired never sees a dialog at all. See [BluetoothRequester] for why
     * the reverse is not available to us.
     */
    private fun raiseBluetooth() {
        if (!bluetooth.isEnabled()) bluetooth.requestEnable()
    }
}

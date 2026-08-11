package com.shadatrahman.bikemode.rotation

import android.content.Context
import com.shadatrahman.bikemode.data.BikeModePreferences
import com.shadatrahman.bikemode.data.BikeModeStore
import com.shadatrahman.bikemode.data.LandscapeDirection
import com.shadatrahman.bikemode.data.PreferencesRepository

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
) {

    constructor(context: Context) : this(
        store = PreferencesRepository(context),
        settings = RotationController(context),
        watchdog = JobRotationWatchdog(context),
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

    suspend fun enable(): Result<Unit> {
        val prefs = store.current()
        // Only capture the previous state on a genuine off -> on transition, otherwise a re-apply
        // would overwrite it with Bike Mode's own values and lose what we owe the user.
        val previous = prefs.previous.takeIf { prefs.bikeModeActive } ?: settings.readState()
        return settings.applyBikeMode(prefs.direction)
            .onSuccess {
                store.markActive(previous)
                watchdog.start()
            }
    }

    suspend fun disable(): Result<Unit> {
        val prefs = store.current()
        return settings.restore(prefs.previous)
            .onSuccess {
                store.markInactive()
                watchdog.stop()
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
}

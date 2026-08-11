package com.shadatrahman.bikemode.rotation

import android.content.Context
import com.shadatrahman.bikemode.data.BikeModePreferences
import com.shadatrahman.bikemode.data.LandscapeDirection
import com.shadatrahman.bikemode.data.PreferencesRepository

/**
 * Orchestrates the Bike Mode toggle: saves the rider's rotation state before locking landscape,
 * and restores that exact state when Bike Mode goes off.
 *
 * Shared by the app UI and the Quick Settings tile so both take the same path.
 */
class BikeModeManager(
    context: Context,
    private val repository: PreferencesRepository = PreferencesRepository(context),
    private val controller: RotationController = RotationController(context),
) {

    /**
     * Bike Mode is only really on if we flagged it on *and* auto-rotate is still off. The user can
     * re-enable auto-rotate from the system Quick Settings behind our back, which silently ends
     * Bike Mode; treat that as off so the next tap re-locks instead of "restoring" stale state.
     */
    suspend fun isActive(): Boolean {
        val prefs = repository.current()
        if (!prefs.bikeModeActive) return false
        if (controller.isAutoRotateEnabled()) {
            repository.markInactive()
            return false
        }
        return true
    }

    suspend fun preferences(): BikeModePreferences = repository.current()

    suspend fun enable(): Result<Unit> {
        val prefs = repository.current()
        // Only capture the previous state on a genuine off -> on transition, otherwise a re-apply
        // would overwrite it with Bike Mode's own values and lose what we owe the user.
        val previous = prefs.previous.takeIf { prefs.bikeModeActive } ?: controller.readState()
        return controller.applyBikeMode(prefs.direction)
            .onSuccess { repository.markActive(previous) }
    }

    suspend fun disable(): Result<Unit> {
        val prefs = repository.current()
        return controller.restore(prefs.previous)
            .onSuccess { repository.markInactive() }
    }

    /** Returns the resulting active state, or the failure that stopped the toggle. */
    suspend fun toggle(): Result<Boolean> =
        if (isActive()) disable().map { false } else enable().map { true }

    /** Changing direction while riding should take effect immediately. */
    suspend fun setDirection(direction: LandscapeDirection): Result<Unit> {
        repository.setDirection(direction)
        return if (isActive()) controller.applyBikeMode(direction) else Result.success(Unit)
    }
}

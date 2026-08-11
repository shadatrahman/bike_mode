package com.shadatrahman.bikemode.data

import kotlinx.coroutines.flow.Flow

/** Persistence seam for Bike Mode state, so the toggle logic can be tested without Android. */
interface BikeModeStore {

    val preferences: Flow<BikeModePreferences>

    suspend fun current(): BikeModePreferences

    suspend fun setDirection(direction: LandscapeDirection)

    suspend fun setFirstLaunchCompleted()

    /** Records that Bike Mode is on, along with the state to restore when it goes off. */
    suspend fun markActive(previous: SavedRotationState)

    suspend fun markInactive()
}

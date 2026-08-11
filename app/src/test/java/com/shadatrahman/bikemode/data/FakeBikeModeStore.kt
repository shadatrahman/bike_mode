package com.shadatrahman.bikemode.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/** In-memory [BikeModeStore] with the same semantics as the DataStore-backed repository. */
class FakeBikeModeStore(
    initial: BikeModePreferences = BikeModePreferences(),
) : BikeModeStore {

    private val state = MutableStateFlow(initial)

    override val preferences: Flow<BikeModePreferences> = state

    override suspend fun current(): BikeModePreferences = preferences.first()

    override suspend fun setDirection(direction: LandscapeDirection) {
        state.update { it.copy(direction = direction) }
    }

    override suspend fun setFirstLaunchCompleted() {
        state.update { it.copy(firstLaunchCompleted = true) }
    }

    override suspend fun markActive(previous: SavedRotationState) {
        state.update { it.copy(bikeModeActive = true, previous = previous) }
    }

    override suspend fun markInactive() {
        state.update { it.copy(bikeModeActive = false, previous = null) }
    }
}

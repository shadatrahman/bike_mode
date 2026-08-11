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

    override suspend fun setBluetoothOnEnable(enabled: Boolean) {
        state.update { it.copy(bluetoothOnEnable = enabled) }
    }

    override suspend fun setHelmet(device: PairedDevice?) {
        state.update { it.copy(helmet = device) }
    }

    override suspend fun setPauseMediaOnDisable(enabled: Boolean) {
        state.update { it.copy(pauseMediaOnDisable = enabled) }
    }

    override suspend fun setKeepScreenOn(enabled: Boolean) {
        state.update { it.copy(keepScreenOn = enabled) }
    }

    override suspend fun setBoostBrightness(enabled: Boolean) {
        state.update { it.copy(boostBrightness = enabled) }
    }

    override suspend fun setAutoStartWithHelmet(enabled: Boolean) {
        state.update { it.copy(autoStartWithHelmet = enabled) }
    }

    override suspend fun setSilenceNotifications(enabled: Boolean) {
        state.update { it.copy(silenceNotifications = enabled) }
    }

    override suspend fun setBatteryGuard(enabled: Boolean) {
        state.update { it.copy(batteryGuard = enabled) }
    }

    override suspend fun markActive(
        previous: SavedRotationState,
        previousDisplay: SavedDisplayState,
        previousInterruptionFilter: Int?,
    ) {
        state.update {
            it.copy(
                bikeModeActive = true,
                previous = previous,
                previousDisplay = previousDisplay.takeIf { saved -> !saved.isEmpty },
                previousInterruptionFilter = previousInterruptionFilter,
            )
        }
    }

    override suspend fun markInactive() {
        state.update {
            it.copy(
                bikeModeActive = false,
                previous = null,
                previousDisplay = null,
                previousInterruptionFilter = null,
            )
        }
    }
}

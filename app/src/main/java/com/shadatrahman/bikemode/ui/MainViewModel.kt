package com.shadatrahman.bikemode.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shadatrahman.bikemode.bluetooth.BluetoothHelmetLink
import com.shadatrahman.bikemode.bluetooth.HelmetLink
import com.shadatrahman.bikemode.companion.HelmetAssociation
import com.shadatrahman.bikemode.data.LandscapeDirection
import com.shadatrahman.bikemode.data.PairedDevice
import com.shadatrahman.bikemode.data.PreferencesRepository
import com.shadatrahman.bikemode.rotation.BikeModeManager
import com.shadatrahman.bikemode.util.PermissionManager
import com.shadatrahman.bikemode.widget.BikeModeWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val loading: Boolean = true,
    val hasPermission: Boolean = false,
    val bikeModeActive: Boolean = false,
    val direction: LandscapeDirection = LandscapeDirection.RIGHT,
    val bluetoothOnEnable: Boolean = true,
    val pauseMediaOnDisable: Boolean = true,
    val keepScreenOn: Boolean = true,
    val boostBrightness: Boolean = false,
    val autoStartWithHelmet: Boolean = false,
    /** Set when a device needs associating; only an activity may ask, so the activity picks it up. */
    val associationRequest: PairedDevice? = null,
    /** Why the last association attempt failed, in the system's own words. Null when it did not. */
    val associationError: String? = null,
    val helmet: PairedDevice? = null,
    val pairedDevices: List<PairedDevice> = emptyList(),
    /** False means the paired list is empty because we may not read it, not because there is none. */
    val canListDevices: Boolean = false,
    val showError: Boolean = false,
)

class MainViewModel(
    private val application: Application,
    private val manager: BikeModeManager = BikeModeManager(application),
    private val repository: PreferencesRepository = PreferencesRepository(application),
    private val helmetLink: HelmetLink = BluetoothHelmetLink(application),
    private val association: HelmetAssociation = HelmetAssociation(application),
) : ViewModel() {

    override fun onCleared() {
        helmetLink.close()
        super.onCleared()
    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch { repository.setFirstLaunchCompleted() }
    }

    /** Called on every resume: WRITE_SETTINGS can be revoked, and rotation can change elsewhere. */
    fun refresh() {
        viewModelScope.launch {
            val hasPermission = PermissionManager.canWriteSettings(application)
            // Coming back to the app also repairs a rotation another app knocked loose.
            val active = hasPermission && manager.reassert()
            val preferences = manager.preferences()
            // Re-read on every resume: the rider may have paired the helmet, or granted Bluetooth
            // access, on a system screen since we last looked.
            val paired = helmetLink.bondedDevices()
            _uiState.update {
                it.copy(
                    loading = false,
                    hasPermission = hasPermission,
                    bikeModeActive = active,
                    direction = preferences.direction,
                    bluetoothOnEnable = preferences.bluetoothOnEnable,
                    pauseMediaOnDisable = preferences.pauseMediaOnDisable,
                    keepScreenOn = preferences.keepScreenOn,
                    boostBrightness = preferences.boostBrightness,
                    autoStartWithHelmet = preferences.autoStartWithHelmet,
                    helmet = preferences.helmet,
                    pairedDevices = paired,
                    canListDevices = PermissionManager.canUseBluetooth(application),
                )
            }
        }
    }

    fun toggleBikeMode() {
        viewModelScope.launch {
            manager.toggle()
                .onSuccess { active -> _uiState.update { it.copy(bikeModeActive = active, showError = false) } }
                .onFailure { _uiState.update { it.copy(showError = true) } }
            BikeModeWidgetProvider.refresh(application)
        }
    }

    fun setDirection(direction: LandscapeDirection) {
        viewModelScope.launch {
            _uiState.update { it.copy(direction = direction) }
            manager.setDirection(direction).onFailure { _uiState.update { it.copy(showError = true) } }
            BikeModeWidgetProvider.refresh(application)
        }
    }

    fun setBluetoothOnEnable(enabled: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(bluetoothOnEnable = enabled) }
            manager.setBluetoothOnEnable(enabled)
        }
    }

    fun setPauseMediaOnDisable(enabled: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(pauseMediaOnDisable = enabled) }
            manager.setPauseMediaOnDisable(enabled)
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(keepScreenOn = enabled) }
            manager.setKeepScreenOn(enabled)
        }
    }

    fun setBoostBrightness(enabled: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(boostBrightness = enabled) }
            manager.setBoostBrightness(enabled)
        }
    }

    fun setHelmet(device: PairedDevice?) {
        viewModelScope.launch {
            val previous = _uiState.value.helmet
            _uiState.update { it.copy(helmet = device) }
            manager.setHelmet(device)
            // Changing helmets must not leave the old one able to wake the app for a ride.
            previous?.takeIf { it.address != device?.address }?.let {
                association.stopObserving(it)
                association.forget(it)
            }
            if (device == null) setAutoStartWithHelmet(false)
        }
    }

    /**
     * Turning this on needs the system's consent for the device, which is a dialog rather than a
     * value we can set. If consent already exists, observing starts at once; otherwise the device
     * goes into state for the activity to ask about, since only an activity may.
     */
    fun setAutoStartWithHelmet(enabled: Boolean) {
        viewModelScope.launch {
            val helmet = _uiState.value.helmet
            _uiState.update { it.copy(autoStartWithHelmet = enabled, associationError = null) }
            manager.setAutoStartWithHelmet(enabled)
            if (helmet == null) return@launch
            when {
                !enabled -> association.stopObserving(helmet)
                association.isAssociated(helmet) -> association.startObserving(helmet)
                else -> _uiState.update { it.copy(associationRequest = helmet) }
            }
        }
    }

    fun onAssociationRequested() = _uiState.update { it.copy(associationRequest = null) }

    /**
     * Reverts the switch and says why. Silently sliding back is what made this impossible to
     * diagnose the first time round.
     */
    fun onAssociationFailed(reason: String) = rejectAutoStart(reason)

    /** The rider dismissed Android's own dialog, so no explanation is owed. */
    fun onAssociationDeclined() = rejectAutoStart(reason = null)

    fun onAssociationApproved() {
        viewModelScope.launch {
            _uiState.value.helmet?.let { association.startObserving(it) }
        }
    }

    fun dismissAssociationError() = _uiState.update { it.copy(associationError = null) }

    private fun rejectAutoStart(reason: String?) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    autoStartWithHelmet = false,
                    associationRequest = null,
                    associationError = reason,
                )
            }
            manager.setAutoStartWithHelmet(false)
        }
    }

    fun dismissError() = _uiState.update { it.copy(showError = false) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MainViewModel(checkNotNull(this[APPLICATION_KEY]))
            }
        }
    }
}

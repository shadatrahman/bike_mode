package com.shadatrahman.bikemode.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shadatrahman.bikemode.data.LandscapeDirection
import com.shadatrahman.bikemode.data.PreferencesRepository
import com.shadatrahman.bikemode.rotation.BikeModeManager
import com.shadatrahman.bikemode.util.PermissionManager
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
    val showError: Boolean = false,
)

class MainViewModel(
    private val application: Application,
    private val manager: BikeModeManager = BikeModeManager(application),
    private val repository: PreferencesRepository = PreferencesRepository(application),
) : ViewModel() {

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
            _uiState.update {
                it.copy(
                    loading = false,
                    hasPermission = hasPermission,
                    bikeModeActive = hasPermission && manager.isActive(),
                    direction = manager.preferences().direction,
                )
            }
        }
    }

    fun toggleBikeMode() {
        viewModelScope.launch {
            manager.toggle()
                .onSuccess { active -> _uiState.update { it.copy(bikeModeActive = active, showError = false) } }
                .onFailure { _uiState.update { it.copy(showError = true) } }
        }
    }

    fun setDirection(direction: LandscapeDirection) {
        viewModelScope.launch {
            _uiState.update { it.copy(direction = direction) }
            manager.setDirection(direction).onFailure { _uiState.update { it.copy(showError = true) } }
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

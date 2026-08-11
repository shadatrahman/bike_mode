package com.shadatrahman.bikemode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.shadatrahman.bikemode.ui.MainScreen
import com.shadatrahman.bikemode.ui.MainViewModel
import com.shadatrahman.bikemode.ui.PermissionScreen
import com.shadatrahman.bikemode.ui.theme.BikeModeTheme
import com.shadatrahman.bikemode.util.PermissionManager
import com.shadatrahman.bikemode.util.QuickSettingsTilePrompt

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BikeModeTheme {
                BikeModeApp(viewModel)
            }
        }
    }

    /**
     * WRITE_SETTINGS is granted on a system screen and rotation can be changed from system Quick
     * Settings, so re-read both every time the app comes back to the foreground.
     */
    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }
}

@Composable
private fun BikeModeApp(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = stringResource(R.string.error_system_rejected)
    val retryLabel = stringResource(R.string.error_try_again)

    LaunchedEffect(state.showError) {
        if (!state.showError) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(message = errorMessage, actionLabel = retryLabel)
        viewModel.dismissError()
        if (result == SnackbarResult.ActionPerformed) viewModel.toggleBikeMode()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        when {
            state.loading -> Unit
            !state.hasPermission -> PermissionScreen(
                onGrantPermission = {
                    context.startActivity(PermissionManager.writeSettingsIntent(context))
                },
                modifier = contentModifier,
            )

            else -> MainScreen(
                state = state,
                onToggleBikeMode = viewModel::toggleBikeMode,
                onDirectionChange = viewModel::setDirection,
                onAddTile = { QuickSettingsTilePrompt.request(context) },
                modifier = contentModifier,
            )
        }
    }
}

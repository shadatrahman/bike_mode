package com.shadatrahman.bikemode

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.shadatrahman.bikemode.bluetooth.BluetoothRequestActivity
import com.shadatrahman.bikemode.companion.HelmetAssociation
import com.shadatrahman.bikemode.ui.BluetoothScreen
import com.shadatrahman.bikemode.ui.MainScreen
import com.shadatrahman.bikemode.ui.MainViewModel
import com.shadatrahman.bikemode.ui.PermissionScreen
import com.shadatrahman.bikemode.ui.theme.BikeModeTheme
import com.shadatrahman.bikemode.util.PermissionManager
import com.shadatrahman.bikemode.util.QuickSettingsTilePrompt

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askForNotifications()
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

    /**
     * The watchdog runs with or without this, but the "Bike Mode on" notification is where the
     * rider's off switch lives while riding, so it is worth asking for once. A refusal is fine.
     */
    private fun askForNotifications() {
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun BikeModeApp(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = stringResource(R.string.error_system_rejected)
    val retryLabel = stringResource(R.string.error_try_again)

    // One extra page is not worth a navigation graph; the back handler lives on the screen itself.
    var showBluetooth by rememberSaveable { mutableStateOf(false) }

    // Only an activity may show Android's companion-device dialog, so the ViewModel hands the
    // IntentSender up and this launches it.
    val associationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onAssociationApproved()
        } else {
            viewModel.onAssociationDeclined()
        }
    }

    // Association must be asked for from an activity, not the application context — asking through
    // the latter simply returns nothing, which is what made the switch slide back in silence.
    val activity = context as? Activity
    val association = remember(activity) { activity?.let { HelmetAssociation(it) } }

    LaunchedEffect(state.associationRequest) {
        val device = state.associationRequest ?: return@LaunchedEffect
        viewModel.onAssociationRequested()
        if (activity == null || association == null) {
            viewModel.onAssociationFailed("No activity to show the pairing dialog")
            return@LaunchedEffect
        }
        association.requestAssociation(
            activity = activity,
            device = device,
            onPending = { associationLauncher.launch(IntentSenderRequest.Builder(it).build()) },
            onFailure = viewModel::onAssociationFailed,
        )
    }

    LaunchedEffect(state.associationError) {
        val reason = state.associationError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(reason)
        viewModel.dismissAssociationError()
    }

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

            showBluetooth -> BluetoothScreen(
                state = state,
                onBluetoothOnEnableChange = viewModel::setBluetoothOnEnable,
                onHelmetChange = viewModel::setHelmet,
                onAutoStartChange = viewModel::setAutoStartWithHelmet,
                // The same trampoline that asks to turn Bluetooth on also asks for the permission,
                // and onResume re-reads the paired list once the rider comes back.
                onGrantBluetooth = {
                    context.startActivity(Intent(context, BluetoothRequestActivity::class.java))
                },
                onBack = { showBluetooth = false },
                modifier = contentModifier,
            )

            else -> MainScreen(
                state = state,
                onToggleBikeMode = viewModel::toggleBikeMode,
                onDirectionChange = viewModel::setDirection,
                onBluetoothOnEnableChange = viewModel::setBluetoothOnEnable,
                onPauseMediaChange = viewModel::setPauseMediaOnDisable,
                onKeepScreenOnChange = viewModel::setKeepScreenOn,
                onBoostBrightnessChange = viewModel::setBoostBrightness,
                onOpenBluetooth = { showBluetooth = true },
                onAddTile = { QuickSettingsTilePrompt.request(context) },
                modifier = contentModifier,
            )
        }
    }
}

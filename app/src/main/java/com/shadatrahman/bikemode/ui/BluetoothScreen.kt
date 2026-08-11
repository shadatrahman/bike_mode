package com.shadatrahman.bikemode.ui

import android.bluetooth.BluetoothAdapter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shadatrahman.bikemode.R
import com.shadatrahman.bikemode.data.PairedDevice

/**
 * Everything Bluetooth, on its own page.
 *
 * It lives apart from the main screen because the device list is long, occasionally empty, and
 * only ever set up once — none of which belongs in front of a rider who opened the app to tap the
 * big toggle. The main screen keeps a one-line summary and the on/off switch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothScreen(
    state: MainUiState,
    onBluetoothOnEnableChange: (Boolean) -> Unit,
    onHelmetChange: (PairedDevice?) -> Unit,
    onAutoStartChange: (Boolean) -> Unit,
    onGrantBluetooth: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    Column(modifier = modifier.fillMaxSize()) {
        // The platform's own pattern: title and back arrow in one bar, not a button above a
        // heading. It also gives the gesture and the button the same target to lead back to.
        TopAppBar(
            title = { Text(stringResource(R.string.bluetooth_screen_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.back),
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SwitchSection(
                headingRes = R.string.bluetooth_heading,
                bodyRes = R.string.bluetooth_body,
                noteRes = R.string.bluetooth_one_way_note,
                enabled = state.bluetoothOnEnable,
                onEnabledChange = onBluetoothOnEnableChange,
            )

            HorizontalDivider()

            HelmetPicker(
                helmet = state.helmet,
                paired = state.pairedDevices,
                canListDevices = state.canListDevices,
                onHelmetChange = onHelmetChange,
                onGrantBluetooth = onGrantBluetooth,
            )

            HorizontalDivider()

            AutoStartSection(
                enabled = state.autoStartWithHelmet,
                hasHelmet = state.helmet != null,
                onEnabledChange = onAutoStartChange,
            )
        }
    }
}

/**
 * Starting and stopping the ride on its own is the one setting here that needs a device first, so
 * it is disabled outright rather than left to fail silently when nothing is chosen.
 */
@Composable
private fun AutoStartSection(
    enabled: Boolean,
    hasHelmet: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SwitchSection(
            headingRes = R.string.auto_start_heading,
            bodyRes = R.string.auto_start_body,
            noteRes = R.string.auto_start_association_note,
            enabled = enabled && hasHelmet,
            onEnabledChange = { if (hasHelmet) onEnabledChange(it) },
        )
        if (!hasHelmet) {
            Text(
                text = stringResource(R.string.auto_start_needs_device),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Picking the helmet: paired devices as a list, plus a typed address for one that is not paired
 * yet. The note about connecting is load-bearing — Android reserves audio-profile connect calls
 * for privileged apps, so the honest promise is "watched and reported", not "connected".
 */
@Composable
private fun HelmetPicker(
    helmet: PairedDevice?,
    paired: List<PairedDevice>,
    canListDevices: Boolean,
    onHelmetChange: (PairedDevice?) -> Unit,
    onGrantBluetooth: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.helmet_heading),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.helmet_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!canListDevices) {
            Text(
                text = stringResource(R.string.helmet_permission_needed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onGrantBluetooth, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.helmet_permission_grant))
            }
        } else {
            Column(Modifier.selectableGroup()) {
                DeviceRow(
                    label = stringResource(R.string.helmet_none),
                    selected = helmet == null,
                    onClick = { onHelmetChange(null) },
                )
                paired.forEach { device ->
                    DeviceRow(
                        label = device.name,
                        detail = device.address,
                        selected = device.address.equals(helmet?.address, ignoreCase = true),
                        onClick = { onHelmetChange(device) },
                    )
                }
            }
            if (paired.isEmpty()) {
                Text(
                    text = stringResource(R.string.helmet_no_paired_devices),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ManualAddressEntry(onHelmetChange = onHelmetChange)

        Text(
            text = stringResource(R.string.helmet_cannot_connect_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * For a helmet that is not paired to this phone yet, so it cannot appear in the list above. The
 * address is validated by the platform's own checker rather than a hand-rolled pattern.
 */
@Composable
private fun ManualAddressEntry(onHelmetChange: (PairedDevice?) -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    val normalised = typed.trim().uppercase()
    val valid = BluetoothAdapter.checkBluetoothAddress(normalised)

    OutlinedTextField(
        value = typed,
        onValueChange = { typed = it },
        label = { Text(stringResource(R.string.helmet_manual_label)) },
        placeholder = { Text(stringResource(R.string.helmet_manual_hint)) },
        singleLine = true,
        isError = typed.isNotBlank() && !valid,
        supportingText = if (typed.isNotBlank() && !valid) {
            { Text(stringResource(R.string.helmet_manual_invalid)) }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(
        onClick = {
            // No name to show for a device this phone has never paired with, so the address is it.
            onHelmetChange(PairedDevice(address = normalised, name = normalised))
            typed = ""
        },
        enabled = valid,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.helmet_manual_use))
    }
}

package com.shadatrahman.bikemode.ui

import android.bluetooth.BluetoothAdapter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shadatrahman.bikemode.R
import com.shadatrahman.bikemode.data.LandscapeDirection
import com.shadatrahman.bikemode.data.PairedDevice
import com.shadatrahman.bikemode.ui.theme.BikeModeTheme

/**
 * Single-screen UI. Deliberately static: no animation or nested navigation, because the rider
 * configures this before moving and uses the Quick Settings tile from then on.
 *
 * Landscape is the mounted orientation, so it gets its own layout: status and a full-height toggle
 * side by side, both on the first screen. Nothing the rider needs mid-ride sits below a scroll.
 */
@Composable
fun MainScreen(
    state: MainUiState,
    onToggleBikeMode: () -> Unit,
    onDirectionChange: (LandscapeDirection) -> Unit,
    onBluetoothOnEnableChange: (Boolean) -> Unit,
    onPauseMediaChange: (Boolean) -> Unit,
    onHelmetChange: (PairedDevice?) -> Unit,
    onGrantBluetooth: () -> Unit,
    onAddTile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            LandscapeLayout(
                state = state,
                viewportHeight = maxHeight,
                onToggleBikeMode = onToggleBikeMode,
                onDirectionChange = onDirectionChange,
                onBluetoothOnEnableChange = onBluetoothOnEnableChange,
                onPauseMediaChange = onPauseMediaChange,
                onHelmetChange = onHelmetChange,
                onGrantBluetooth = onGrantBluetooth,
                onAddTile = onAddTile,
            )
        } else {
            PortraitLayout(
                state = state,
                onToggleBikeMode = onToggleBikeMode,
                onDirectionChange = onDirectionChange,
                onBluetoothOnEnableChange = onBluetoothOnEnableChange,
                onPauseMediaChange = onPauseMediaChange,
                onHelmetChange = onHelmetChange,
                onGrantBluetooth = onGrantBluetooth,
                onAddTile = onAddTile,
            )
        }
    }
}

@Composable
private fun PortraitLayout(
    state: MainUiState,
    onToggleBikeMode: () -> Unit,
    onDirectionChange: (LandscapeDirection) -> Unit,
    onBluetoothOnEnableChange: (Boolean) -> Unit,
    onPauseMediaChange: (Boolean) -> Unit,
    onHelmetChange: (PairedDevice?) -> Unit,
    onGrantBluetooth: () -> Unit,
    onAddTile: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )

        StatusCard(active = state.bikeModeActive, direction = state.direction)

        ToggleButton(
            active = state.bikeModeActive,
            onClick = onToggleBikeMode,
            textStyle = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        )

        HorizontalDivider()

        DirectionPicker(selected = state.direction, onDirectionChange = onDirectionChange)

        HorizontalDivider()

        SwitchSection(
            headingRes = R.string.bluetooth_heading,
            bodyRes = R.string.bluetooth_body,
            noteRes = R.string.bluetooth_one_way_note,
            enabled = state.bluetoothOnEnable,
            onEnabledChange = onBluetoothOnEnableChange,
        )

        HorizontalDivider()

        SwitchSection(
            headingRes = R.string.media_pause_heading,
            bodyRes = R.string.media_pause_body,
            noteRes = R.string.media_pause_scope_note,
            enabled = state.pauseMediaOnDisable,
            onEnabledChange = onPauseMediaChange,
        )

        HorizontalDivider()

        HelmetSection(
            helmet = state.helmet,
            paired = state.pairedDevices,
            canListDevices = state.canListDevices,
            onHelmetChange = onHelmetChange,
            onGrantBluetooth = onGrantBluetooth,
        )

        HorizontalDivider()

        QuickSettingsSection(onAddTile = onAddTile)

        Text(
            text = stringResource(R.string.third_party_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Status and toggle share the first screen as equal halves. The toggle sits opposite the side the
 * screen is rotated towards: Landscape Left puts it on the right, Landscape Right on the left.
 */
@Composable
private fun LandscapeLayout(
    state: MainUiState,
    viewportHeight: Dp,
    onToggleBikeMode: () -> Unit,
    onDirectionChange: (LandscapeDirection) -> Unit,
    onBluetoothOnEnableChange: (Boolean) -> Unit,
    onPauseMediaChange: (Boolean) -> Unit,
    onHelmetChange: (PairedDevice?) -> Unit,
    onGrantBluetooth: () -> Unit,
    onAddTile: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(viewportHeight)
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val toggle: @Composable RowScope.() -> Unit = {
                ToggleButton(
                    active = state.bikeModeActive,
                    onClick = onToggleBikeMode,
                    textStyle = MaterialTheme.typography.displaySmall,
                    // A pill this tall reads as a blob; match the status card's corners instead.
                    shape = CardDefaults.shape,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
            val status: @Composable RowScope.() -> Unit = {
                StatusCard(
                    active = state.bikeModeActive,
                    direction = state.direction,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
            if (state.direction == LandscapeDirection.LEFT) {
                status()
                toggle()
            } else {
                toggle()
                status()
            }
        }

        // Configuration lives below the fold: the rider sets it before moving, per the safety UX.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            DirectionPicker(selected = state.direction, onDirectionChange = onDirectionChange)
            HorizontalDivider()
            SwitchSection(
                headingRes = R.string.bluetooth_heading,
                bodyRes = R.string.bluetooth_body,
                noteRes = R.string.bluetooth_one_way_note,
                enabled = state.bluetoothOnEnable,
                onEnabledChange = onBluetoothOnEnableChange,
            )
            HorizontalDivider()
            SwitchSection(
                headingRes = R.string.media_pause_heading,
                bodyRes = R.string.media_pause_body,
                noteRes = R.string.media_pause_scope_note,
                enabled = state.pauseMediaOnDisable,
                onEnabledChange = onPauseMediaChange,
            )
            HorizontalDivider()
            HelmetSection(
                helmet = state.helmet,
                paired = state.pairedDevices,
                canListDevices = state.canListDevices,
                onHelmetChange = onHelmetChange,
                onGrantBluetooth = onGrantBluetooth,
            )
            HorizontalDivider()
            QuickSettingsSection(onAddTile = onAddTile)
            Text(
                text = stringResource(R.string.third_party_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToggleButton(
    active: Boolean,
    onClick: () -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    shape: Shape = ButtonDefaults.shape,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        colors = if (active) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Text(
            text = stringResource(
                if (active) R.string.bike_mode_turn_off else R.string.bike_mode_turn_on
            ),
            style = textStyle,
        )
    }
}

@Composable
private fun StatusCard(
    active: Boolean,
    direction: LandscapeDirection,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.bike_mode_heading),
                style = MaterialTheme.typography.labelLarge,
            )
            Icon(
                painter = painterResource(R.drawable.ic_bike_mode),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = stringResource(if (active) R.string.bike_mode_on else R.string.bike_mode_off),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            if (active) {
                Text(
                    text = stringResource(direction.labelRes),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.bike_mode_auto_rotate_disabled),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun DirectionPicker(
    selected: LandscapeDirection,
    onDirectionChange: (LandscapeDirection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.direction_heading),
            style = MaterialTheme.typography.titleMedium,
        )
        LandscapeDirection.entries.forEach { direction ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = direction == selected,
                        role = Role.RadioButton,
                        onClick = { onDirectionChange(direction) },
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = direction == selected, onClick = null)
                Text(
                    text = stringResource(direction.labelRes),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

/**
 * Picking the helmet: paired devices as a list, plus a typed address for one that is not paired
 * yet. The note about connecting is load-bearing — Android reserves audio-profile connect calls
 * for privileged apps, so the honest promise is "watched and reported", not "connected".
 */
@Composable
private fun HelmetSection(
    helmet: PairedDevice?,
    paired: List<PairedDevice>,
    canListDevices: Boolean,
    onHelmetChange: (PairedDevice?) -> Unit,
    onGrantBluetooth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
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

@Composable
private fun DeviceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    detail: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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

/**
 * A switch, what it does, and the limit that comes with it.
 *
 * Both switches on this screen carry that third line, and neither is a disclaimer for its own
 * sake: each names something Android will not let an app do — turn Bluetooth back off, aim a pause
 * at one output — that the rider would otherwise meet as a bug.
 */
@Composable
private fun SwitchSection(
    headingRes: Int,
    bodyRes: Int,
    noteRes: Int,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = enabled, role = Role.Switch, onValueChange = onEnabledChange)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(headingRes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = enabled, onCheckedChange = null)
        }
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(noteRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QuickSettingsSection(
    onAddTile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.quick_settings_heading),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.quick_settings_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onAddTile, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.quick_settings_add))
        }
    }
}

private val LandscapeDirection.labelRes: Int
    get() = when (this) {
        LandscapeDirection.LEFT -> R.string.direction_left
        LandscapeDirection.RIGHT -> R.string.direction_right
    }

@Preview(showBackground = true)
@Composable
private fun MainScreenOffPreview() {
    BikeModeTheme {
        MainScreen(
            state = MainUiState(loading = false, hasPermission = true),
            onToggleBikeMode = {},
            onDirectionChange = {},
            onBluetoothOnEnableChange = {},
            onPauseMediaChange = {},
            onHelmetChange = {},
            onGrantBluetooth = {},
            onAddTile = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 380)
@Composable
private fun MainScreenLandscapeRightPreview() {
    BikeModeTheme {
        MainScreen(
            state = MainUiState(
                loading = false,
                hasPermission = true,
                bikeModeActive = true,
                direction = LandscapeDirection.RIGHT,
            ),
            onToggleBikeMode = {},
            onDirectionChange = {},
            onBluetoothOnEnableChange = {},
            onPauseMediaChange = {},
            onHelmetChange = {},
            onGrantBluetooth = {},
            onAddTile = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 380)
@Composable
private fun MainScreenLandscapeLeftPreview() {
    BikeModeTheme {
        MainScreen(
            state = MainUiState(
                loading = false,
                hasPermission = true,
                bikeModeActive = true,
                direction = LandscapeDirection.LEFT,
            ),
            onToggleBikeMode = {},
            onDirectionChange = {},
            onBluetoothOnEnableChange = {},
            onPauseMediaChange = {},
            onHelmetChange = {},
            onGrantBluetooth = {},
            onAddTile = {},
        )
    }
}

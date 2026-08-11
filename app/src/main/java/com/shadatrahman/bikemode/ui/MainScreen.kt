package com.shadatrahman.bikemode.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shadatrahman.bikemode.R
import com.shadatrahman.bikemode.data.LandscapeDirection
import com.shadatrahman.bikemode.ui.theme.BikeModeTheme

/**
 * Single-screen UI. Deliberately static: no animation or nested navigation, because the rider
 * configures this before moving and uses the Quick Settings tile from then on.
 */
@Composable
fun MainScreen(
    state: MainUiState,
    onToggleBikeMode: () -> Unit,
    onDirectionChange: (LandscapeDirection) -> Unit,
    onAddTile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
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

        Button(
            onClick = onToggleBikeMode,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = if (state.bikeModeActive) {
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
                    if (state.bikeModeActive) R.string.bike_mode_turn_off else R.string.bike_mode_turn_on
                ),
                style = MaterialTheme.typography.titleLarge,
            )
        }

        HorizontalDivider()

        DirectionPicker(selected = state.direction, onDirectionChange = onDirectionChange)

        HorizontalDivider()

        Column(
            modifier = Modifier.fillMaxWidth(),
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

        Text(
            text = stringResource(R.string.third_party_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
            onAddTile = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenOnPreview() {
    BikeModeTheme {
        MainScreen(
            state = MainUiState(loading = false, hasPermission = true, bikeModeActive = true),
            onToggleBikeMode = {},
            onDirectionChange = {},
            onAddTile = {},
        )
    }
}

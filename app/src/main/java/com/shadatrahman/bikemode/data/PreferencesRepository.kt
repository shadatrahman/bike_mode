package com.shadatrahman.bikemode.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bike_mode")

/** DataStore-backed [BikeModeStore]. No database and no user-identifiable data. */
class PreferencesRepository(context: Context) : BikeModeStore {

    private val dataStore = context.applicationContext.dataStore

    override val preferences: Flow<BikeModePreferences> =
        dataStore.data.map { it.toBikeModePreferences() }

    override suspend fun current(): BikeModePreferences = preferences.first()

    override suspend fun setDirection(direction: LandscapeDirection) {
        dataStore.edit { it[KEY_DIRECTION] = direction.surfaceRotation }
    }

    override suspend fun setFirstLaunchCompleted() {
        dataStore.edit { it[KEY_FIRST_LAUNCH_COMPLETED] = true }
    }

    override suspend fun setBluetoothOnEnable(enabled: Boolean) {
        dataStore.edit { it[KEY_BLUETOOTH_ON_ENABLE] = enabled }
    }

    override suspend fun setPauseMediaOnDisable(enabled: Boolean) {
        dataStore.edit { it[KEY_PAUSE_MEDIA_ON_DISABLE] = enabled }
    }

    override suspend fun setHelmet(device: PairedDevice?) {
        dataStore.edit {
            if (device == null) {
                it.remove(KEY_HELMET_ADDRESS)
                it.remove(KEY_HELMET_NAME)
            } else {
                it[KEY_HELMET_ADDRESS] = device.address
                it[KEY_HELMET_NAME] = device.name
            }
        }
    }

    override suspend fun markActive(previous: SavedRotationState) {
        dataStore.edit {
            it[KEY_ACTIVE] = true
            it[KEY_PREV_ACCELEROMETER_ROTATION] = previous.accelerometerRotation
            it[KEY_PREV_USER_ROTATION] = previous.userRotation
        }
    }

    override suspend fun markInactive() {
        dataStore.edit {
            it[KEY_ACTIVE] = false
            it.remove(KEY_PREV_ACCELEROMETER_ROTATION)
            it.remove(KEY_PREV_USER_ROTATION)
        }
    }

    private fun Preferences.toBikeModePreferences(): BikeModePreferences {
        val previousAccelerometer = this[KEY_PREV_ACCELEROMETER_ROTATION]
        val previousUser = this[KEY_PREV_USER_ROTATION]
        return BikeModePreferences(
            direction = LandscapeDirection.fromSurfaceRotation(
                this[KEY_DIRECTION] ?: LandscapeDirection.RIGHT.surfaceRotation
            ),
            bikeModeActive = this[KEY_ACTIVE] == true,
            previous = if (previousAccelerometer != null && previousUser != null) {
                SavedRotationState(previousAccelerometer, previousUser)
            } else {
                null
            },
            firstLaunchCompleted = this[KEY_FIRST_LAUNCH_COMPLETED] == true,
            // Absent means never chosen, which is the opt-out default rather than off.
            bluetoothOnEnable = this[KEY_BLUETOOTH_ON_ENABLE] != false,
            pauseMediaOnDisable = this[KEY_PAUSE_MEDIA_ON_DISABLE] != false,
            // The address is what identifies the device; a missing name just falls back to it.
            helmet = this[KEY_HELMET_ADDRESS]?.let {
                PairedDevice(address = it, name = this[KEY_HELMET_NAME] ?: it)
            },
        )
    }

    private companion object {
        val KEY_DIRECTION = intPreferencesKey("preferred_rotation")
        val KEY_ACTIVE = booleanPreferencesKey("bike_mode_active")
        val KEY_PREV_ACCELEROMETER_ROTATION = intPreferencesKey("previous_accelerometer_rotation")
        val KEY_PREV_USER_ROTATION = intPreferencesKey("previous_user_rotation")
        val KEY_FIRST_LAUNCH_COMPLETED = booleanPreferencesKey("first_launch_completed")
        val KEY_BLUETOOTH_ON_ENABLE = booleanPreferencesKey("bluetooth_on_enable")
        val KEY_PAUSE_MEDIA_ON_DISABLE = booleanPreferencesKey("pause_media_on_disable")
        val KEY_HELMET_ADDRESS = stringPreferencesKey("helmet_address")
        val KEY_HELMET_NAME = stringPreferencesKey("helmet_name")
    }
}

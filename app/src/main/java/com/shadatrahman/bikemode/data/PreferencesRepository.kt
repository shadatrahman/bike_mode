package com.shadatrahman.bikemode.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
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

    override suspend fun setKeepScreenOn(enabled: Boolean) {
        dataStore.edit { it[KEY_KEEP_SCREEN_ON] = enabled }
    }

    override suspend fun setBoostBrightness(enabled: Boolean) {
        dataStore.edit { it[KEY_BOOST_BRIGHTNESS] = enabled }
    }

    override suspend fun setAutoStartWithHelmet(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_START_WITH_HELMET] = enabled }
    }

    override suspend fun setSilenceNotifications(enabled: Boolean) {
        dataStore.edit { it[KEY_SILENCE_NOTIFICATIONS] = enabled }
    }

    override suspend fun markActive(
        previous: SavedRotationState,
        previousDisplay: SavedDisplayState,
        previousInterruptionFilter: Int?,
    ) {
        dataStore.edit {
            it[KEY_ACTIVE] = true
            it[KEY_PREV_ACCELEROMETER_ROTATION] = previous.accelerometerRotation
            it[KEY_PREV_USER_ROTATION] = previous.userRotation
            it.putOrRemove(KEY_PREV_INTERRUPTION_FILTER, previousInterruptionFilter)
            // A null field means Bike Mode is not changing that setting, so there is nothing owed
            // back and the key must go rather than keep a stale value from an earlier ride.
            it.putOrRemove(KEY_PREV_SCREEN_OFF_TIMEOUT, previousDisplay.screenOffTimeout)
            it.putOrRemove(KEY_PREV_BRIGHTNESS, previousDisplay.brightness)
            it.putOrRemove(KEY_PREV_BRIGHTNESS_MODE, previousDisplay.brightnessMode)
        }
    }

    override suspend fun markInactive() {
        dataStore.edit {
            it[KEY_ACTIVE] = false
            it.remove(KEY_PREV_ACCELEROMETER_ROTATION)
            it.remove(KEY_PREV_USER_ROTATION)
            it.remove(KEY_PREV_SCREEN_OFF_TIMEOUT)
            it.remove(KEY_PREV_BRIGHTNESS)
            it.remove(KEY_PREV_BRIGHTNESS_MODE)
            it.remove(KEY_PREV_INTERRUPTION_FILTER)
        }
    }

    private fun MutablePreferences.putOrRemove(key: Preferences.Key<Int>, value: Int?) {
        if (value == null) remove(key) else set(key, value)
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
            keepScreenOn = this[KEY_KEEP_SCREEN_ON] != false,
            // Opt-in, so absent means off rather than on.
            boostBrightness = this[KEY_BOOST_BRIGHTNESS] == true,
            autoStartWithHelmet = this[KEY_AUTO_START_WITH_HELMET] == true,
            silenceNotifications = this[KEY_SILENCE_NOTIFICATIONS] == true,
            previousInterruptionFilter = this[KEY_PREV_INTERRUPTION_FILTER],
            previousDisplay = SavedDisplayState(
                screenOffTimeout = this[KEY_PREV_SCREEN_OFF_TIMEOUT],
                brightness = this[KEY_PREV_BRIGHTNESS],
                brightnessMode = this[KEY_PREV_BRIGHTNESS_MODE],
            ).takeIf { !it.isEmpty },
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
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_BOOST_BRIGHTNESS = booleanPreferencesKey("boost_brightness")
        val KEY_AUTO_START_WITH_HELMET = booleanPreferencesKey("auto_start_with_helmet")
        val KEY_PREV_SCREEN_OFF_TIMEOUT = intPreferencesKey("previous_screen_off_timeout")
        val KEY_PREV_BRIGHTNESS = intPreferencesKey("previous_brightness")
        val KEY_PREV_BRIGHTNESS_MODE = intPreferencesKey("previous_brightness_mode")
        val KEY_SILENCE_NOTIFICATIONS = booleanPreferencesKey("silence_notifications")
        val KEY_PREV_INTERRUPTION_FILTER = intPreferencesKey("previous_interruption_filter")
        val KEY_HELMET_ADDRESS = stringPreferencesKey("helmet_address")
        val KEY_HELMET_NAME = stringPreferencesKey("helmet_name")
    }
}

package com.shadatrahman.bikemode.data

import android.content.Context
import android.view.Surface
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The two landscape orientations a rider can mount the phone in. */
enum class LandscapeDirection(val surfaceRotation: Int) {
    LEFT(Surface.ROTATION_90),
    RIGHT(Surface.ROTATION_270);

    companion object {
        fun fromSurfaceRotation(rotation: Int): LandscapeDirection =
            entries.firstOrNull { it.surfaceRotation == rotation } ?: RIGHT
    }
}

/** The Android rotation settings as they were before Bike Mode touched them. */
data class SavedRotationState(
    val accelerometerRotation: Int,
    val userRotation: Int,
)

data class BikeModePreferences(
    val direction: LandscapeDirection = LandscapeDirection.RIGHT,
    val bikeModeActive: Boolean = false,
    val previous: SavedRotationState? = null,
    val firstLaunchCompleted: Boolean = false,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bike_mode")

class PreferencesRepository(context: Context) {

    private val dataStore = context.applicationContext.dataStore

    val preferences: Flow<BikeModePreferences> = dataStore.data.map { it.toBikeModePreferences() }

    suspend fun current(): BikeModePreferences = preferences.first()

    suspend fun setDirection(direction: LandscapeDirection) {
        dataStore.edit { it[KEY_DIRECTION] = direction.surfaceRotation }
    }

    suspend fun setFirstLaunchCompleted() {
        dataStore.edit { it[KEY_FIRST_LAUNCH_COMPLETED] = true }
    }

    /** Records that Bike Mode is on, along with the state to restore when it goes off. */
    suspend fun markActive(previous: SavedRotationState) {
        dataStore.edit {
            it[KEY_ACTIVE] = true
            it[KEY_PREV_ACCELEROMETER_ROTATION] = previous.accelerometerRotation
            it[KEY_PREV_USER_ROTATION] = previous.userRotation
        }
    }

    suspend fun markInactive() {
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
        )
    }

    private companion object {
        val KEY_DIRECTION = intPreferencesKey("preferred_rotation")
        val KEY_ACTIVE = booleanPreferencesKey("bike_mode_active")
        val KEY_PREV_ACCELEROMETER_ROTATION = intPreferencesKey("previous_accelerometer_rotation")
        val KEY_PREV_USER_ROTATION = intPreferencesKey("previous_user_rotation")
        val KEY_FIRST_LAUNCH_COMPLETED = booleanPreferencesKey("first_launch_completed")
    }
}

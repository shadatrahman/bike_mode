package com.shadatrahman.bikemode.rotation

import android.view.Surface
import com.shadatrahman.bikemode.data.BikeModePreferences
import com.shadatrahman.bikemode.data.FakeBikeModeStore
import com.shadatrahman.bikemode.data.LandscapeDirection
import com.shadatrahman.bikemode.data.SavedRotationState
import com.shadatrahman.bikemode.rotation.RotationSettings.Companion.AUTO_ROTATE_OFF
import com.shadatrahman.bikemode.rotation.RotationSettings.Companion.AUTO_ROTATE_ON
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the restore contract from the PRD: disabling Bike Mode must put back whatever the rider
 * had before the ride, not blindly re-enable auto-rotate.
 */
class BikeModeManagerTest {

    @Test
    fun `enable turns off auto-rotate and pins the preferred landscape direction`() = runTest {
        val settings = FakeRotationSettings()
        val store = FakeBikeModeStore(BikeModePreferences(direction = LandscapeDirection.LEFT))
        val manager = BikeModeManager(store, settings)

        assertTrue(manager.enable().isSuccess)

        assertEquals(AUTO_ROTATE_OFF, settings.state.accelerometerRotation)
        assertEquals(Surface.ROTATION_90, settings.state.userRotation)
        assertTrue(store.current().bikeModeActive)
    }

    @Test
    fun `enable records the rotation state that was live before Bike Mode`() = runTest {
        val settings = FakeRotationSettings(
            accelerometerRotation = AUTO_ROTATE_ON,
            userRotation = Surface.ROTATION_0,
        )
        val store = FakeBikeModeStore()
        val manager = BikeModeManager(store, settings)

        manager.enable()

        assertEquals(SavedRotationState(AUTO_ROTATE_ON, Surface.ROTATION_0), store.current().previous)
    }

    @Test
    fun `disable restores auto-rotate and the prior user rotation`() = runTest {
        val settings = FakeRotationSettings(
            accelerometerRotation = AUTO_ROTATE_ON,
            userRotation = Surface.ROTATION_0,
        )
        val store = FakeBikeModeStore()
        val manager = BikeModeManager(store, settings)
        manager.enable()

        assertTrue(manager.disable().isSuccess)

        assertEquals(AUTO_ROTATE_ON, settings.state.accelerometerRotation)
        assertEquals(Surface.ROTATION_0, settings.state.userRotation)
        assertFalse(store.current().bikeModeActive)
        assertNull(store.current().previous)
    }

    @Test
    fun `disable restores a locked portrait instead of enabling auto-rotate`() = runTest {
        // The rider had auto-rotate off and portrait locked before the ride.
        val settings = FakeRotationSettings(
            accelerometerRotation = AUTO_ROTATE_OFF,
            userRotation = Surface.ROTATION_0,
        )
        val manager = BikeModeManager(FakeBikeModeStore(), settings)
        manager.enable()

        manager.disable()

        assertEquals(AUTO_ROTATE_OFF, settings.state.accelerometerRotation)
        assertEquals(Surface.ROTATION_0, settings.state.userRotation)
    }

    @Test
    fun `re-enabling while active keeps the originally saved state`() = runTest {
        val settings = FakeRotationSettings(
            accelerometerRotation = AUTO_ROTATE_OFF,
            userRotation = Surface.ROTATION_0,
        )
        val store = FakeBikeModeStore()
        val manager = BikeModeManager(store, settings)
        manager.enable()

        manager.enable()
        manager.disable()

        // Without the guard, the second enable would have saved Bike Mode's own landscape values.
        assertEquals(AUTO_ROTATE_OFF, settings.state.accelerometerRotation)
        assertEquals(Surface.ROTATION_0, settings.state.userRotation)
    }

    @Test
    fun `disable without a saved state falls back to auto-rotate`() = runTest {
        val settings = FakeRotationSettings(
            accelerometerRotation = AUTO_ROTATE_OFF,
            userRotation = Surface.ROTATION_270,
        )
        val store = FakeBikeModeStore(BikeModePreferences(bikeModeActive = true, previous = null))
        val manager = BikeModeManager(store, settings)

        manager.disable()

        assertEquals(AUTO_ROTATE_ON, settings.state.accelerometerRotation)
        assertFalse(store.current().bikeModeActive)
    }

    @Test
    fun `a failed enable leaves Bike Mode off and saves nothing`() = runTest {
        val settings = FakeRotationSettings().apply { failWrites = true }
        val store = FakeBikeModeStore()
        val manager = BikeModeManager(store, settings)

        assertTrue(manager.enable().isFailure)

        assertFalse(store.current().bikeModeActive)
        assertNull(store.current().previous)
    }

    @Test
    fun `a failed disable keeps Bike Mode active and keeps the saved state`() = runTest {
        val settings = FakeRotationSettings(
            accelerometerRotation = AUTO_ROTATE_ON,
            userRotation = Surface.ROTATION_0,
        )
        val store = FakeBikeModeStore()
        val manager = BikeModeManager(store, settings)
        manager.enable()
        settings.failWrites = true

        assertTrue(manager.disable().isFailure)

        assertTrue(store.current().bikeModeActive)
        assertEquals(SavedRotationState(AUTO_ROTATE_ON, Surface.ROTATION_0), store.current().previous)

        // Once the device cooperates again, the original state is still recoverable.
        settings.failWrites = false
        manager.disable()
        assertEquals(AUTO_ROTATE_ON, settings.state.accelerometerRotation)
        assertEquals(Surface.ROTATION_0, settings.state.userRotation)
    }

    @Test
    fun `isActive reports off and clears state when auto-rotate was re-enabled elsewhere`() = runTest {
        val settings = FakeRotationSettings()
        val store = FakeBikeModeStore()
        val manager = BikeModeManager(store, settings)
        manager.enable()

        // Rider flips auto-rotate back on from the system Quick Settings.
        settings.restore(SavedRotationState(AUTO_ROTATE_ON, Surface.ROTATION_90))

        assertFalse(manager.isActive())
        assertFalse(store.current().bikeModeActive)
        assertNull(store.current().previous)
    }

    @Test
    fun `a tap after external auto-rotate re-locks instead of restoring stale state`() = runTest {
        val settings = FakeRotationSettings(AUTO_ROTATE_OFF, Surface.ROTATION_0)
        val manager = BikeModeManager(FakeBikeModeStore(), settings)
        manager.enable()
        settings.restore(SavedRotationState(AUTO_ROTATE_ON, Surface.ROTATION_0))

        assertEquals(true, manager.toggle().getOrNull())

        assertEquals(AUTO_ROTATE_OFF, settings.state.accelerometerRotation)
        assertEquals(Surface.ROTATION_270, settings.state.userRotation)
    }

    @Test
    fun `toggle turns Bike Mode on then restores the original state`() = runTest {
        val settings = FakeRotationSettings(AUTO_ROTATE_ON, Surface.ROTATION_0)
        val manager = BikeModeManager(FakeBikeModeStore(), settings)

        assertEquals(true, manager.toggle().getOrNull())
        assertEquals(false, manager.toggle().getOrNull())

        assertEquals(AUTO_ROTATE_ON, settings.state.accelerometerRotation)
        assertEquals(Surface.ROTATION_0, settings.state.userRotation)
    }

    @Test
    fun `changing direction while active re-applies immediately and survives restore`() = runTest {
        val settings = FakeRotationSettings(AUTO_ROTATE_ON, Surface.ROTATION_0)
        val store = FakeBikeModeStore()
        val manager = BikeModeManager(store, settings)
        manager.enable()

        assertTrue(manager.setDirection(LandscapeDirection.LEFT).isSuccess)

        assertEquals(Surface.ROTATION_90, settings.state.userRotation)
        assertEquals(LandscapeDirection.LEFT, store.current().direction)

        // Re-applying must not have clobbered the state owed back to the rider.
        manager.disable()
        assertEquals(AUTO_ROTATE_ON, settings.state.accelerometerRotation)
        assertEquals(Surface.ROTATION_0, settings.state.userRotation)
    }

    @Test
    fun `changing direction while off only persists the preference`() = runTest {
        val settings = FakeRotationSettings(AUTO_ROTATE_ON, Surface.ROTATION_0)
        val store = FakeBikeModeStore()
        val manager = BikeModeManager(store, settings)

        manager.setDirection(LandscapeDirection.LEFT)

        assertEquals(LandscapeDirection.LEFT, store.current().direction)
        assertEquals(AUTO_ROTATE_ON, settings.state.accelerometerRotation)
        assertEquals(Surface.ROTATION_0, settings.state.userRotation)
    }
}

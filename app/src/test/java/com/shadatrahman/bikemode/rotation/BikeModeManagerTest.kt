package com.shadatrahman.bikemode.rotation

import android.view.Surface
import com.shadatrahman.bikemode.bluetooth.BluetoothRequester
import com.shadatrahman.bikemode.bluetooth.FakeBluetoothRequester
import com.shadatrahman.bikemode.data.BikeModePreferences
import com.shadatrahman.bikemode.data.BikeModeStore
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
 * Covers the restore contract from the PRD — disabling Bike Mode must put back whatever the rider
 * had before the ride, not blindly re-enable auto-rotate — and the drift repair that keeps the
 * pinned rotation from being quietly undone mid-ride.
 */
class BikeModeManagerTest {

    private fun bikeModeManager(
        store: BikeModeStore,
        settings: RotationSettings,
        watchdog: RotationWatchdog = FakeRotationWatchdog(),
        bluetooth: BluetoothRequester = FakeBluetoothRequester(enabled = true),
    ) = BikeModeManager(store, settings, watchdog, bluetooth)

    @Test
    fun `enable turns off auto-rotate and pins the preferred landscape direction`() = runTest {
        val settings = FakeRotationSettings()
        val store = FakeBikeModeStore(BikeModePreferences(direction = LandscapeDirection.LEFT))
        val manager = bikeModeManager(store, settings)

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
        val manager = bikeModeManager(store, settings)

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
        val manager = bikeModeManager(store, settings)
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
        val manager = bikeModeManager(FakeBikeModeStore(), settings)
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
        val manager = bikeModeManager(store, settings)
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
        val manager = bikeModeManager(store, settings)

        manager.disable()

        assertEquals(AUTO_ROTATE_ON, settings.state.accelerometerRotation)
        assertFalse(store.current().bikeModeActive)
    }

    @Test
    fun `a failed enable leaves Bike Mode off and saves nothing`() = runTest {
        val settings = FakeRotationSettings().apply { failWrites = true }
        val store = FakeBikeModeStore()
        val manager = bikeModeManager(store, settings)

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
        val manager = bikeModeManager(store, settings)
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
        val manager = bikeModeManager(store, settings)
        manager.enable()

        // Rider flips auto-rotate back on from the system Quick Settings.
        settings.rewrittenExternallyTo(SavedRotationState(AUTO_ROTATE_ON, Surface.ROTATION_90))

        assertFalse(manager.isActive())
        assertFalse(store.current().bikeModeActive)
        assertNull(store.current().previous)
    }

    @Test
    fun `a tap after external auto-rotate re-locks instead of restoring stale state`() = runTest {
        val settings = FakeRotationSettings(AUTO_ROTATE_OFF, Surface.ROTATION_0)
        val manager = bikeModeManager(FakeBikeModeStore(), settings)
        manager.enable()
        settings.rewrittenExternallyTo(SavedRotationState(AUTO_ROTATE_ON, Surface.ROTATION_0))

        assertEquals(true, manager.toggle().getOrNull())

        assertEquals(AUTO_ROTATE_OFF, settings.state.accelerometerRotation)
        assertEquals(Surface.ROTATION_270, settings.state.userRotation)
    }

    @Test
    fun `toggle turns Bike Mode on then restores the original state`() = runTest {
        val settings = FakeRotationSettings(AUTO_ROTATE_ON, Surface.ROTATION_0)
        val manager = bikeModeManager(FakeBikeModeStore(), settings)

        assertEquals(true, manager.toggle().getOrNull())
        assertEquals(false, manager.toggle().getOrNull())

        assertEquals(AUTO_ROTATE_ON, settings.state.accelerometerRotation)
        assertEquals(Surface.ROTATION_0, settings.state.userRotation)
    }

    @Test
    fun `changing direction while active re-applies immediately and survives restore`() = runTest {
        val settings = FakeRotationSettings(AUTO_ROTATE_ON, Surface.ROTATION_0)
        val store = FakeBikeModeStore()
        val manager = bikeModeManager(store, settings)
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
        val manager = bikeModeManager(store, settings)

        manager.setDirection(LandscapeDirection.LEFT)

        assertEquals(LandscapeDirection.LEFT, store.current().direction)
        assertEquals(AUTO_ROTATE_ON, settings.state.accelerometerRotation)
        assertEquals(Surface.ROTATION_0, settings.state.userRotation)
    }

    @Test
    fun `enable starts the drift watchdog and disable stops it`() = runTest {
        val watchdog = FakeRotationWatchdog()
        val manager = bikeModeManager(FakeBikeModeStore(), FakeRotationSettings(), watchdog)

        manager.enable()
        assertTrue(watchdog.running)

        manager.disable()
        assertFalse(watchdog.running)
    }

    @Test
    fun `a failed enable does not leave the watchdog running`() = runTest {
        val watchdog = FakeRotationWatchdog()
        val settings = FakeRotationSettings().apply { failWrites = true }
        val manager = bikeModeManager(FakeBikeModeStore(), settings, watchdog)

        manager.enable()

        assertFalse(watchdog.running)
    }

    @Test
    fun `reassert re-pins the rotation after another app rewrote it`() = runTest {
        val settings = FakeRotationSettings(AUTO_ROTATE_ON, Surface.ROTATION_0)
        val manager = bikeModeManager(FakeBikeModeStore(), settings)
        manager.enable()

        // A portrait-locked app takes the foreground and the system rewrites USER_ROTATION to 0,
        // leaving auto-rotate off: Bike Mode looks on but no longer holds landscape.
        settings.rewrittenExternallyTo(SavedRotationState(AUTO_ROTATE_OFF, Surface.ROTATION_0))

        assertTrue(manager.reassert())
        assertEquals(AUTO_ROTATE_OFF, settings.state.accelerometerRotation)
        assertEquals(Surface.ROTATION_270, settings.state.userRotation)
    }

    @Test
    fun `reassert leaves an intact rotation alone`() = runTest {
        val settings = FakeRotationSettings(AUTO_ROTATE_ON, Surface.ROTATION_0)
        val manager = bikeModeManager(FakeBikeModeStore(), settings)
        manager.enable()
        val appliedByEnable = settings.applyCount

        assertTrue(manager.reassert())

        // No redundant write, so the watchdog cannot ping-pong with the app that owns the screen.
        assertEquals(appliedByEnable, settings.applyCount)
    }

    @Test
    fun `reassert keeps the state owed back to the rider`() = runTest {
        val settings = FakeRotationSettings(AUTO_ROTATE_OFF, Surface.ROTATION_0)
        val store = FakeBikeModeStore()
        val manager = bikeModeManager(store, settings)
        manager.enable()
        settings.rewrittenExternallyTo(SavedRotationState(AUTO_ROTATE_OFF, Surface.ROTATION_0))

        manager.reassert()
        manager.disable()

        // The rider had a portrait lock before the ride and still gets it back.
        assertEquals(AUTO_ROTATE_OFF, settings.state.accelerometerRotation)
        assertEquals(Surface.ROTATION_0, settings.state.userRotation)
    }

    @Test
    fun `reassert reports inactive and stops watching when auto-rotate came back`() = runTest {
        val watchdog = FakeRotationWatchdog()
        val settings = FakeRotationSettings(AUTO_ROTATE_OFF, Surface.ROTATION_0)
        val manager = bikeModeManager(FakeBikeModeStore(), settings, watchdog)
        manager.enable()
        settings.rewrittenExternallyTo(SavedRotationState(AUTO_ROTATE_ON, Surface.ROTATION_0))

        assertFalse(manager.reassert())
        assertFalse(watchdog.running)
        assertEquals(AUTO_ROTATE_ON, settings.state.accelerometerRotation)
    }

    @Test
    fun `enable asks for Bluetooth when it is off and the rider opted in`() = runTest {
        val bluetooth = FakeBluetoothRequester(enabled = false)
        val manager = bikeModeManager(
            FakeBikeModeStore(), FakeRotationSettings(), bluetooth = bluetooth
        )

        manager.enable()

        assertEquals(1, bluetooth.requests)
    }

    @Test
    fun `enable leaves Bluetooth alone when it is already on`() = runTest {
        val bluetooth = FakeBluetoothRequester(enabled = true)
        val manager = bikeModeManager(
            FakeBikeModeStore(), FakeRotationSettings(), bluetooth = bluetooth
        )

        manager.enable()

        // A rider with an intercom already paired should never see the dialog.
        assertEquals(0, bluetooth.requests)
    }

    @Test
    fun `enable leaves Bluetooth alone when the rider opted out`() = runTest {
        val bluetooth = FakeBluetoothRequester(enabled = false)
        val store = FakeBikeModeStore(BikeModePreferences(bluetoothOnEnable = false))
        val manager = bikeModeManager(store, FakeRotationSettings(), bluetooth = bluetooth)

        manager.enable()

        assertEquals(0, bluetooth.requests)
    }

    @Test
    fun `the boot path re-arms without raising a dialog`() = runTest {
        val bluetooth = FakeBluetoothRequester(enabled = false)
        val store = FakeBikeModeStore()
        val settings = FakeRotationSettings(AUTO_ROTATE_ON, Surface.ROTATION_0)
        val manager = bikeModeManager(store, settings, bluetooth = bluetooth)

        manager.enable(requestBluetooth = false)

        // The lock is back, but nothing was thrown at a rider who just restarted their phone.
        assertEquals(AUTO_ROTATE_OFF, settings.state.accelerometerRotation)
        assertEquals(Surface.ROTATION_270, settings.state.userRotation)
        assertEquals(0, bluetooth.requests)
    }

    @Test
    fun `a failed enable does not ask for Bluetooth`() = runTest {
        val bluetooth = FakeBluetoothRequester(enabled = false)
        val settings = FakeRotationSettings().apply { failWrites = true }
        val manager = bikeModeManager(FakeBikeModeStore(), settings, bluetooth = bluetooth)

        manager.enable()

        assertEquals(0, bluetooth.requests)
    }

    @Test
    fun `disable never turns Bluetooth back off`() = runTest {
        val bluetooth = FakeBluetoothRequester(enabled = false)
        val manager = bikeModeManager(
            FakeBikeModeStore(), FakeRotationSettings(), bluetooth = bluetooth
        )
        manager.enable()

        manager.disable()

        // Android offers apps no "request disable", so raising it is a one-way trip by design.
        assertTrue(bluetooth.isEnabled())
    }

    @Test
    fun `opting in mid-ride asks straight away`() = runTest {
        val bluetooth = FakeBluetoothRequester(enabled = false)
        val store = FakeBikeModeStore(BikeModePreferences(bluetoothOnEnable = false))
        val manager = bikeModeManager(store, FakeRotationSettings(), bluetooth = bluetooth)
        manager.enable()

        manager.setBluetoothOnEnable(true)

        assertTrue(store.current().bluetoothOnEnable)
        assertEquals(1, bluetooth.requests)
    }

    @Test
    fun `opting in while Bike Mode is off only persists the preference`() = runTest {
        val bluetooth = FakeBluetoothRequester(enabled = false)
        val store = FakeBikeModeStore(BikeModePreferences(bluetoothOnEnable = false))
        val manager = bikeModeManager(store, FakeRotationSettings(), bluetooth = bluetooth)

        manager.setBluetoothOnEnable(true)

        assertTrue(store.current().bluetoothOnEnable)
        assertEquals(0, bluetooth.requests)
    }

    @Test
    fun `reassert does nothing while Bike Mode is off`() = runTest {
        val settings = FakeRotationSettings(AUTO_ROTATE_ON, Surface.ROTATION_0)
        val manager = bikeModeManager(FakeBikeModeStore(), settings)

        assertFalse(manager.reassert())
        assertEquals(0, settings.applyCount)
    }
}

package com.shadatrahman.bikemode.rotation

import android.view.Surface
import com.shadatrahman.bikemode.bluetooth.BluetoothRequester
import com.shadatrahman.bikemode.bluetooth.FakeBluetoothRequester
import com.shadatrahman.bikemode.data.BikeModePreferences
import com.shadatrahman.bikemode.data.BikeModeStore
import com.shadatrahman.bikemode.data.FakeBikeModeStore
import com.shadatrahman.bikemode.data.LandscapeDirection
import com.shadatrahman.bikemode.data.SavedRotationState
import com.shadatrahman.bikemode.display.AmbientLight
import com.shadatrahman.bikemode.display.DisplaySettings
import com.shadatrahman.bikemode.display.FakeAmbientLight
import com.shadatrahman.bikemode.display.FakeDisplaySettings
import com.shadatrahman.bikemode.media.FakeMediaPauser
import com.shadatrahman.bikemode.media.MediaPauser
import com.shadatrahman.bikemode.notifications.FakeInterruptionSettings
import com.shadatrahman.bikemode.notifications.InterruptionSettings
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
        media: MediaPauser = FakeMediaPauser(),
        display: DisplaySettings = FakeDisplaySettings(),
        ambientLight: AmbientLight = FakeAmbientLight(),
        interruptions: InterruptionSettings = FakeInterruptionSettings(),
    ) = BikeModeManager(
        store, settings, watchdog, bluetooth, media, display, ambientLight, interruptions,
    )

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
    fun `disable pauses whatever was playing`() = runTest {
        val media = FakeMediaPauser()
        val manager = bikeModeManager(FakeBikeModeStore(), FakeRotationSettings(), media = media)
        manager.enable()

        manager.disable()

        assertEquals(1, media.pauses)
    }

    @Test
    fun `enable never pauses media`() = runTest {
        val media = FakeMediaPauser()
        val manager = bikeModeManager(FakeBikeModeStore(), FakeRotationSettings(), media = media)

        manager.enable()

        // Starting a ride is when the rider wants music, not when they want it stopped.
        assertEquals(0, media.pauses)
    }

    @Test
    fun `disable leaves media alone when the rider opted out`() = runTest {
        val media = FakeMediaPauser()
        val store = FakeBikeModeStore(BikeModePreferences(pauseMediaOnDisable = false))
        val manager = bikeModeManager(store, FakeRotationSettings(), media = media)
        manager.enable()

        manager.disable()

        assertEquals(0, media.pauses)
    }

    @Test
    fun `a failed disable does not pause media`() = runTest {
        val media = FakeMediaPauser()
        val settings = FakeRotationSettings(AUTO_ROTATE_ON, Surface.ROTATION_0)
        val manager = bikeModeManager(FakeBikeModeStore(), settings, media = media)
        manager.enable()
        settings.failWrites = true

        assertTrue(manager.disable().isFailure)

        // Bike Mode is still on, so the ride has not ended and the music should keep playing.
        assertEquals(0, media.pauses)
    }

    @Test
    fun `toggling off through the shared path pauses media once`() = runTest {
        val media = FakeMediaPauser()
        val manager = bikeModeManager(FakeBikeModeStore(), FakeRotationSettings(), media = media)
        manager.toggle()

        manager.toggle()

        // Tile, widget and notification all reach disable() this way, so one hook covers them all.
        assertEquals(1, media.pauses)
    }

    @Test
    fun `enable holds the screen awake and disable gives the timeout back`() = runTest {
        val display = FakeDisplaySettings(timeout = 15_000)
        val manager = bikeModeManager(FakeBikeModeStore(), FakeRotationSettings(), display = display)

        manager.enable()
        assertEquals(FakeDisplaySettings.RIDE_TIMEOUT, display.timeout)

        manager.disable()
        assertEquals(15_000, display.timeout)
    }

    @Test
    fun `brightness is left alone unless the rider opted in`() = runTest {
        val display = FakeDisplaySettings(brightness = 40, brightnessMode = FakeDisplaySettings.AUTOMATIC)
        val manager = bikeModeManager(FakeBikeModeStore(), FakeRotationSettings(), display = display)

        manager.enable()

        // Keep-screen-on is on by default; brightness boost is not, so nothing here should move.
        assertEquals(40, display.brightness)
        assertEquals(FakeDisplaySettings.AUTOMATIC, display.brightnessMode)
    }

    @Test
    fun `brightness boost goes manual and comes back automatic`() = runTest {
        val display = FakeDisplaySettings(brightness = 40, brightnessMode = FakeDisplaySettings.AUTOMATIC)
        val store = FakeBikeModeStore(BikeModePreferences(boostBrightness = true))
        val manager = bikeModeManager(store, FakeRotationSettings(), display = display)

        manager.enable()
        assertEquals(FakeDisplaySettings.MAX, display.brightness)
        assertEquals(FakeDisplaySettings.MANUAL, display.brightnessMode)

        manager.disable()
        assertEquals(40, display.brightness)
        assertEquals(FakeDisplaySettings.AUTOMATIC, display.brightnessMode)
    }

    @Test
    fun `a ride starting after dark does not boost brightness`() = runTest {
        val display = FakeDisplaySettings(brightness = 40, brightnessMode = FakeDisplaySettings.AUTOMATIC)
        val store = FakeBikeModeStore(BikeModePreferences(boostBrightness = true))
        val manager = bikeModeManager(
            store,
            FakeRotationSettings(),
            display = display,
            ambientLight = FakeAmbientLight(lux = FakeAmbientLight.NIGHT),
        )

        manager.enable()

        // The switch says "bright in sun", and the sun is not out.
        assertEquals(40, display.brightness)
        assertEquals(FakeDisplaySettings.AUTOMATIC, display.brightnessMode)
    }

    @Test
    fun `brightness still comes back after a night ride that never boosted`() = runTest {
        val display = FakeDisplaySettings(brightness = 40, brightnessMode = FakeDisplaySettings.AUTOMATIC)
        val store = FakeBikeModeStore(BikeModePreferences(boostBrightness = true))
        val manager = bikeModeManager(
            store,
            FakeRotationSettings(),
            display = display,
            ambientLight = FakeAmbientLight(lux = FakeAmbientLight.NIGHT),
        )
        manager.enable()

        manager.disable()

        // The watchdog may have boosted mid-ride, so what was captured is still owed back.
        assertEquals(40, display.brightness)
        assertEquals(FakeDisplaySettings.AUTOMATIC, display.brightnessMode)
    }

    @Test
    fun `a phone with no light sensor still honours the switch`() = runTest {
        val display = FakeDisplaySettings(brightness = 40)
        val store = FakeBikeModeStore(BikeModePreferences(boostBrightness = true))
        val manager = bikeModeManager(
            store,
            FakeRotationSettings(),
            display = display,
            ambientLight = FakeAmbientLight(isAvailable = false, lux = null),
        )

        manager.enable()

        // Nothing to ask, so the rider's explicit choice stands rather than being silently dropped.
        assertEquals(FakeDisplaySettings.MAX, display.brightness)
    }

    @Test
    fun `disable does not touch a display setting Bike Mode never changed`() = runTest {
        val display = FakeDisplaySettings(brightness = 40)
        val store = FakeBikeModeStore(BikeModePreferences(boostBrightness = false))
        val manager = bikeModeManager(store, FakeRotationSettings(), display = display)
        manager.enable()

        // Rider dims the screen by hand mid-ride. Bike Mode never owned brightness, so it keeps out.
        display.brightness = 10

        manager.disable()
        assertEquals(10, display.brightness)
    }

    @Test
    fun `re-enabling while active keeps the display state owed to the rider`() = runTest {
        val display = FakeDisplaySettings(timeout = 15_000)
        val manager = bikeModeManager(FakeBikeModeStore(), FakeRotationSettings(), display = display)
        manager.enable()

        manager.enable()
        manager.disable()

        // Without the guard the second enable would have saved the ride's own 30-minute timeout.
        assertEquals(15_000, display.timeout)
    }

    @Test
    fun `a refused display write still leaves Bike Mode on`() = runTest {
        val display = FakeDisplaySettings().apply { failWrites = true }
        val store = FakeBikeModeStore()
        val manager = bikeModeManager(store, FakeRotationSettings(), display = display)

        assertTrue(manager.enable().isSuccess)

        // Rotation is the feature; the display settings are comfort that must not veto it.
        assertTrue(store.current().bikeModeActive)
    }

    @Test
    fun `switching keep-screen-on off mid-ride hands the timeout straight back`() = runTest {
        val display = FakeDisplaySettings(timeout = 15_000)
        val store = FakeBikeModeStore()
        val manager = bikeModeManager(store, FakeRotationSettings(), display = display)
        manager.enable()

        manager.setKeepScreenOn(false)

        assertEquals(15_000, display.timeout)
        assertNull(store.current().previousDisplay?.screenOffTimeout)
    }

    @Test
    fun `switching brightness boost on mid-ride applies it and remembers what it replaced`() = runTest {
        val display = FakeDisplaySettings(brightness = 40, brightnessMode = FakeDisplaySettings.AUTOMATIC)
        val store = FakeBikeModeStore()
        val manager = bikeModeManager(store, FakeRotationSettings(), display = display)
        manager.enable()

        manager.setBoostBrightness(true)
        assertEquals(FakeDisplaySettings.MAX, display.brightness)

        manager.disable()
        assertEquals(40, display.brightness)
        // The timeout was owed from the original enable and must survive the mid-ride edit.
        assertEquals(FakeDisplaySettings.DEFAULT_TIMEOUT, display.timeout)
    }

    @Test
    fun `changing a display setting while off only persists the preference`() = runTest {
        val display = FakeDisplaySettings(timeout = 15_000)
        val store = FakeBikeModeStore()
        val manager = bikeModeManager(store, FakeRotationSettings(), display = display)

        manager.setBoostBrightness(true)

        assertTrue(store.current().boostBrightness)
        assertEquals(15_000, display.timeout)
        assertNull(store.current().previousDisplay)
    }

    @Test
    fun `enable silences notifications and disable hands the filter back`() = runTest {
        val interruptions = FakeInterruptionSettings(filter = FakeInterruptionSettings.FILTER_ALL)
        val store = FakeBikeModeStore(BikeModePreferences(silenceNotifications = true))
        val manager = bikeModeManager(store, FakeRotationSettings(), interruptions = interruptions)

        manager.enable()
        assertEquals(FakeInterruptionSettings.FILTER_PRIORITY, interruptions.filter)

        manager.disable()
        assertEquals(FakeInterruptionSettings.FILTER_ALL, interruptions.filter)
    }

    @Test
    fun `a rider already on Do Not Disturb gets it back, not silence undone`() = runTest {
        val interruptions = FakeInterruptionSettings(filter = FakeInterruptionSettings.FILTER_ALARMS)
        val store = FakeBikeModeStore(BikeModePreferences(silenceNotifications = true))
        val manager = bikeModeManager(store, FakeRotationSettings(), interruptions = interruptions)
        manager.enable()

        manager.disable()

        // Same contract as rotation: put back what they had, do not assume the default.
        assertEquals(FakeInterruptionSettings.FILTER_ALARMS, interruptions.filter)
    }

    @Test
    fun `notifications are left alone unless the rider opted in`() = runTest {
        val interruptions = FakeInterruptionSettings(filter = FakeInterruptionSettings.FILTER_ALL)
        val manager = bikeModeManager(
            FakeBikeModeStore(), FakeRotationSettings(), interruptions = interruptions
        )

        manager.enable()

        assertEquals(FakeInterruptionSettings.FILTER_ALL, interruptions.filter)
    }

    @Test
    fun `disable does not touch a filter the rider set mid-ride themselves`() = runTest {
        val interruptions = FakeInterruptionSettings(filter = FakeInterruptionSettings.FILTER_ALL)
        val manager = bikeModeManager(
            FakeBikeModeStore(), FakeRotationSettings(), interruptions = interruptions
        )
        manager.enable()

        // Bike Mode never owned the filter, so their own Do Not Disturb must survive the ride.
        interruptions.filter = FakeInterruptionSettings.FILTER_NONE

        manager.disable()
        assertEquals(FakeInterruptionSettings.FILTER_NONE, interruptions.filter)
    }

    @Test
    fun `without policy access nothing is silenced and nothing is owed`() = runTest {
        val interruptions = FakeInterruptionSettings(canControl = false)
        val store = FakeBikeModeStore(BikeModePreferences(silenceNotifications = true))
        val manager = bikeModeManager(store, FakeRotationSettings(), interruptions = interruptions)

        assertTrue(manager.enable().isSuccess)

        // The switch can read on before access is granted, so this must not pretend to have saved.
        assertNull(store.current().previousInterruptionFilter)
    }

    @Test
    fun `switching silence off mid-ride hands the filter straight back`() = runTest {
        val interruptions = FakeInterruptionSettings(filter = FakeInterruptionSettings.FILTER_ALL)
        val store = FakeBikeModeStore(BikeModePreferences(silenceNotifications = true))
        val manager = bikeModeManager(store, FakeRotationSettings(), interruptions = interruptions)
        manager.enable()

        manager.setSilenceNotifications(false)

        assertEquals(FakeInterruptionSettings.FILTER_ALL, interruptions.filter)
        assertNull(store.current().previousInterruptionFilter)
    }

    @Test
    fun `switching silence on mid-ride keeps what the display half is owed`() = runTest {
        val display = FakeDisplaySettings(timeout = 15_000)
        val interruptions = FakeInterruptionSettings(filter = FakeInterruptionSettings.FILTER_ALL)
        val store = FakeBikeModeStore()
        val manager = bikeModeManager(
            store, FakeRotationSettings(), display = display, interruptions = interruptions
        )
        manager.enable()

        manager.setSilenceNotifications(true)
        assertEquals(FakeInterruptionSettings.FILTER_PRIORITY, interruptions.filter)

        manager.disable()
        // Both halves of what Bike Mode owes have to survive the other being rewritten.
        assertEquals(15_000, display.timeout)
        assertEquals(FakeInterruptionSettings.FILTER_ALL, interruptions.filter)
    }

    @Test
    fun `a refused Do Not Disturb write still leaves Bike Mode on`() = runTest {
        val interruptions = FakeInterruptionSettings().apply { failWrites = true }
        val store = FakeBikeModeStore(BikeModePreferences(silenceNotifications = true))
        val manager = bikeModeManager(store, FakeRotationSettings(), interruptions = interruptions)

        assertTrue(manager.enable().isSuccess)

        assertTrue(store.current().bikeModeActive)
    }

    @Test
    fun `reassert does nothing while Bike Mode is off`() = runTest {
        val settings = FakeRotationSettings(AUTO_ROTATE_ON, Surface.ROTATION_0)
        val manager = bikeModeManager(FakeBikeModeStore(), settings)

        assertFalse(manager.reassert())
        assertEquals(0, settings.applyCount)
    }
}

package com.shadatrahman.bikemode.battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The gaps between the thresholds are the reason this class exists, so most of these are about what
 * should *not* happen: no easing off on a powered mount, and no flapping at the boundary.
 */
class PowerSaveGateTest {

    private fun onBattery(percent: Int) = ChargeState(percent, charging = false)

    private fun charging(percent: Int) = ChargeState(percent, charging = true)

    @Test
    fun `a healthy battery gives nothing up`() {
        assertNull(PowerSaveGate().update(onBattery(80)))
    }

    @Test
    fun `below twenty percent the brightness boost goes`() {
        assertEquals(BatterySaving.BRIGHTNESS, PowerSaveGate().update(onBattery(18)))
    }

    @Test
    fun `below ten percent the screen is allowed to sleep as well`() {
        assertEquals(BatterySaving.BRIGHTNESS_AND_TIMEOUT, PowerSaveGate().update(onBattery(8)))
    }

    @Test
    fun `a charging phone has no problem to solve`() {
        // The common case on a powered mount: low, but filling. Easing off would be pure loss.
        assertNull(PowerSaveGate().update(charging(5)))
    }

    @Test
    fun `plugging in mid-ride gives everything back at once`() {
        val gate = PowerSaveGate()
        gate.update(onBattery(8))

        assertEquals(BatterySaving.NONE, gate.update(charging(8)))
    }

    @Test
    fun `hovering at the threshold does not flap the screen`() {
        val gate = PowerSaveGate()
        assertEquals(BatterySaving.BRIGHTNESS, gate.update(onBattery(20)))

        // A percent either way is noise, not recovery, and each flip is a visible jump in brightness.
        assertNull(gate.update(onBattery(21)))
        assertNull(gate.update(onBattery(22)))
        assertNull(gate.update(onBattery(19)))
    }

    @Test
    fun `the boost only comes back once the battery has clearly recovered`() {
        val gate = PowerSaveGate()
        gate.update(onBattery(18))

        assertNull(gate.update(onBattery(24)))
        assertEquals(BatterySaving.NONE, gate.update(onBattery(25)))
    }

    @Test
    fun `sleeping is given back before the brightness is`() {
        val gate = PowerSaveGate()
        gate.update(onBattery(8))

        // 16% is out of the sleep band but still inside the dim one, so only half comes back.
        assertEquals(BatterySaving.BRIGHTNESS, gate.update(onBattery(16)))
        assertEquals(BatterySaving.NONE, gate.update(onBattery(26)))
    }

    @Test
    fun `a drop straight past both thresholds lands on the deeper one`() {
        val gate = PowerSaveGate()

        assertEquals(BatterySaving.BRIGHTNESS_AND_TIMEOUT, gate.update(onBattery(9)))
    }

    @Test
    fun `easing deeper is reported even from an already-eased state`() {
        val gate = PowerSaveGate()
        gate.update(onBattery(18))

        assertEquals(BatterySaving.BRIGHTNESS_AND_TIMEOUT, gate.update(onBattery(7)))
    }

    @Test
    fun `a gate that starts eased off does not re-report the same level`() {
        val gate = PowerSaveGate(BatterySaving.BRIGHTNESS)

        assertNull(gate.update(onBattery(18)))
    }
}

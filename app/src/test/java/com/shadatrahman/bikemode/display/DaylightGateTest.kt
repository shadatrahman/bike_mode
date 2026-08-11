package com.shadatrahman.bikemode.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The dead band between the two thresholds is the reason this class exists, so most of these are
 * about what should *not* happen: no flip indoors, and no flicker while riding past shade.
 */
class DaylightGateTest {

    @Test
    fun `real daylight turns the boost on`() {
        assertEquals(true, DaylightGate().update(20_000f))
    }

    @Test
    fun `night leaves the boost off`() {
        // The rider opted in for sun, not for a torch in the face on the way home.
        assertNull(DaylightGate().update(5f))
    }

    @Test
    fun `a bright indoor room is not daylight`() {
        // Well-lit offices sit near 500 lux, so setting up the app at a desk must not boost.
        assertNull(DaylightGate().update(600f))
    }

    @Test
    fun `overcast daylight holds a boost that is already on`() {
        val gate = DaylightGate()
        gate.update(20_000f)

        // 3000 lux is between the thresholds: dimmer, but still plainly outdoors in daylight.
        assertNull(gate.update(3_000f))
    }

    @Test
    fun `dusk releases a boost that was on`() {
        val gate = DaylightGate()
        gate.update(20_000f)

        assertEquals(false, gate.update(500f))
    }

    @Test
    fun `passing under a bridge does not flicker the screen`() {
        val gate = DaylightGate()
        gate.update(30_000f)

        // Brief shade drops the reading into the dead band and back; neither should change anything.
        assertNull(gate.update(2_500f))
        assertNull(gate.update(30_000f))
    }

    @Test
    fun `a gate that starts boosted knows it, so a restarted ride does not re-apply`() {
        val gate = DaylightGate(boosted = true)

        assertNull(gate.update(30_000f))
        assertEquals(false, gate.update(10f))
    }

    @Test
    fun `crossing back into daylight boosts again`() {
        val gate = DaylightGate()
        gate.update(30_000f)
        gate.update(10f)

        assertEquals(true, gate.update(30_000f))
    }
}

package com.shadatrahman.bikemode.bluetooth

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The monitor is the part of the helmet feature that has rules worth pinning down: it must give
 * Android's own auto-connect a fair chance before doing anything, must nudge only once, and must
 * end up telling the truth either way. The waits are long, so the test scheduler skips them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HelmetMonitorTest {

    private val address = "AA:BB:CC:DD:EE:FF"

    private fun monitor(link: HelmetLink, scope: TestScope, states: MutableList<HelmetState>) =
        HelmetMonitor(link, scope) { states += it }

    @Test
    fun `reports connected straight away when the helmet is already up`() = runTest {
        val link = FakeHelmetLink(connected = true)
        val states = mutableListOf<HelmetState>()

        monitor(link, this, states).watch(address)
        advanceUntilIdle()

        assertEquals(listOf(HelmetState.CONNECTED), states)
        // Nothing to prompt, so the socket hack never runs.
        assertEquals(0, link.nudges)
    }

    @Test
    fun `waits for the system's own auto-connect before nudging`() = runTest {
        val link = FakeHelmetLink(connected = false)
        val states = mutableListOf<HelmetState>()
        val subject = monitor(link, this, states)

        subject.watch(address)
        // The helmet turns up on its own a few seconds in, as it usually does.
        advanceTimeBy(3_000)
        link.connected = true
        advanceUntilIdle()

        assertEquals(listOf(HelmetState.WAITING, HelmetState.CONNECTED), states)
        assertEquals(0, link.nudges)
    }

    @Test
    fun `nudges once when the grace period passes with no connection`() = runTest {
        val link = FakeHelmetLink(connected = false, nudgeConnects = true)
        val states = mutableListOf<HelmetState>()

        monitor(link, this, states).watch(address)
        advanceUntilIdle()

        assertEquals(1, link.nudges)
        assertEquals(listOf(HelmetState.WAITING, HelmetState.CONNECTED), states)
    }

    @Test
    fun `reports missing when even the nudge does not bring it up`() = runTest {
        val link = FakeHelmetLink(connected = false, nudgeConnects = false)
        val states = mutableListOf<HelmetState>()

        monitor(link, this, states).watch(address)
        advanceUntilIdle()

        assertEquals(1, link.nudges)
        assertEquals(listOf(HelmetState.WAITING, HelmetState.MISSING), states)
    }

    @Test
    fun `no chosen device means nothing is watched and nothing is claimed`() = runTest {
        val link = FakeHelmetLink(connected = false)
        val states = mutableListOf<HelmetState>()

        monitor(link, this, states).watch(null)
        advanceUntilIdle()

        assertEquals(listOf(HelmetState.NONE), states)
        assertEquals(0, link.nudges)
    }

    @Test
    fun `a late connection still corrects the report`() = runTest {
        val link = FakeHelmetLink(connected = false)
        val states = mutableListOf<HelmetState>()
        val subject = monitor(link, this, states)
        subject.watch(address)
        advanceUntilIdle()
        assertTrue(states.last() == HelmetState.MISSING)

        // The rider powers the helmet on after setting off; the ACL broadcast lands.
        link.connected = true
        subject.onConnectionChanged(address)

        assertEquals(HelmetState.CONNECTED, states.last())
    }

    @Test
    fun `re-watching cancels the previous watch instead of running two`() = runTest {
        val link = FakeHelmetLink(connected = false)
        val states = mutableListOf<HelmetState>()
        val subject = monitor(link, this, states)

        subject.watch(address)
        subject.watch(address)
        advanceUntilIdle()

        // Two overlapping watches would nudge twice and fight over the reported state.
        assertEquals(1, link.nudges)
    }

    @Test
    fun `stopping ends the watch before it can report`() = runTest {
        val link = FakeHelmetLink(connected = false)
        val states = mutableListOf<HelmetState>()
        val subject = monitor(link, this, states)

        subject.watch(address)
        advanceTimeBy(3_000)
        subject.stop()
        advanceUntilIdle()

        assertEquals(listOf(HelmetState.WAITING), states)
        assertEquals(0, link.nudges)
    }
}

package com.shadatrahman.bikemode.bluetooth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Watches for the rider's helmet after a ride starts, and reports what it finds.
 *
 * The sequence exists because Android does the connecting, not us. First it waits, because the
 * system's own auto-connect usually gets there on its own once Bluetooth comes up. Only if that
 * has not happened does it try [HelmetLink.nudge], and only if *that* fails does it report
 * [HelmetState.MISSING] — at which point the rider is told, and can fix it in one tap before
 * setting off rather than discovering it at speed.
 *
 * The watch is bounded. Once it settles the polling stops for good, so nothing keeps waking the
 * phone for the rest of the ride.
 */
class HelmetMonitor(
    private val link: HelmetLink,
    private val scope: CoroutineScope,
    private val onState: (HelmetState) -> Unit,
) {

    private var job: Job? = null

    /** Restarts the watch for [address]. Passing null stops watching and reports [HelmetState.NONE]. */
    fun watch(address: String?) {
        job?.cancel()
        if (address == null) {
            onState(HelmetState.NONE)
            return
        }
        job = scope.launch {
            if (link.isConnected(address)) {
                onState(HelmetState.CONNECTED)
                return@launch
            }
            onState(HelmetState.WAITING)
            if (awaitConnected(address, GRACE_MS)) {
                onState(HelmetState.CONNECTED)
                return@launch
            }
            link.nudge(address)
            onState(if (awaitConnected(address, AFTER_NUDGE_MS)) HelmetState.CONNECTED else HelmetState.MISSING)
        }
    }

    /** Called by the ACL broadcast, so a late connection still corrects the notification. */
    fun onConnectionChanged(address: String) {
        onState(if (link.isConnected(address)) HelmetState.CONNECTED else HelmetState.MISSING)
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun awaitConnected(address: String, window: Long): Boolean {
        var waited = 0L
        while (waited < window) {
            delay(POLL_MS)
            waited += POLL_MS
            if (link.isConnected(address)) return true
        }
        return false
    }

    private companion object {
        /** Long enough for Android's own auto-connect, short enough to still act before moving off. */
        const val GRACE_MS = 15_000L
        const val AFTER_NUDGE_MS = 10_000L
        const val POLL_MS = 1_000L
    }
}

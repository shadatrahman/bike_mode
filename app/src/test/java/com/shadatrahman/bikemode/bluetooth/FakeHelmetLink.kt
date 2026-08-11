package com.shadatrahman.bikemode.bluetooth

import com.shadatrahman.bikemode.data.PairedDevice

/** In-memory [HelmetLink]. Records nudges instead of opening sockets. */
class FakeHelmetLink(
    private val bonded: List<PairedDevice> = emptyList(),
    var connected: Boolean = false,
    /** Whether a nudge is what finally brings the helmet up, the case the retry exists for. */
    var nudgeConnects: Boolean = false,
) : HelmetLink {

    var nudges = 0
        private set

    var closed = false
        private set

    override fun bondedDevices(): List<PairedDevice> = bonded

    override fun isConnected(address: String): Boolean = connected

    override suspend fun nudge(address: String): Boolean {
        nudges++
        if (nudgeConnects) connected = true
        return nudgeConnects
    }

    override fun close() {
        closed = true
    }
}

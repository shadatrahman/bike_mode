package com.shadatrahman.bikemode.media

/**
 * Stops whatever is playing when the ride ends, so music does not carry on in a helmet the rider
 * has just taken off.
 *
 * A seam rather than a direct call, so the toggle rules stay testable without Android.
 */
interface MediaPauser {

    /** Pauses the active media session. Does nothing when nothing is playing. */
    fun pause()
}

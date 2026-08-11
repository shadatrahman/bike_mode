package com.shadatrahman.bikemode.display

/**
 * Decides from a light reading whether the screen should be forced bright, with a wide dead band
 * between the two thresholds.
 *
 * The gap is the whole point. A single threshold would flap every time the rider passed under a
 * bridge or a tree, and each flip is a visible jump in screen brightness right in their eye line.
 * Boosting needs unambiguous daylight; releasing needs it to be unambiguously gone.
 */
class DaylightGate(private var boosted: Boolean = false) {

    /** Returns the new state when it changes, or null when the reading changes nothing. */
    fun update(lux: Float): Boolean? = when {
        !boosted && lux >= BOOST_ABOVE_LUX -> true.also { boosted = it }
        boosted && lux <= RELEASE_BELOW_LUX -> false.also { boosted = it }
        else -> null
    }

    private companion object {
        /**
         * Well above a bright indoor room, which sits nearer 500 lux: this should only fire
         * outdoors in real daylight, not on a desk under a window.
         */
        const val BOOST_ABOVE_LUX = 5_000f

        /** Overcast daylight still reads in the thousands, so releasing here means dusk or later. */
        const val RELEASE_BELOW_LUX = 2_000f
    }
}

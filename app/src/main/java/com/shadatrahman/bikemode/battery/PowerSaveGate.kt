package com.shadatrahman.bikemode.battery

/** How much of the ride's comfort Bike Mode gives back to keep the phone alive. */
enum class BatterySaving {
    /** Nothing given up: the ride behaves exactly as configured. */
    NONE,

    /** Full brightness released — much the most expensive thing Bike Mode does. */
    BRIGHTNESS,

    /** The screen is allowed to sleep between glances too. Navigation still runs. */
    BRIGHTNESS_AND_TIMEOUT,
}

/**
 * Decides how far to ease off from the charge state, easing back on only once the battery has
 * clearly recovered.
 *
 * The gaps between the thresholds matter as much as the thresholds. A phone hovering at exactly
 * twenty percent would otherwise flip the screen bright and dim again every time the reading
 * twitched, which on a mount is both distracting and self-defeating.
 *
 * Charging beats everything: a rider on a powered mount has no problem to solve, so the guard gets
 * out of the way entirely rather than second-guessing a phone that is filling up.
 */
class PowerSaveGate(private var level: BatterySaving = BatterySaving.NONE) {

    /** Returns the new level when it changes, or null when the reading changes nothing. */
    fun update(state: ChargeState): BatterySaving? {
        val next = when {
            state.charging -> BatterySaving.NONE
            state.percent <= SLEEP_AT -> BatterySaving.BRIGHTNESS_AND_TIMEOUT
            state.percent <= DIM_AT && level < BatterySaving.BRIGHTNESS -> BatterySaving.BRIGHTNESS
            state.percent >= DIM_CLEAR_AT -> BatterySaving.NONE
            state.percent >= SLEEP_CLEAR_AT && level == BatterySaving.BRIGHTNESS_AND_TIMEOUT ->
                BatterySaving.BRIGHTNESS

            else -> level
        }
        return next.takeIf { it != level }?.also { level = it }
    }

    private companion object {
        const val DIM_AT = 20
        const val DIM_CLEAR_AT = 25
        const val SLEEP_AT = 10
        const val SLEEP_CLEAR_AT = 15
    }
}

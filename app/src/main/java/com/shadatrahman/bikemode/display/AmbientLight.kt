package com.shadatrahman.bikemode.display

/**
 * The phone's light sensor, which is what decides whether a brightness boost is wanted.
 *
 * The switch alone is not enough to go by: full brightness is the right answer in direct sun and
 * the wrong one — dazzling, and a genuine hazard — after dark. A commute that sets off in daylight
 * and finishes at night crosses that line mid-ride, so the reading has to be watched rather than
 * taken once.
 */
interface AmbientLight {

    /** False on a phone with no light sensor, where daylight simply cannot be judged. */
    val isAvailable: Boolean

    /** A single reading, or null if the sensor gives nothing promptly. */
    suspend fun currentLux(): Float?

    /** Readings for as long as the ride lasts. Replaces any previous observer. */
    fun observe(onLux: (Float) -> Unit)

    fun stop()
}

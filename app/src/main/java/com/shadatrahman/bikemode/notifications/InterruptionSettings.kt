package com.shadatrahman.bikemode.notifications

/**
 * Silencing notifications for the length of a ride.
 *
 * The one interruption Bike Mode had left untouched, and the only one that is actually dangerous:
 * a burst of message tones fired straight into a helmet intercom at speed takes a rider's attention
 * off the road in a way a lit screen never does.
 *
 * Deliberately the priority filter rather than total silence — see [apply]. A seam rather than a
 * direct call, so the save-and-restore rules stay testable without Android.
 */
interface InterruptionSettings {

    /**
     * Whether the rider has granted notification policy access. It cannot be requested at runtime,
     * only granted on a system screen, exactly like WRITE_SETTINGS.
     */
    val canControl: Boolean

    /** The filter in force right now, so it can be handed back when the ride ends. */
    fun current(): Int

    /**
     * Silences notifications while letting urgent calls through.
     *
     * Priority, never total silence: the priority filter still passes repeat callers and starred
     * contacts, so somebody who genuinely needs the rider gets through on a second attempt. Total
     * silence would block that too, which is the wrong trade to make on a motorbike.
     *
     * Navigation is unaffected either way — voice guidance goes out over the media stream, which
     * no interruption filter touches.
     */
    fun apply(): Result<Unit>

    /** Writes [previous] back. Null means Bike Mode never silenced anything, so nothing is owed. */
    fun restore(previous: Int?): Result<Unit>
}

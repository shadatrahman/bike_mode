package com.shadatrahman.bikemode.battery

/** What the battery is doing: how full, and whether anything is filling it. */
data class ChargeState(val percent: Int, val charging: Boolean)

/**
 * The battery, watched for as long as a ride lasts.
 *
 * Bike Mode spends the ride making the phone work harder — screen never sleeping, brightness at
 * maximum, Bluetooth up, a resident service — and none of that watched what it cost. Arriving with
 * a flat phone is the one failure that takes the navigation and the emergency call with it.
 *
 * Needs no permission: charge level and charging state are open API.
 */
interface BatteryStatus {

    fun current(): ChargeState

    /** Readings for as long as the ride lasts. Replaces any previous observer. */
    fun observe(onChange: (ChargeState) -> Unit)

    fun stop()
}

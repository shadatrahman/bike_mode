package com.shadatrahman.bikemode.rotation

/**
 * Watches for other apps knocking the pinned rotation loose while Bike Mode is on.
 *
 * A portrait-locked app coming to the foreground (the launcher, for one) makes the system rewrite
 * USER_ROTATION back to 0, which leaves Bike Mode nominally on while the screen no longer holds
 * landscape. The watchdog exists to notice that and put the rider's direction back.
 */
interface RotationWatchdog {

    fun start()

    fun stop()
}

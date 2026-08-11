package com.shadatrahman.bikemode.rotation

/** Records whether Bike Mode asked for the drift watchdog to be running. */
class FakeRotationWatchdog : RotationWatchdog {

    var running = false
        private set

    var startCount = 0
        private set

    override fun start() {
        running = true
        startCount++
    }

    override fun stop() {
        running = false
    }
}

package com.shadatrahman.bikemode.media

/** In-memory [MediaPauser]. Counts pauses instead of dispatching key events. */
class FakeMediaPauser : MediaPauser {

    var pauses = 0
        private set

    override fun pause() {
        pauses++
    }
}

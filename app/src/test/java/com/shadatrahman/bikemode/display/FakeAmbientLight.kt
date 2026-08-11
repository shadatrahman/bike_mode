package com.shadatrahman.bikemode.display

/** In-memory [AmbientLight]. [lux] stands in for whatever the sky is doing. */
class FakeAmbientLight(
    override val isAvailable: Boolean = true,
    var lux: Float? = DAYLIGHT,
) : AmbientLight {

    private var observer: ((Float) -> Unit)? = null

    override suspend fun currentLux(): Float? = lux

    override fun observe(onLux: (Float) -> Unit) {
        observer = onLux
    }

    override fun stop() {
        observer = null
    }

    /** Stands in for the light changing during a ride. */
    fun emit(value: Float) {
        lux = value
        observer?.invoke(value)
    }

    companion object {
        const val DAYLIGHT = 20_000f
        const val OVERCAST = 3_000f
        const val NIGHT = 5f
    }
}

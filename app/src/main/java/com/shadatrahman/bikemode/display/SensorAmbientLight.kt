package com.shadatrahman.bikemode.display

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.getSystemService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * [AmbientLight] over `TYPE_LIGHT`.
 *
 * Cheap to leave running: the light sensor is among the lowest-power sensors on the device and
 * Android already keeps it going for its own automatic brightness, so observing it for the length
 * of a ride adds no meaningful drain. The slowest delivery rate is plenty — daylight does not
 * change in milliseconds.
 */
class SensorAmbientLight(context: Context) : AmbientLight {

    private val sensors = context.applicationContext.getSystemService<SensorManager>()

    private val sensor: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_LIGHT)

    private var listener: SensorEventListener? = null

    override val isAvailable: Boolean get() = sensor != null

    override suspend fun currentLux(): Float? {
        val target = sensor ?: return null
        return withTimeoutOrNull(READING_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val oneShot = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        sensors?.unregisterListener(this)
                        if (continuation.isActive) continuation.resume(event.values.firstOrNull())
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                sensors?.registerListener(oneShot, target, SensorManager.SENSOR_DELAY_NORMAL)
                continuation.invokeOnCancellation { sensors?.unregisterListener(oneShot) }
            }
        }
    }

    override fun observe(onLux: (Float) -> Unit) {
        val target = sensor ?: return
        stop()
        val watcher = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                event.values.firstOrNull()?.let(onLux)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensors?.registerListener(watcher, target, SensorManager.SENSOR_DELAY_NORMAL)
        listener = watcher
    }

    override fun stop() {
        listener?.let { sensors?.unregisterListener(it) }
        listener = null
    }

    private companion object {
        /** A live sensor answers in well under this; the timeout only covers one that never does. */
        const val READING_TIMEOUT_MS = 1_000L
    }
}

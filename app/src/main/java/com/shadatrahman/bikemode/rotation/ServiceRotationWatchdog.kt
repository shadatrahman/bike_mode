package com.shadatrahman.bikemode.rotation

import android.content.Context

/**
 * The watchdog Bike Mode actually uses: a resident [RotationWatchdogService] for instant, reliable
 * repair, with [JobRotationWatchdog] behind it as the recovery path.
 *
 * Neither alone is enough. The service cannot come back on its own once the system kills the
 * process or the phone reboots; the job can, but only on the system's schedule and never across a
 * reboot without help. Run both and each covers the other's blind spot, at no idle cost — the
 * service sleeps on a content observer, and an unfired job draws nothing.
 */
class ServiceRotationWatchdog(context: Context) : RotationWatchdog {

    private val appContext = context.applicationContext
    private val job = JobRotationWatchdog(appContext)

    override fun start() {
        job.start()
        RotationWatchdogService.start(appContext)
    }

    override fun stop() {
        RotationWatchdogService.stop(appContext)
        job.stop()
    }
}

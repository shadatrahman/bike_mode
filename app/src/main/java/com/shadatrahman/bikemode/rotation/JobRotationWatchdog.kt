package com.shadatrahman.bikemode.rotation

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.core.content.getSystemService
import com.shadatrahman.bikemode.widget.BikeModeWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * [RotationWatchdog] built on a JobScheduler content trigger, so the system wakes the app only
 * when a rotation setting actually changes. Nothing polls and nothing draws power while it waits.
 *
 * This is the recovery half of [ServiceRotationWatchdog], not the whole watchdog: a job outlives
 * the process, so it can bring [RotationWatchdogService] back after a kill, but the system is free
 * to defer it by the app's standby bucket and it cannot survive a reboot (content-trigger jobs
 * cannot be persisted — see BootReceiver for that half).
 */
class JobRotationWatchdog(context: Context) : RotationWatchdog {

    private val appContext = context.applicationContext

    override fun start() {
        schedule(appContext)
    }

    override fun stop() {
        appContext.getSystemService<JobScheduler>()?.cancel(JOB_ID)
    }

    companion object {
        private const val JOB_ID = 1001

        /**
         * Content-trigger jobs fire once, so every run re-arms the next one. The delays let the
         * system batch a burst of writes (our own two writes included) into a single wake-up.
         */
        fun schedule(context: Context) {
            val scheduler = context.getSystemService<JobScheduler>() ?: return
            val job = JobInfo.Builder(
                JOB_ID,
                ComponentName(context, RotationWatchdogJobService::class.java),
            )
                .addTriggerContentUri(triggerFor(Settings.System.USER_ROTATION))
                .addTriggerContentUri(triggerFor(Settings.System.ACCELEROMETER_ROTATION))
                .setTriggerContentUpdateDelay(TRIGGER_DELAY_MS)
                .setTriggerContentMaxDelay(TRIGGER_MAX_DELAY_MS)
                .build()
            scheduler.schedule(job)
        }

        private fun triggerFor(setting: String) =
            JobInfo.TriggerContentUri(Settings.System.getUriFor(setting), 0)

        private const val TRIGGER_DELAY_MS = 500L
        private const val TRIGGER_MAX_DELAY_MS = 2_000L
    }
}

/** Runs when a rotation setting changes: puts the rider's direction back if it drifted. */
class RotationWatchdogJobService : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartJob(params: JobParameters?): Boolean {
        // Spelled out here once, but it had drifted into an exact copy of the context constructor.
        val manager = BikeModeManager(applicationContext)
        scope.launch {
            val stillActive = manager.reassert()
            if (stillActive) {
                // Re-arm only while Bike Mode is on; turning it off leaves no job behind.
                JobRotationWatchdog.schedule(applicationContext)
                // Being woken at all means the process may have been killed, taking the observer
                // with it. Try to bring it back; the system may refuse a background start, in which
                // case this job keeps carrying the repairs on its own.
                RotationWatchdogService.start(applicationContext)
            }
            // This also fires when the rider re-enables auto-rotate from the system Quick
            // Settings, which ends Bike Mode behind our back — the widget has to follow.
            BikeModeWidgetProvider.refresh(applicationContext)
            jobFinished(params, false)
        }
        return true
    }

    /** Returning true asks the system to re-run us, since the drift is still unhandled. */
    override fun onStopJob(params: JobParameters?): Boolean = true

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

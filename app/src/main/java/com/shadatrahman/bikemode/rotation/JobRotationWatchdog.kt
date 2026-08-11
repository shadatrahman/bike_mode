package com.shadatrahman.bikemode.rotation

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.core.content.getSystemService
import com.shadatrahman.bikemode.data.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * [RotationWatchdog] built on a JobScheduler content trigger, so the system wakes the app only
 * when a rotation setting actually changes. Nothing polls, nothing stays resident, and no
 * foreground service is needed — the app still costs nothing while the rider is moving.
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
        val manager = BikeModeManager(
            store = PreferencesRepository(applicationContext),
            settings = RotationController(applicationContext),
            watchdog = JobRotationWatchdog(applicationContext),
        )
        scope.launch {
            val stillActive = manager.reassert()
            // Re-arm only while Bike Mode is on; turning it off leaves no job behind.
            if (stillActive) JobRotationWatchdog.schedule(applicationContext)
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

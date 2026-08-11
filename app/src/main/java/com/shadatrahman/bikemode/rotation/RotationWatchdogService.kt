package com.shadatrahman.bikemode.rotation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.shadatrahman.bikemode.MainActivity
import com.shadatrahman.bikemode.R
import com.shadatrahman.bikemode.data.LandscapeDirection
import com.shadatrahman.bikemode.widget.BikeModeWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps Bike Mode's landscape lock alive for as long as it is on, and only that long.
 *
 * The JobScheduler watchdog on its own is not enough: a content-trigger job cannot be persisted, so
 * it dies at every reboot, and once the process is gone the system is free to defer or drop it
 * depending on the app's standby bucket. Riders saw the lock quietly stop being re-applied after
 * the app left memory.
 *
 * A foreground service fixes that by keeping the process resident, which costs effectively no
 * battery here: the service holds no wake lock, reads no sensors, touches no network and never
 * polls. It registers a [ContentObserver] on the two rotation settings and then sleeps until the
 * system pushes a change — the same signal the job used, minus the wake-up latency.
 */
class RotationWatchdogService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val manager by lazy { BikeModeManager(applicationContext) }

    private var observer: ContentObserver? = null
    private var pendingReassert: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            turnOff()
            return START_NOT_STICKY
        }

        // Must happen before anything slow: the system gives us five seconds to go foreground.
        goForeground(direction = null)
        observe()
        // If the system kills this process anyway, the content-trigger job survives to restart us.
        JobRotationWatchdog.schedule(applicationContext)
        // Whatever drifted while we were not running gets repaired the moment we come back up.
        reassertSoon(delayMs = 0)
        return START_STICKY
    }

    override fun onDestroy() {
        observer?.let { contentResolver.unregisterContentObserver(it) }
        observer = null
        scope.cancel()
        super.onDestroy()
    }

    /**
     * One observer on both settings. Our own two writes fire it as well, but a reassert whose
     * rotation already matches writes nothing, so there is no feedback loop.
     */
    private fun observe() {
        if (observer != null) return
        val watcher = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = reassertSoon(SETTLE_MS)
        }
        listOf(Settings.System.USER_ROTATION, Settings.System.ACCELEROMETER_ROTATION).forEach {
            contentResolver.registerContentObserver(Settings.System.getUriFor(it), false, watcher)
        }
        observer = watcher
    }

    /**
     * Coalesces a burst of writes into one repair — turning auto-rotate off and pinning a direction
     * is two writes, and other apps are no tidier.
     */
    private fun reassertSoon(delayMs: Long) {
        pendingReassert?.cancel()
        pendingReassert = scope.launch {
            if (delayMs > 0) delay(delayMs)
            // Bike Mode ends behind our back when the rider re-enables auto-rotate from the system
            // Quick Settings; reassert reports that as inactive and we take ourselves down with it.
            if (!manager.reassert()) {
                stopSelf()
            } else {
                goForeground(manager.preferences().direction)
            }
            BikeModeWidgetProvider.refresh(applicationContext)
        }
    }

    /** The notification's "Turn off" action: ends Bike Mode outright, not just this service. */
    private fun turnOff() {
        scope.launch {
            manager.disable()
            BikeModeWidgetProvider.refresh(applicationContext)
            stopSelf()
        }
    }

    /** The special-use type only exists from API 34; below it the untyped call is the correct one. */
    private fun goForeground(direction: LandscapeDirection?) {
        val notification = buildNotification(direction)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(direction: LandscapeDirection?): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, RotationWatchdogService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = when (direction) {
            LandscapeDirection.LEFT -> R.string.watchdog_text_left
            LandscapeDirection.RIGHT -> R.string.watchdog_text_right
            null -> R.string.bike_mode_locked_landscape
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bike_mode)
            .setContentTitle(getString(R.string.watchdog_title))
            .setContentText(getString(text))
            .setContentIntent(open)
            .addAction(0, getString(R.string.watchdog_stop), stop)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** Low importance: the notification is a status line and an off switch, never an alert. */
    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.watchdog_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.watchdog_channel_description)
            setShowBadge(false)
        }
        getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "bike_mode_active"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.shadatrahman.bikemode.rotation.STOP"

        /** Long enough to swallow a two-write burst, short enough that the rider sees no lag. */
        private const val SETTLE_MS = 250L

        /**
         * Android 12+ refuses foreground service starts from the background. Every path that turns
         * Bike Mode on is an exempted one — a tap in the app, on the tile, on the widget, or a
         * BOOT_COMPLETED broadcast — but the JobScheduler backstop is not, so a refusal is normal
         * and survivable: the job alone still repairs rotation, just on the system's schedule.
         */
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, RotationWatchdogService::class.java),
                )
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RotationWatchdogService::class.java))
        }
    }
}

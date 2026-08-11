package com.shadatrahman.bikemode.rotation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.shadatrahman.bikemode.battery.BatterySaving
import com.shadatrahman.bikemode.battery.PowerSaveGate
import com.shadatrahman.bikemode.battery.SystemBatteryStatus
import com.shadatrahman.bikemode.bluetooth.BluetoothHelmetLink
import com.shadatrahman.bikemode.bluetooth.HelmetMonitor
import com.shadatrahman.bikemode.bluetooth.HelmetState
import com.shadatrahman.bikemode.data.LandscapeDirection
import com.shadatrahman.bikemode.data.SavedDisplayState
import com.shadatrahman.bikemode.display.DaylightGate
import com.shadatrahman.bikemode.display.DisplayController
import com.shadatrahman.bikemode.display.SensorAmbientLight
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
    private val helmetLink by lazy { BluetoothHelmetLink(applicationContext) }
    private val ambientLight by lazy { SensorAmbientLight(applicationContext) }
    private val battery by lazy { SystemBatteryStatus(applicationContext) }
    private val display by lazy { DisplayController(applicationContext) }
    private val helmetMonitor by lazy {
        HelmetMonitor(helmetLink, scope) { state ->
            helmetState = state
            refreshNotification()
        }
    }

    private var observer: ContentObserver? = null
    private var pendingReassert: Job? = null
    private var aclReceiver: BroadcastReceiver? = null

    /** Cached so the notification can be rebuilt from any of the things that change it. */
    @Volatile private var direction: LandscapeDirection? = null

    @Volatile private var helmetState: HelmetState = HelmetState.NONE

    @Volatile private var helmetAddress: String? = null

    /** The two inputs [applyScreenDecision] settles between, plus what is owed back either way. */
    @Volatile private var daylight = false

    @Volatile private var saving: BatterySaving = BatterySaving.NONE

    @Volatile private var chargePercent = 100

    @Volatile private var keepAwakeWanted = false

    @Volatile private var owedDisplay: SavedDisplayState? = null

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
        refreshNotification()
        observe()
        watchHelmet()
        watchPower()
        // If the system kills this process anyway, the content-trigger job survives to restart us.
        JobRotationWatchdog.schedule(applicationContext)
        // Whatever drifted while we were not running gets repaired the moment we come back up.
        reassertSoon(delayMs = 0)
        return START_STICKY
    }

    override fun onDestroy() {
        observer?.let { contentResolver.unregisterContentObserver(it) }
        observer = null
        aclReceiver?.let { runCatching { unregisterReceiver(it) } }
        aclReceiver = null
        helmetMonitor.stop()
        helmetLink.close()
        ambientLight.stop()
        battery.stop()
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Follows the light and the battery for the length of the ride.
     *
     * They are watched together because they argue over the same setting: daylight asks for full
     * brightness, a draining battery asks for it back. Rather than let two observers fight, both
     * feed flags and [applyScreenDecision] settles it — with the battery winning, since the point
     * of easing off is undone by anything that overrides it.
     *
     * Only what was captured at the start is ever restored; the rest of what Bike Mode owes stays
     * untouched until the ride actually ends.
     */
    private fun watchPower() {
        scope.launch {
            val prefs = manager.preferences()
            owedDisplay = prefs.previousDisplay
            keepAwakeWanted = prefs.keepScreenOn

            if (prefs.boostBrightness && ambientLight.isAvailable) {
                daylight = display.brightnessMode() == MANUAL_BRIGHTNESS
                val gate = DaylightGate(boosted = daylight)
                ambientLight.observe { lux ->
                    gate.update(lux)?.let {
                        daylight = it
                        applyScreenDecision()
                    }
                }
            } else {
                // No sensor, or no boost wanted: the switch alone decides, as the manager already did.
                daylight = prefs.boostBrightness
            }

            if (prefs.batteryGuard) {
                val gate = PowerSaveGate()
                battery.observe { charge ->
                    gate.update(charge)?.let {
                        saving = it
                        chargePercent = charge.percent
                        applyScreenDecision()
                        refreshNotification()
                    }
                }
            }
        }
    }

    /**
     * The one place the screen settings are decided, so the two watchers cannot undo each other.
     *
     * Everything here is a re-application of a state already chosen, which is why it is safe to run
     * on every reading: writing the same value twice costs nothing and changes nothing.
     */
    private fun applyScreenDecision() {
        val owed = owedDisplay
        if (daylight && saving == BatterySaving.NONE) {
            display.applyBrightnessBoost()
        } else {
            display.restore(
                SavedDisplayState(brightness = owed?.brightness, brightnessMode = owed?.brightnessMode)
            )
        }
        when {
            saving == BatterySaving.BRIGHTNESS_AND_TIMEOUT ->
                display.restore(SavedDisplayState(screenOffTimeout = owed?.screenOffTimeout))

            keepAwakeWanted -> display.applyKeepAwake()
        }
    }

    /**
     * Starts the bounded connect watch, plus an ACL listener that keeps the notification honest if
     * the helmet turns up later — a broadcast costs nothing while nothing happens, unlike a poll.
     */
    private fun watchHelmet() {
        scope.launch {
            val helmet = manager.preferences().helmet
            helmetAddress = helmet?.address
            helmetMonitor.watch(helmet?.address)
        }
        if (aclReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val address = helmetAddress ?: return
                helmetMonitor.onConnectionChanged(address)
            }
        }
        registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            },
            RECEIVER_NOT_EXPORTED,
        )
        aclReceiver = receiver
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
                direction = manager.preferences().direction
                refreshNotification()
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

    /**
     * Also the update path: calling startForeground again while already foreground just replaces
     * the notification, which is what every state change here needs.
     *
     * The special-use type only exists from API 34; below it the untyped call is the correct one.
     */
    private fun refreshNotification() {
        val notification = buildNotification()
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

    private fun buildNotification(): Notification {
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
        val rotation = getString(
            when (direction) {
                LandscapeDirection.LEFT -> R.string.watchdog_text_left
                LandscapeDirection.RIGHT -> R.string.watchdog_text_right
                null -> R.string.bike_mode_locked_landscape
            }
        )
        val helmet = helmetState.summaryRes()?.let { getString(it) }
        // Named outright, so easing off never reads as the brightness setting having failed.
        val power = if (saving == BatterySaving.NONE) null
        else getString(R.string.watchdog_saving_battery, chargePercent)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bike_mode)
            .setContentTitle(getString(R.string.watchdog_title))
            .setContentText(
                listOfNotNull(rotation, helmet, power).joinToString(SEPARATOR)
            )
            .setContentIntent(open)
            .addAction(0, getString(R.string.watchdog_stop), stop)
            // Only offered when it is the useful thing to tap: the helmet did not turn up.
            .apply {
                if (helmetState == HelmetState.MISSING) {
                    addAction(0, getString(R.string.watchdog_bluetooth_settings), bluetoothSettings())
                }
            }
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** Nothing to say when no device was chosen, so the notification stays about rotation alone. */
    private fun HelmetState.summaryRes(): Int? = when (this) {
        HelmetState.NONE -> null
        HelmetState.WAITING -> R.string.watchdog_helmet_waiting
        HelmetState.CONNECTED -> R.string.watchdog_helmet_connected
        HelmetState.MISSING -> R.string.watchdog_helmet_missing
    }

    private fun bluetoothSettings(): PendingIntent = PendingIntent.getActivity(
        this,
        2,
        Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

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

        /** `Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL`, which is how a live boost reads. */
        private const val MANUAL_BRIGHTNESS = 0

        /** Between the parts of the notification's one status line. */
        private const val SEPARATOR = " · "

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

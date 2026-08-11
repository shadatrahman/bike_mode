package com.shadatrahman.bikemode.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService

/** [InterruptionSettings] backed by the real [NotificationManager]. */
class InterruptionController(context: Context) : InterruptionSettings {

    private val appContext = context.applicationContext

    private val notifications get() = appContext.getSystemService<NotificationManager>()

    override val canControl: Boolean
        get() = notifications?.isNotificationPolicyAccessGranted == true

    override fun current(): Int =
        notifications?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL

    override fun apply(): Result<Unit> = set(NotificationManager.INTERRUPTION_FILTER_PRIORITY)

    /**
     * An unknown filter is not something to write back: the system reports it when it cannot say
     * what is in force, and guessing would leave the rider somewhere they never chose.
     */
    override fun restore(previous: Int?): Result<Unit> {
        if (previous == null || previous == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
            return Result.success(Unit)
        }
        return set(previous)
    }

    private fun set(filter: Int): Result<Unit> = runCatching {
        val manager = checkNotNull(notifications) { "No NotificationManager" }
        // Setting the filter without access throws, and the rider may have revoked it mid-ride.
        check(manager.isNotificationPolicyAccessGranted) { "Notification policy access not granted" }
        manager.setInterruptionFilter(filter)
    }
}

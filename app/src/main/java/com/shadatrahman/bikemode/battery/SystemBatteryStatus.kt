package com.shadatrahman.bikemode.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.shadatrahman.bikemode.util.reportingFailure

/**
 * [BatteryStatus] over [BatteryManager] and the battery broadcast.
 *
 * Costs nothing to keep running: ACTION_BATTERY_CHANGED is sticky and system-sent, so registering
 * for it hands back the current reading immediately and then wakes nobody until the level actually
 * moves. No polling, no permission.
 */
class SystemBatteryStatus(context: Context) : BatteryStatus {

    private val appContext = context.applicationContext

    private var receiver: BroadcastReceiver? = null

    override fun current(): ChargeState = reportingFailure(TAG, "Reading the battery", UNKNOWN) {
        val manager = appContext.getSystemService<BatteryManager>() ?: return@reportingFailure UNKNOWN
        ChargeState(
            percent = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            charging = manager.isCharging,
        )
    }

    override fun observe(onChange: (ChargeState) -> Unit) {
        stop()
        val watcher = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                onChange(intent.toChargeState() ?: return)
            }
        }
        // The registration itself returns the sticky value, so the first reading needs no wait.
        ContextCompat.registerReceiver(
            appContext,
            watcher,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )?.toChargeState()?.let(onChange)
        receiver = watcher
    }

    override fun stop() {
        receiver?.let { reportingFailure(TAG, "Unregistering the battery receiver", Unit) {
            appContext.unregisterReceiver(it)
        } }
        receiver = null
    }

    /**
     * The broadcast reports level against a scale rather than a percentage, and both are absent on
     * a malformed intent — worth returning null over rather than reporting a phone at zero.
     */
    private fun Intent.toChargeState(): ChargeState? {
        val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        val status = getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        return ChargeState(
            percent = level * 100 / scale,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL,
        )
    }

    private companion object {
        const val TAG = "SystemBatteryStatus"

        /** Full and charging: the reading that makes the guard keep its hands off. */
        val UNKNOWN = ChargeState(percent = 100, charging = true)
    }
}

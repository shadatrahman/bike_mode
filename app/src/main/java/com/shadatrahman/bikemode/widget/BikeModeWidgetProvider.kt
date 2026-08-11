package com.shadatrahman.bikemode.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.SizeF
import android.widget.RemoteViews
import com.shadatrahman.bikemode.MainActivity
import com.shadatrahman.bikemode.R
import com.shadatrahman.bikemode.data.LandscapeDirection
import com.shadatrahman.bikemode.rotation.BikeModeManager
import com.shadatrahman.bikemode.util.PermissionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Home-screen widget. One tap toggles Bike Mode without opening anything, the same as the Quick
 * Settings tile.
 *
 * Styled to sit beside Nothing OS's own widgets: pure black when off, Nothing red when on,
 * monospaced uppercase with wide tracking, and the platform widget corner radius. It ships three
 * responsive layouts so dragging the corner reflows it rather than just stretching one design.
 */
class BikeModeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        refresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TOGGLE) return

        // The broadcast ends as soon as onReceive returns, so hold it open for the toggle.
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                if (PermissionManager.canWriteSettings(appContext)) {
                    BikeModeManager(appContext).toggle()
                } else {
                    appContext.startActivity(
                        Intent(appContext, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                refresh(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {

        private const val ACTION_TOGGLE = "com.shadatrahman.bikemode.widget.TOGGLE"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Redraws every placed widget. Safe to call when none exist. */
        fun refresh(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(
                ComponentName(appContext, BikeModeWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            scope.launch {
                val active = PermissionManager.canWriteSettings(appContext) &&
                    BikeModeManager(appContext).isActive()
                val direction = BikeModeManager(appContext).preferences().direction
                val views = buildViews(appContext, active, direction)
                ids.forEach { manager.updateAppWidget(it, views) }
            }
        }

        private fun buildViews(
            context: Context,
            active: Boolean,
            direction: LandscapeDirection,
        ): RemoteViews {
            // Breakpoints, not scaling: each form drops what no longer fits at that size. Sized
            // against real cells — a 2x1 lands near 164x104dp, a 2x2 near 164x210, a 4x2 near
            // 340x210 — so dragging a corner crosses a boundary instead of just stretching.
            val layouts = mapOf(
                SizeF(110f, 40f) to layout(context, R.layout.widget_bike_mode_compact, active, direction),
                SizeF(250f, 40f) to layout(context, R.layout.widget_bike_mode_large, active, direction),
                SizeF(110f, 150f) to layout(context, R.layout.widget_bike_mode_small, active, direction),
                SizeF(250f, 150f) to layout(context, R.layout.widget_bike_mode_large, active, direction),
            )
            return RemoteViews(layouts)
        }

        private fun layout(
            context: Context,
            layoutId: Int,
            active: Boolean,
            direction: LandscapeDirection,
        ): RemoteViews = RemoteViews(context.packageName, layoutId).apply {
            setInt(
                R.id.widget_root,
                "setBackgroundResource",
                if (active) R.drawable.widget_bg_on else R.drawable.widget_bg_off,
            )
            setTextViewText(
                R.id.widget_state,
                context.getString(if (active) R.string.widget_state_on else R.string.widget_state_off),
            )
            if (layoutId == R.layout.widget_bike_mode_large) {
                setTextViewText(
                    R.id.widget_detail,
                    if (active) {
                        context.getString(direction.widgetLabelRes)
                    } else {
                        context.getString(R.string.widget_detail_off)
                    },
                )
            }
            setOnClickPendingIntent(R.id.widget_root, togglePendingIntent(context))
        }

        private fun togglePendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, BikeModeWidgetProvider::class.java).setAction(ACTION_TOGGLE)
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        private val LandscapeDirection.widgetLabelRes: Int
            get() = when (this) {
                LandscapeDirection.LEFT -> R.string.widget_detail_left
                LandscapeDirection.RIGHT -> R.string.widget_detail_right
            }
    }
}

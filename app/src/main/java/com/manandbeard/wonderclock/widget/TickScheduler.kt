package com.manandbeard.wonderclock.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Keeps the widgets ticking over on the minute.
 *
 * A one-shot alarm is re-armed after every fire rather than using a repeating
 * alarm, because repeating alarms below fifteen minutes are silently made
 * inexact. `RTC` rather than `RTC_WAKEUP`: there is no point waking a sleeping
 * device to redraw something nobody is looking at — the widget is refreshed
 * again on the next tick, and the framework's own half-hourly update acts as a
 * backstop.
 */
object TickScheduler {

    const val ACTION_TICK = "com.manandbeard.wonderclock.action.TICK"
    private const val REQUEST_CODE = 0x77C1

    /** Arms the tick when widgets exist, cancels it when the last one goes. */
    fun sync(context: Context) {
        if (WidgetUpdater.allWidgetIds(context).isEmpty()) cancel(context) else schedule(context)
    }

    fun schedule(context: Context) {
        val manager = alarmManager(context) ?: return
        val intent = pendingIntent(context)
        val now = System.currentTimeMillis()
        // A little past the boundary, so the redraw never lands on the previous minute.
        val nextMinute = ((now / 60_000L) + 1L) * 60_000L + 250L

        val scheduled = canScheduleExact(context) && runCatching {
            manager.setExact(AlarmManager.RTC, nextMinute, intent)
        }.isSuccess

        if (!scheduled) {
            runCatching { manager.set(AlarmManager.RTC, nextMinute, intent) }
        }
    }

    fun cancel(context: Context) {
        val manager = alarmManager(context) ?: return
        runCatching { manager.cancel(pendingIntent(context)) }
    }

    /**
     * On Android 12+ exact alarms need a user-granted permission. Without it
     * the clock still updates, just with the few seconds of slop the system
     * allows itself.
     */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = alarmManager(context) ?: return false
        return runCatching { manager.canScheduleExactAlarms() }.getOrDefault(false)
    }

    private fun alarmManager(context: Context): AlarmManager? =
        context.applicationContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context.applicationContext, TickReceiver::class.java)
            .setAction(ACTION_TICK)
        return PendingIntent.getBroadcast(
            context.applicationContext,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

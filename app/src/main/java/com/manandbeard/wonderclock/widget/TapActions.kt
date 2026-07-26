package com.manandbeard.wonderclock.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import com.manandbeard.wonderclock.data.TapAction
import com.manandbeard.wonderclock.ui.ConfigActivity

/** Builds the PendingIntent behind a tap on the widget. */
object TapActions {

    fun pendingIntent(
        context: Context,
        widgetId: Int,
        action: TapAction,
        packageName: String,
        secondary: Boolean,
    ): PendingIntent? {
        val intent = intentFor(context, widgetId, action, packageName) ?: return null
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Distinct request codes so two zones on one widget do not collide, and
        // two widgets never share a PendingIntent.
        val requestCode = widgetId * 2 + if (secondary) 1 else 0
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun intentFor(
        context: Context,
        widgetId: Int,
        action: TapAction,
        packageName: String,
    ): Intent? = when (action) {
        TapAction.NONE -> null

        TapAction.CLOCK -> clockAppIntent(context) ?: Intent(AlarmClock.ACTION_SHOW_ALARMS)

        TapAction.ALARMS -> Intent(AlarmClock.ACTION_SHOW_ALARMS)

        TapAction.TIMER -> Intent(AlarmClock.ACTION_SHOW_TIMERS)

        TapAction.CALENDAR -> Intent(
            Intent.ACTION_VIEW,
            CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build(),
        )

        TapAction.WIDGET_SETTINGS -> ConfigActivity.editIntent(context, widgetId)

        TapAction.APP -> packageName
            .takeIf { it.isNotBlank() }
            ?.let { context.packageManager.getLaunchIntentForPackage(it) }
    }

    /**
     * The device's clock app, found via whoever handles "show alarms". Falls
     * back to null so the caller can use the bare intent instead.
     */
    private fun clockAppIntent(context: Context): Intent? {
        val packageManager = context.packageManager
        val probe = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        val resolved = runCatching { probe.resolveActivity(packageManager) }.getOrNull()
            ?: return null
        return runCatching { packageManager.getLaunchIntentForPackage(resolved.packageName) }
            .getOrNull()
    }

    /** Sends the user straight to a widget's settings, e.g. from the app. */
    fun configureIntent(context: Context, widgetId: Int): Intent =
        ConfigActivity.editIntent(context, widgetId)
}

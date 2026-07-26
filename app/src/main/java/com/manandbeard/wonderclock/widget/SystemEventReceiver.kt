package com.manandbeard.wonderclock.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Anything that changes what the widget should say — a reboot, the clock or
 * time zone being changed, a new alarm, a language switch — lands here.
 *
 * A reboot also clears our pending alarm, so every one of these re-arms the
 * tick as well as redrawing.
 */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WidgetUpdater.updateAll(context)
        TickScheduler.sync(context)
    }
}

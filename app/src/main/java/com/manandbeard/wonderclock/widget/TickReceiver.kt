package com.manandbeard.wonderclock.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires once a minute; redraws every widget and arms the next tick. */
class TickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WidgetUpdater.updateAll(context)
        TickScheduler.sync(context)
    }
}

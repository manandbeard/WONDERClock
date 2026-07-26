package com.manandbeard.wonderclock

import android.app.Application
import com.manandbeard.wonderclock.widget.TickScheduler

class WonderClockApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // If the process was killed while an alarm was pending, this puts the
        // minute tick back without waiting for the framework's half-hourly update.
        TickScheduler.sync(this)
    }
}

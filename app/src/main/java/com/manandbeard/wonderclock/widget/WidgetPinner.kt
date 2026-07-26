package com.manandbeard.wonderclock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.manandbeard.wonderclock.data.ClockStyle

/**
 * Asks the launcher to place a widget, so the user does not have to go hunting
 * through the widget drawer. Not every launcher supports this.
 */
object WidgetPinner {

    fun isSupported(context: Context): Boolean = runCatching {
        AppWidgetManager.getInstance(context)?.isRequestPinAppWidgetSupported == true
    }.getOrDefault(false)

    fun requestPin(context: Context, style: ClockStyle): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        if (!isSupported(context)) return false
        val provider = ComponentName(context.applicationContext, providerFor(style))
        // The callback fires once the widget lands, which is our cue to draw it.
        val callback = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, SystemEventReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return runCatching { manager.requestPinAppWidget(provider, null, callback) }
            .getOrDefault(false)
    }

    fun providerFor(style: ClockStyle): Class<out BaseClockWidgetProvider> = when (style) {
        ClockStyle.ANALOG -> AnalogClockWidgetProvider::class.java
        ClockStyle.WORDS, ClockStyle.WORD_GRID -> WordClockWidgetProvider::class.java
        else -> DigitalClockWidgetProvider::class.java
    }

    /** The style a freshly dropped widget should default to, from its provider. */
    fun styleForProvider(context: Context, widgetId: Int): ClockStyle? {
        val manager = AppWidgetManager.getInstance(context) ?: return null
        val info = runCatching { manager.getAppWidgetInfo(widgetId) }.getOrNull() ?: return null
        return when (info.provider?.className) {
            AnalogClockWidgetProvider::class.java.name -> ClockStyle.ANALOG
            WordClockWidgetProvider::class.java.name -> ClockStyle.WORDS
            DigitalClockWidgetProvider::class.java.name -> ClockStyle.DIGITAL
            else -> null
        }
    }
}

package com.manandbeard.wonderclock.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import com.manandbeard.wonderclock.data.ClockConfig
import com.manandbeard.wonderclock.data.ClockStyle
import com.manandbeard.wonderclock.data.ConfigStore
import com.manandbeard.wonderclock.data.Presets
import com.manandbeard.wonderclock.render.RenderEnv

/**
 * All three widgets behave identically; they differ only in the default size
 * the launcher offers and the preset a fresh one starts from. Every widget can
 * still be switched to any style afterwards.
 */
abstract class BaseClockWidgetProvider : AppWidgetProvider() {

    protected abstract val defaultStyle: ClockStyle

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val env = RenderEnv.capture(context)
        appWidgetIds.forEach { widgetId ->
            if (!ConfigStore.isConfigured(context, widgetId)) {
                // A placeholder so the widget is never blank; it does not count
                // as something the user chose.
                ConfigStore.save(context, widgetId, startingConfig(), rememberAsLast = false)
            }
            WidgetUpdater.update(context, appWidgetManager, widgetId, env)
        }
        TickScheduler.sync(context)
    }

    /** Resize and rotation both land here; the bitmap has to be redrawn to fit. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        WidgetUpdater.update(context, appWidgetManager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        ConfigStore.delete(context, appWidgetIds)
        TickScheduler.sync(context)
    }

    override fun onEnabled(context: Context) {
        TickScheduler.sync(context)
    }

    override fun onDisabled(context: Context) {
        TickScheduler.cancel(context)
    }

    private fun startingConfig(): ClockConfig = Presets.forStyle(defaultStyle).config
}

class DigitalClockWidgetProvider : BaseClockWidgetProvider() {
    override val defaultStyle: ClockStyle = ClockStyle.DIGITAL
}

class AnalogClockWidgetProvider : BaseClockWidgetProvider() {
    override val defaultStyle: ClockStyle = ClockStyle.ANALOG
}

class WordClockWidgetProvider : BaseClockWidgetProvider() {
    override val defaultStyle: ClockStyle = ClockStyle.WORDS
}

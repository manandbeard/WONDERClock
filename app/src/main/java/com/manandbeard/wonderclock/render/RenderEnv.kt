package com.manandbeard.wonderclock.render

import android.app.AlarmManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.text.format.DateFormat
import java.util.Locale

/** Wallpaper-derived colors, available from Android 12 onwards. */
data class DynamicPalette(
    val accent: Int,
    val accentSoft: Int,
    val secondary: Int,
    val surface: Int,
    val onSurface: Int,
)

/**
 * Everything the renderer needs that does not come from the user's config.
 *
 * Passing this in rather than reading it inside the renderer keeps rendering a
 * pure function, which is what lets the in-app preview and the real widget
 * share one code path — and lets previews show plausible sample data.
 */
data class RenderEnv(
    val nowMillis: Long = System.currentTimeMillis(),
    val batteryPercent: Int = 76,
    val charging: Boolean = false,
    /** Epoch millis of the next alarm, or 0 when none is set. */
    val nextAlarmMillis: Long = 0L,
    val system24Hour: Boolean = false,
    val locale: Locale = Locale.getDefault(),
    val dynamic: DynamicPalette? = null,
) {
    companion object {

        fun capture(context: Context): RenderEnv {
            val app = context.applicationContext
            val battery = app.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val percent = battery
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.takeIf { it in 0..100 }
                ?: 100
            val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val nextAlarm = runCatching { alarmManager?.nextAlarmClock?.triggerTime ?: 0L }
                .getOrDefault(0L)

            return RenderEnv(
                nowMillis = System.currentTimeMillis(),
                batteryPercent = percent,
                charging = battery?.isCharging ?: false,
                nextAlarmMillis = nextAlarm,
                system24Hour = DateFormat.is24HourFormat(app),
                locale = primaryLocale(app),
                dynamic = dynamicPalette(app),
            )
        }

        /**
         * Sample data for previews: a fixed-looking battery level and a plausible
         * alarm so every module has something to show even on a fresh device.
         */
        fun preview(context: Context): RenderEnv {
            val real = capture(context)
            return real.copy(
                batteryPercent = 76,
                charging = false,
                // Show a plausible alarm so the module is not blank on a device
                // that happens to have none set.
                nextAlarmMillis = if (real.nextAlarmMillis > 0L) {
                    real.nextAlarmMillis
                } else {
                    real.nowMillis + 8 * 60 * 60 * 1000L
                },
            )
        }

        private fun primaryLocale(context: Context): Locale {
            val locales = context.resources.configuration.locales
            return if (locales.isEmpty) Locale.getDefault() else locales[0]
        }

        private fun dynamicPalette(context: Context): DynamicPalette? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
            return runCatching {
                DynamicPalette(
                    accent = context.getColor(android.R.color.system_accent1_200),
                    accentSoft = context.getColor(android.R.color.system_accent1_100),
                    secondary = context.getColor(android.R.color.system_accent2_200),
                    surface = context.getColor(android.R.color.system_neutral1_900),
                    onSurface = context.getColor(android.R.color.system_neutral1_50),
                )
            }.getOrNull()
        }
    }
}

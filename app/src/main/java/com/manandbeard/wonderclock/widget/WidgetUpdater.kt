package com.manandbeard.wonderclock.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import com.manandbeard.wonderclock.R
import com.manandbeard.wonderclock.data.ClockConfig
import com.manandbeard.wonderclock.data.ConfigStore
import com.manandbeard.wonderclock.render.ClockRenderer
import com.manandbeard.wonderclock.render.RenderEnv
import com.manandbeard.wonderclock.render.TimeText

/** Renders configs into RemoteViews and pushes them to the launcher. */
object WidgetUpdater {

    private val PROVIDERS = listOf(
        DigitalClockWidgetProvider::class.java,
        AnalogClockWidgetProvider::class.java,
        WordClockWidgetProvider::class.java,
    )

    /** Widget ids across all three providers. */
    fun allWidgetIds(context: Context): IntArray {
        val manager = AppWidgetManager.getInstance(context) ?: return IntArray(0)
        val ids = ArrayList<Int>()
        PROVIDERS.forEach { provider ->
            val component = ComponentName(context.applicationContext, provider)
            runCatching { manager.getAppWidgetIds(component) }
                .getOrNull()
                ?.forEach { ids += it }
        }
        return ids.toIntArray()
    }

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = allWidgetIds(context)
        if (ids.isEmpty()) return
        val env = RenderEnv.capture(context)
        ids.forEach { id -> update(context, manager, id, env) }
    }

    fun update(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        env: RenderEnv = RenderEnv.capture(context),
    ) {
        val config = ConfigStore.load(context, widgetId)
        val options = runCatching { manager.getAppWidgetOptions(widgetId) }.getOrNull() ?: Bundle()
        val views = buildViews(context, config, widgetId, options, env)
        runCatching { manager.updateAppWidget(widgetId, views) }
    }

    private fun buildViews(
        context: Context,
        config: ClockConfig,
        widgetId: Int,
        options: Bundle,
        env: RenderEnv,
    ): RemoteViews {
        val sizes = sizesFor(context, options)
        val budget = bitmapBudget(context, sizes.size)
        // Android 12 can hold one layout per size and swap without a round trip
        // to us, which is what makes rotation and resizing look instant.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && sizes.size > 1) {
            RemoteViews(
                sizes.associateWith { size ->
                    viewsForSize(context, config, widgetId, size, env, budget)
                },
            )
        } else {
            viewsForSize(context, config, widgetId, sizes.first(), env, budget)
        }
    }

    /**
     * Pixels each size variant may use.
     *
     * The launcher refuses a RemoteViews whose bitmaps exceed roughly two
     * screens' worth of pixels, and that ceiling scales with the display — so a
     * fixed cap that is comfortable on a 1080p phone blows the budget on a 720p
     * one. Budgeting from the actual display, and splitting it across the
     * variants, keeps the total at half the allowance on any device.
     */
    private fun bitmapBudget(context: Context, variantCount: Int): Long {
        val metrics = context.resources.displayMetrics
        val screenPixels = metrics.widthPixels.toLong() * metrics.heightPixels.toLong()
        if (screenPixels <= 0L) return ClockRenderer.DEFAULT_MAX_PIXELS
        return (screenPixels / variantCount.coerceAtLeast(1)).coerceAtLeast(120_000L)
    }

    private fun viewsForSize(
        context: Context,
        config: ClockConfig,
        widgetId: Int,
        sizeDp: SizeF,
        env: RenderEnv,
        maxPixels: Long,
    ): RemoteViews {
        val density = context.resources.displayMetrics.density
        val widthPx = (sizeDp.width * density).toInt().coerceAtLeast(1)
        val heightPx = (sizeDp.height * density).toInt().coerceAtLeast(1)

        val layout = if (config.hasSecondaryTapZone) {
            R.layout.widget_clock_dual
        } else {
            R.layout.widget_clock
        }
        val views = RemoteViews(context.packageName, layout)
        views.setImageViewBitmap(
            R.id.wc_image,
            ClockRenderer.render(config, widthPx, heightPx, env, maxPixels),
        )
        views.setContentDescription(R.id.wc_image, spokenTime(config, env))

        if (config.hasSecondaryTapZone) {
            TapActions.pendingIntent(
                context, widgetId, config.tapAction, config.tapPackage, secondary = false,
            )?.let { views.setOnClickPendingIntent(R.id.wc_tap_primary, it) }
            TapActions.pendingIntent(
                context,
                widgetId,
                config.tapActionSecondary,
                config.tapPackageSecondary,
                secondary = true,
            )?.let { views.setOnClickPendingIntent(R.id.wc_tap_secondary, it) }
        } else {
            TapActions.pendingIntent(
                context, widgetId, config.tapAction, config.tapPackage, secondary = false,
            )?.let { views.setOnClickPendingIntent(R.id.wc_root, it) }
        }
        return views
    }

    /** What TalkBack reads out, since the widget is only a picture. */
    private fun spokenTime(config: ClockConfig, env: RenderEnv): String {
        val time = TimeText.now(config, env)
        val pattern = if (TimeText.use24Hour(config, env)) "HH:mm" else "h:mm a"
        val clock = TimeText.format(time, pattern, env.locale)
        if (!config.showDate) return clock
        val date = TimeText.dateString(config, env, time)
        return if (date.isBlank()) clock else "$clock, $date"
    }

    /**
     * The sizes the widget can currently be shown at, in dp.
     *
     * Android 12+ reports every size the launcher may use. Older releases only
     * report a min/max box, where the useful pair depends on orientation.
     */
    private fun sizesFor(context: Context, options: Bundle): List<SizeF> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            val reported = options.getParcelableArrayList<SizeF>(
                AppWidgetManager.OPTION_APPWIDGET_SIZES,
            )
            val usable = reported
                ?.filter { it.width > 0f && it.height > 0f }
                ?.take(MAX_SIZE_VARIANTS)
                .orEmpty()
            if (usable.isNotEmpty()) return usable
        }

        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        val portrait =
            context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE

        val width = (if (portrait) minWidth else maxWidth).takeIf { it > 0 } ?: FALLBACK_WIDTH_DP
        val height = (if (portrait) maxHeight else minHeight).takeIf { it > 0 } ?: FALLBACK_HEIGHT_DP
        return listOf(SizeF(width.toFloat(), height.toFloat()))
    }

    /** Portrait and landscape are what actually get used; more just costs memory. */
    private const val MAX_SIZE_VARIANTS = 2
    private const val FALLBACK_WIDTH_DP = 250
    private const val FALLBACK_HEIGHT_DP = 110
}

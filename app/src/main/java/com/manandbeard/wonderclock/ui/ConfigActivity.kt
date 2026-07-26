package com.manandbeard.wonderclock.ui

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.manandbeard.wonderclock.data.ClockConfig
import com.manandbeard.wonderclock.data.ConfigStore
import com.manandbeard.wonderclock.data.Presets
import com.manandbeard.wonderclock.ui.screens.EditorScreen
import com.manandbeard.wonderclock.ui.theme.WonderClockTheme
import com.manandbeard.wonderclock.widget.TickScheduler
import com.manandbeard.wonderclock.widget.WidgetPinner
import com.manandbeard.wonderclock.widget.WidgetUpdater

/**
 * Widget settings.
 *
 * Reached two ways: the launcher opens it when a widget is dropped (which is
 * why the result has to be set carefully — a cancelled first run means the
 * launcher throws the new widget away), and the app opens it to edit one that
 * is already placed.
 */
class ConfigActivity : ComponentActivity() {

    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setResult(RESULT_CANCELED, resultIntent())
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val initial = startingConfig()

        setContent {
            WonderClockTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    EditorScreen(
                        initial = initial,
                        title = "Widget settings",
                        onSave = ::save,
                        onCancel = { finish() },
                    )
                }
            }
        }
    }

    /**
     * An existing widget keeps its config. A brand new one starts from the last
     * config the user saved when that matches the kind of widget they just
     * dropped — so a second digital clock looks like the first — and otherwise
     * from the preset for that kind.
     */
    private fun startingConfig(): ClockConfig {
        ConfigStore.loadOrNull(this, widgetId)?.let { return it }

        val style = WidgetPinner.styleForProvider(this, widgetId) ?: return Presets.default().config
        val lastSaved = ConfigStore.lastSaved(this)
        if (lastSaved != null && lastSaved.style == style) return lastSaved
        return Presets.forStyle(style).config
    }

    private fun save(config: ClockConfig) {
        ConfigStore.save(this, widgetId, config)
        AppWidgetManager.getInstance(this)?.let { manager ->
            WidgetUpdater.update(this, manager, widgetId)
        }
        TickScheduler.sync(this)
        setResult(RESULT_OK, resultIntent())
        finish()
    }

    private fun resultIntent(): Intent =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)

    companion object {
        fun editIntent(context: Context, widgetId: Int): Intent =
            Intent(context, ConfigActivity::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
    }
}

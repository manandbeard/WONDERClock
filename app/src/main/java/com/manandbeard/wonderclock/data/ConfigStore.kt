package com.manandbeard.wonderclock.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A saved configuration the user gave a name to. */
@Serializable
data class SavedPreset(
    val id: String,
    val name: String,
    val config: ClockConfig,
)

/**
 * Widget configs live in SharedPreferences, one JSON blob per widget id.
 *
 * Reads have to work from a BroadcastReceiver on the main thread, so this is
 * deliberately synchronous rather than DataStore-backed. The payloads are a
 * couple of kilobytes at most.
 */
object ConfigStore {

    private const val WIDGET_PREFS = "wonderclock_widgets"
    private const val APP_PREFS = "wonderclock_app"
    private const val KEY_WIDGET_PREFIX = "w_"
    private const val KEY_USER_PRESETS = "user_presets"
    private const val KEY_LAST_CONFIG = "last_config"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val prettyJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private fun widgetPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)

    private fun appPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)

    // ------------------------------------------------------ widget configs --

    fun loadOrNull(context: Context, widgetId: Int): ClockConfig? {
        val raw = widgetPrefs(context).getString(KEY_WIDGET_PREFIX + widgetId, null) ?: return null
        return decode(raw)
    }

    /** Never fails: falls back to the default preset for unconfigured widgets. */
    fun load(context: Context, widgetId: Int): ClockConfig =
        loadOrNull(context, widgetId) ?: Presets.default().config

    /**
     * [rememberAsLast] records this config as the starting point for the next
     * widget the user adds. Placeholder configs written before the user has
     * actually chosen anything pass false.
     */
    fun save(
        context: Context,
        widgetId: Int,
        config: ClockConfig,
        rememberAsLast: Boolean = true,
    ) {
        val encoded = json.encodeToString(config)
        widgetPrefs(context).edit()
            .putString(KEY_WIDGET_PREFIX + widgetId, encoded)
            .apply()
        if (rememberAsLast) {
            appPrefs(context).edit().putString(KEY_LAST_CONFIG, encoded).apply()
        }
    }

    fun delete(context: Context, widgetIds: IntArray) {
        val editor = widgetPrefs(context).edit()
        widgetIds.forEach { editor.remove(KEY_WIDGET_PREFIX + it) }
        editor.apply()
    }

    fun isConfigured(context: Context, widgetId: Int): Boolean =
        widgetPrefs(context).contains(KEY_WIDGET_PREFIX + widgetId)

    /** The last config the user saved — a sensible starting point for the next widget. */
    fun lastSaved(context: Context): ClockConfig? =
        appPrefs(context).getString(KEY_LAST_CONFIG, null)?.let { decode(it) }

    // -------------------------------------------------------- user presets --

    fun userPresets(context: Context): List<SavedPreset> {
        val raw = appPrefs(context).getString(KEY_USER_PRESETS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<SavedPreset>>(raw) }.getOrDefault(emptyList())
    }

    fun saveUserPreset(context: Context, name: String, config: ClockConfig): SavedPreset {
        val preset = SavedPreset(
            id = "user-" + System.currentTimeMillis().toString(36),
            name = name.ifBlank { "My preset" },
            config = config,
        )
        val updated = userPresets(context) + preset
        writeUserPresets(context, updated)
        return preset
    }

    fun deleteUserPreset(context: Context, id: String) {
        writeUserPresets(context, userPresets(context).filterNot { it.id == id })
    }

    private fun writeUserPresets(context: Context, presets: List<SavedPreset>) {
        appPrefs(context).edit()
            .putString(KEY_USER_PRESETS, json.encodeToString(presets))
            .apply()
    }

    // ----------------------------------------------------- import / export --

    fun exportJson(config: ClockConfig): String = prettyJson.encodeToString(config)

    /** Returns null when the text is not a WonderClock config. */
    fun importJson(text: String): ClockConfig? = decode(text)

    private fun decode(raw: String): ClockConfig? =
        runCatching { json.decodeFromString<ClockConfig>(raw) }.getOrNull()
}

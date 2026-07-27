package com.manandbeard.wonderclock.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.manandbeard.wonderclock.data.ClockConfig
import com.manandbeard.wonderclock.data.ClockStyle
import com.manandbeard.wonderclock.data.ConfigStore
import com.manandbeard.wonderclock.data.Preset
import com.manandbeard.wonderclock.data.Presets
import com.manandbeard.wonderclock.render.RenderEnv
import com.manandbeard.wonderclock.ui.components.ClockPreview
import com.manandbeard.wonderclock.ui.components.rememberLiveEnv
import com.manandbeard.wonderclock.widget.TickScheduler
import com.manandbeard.wonderclock.widget.WidgetPinner
import com.manandbeard.wonderclock.widget.WidgetUpdater

private val WALLPAPER = Brush.linearGradient(
    listOf(Color(0xFF2B2251), Color(0xFF123043), Color(0xFF4A2338)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onEditWidget: (Int) -> Unit) {
    val context = LocalContext.current
    val resumeCount = rememberResumeCounter()
    val env = rememberLiveEnv(60_000L)
    val scope = rememberCoroutineScope()

    var applying by remember { mutableStateOf<Preset?>(null) }
    // Bumped when we change a widget ourselves, so the thumbnails refresh
    // without waiting for the next resume.
    var revision by remember { mutableStateOf(0) }

    // Re-read on every resume: widgets can be added, removed or edited while
    // this screen is in the background. Reading them here rather than inside
    // the cards keeps the prefs hit and JSON parse off every recomposition.
    val widgetIds = remember(resumeCount, revision) {
        WidgetUpdater.allWidgetIds(context).toList()
    }
    val configs = remember(resumeCount, revision) {
        widgetIds.associateWith { ConfigStore.load(context, it) }
    }
    val exactAlarms = remember(resumeCount) { TickScheduler.canScheduleExact(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WonderClock") },
                actions = {
                    Text(
                        text = "${widgetIds.size} placed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item("hero") {
                HeroCard(env)
            }

            item("widgets-header") {
                SectionHeading(
                    title = "Your widgets",
                    subtitle = if (widgetIds.isEmpty()) {
                        "Nothing on the home screen yet"
                    } else {
                        "Tap one to change how it looks"
                    },
                )
            }

            if (widgetIds.isEmpty()) {
                item("add") { AddWidgetCard() }
            } else {
                items2("widget", widgetIds) { widgetId ->
                    WidgetCard(
                        config = configs[widgetId] ?: Presets.default().config,
                        env = env,
                        onClick = { onEditWidget(widgetId) },
                    )
                }
                item("add-more") { AddWidgetCard() }
            }

            item("presets-header") {
                SectionHeading(
                    title = "Presets",
                    subtitle = "A finished look you can drop on and then tweak",
                )
            }
            items2("preset", Presets.all) { preset ->
                PresetCard(
                    preset = preset,
                    env = env,
                    onClick = { applying = preset },
                )
            }

            item("settings-header") {
                SectionHeading(title = "Settings", subtitle = "Keeping the clock honest")
            }
            item("exact") {
                ActionCard(
                    title = "Minute-accurate updates",
                    body = if (exactAlarms) {
                        "Granted. The clock flips the moment the minute changes."
                    } else {
                        "Allow exact alarms and the clock will flip exactly on the " +
                            "minute instead of drifting by a minute or so."
                    },
                    actionLabel = if (exactAlarms) null else "Allow",
                    onAction = { openExactAlarmSettings(context) },
                )
            }
            item("battery") {
                ActionCard(
                    title = "Background restrictions",
                    body = "If the clock freezes, exempt WonderClock from battery " +
                        "optimisation so its minute alarm keeps firing.",
                    actionLabel = "Open",
                    onAction = { openBatterySettings(context) },
                )
            }
            item("about") {
                ActionCard(
                    title = "About",
                    body = "WonderClock draws every widget with the same renderer the " +
                        "settings preview uses, so what you design is exactly what lands " +
                        "on your home screen. Widgets redraw once a minute — seconds are " +
                        "deliberately not shown, because a ticking widget would cost far " +
                        "more battery than it is worth.",
                    actionLabel = null,
                    onAction = {},
                )
            }
        }
    }

    applying?.let { preset ->
        ApplyPresetDialog(
            preset = preset,
            widgetIds = widgetIds,
            configs = configs,
            env = env,
            onDismiss = { applying = null },
            onApply = { widgetId ->
                ConfigStore.save(context, widgetId, preset.config)
                applying = null
                revision++
                Toast.makeText(context, "${preset.name} applied", Toast.LENGTH_SHORT).show()
                // Re-rendering every placed widget is real work; keep it off the
                // frame that is currently dismissing the dialog.
                scope.launch(Dispatchers.Default) { WidgetUpdater.updateAll(context) }
            },
        )
    }
}

// ---------------------------------------------------------------- cards --

@Composable
private fun HeroCard(env: State<RenderEnv>) {
    val showcase = remember { Presets.byId("neon")?.config ?: Presets.default().config }
    Card(shape = RoundedCornerShape(26.dp)) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(WALLPAPER),
                contentAlignment = Alignment.Center,
            ) {
                ClockPreview(
                    config = showcase,
                    env = env,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                )
            }
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "A clock widget that looks like you made it",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Six faces, every colour, gradients, outlines, the date, " +
                        "battery, alarms, a second time zone and a progress bar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WidgetCard(config: ClockConfig, env: State<RenderEnv>, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(250f / 110f)
                .background(WALLPAPER),
        ) {
            ClockPreview(config = config, env = env, modifier = Modifier.fillMaxSize())
        }
        Text(
            text = config.label.ifBlank { config.style.label },
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun PresetCard(preset: Preset, env: State<RenderEnv>, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(250f / 130f)
                .background(WALLPAPER),
        ) {
            ClockPreview(config = preset.config, env = env, modifier = Modifier.fillMaxSize())
        }
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(text = preset.name, style = MaterialTheme.typography.labelLarge)
            Text(
                text = preset.blurb,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddWidgetCard() {
    val context = LocalContext.current
    val canPin = remember { WidgetPinner.isSupported(context) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(text = "Add a widget", style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (canPin) {
                    "Pick a starting shape and your launcher will offer to place it."
                } else {
                    "Long-press the home screen, choose Widgets, and look for WonderClock."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (canPin) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ClockStyle.DIGITAL to "Digital",
                        ClockStyle.ANALOG to "Analog",
                        ClockStyle.WORDS to "Words",
                    ).forEach { (style, label) ->
                        PillButton(text = label, selected = false) {
                            if (!WidgetPinner.requestPin(context, style)) {
                                Toast.makeText(
                                    context,
                                    "Your launcher would not take it — add it from the widget list instead",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    body: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null) {
                Spacer(modifier = Modifier.height(12.dp))
                PillButton(text = actionLabel, selected = true, onClick = onAction)
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ApplyPresetDialog(
    preset: Preset,
    widgetIds: List<Int>,
    configs: Map<Int, ClockConfig>,
    env: State<RenderEnv>,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(preset.name) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(250f / 110f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(WALLPAPER),
                ) {
                    ClockPreview(
                        config = preset.config,
                        env = env,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Text(text = preset.blurb, style = MaterialTheme.typography.bodyMedium)
                if (widgetIds.isEmpty()) {
                    Text(
                        text = "Add a WonderClock widget to your home screen first, " +
                            "then come back and apply this.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                    )
                } else {
                    Text(text = "Apply to:", style = MaterialTheme.typography.labelLarge)
                    widgetIds.forEach { widgetId ->
                        val existing = configs[widgetId] ?: Presets.default().config
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onApply(widgetId) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(34.dp)
                                    .aspectRatio(250f / 110f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(WALLPAPER),
                            ) {
                                ClockPreview(
                                    config = existing,
                                    env = env,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Text(
                                text = existing.style.label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            }
        },
    )
}

// -------------------------------------------------------------- helpers --

/** Bumps every time the screen comes back to the foreground. */
@Composable
private fun rememberResumeCounter(): Int {
    val owner = LocalLifecycleOwner.current
    var count by remember { mutableStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) count++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return count
}

/** Lays a list out two-up, since LazyVerticalGrid cannot nest in a LazyColumn. */
private fun <T> LazyListScope.items2(
    prefix: String,
    values: List<T>,
    card: @Composable (T) -> Unit,
) {
    val rows = values.chunked(2)
    rows.forEachIndexed { index, row ->
        item(key = "$prefix-row-$index") {
            // Intrinsic min height so a short card next to a tall one still
            // gives a straight row rather than a ragged one.
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(IntrinsicSize.Min),
            ) {
                row.forEach { value ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) { card(value) }
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        .setData(Uri.parse("package:" + context.packageName))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (!startSafely(context, intent)) {
        startSafely(context, appDetailsIntent(context))
    }
}

private fun openBatterySettings(context: Context) {
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (!startSafely(context, intent)) {
        startSafely(context, appDetailsIntent(context))
    }
}

private fun appDetailsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.parse("package:" + context.packageName))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun startSafely(context: Context, intent: Intent): Boolean =
    runCatching { context.startActivity(intent); true }.getOrDefault(false)

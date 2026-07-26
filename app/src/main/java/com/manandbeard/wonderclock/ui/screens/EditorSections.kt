package com.manandbeard.wonderclock.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.manandbeard.wonderclock.data.AmPmPlacement
import com.manandbeard.wonderclock.data.BackgroundMode
import com.manandbeard.wonderclock.data.BinaryStyle
import com.manandbeard.wonderclock.data.ClockConfig
import com.manandbeard.wonderclock.data.ClockStyle
import com.manandbeard.wonderclock.data.ConfigStore
import com.manandbeard.wonderclock.data.DialStyle
import com.manandbeard.wonderclock.data.FontSpec
import com.manandbeard.wonderclock.data.HAlign
import com.manandbeard.wonderclock.data.HandStyle
import com.manandbeard.wonderclock.data.HourFormat
import com.manandbeard.wonderclock.data.ModuleType
import com.manandbeard.wonderclock.data.PaintMode
import com.manandbeard.wonderclock.data.Presets
import com.manandbeard.wonderclock.data.ProgressStyle
import com.manandbeard.wonderclock.data.ProgressType
import com.manandbeard.wonderclock.data.RowPlacement
import com.manandbeard.wonderclock.data.Separator
import com.manandbeard.wonderclock.data.TapAction
import com.manandbeard.wonderclock.data.TextCase
import com.manandbeard.wonderclock.data.VAlign
import com.manandbeard.wonderclock.render.TimeText
import com.manandbeard.wonderclock.ui.components.AppPickerDialog
import com.manandbeard.wonderclock.ui.components.ChipRow
import com.manandbeard.wonderclock.ui.components.ColorRow
import com.manandbeard.wonderclock.ui.components.SectionCard
import com.manandbeard.wonderclock.ui.components.SettingGroupLabel
import com.manandbeard.wonderclock.ui.components.SliderRow
import com.manandbeard.wonderclock.ui.components.SwitchRow
import com.manandbeard.wonderclock.ui.components.TextPromptDialog
import com.manandbeard.wonderclock.ui.components.TextRow
import com.manandbeard.wonderclock.ui.components.ZonePickerDialog
import com.manandbeard.wonderclock.ui.components.percentLabel
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.roundToInt

private typealias Edit = ((ClockConfig) -> ClockConfig) -> Unit

private val DATE_PATTERNS = listOf(
    "EEE, MMM d",
    "EEEE",
    "MMM d",
    "d MMMM",
    "EEEE, d MMM",
    "yyyy-MM-dd",
    "dd.MM.yyyy",
    "MM/dd",
    "EEE d",
    "MMMM yyyy",
)

private val SELECTABLE_FONTS = FontSpec.entries.filter { it != FontSpec.INHERIT }

/** Every settings group, in the order they appear under the preview. */
fun LazyListScope.editorSections(
    config: ClockConfig,
    onChange: Edit,
    isOpen: (String) -> Boolean,
    onToggle: (String) -> Unit,
) {
    section("style", "Face & layout", config.style.description, isOpen, onToggle) {
        StyleSection(config, onChange)
    }
    section("time", "Time", timeSummary(config), isOpen, onToggle) {
        TimeSection(config, onChange)
    }
    section("type", "Typography", config.font.label, isOpen, onToggle) {
        TypographySection(config, onChange)
    }
    section("color", "Colour & effects", colorSummary(config), isOpen, onToggle) {
        ColorSection(config, onChange)
    }
    section("background", "Background", config.backgroundMode.label, isOpen, onToggle) {
        BackgroundSection(config, onChange)
    }
    section("date", "Date", if (config.showDate) config.datePattern else "Hidden", isOpen, onToggle) {
        DateSection(config, onChange)
    }
    section("extras", "Extras", extrasSummary(config), isOpen, onToggle) {
        ExtrasSection(config, onChange)
    }
    section(
        "progress",
        "Progress bar",
        if (config.showProgress) config.progressType.label else "Off",
        isOpen,
        onToggle,
    ) {
        ProgressSection(config, onChange)
    }
    if (config.style == ClockStyle.ANALOG) {
        section("analog", "Dial & hands", config.handStyle.label, isOpen, onToggle) {
            AnalogSection(config, onChange)
        }
    }
    if (config.style == ClockStyle.WORDS || config.style == ClockStyle.WORD_GRID) {
        section("words", "Words", config.wordCase.label, isOpen, onToggle) {
            WordsSection(config, onChange)
        }
    }
    if (config.style == ClockStyle.BINARY) {
        section("binary", "Binary", config.binaryStyle.label, isOpen, onToggle) {
            BinarySection(config, onChange)
        }
    }
    section("behavior", "When tapped", config.tapAction.label, isOpen, onToggle) {
        BehaviorSection(config, onChange)
    }
    section("advanced", "Backup & presets", "Copy, paste, save", isOpen, onToggle) {
        AdvancedSection(config, onChange)
    }
}

private fun LazyListScope.section(
    key: String,
    title: String,
    subtitle: String,
    isOpen: (String) -> Boolean,
    onToggle: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    item(key = key) {
        SectionCard(
            title = title,
            subtitle = subtitle,
            expanded = isOpen(key),
            onToggle = { onToggle(key) },
            content = content,
        )
    }
}

// ---------------------------------------------------------------- style --

@Composable
private fun StyleSection(config: ClockConfig, onChange: Edit) {
    ChipRow(
        label = "Face",
        options = ClockStyle.entries.toList(),
        selected = config.style,
        labelOf = { it.label },
        onSelect = { style -> onChange { it.copy(style = style) } },
    )
    SettingGroupLabel("Start from a preset")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Presets.all.forEach { preset ->
            PillButton(
                text = preset.name,
                selected = config.presetId == preset.id,
                onClick = { onChange { preset.config } },
            )
        }
    }
    SettingGroupLabel("Placement")
    ChipRow(
        label = "Horizontal",
        options = HAlign.entries.toList(),
        selected = config.hAlign,
        labelOf = { it.label },
        onSelect = { value -> onChange { it.copy(hAlign = value) } },
    )
    ChipRow(
        label = "Vertical",
        options = VAlign.entries.toList(),
        selected = config.vAlign,
        labelOf = { it.label },
        onSelect = { value -> onChange { it.copy(vAlign = value) } },
    )
    SliderRow(
        label = "Edge padding",
        value = config.padding,
        range = 0f..0.3f,
        valueLabel = percentLabel(config.padding),
        onChange = { value -> onChange { it.copy(padding = value) } },
    )
    SliderRow(
        label = "Space between rows",
        value = config.rowGap,
        range = 0f..0.25f,
        valueLabel = percentLabel(config.rowGap),
        onChange = { value -> onChange { it.copy(rowGap = value) } },
    )
}

// ----------------------------------------------------------------- time --

@Composable
private fun TimeSection(config: ClockConfig, onChange: Edit) {
    var pickingZone by remember { mutableStateOf(false) }

    ChipRow(
        label = "Clock",
        options = HourFormat.entries.toList(),
        selected = config.hourFormat,
        labelOf = { it.label },
        onSelect = { value -> onChange { it.copy(hourFormat = value) } },
    )
    SwitchRow(
        label = "Leading zero",
        subtitle = "09:05 instead of 9:05",
        checked = config.leadingZero,
        onChange = { value -> onChange { it.copy(leadingZero = value) } },
    )
    ChipRow(
        label = "Separator",
        options = Separator.entries.toList(),
        selected = config.separator,
        labelOf = { it.label },
        onSelect = { value -> onChange { it.copy(separator = value) } },
    )
    SwitchRow(
        label = "Show AM / PM",
        checked = config.showAmPm,
        onChange = { value -> onChange { it.copy(showAmPm = value) } },
    )
    if (config.showAmPm) {
        ChipRow(
            label = "AM / PM position",
            options = AmPmPlacement.entries.toList(),
            selected = config.amPmPlacement,
            labelOf = { it.label },
            onSelect = { value -> onChange { it.copy(amPmPlacement = value) } },
        )
        SliderRow(
            label = "AM / PM size",
            value = config.amPmScale,
            range = 0.15f..0.8f,
            valueLabel = percentLabel(config.amPmScale),
            onChange = { value -> onChange { it.copy(amPmScale = value) } },
        )
        SwitchRow(
            label = "Uppercase AM / PM",
            checked = config.amPmUpperCase,
            onChange = { value -> onChange { it.copy(amPmUpperCase = value) } },
        )
    }
    ValueRow(
        label = "Time zone",
        value = config.timeZoneId ?: "Device",
        onClick = { pickingZone = true },
    )
    if (pickingZone) {
        ZonePickerDialog(
            current = config.timeZoneId,
            onDismiss = { pickingZone = false },
            onPick = { zone -> onChange { it.copy(timeZoneId = zone) } },
        )
    }
}

// ----------------------------------------------------------- typography --

@Composable
private fun TypographySection(config: ClockConfig, onChange: Edit) {
    ChipRow(
        label = "Font",
        options = SELECTABLE_FONTS,
        selected = config.font,
        labelOf = { it.label },
        onSelect = { value -> onChange { it.copy(font = value) } },
    )
    SwitchRow(
        label = "Bold",
        checked = config.bold,
        onChange = { value -> onChange { it.copy(bold = value) } },
    )
    SwitchRow(
        label = "Italic",
        checked = config.italic,
        onChange = { value -> onChange { it.copy(italic = value) } },
    )
    SliderRow(
        label = "Size",
        value = config.timeScale,
        range = 0.3f..1.4f,
        valueLabel = percentLabel(config.timeScale),
        onChange = { value -> onChange { it.copy(timeScale = value) } },
    )
    SliderRow(
        label = "Letter spacing",
        value = config.letterSpacing,
        range = -0.1f..0.4f,
        valueLabel = String.format(Locale.US, "%.2f", config.letterSpacing),
        onChange = { value -> onChange { it.copy(letterSpacing = value) } },
    )
}

// ---------------------------------------------------------------- color --

@Composable
private fun ColorSection(config: ClockConfig, onChange: Edit) {
    ColorRow("Time", config.timeColor) { value -> onChange { it.copy(timeColor = value) } }
    SwitchRow(
        label = "Separate hour and minute colours",
        checked = config.splitColors,
        onChange = { value -> onChange { it.copy(splitColors = value) } },
    )
    if (config.splitColors) {
        ColorRow("Hours", config.hourColor) { value -> onChange { it.copy(hourColor = value) } }
        ColorRow("Minutes", config.minuteColor) { value ->
            onChange { it.copy(minuteColor = value) }
        }
        ColorRow("Separator", config.separatorColor) { value ->
            onChange { it.copy(separatorColor = value) }
        }
    }

    SettingGroupLabel("Gradient")
    SwitchRow(
        label = "Gradient fill",
        checked = config.gradient,
        onChange = { value -> onChange { it.copy(gradient = value) } },
    )
    if (config.gradient) {
        ColorRow("Gradient end", config.gradientColor) { value ->
            onChange { it.copy(gradientColor = value) }
        }
        SliderRow(
            label = "Gradient angle",
            value = config.gradientAngle,
            range = 0f..360f,
            valueLabel = "${config.gradientAngle.roundToInt()}°",
            onChange = { value -> onChange { it.copy(gradientAngle = value) } },
        )
    }

    SettingGroupLabel("Outline")
    ChipRow(
        label = "Fill style",
        options = PaintMode.entries.toList(),
        selected = config.paintMode,
        labelOf = { it.label },
        onSelect = { value -> onChange { it.copy(paintMode = value) } },
    )
    if (config.paintMode != PaintMode.FILL) {
        SliderRow(
            label = "Outline width",
            value = config.strokeWidth,
            range = 0.005f..0.12f,
            valueLabel = String.format(Locale.US, "%.3f", config.strokeWidth),
            onChange = { value -> onChange { it.copy(strokeWidth = value) } },
        )
        ColorRow("Outline colour", config.strokeColor) { value ->
            onChange { it.copy(strokeColor = value) }
        }
    }

    SettingGroupLabel("Shadow")
    SwitchRow(
        label = "Drop shadow / glow",
        checked = config.shadow,
        onChange = { value -> onChange { it.copy(shadow = value) } },
    )
    if (config.shadow) {
        SliderRow(
            label = "Blur",
            value = config.shadowRadius,
            range = 0.01f..0.3f,
            valueLabel = percentLabel(config.shadowRadius),
            onChange = { value -> onChange { it.copy(shadowRadius = value) } },
        )
        SliderRow(
            label = "Horizontal offset",
            value = config.shadowDx,
            range = -0.15f..0.15f,
            valueLabel = String.format(Locale.US, "%.2f", config.shadowDx),
            onChange = { value -> onChange { it.copy(shadowDx = value) } },
        )
        SliderRow(
            label = "Vertical offset",
            value = config.shadowDy,
            range = -0.15f..0.15f,
            valueLabel = String.format(Locale.US, "%.2f", config.shadowDy),
            onChange = { value -> onChange { it.copy(shadowDy = value) } },
        )
        ColorRow("Shadow colour", config.shadowColor) { value ->
            onChange { it.copy(shadowColor = value) }
        }
    }

    SettingGroupLabel("System")
    SwitchRow(
        label = "Match wallpaper colours",
        subtitle = "Android 12 and later. Keeps your transparency, swaps the hues.",
        checked = config.dynamicColor,
        onChange = { value -> onChange { it.copy(dynamicColor = value) } },
    )
}

// ----------------------------------------------------------- background --

@Composable
private fun BackgroundSection(config: ClockConfig, onChange: Edit) {
    ChipRow(
        label = "Style",
        options = BackgroundMode.entries.toList(),
        selected = config.backgroundMode,
        labelOf = { it.label },
        onSelect = { value -> onChange { it.copy(backgroundMode = value) } },
    )
    if (config.backgroundMode != BackgroundMode.NONE) {
        ColorRow("Background", config.backgroundColor) { value ->
            onChange { it.copy(backgroundColor = value) }
        }
    }
    if (config.backgroundMode == BackgroundMode.GRADIENT) {
        ColorRow("Background end", config.backgroundColor2) { value ->
            onChange { it.copy(backgroundColor2 = value) }
        }
        SliderRow(
            label = "Gradient angle",
            value = config.backgroundAngle,
            range = 0f..360f,
            valueLabel = "${config.backgroundAngle.roundToInt()}°",
            onChange = { value -> onChange { it.copy(backgroundAngle = value) } },
        )
    }
    SliderRow(
        label = "Corner radius",
        value = config.cornerRadius,
        range = 0f..0.5f,
        valueLabel = percentLabel(config.cornerRadius),
        onChange = { value -> onChange { it.copy(cornerRadius = value) } },
    )
    SliderRow(
        label = "Border width",
        value = config.borderWidth,
        range = 0f..0.04f,
        valueLabel = String.format(Locale.US, "%.3f", config.borderWidth),
        onChange = { value -> onChange { it.copy(borderWidth = value) } },
    )
    if (config.borderWidth > 0f) {
        ColorRow("Border colour", config.borderColor) { value ->
            onChange { it.copy(borderColor = value) }
        }
    }
}

// ----------------------------------------------------------------- date --

@Composable
private fun DateSection(config: ClockConfig, onChange: Edit) {
    SwitchRow(
        label = "Show the date",
        checked = config.showDate,
        onChange = { value -> onChange { it.copy(showDate = value) } },
    )
    if (!config.showDate) return

    val now = remember { ZonedDateTime.now() }
    val locale = Locale.getDefault()
    ChipRow(
        label = "Format",
        options = DATE_PATTERNS,
        selected = config.datePattern,
        labelOf = { pattern -> TimeText.format(now, pattern, locale).ifBlank { pattern } },
        onSelect = { value -> onChange { it.copy(datePattern = value) } },
    )
    TextRow(
        label = "Custom format",
        value = config.datePattern,
        placeholder = "EEE, MMM d",
        onChange = { value -> onChange { it.copy(datePattern = value) } },
    )
    ChipRow(
        label = "Position",
        options = RowPlacement.entries.toList(),
        selected = config.datePlacement,
        labelOf = { it.label },
        onSelect = { value -> onChange { it.copy(datePlacement = value) } },
    )
    ColorRow("Date colour", config.dateColor) { value -> onChange { it.copy(dateColor = value) } }
    SliderRow(
        label = "Date size",
        value = config.dateScale,
        range = 0.08f..0.45f,
        valueLabel = percentLabel(config.dateScale),
        onChange = { value -> onChange { it.copy(dateScale = value) } },
    )
    ChipRow(
        label = "Capitalisation",
        options = TextCase.entries.toList(),
        selected = config.dateCase,
        labelOf = { it.label },
        onSelect = { value -> onChange { it.copy(dateCase = value) } },
    )
    SliderRow(
        label = "Letter spacing",
        value = config.dateLetterSpacing,
        range = 0f..0.4f,
        valueLabel = String.format(Locale.US, "%.2f", config.dateLetterSpacing),
        onChange = { value -> onChange { it.copy(dateLetterSpacing = value) } },
    )
    ChipRow(
        label = "Font",
        options = FontSpec.entries.toList(),
        selected = config.dateFont,
        labelOf = { it.label },
        onSelect = { value -> onChange { it.copy(dateFont = value) } },
    )
    SwitchRow(
        label = "Bold date",
        checked = config.dateBold,
        onChange = { value -> onChange { it.copy(dateBold = value) } },
    )
}

// --------------------------------------------------------------- extras --

@Composable
private fun ExtrasSection(config: ClockConfig, onChange: Edit) {
    var pickingZone by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }

    ChipRow(
        label = "Above the time",
        options = ModuleType.entries.toList(),
        selected = config.topModule,
        labelOf = { it.label },
        onSelect = { value -> onChange { it.copy(topModule = value) } },
    )
    ChipRow(
        label = "Below the time",
        options = ModuleType.entries.toList(),
        selected = config.bottomModule,
        labelOf = { it.label },
        onSelect = { value -> onChange { it.copy(bottomModule = value) } },
    )

    val active = setOf(config.topModule, config.bottomModule)
    if (ModuleType.CUSTOM_TEXT in active) {
        TextRow(
            label = "Custom text",
            value = config.customText,
            placeholder = "Anything you like",
            onChange = { value -> onChange { it.copy(customText = value) } },
        )
    }
    if (ModuleType.SECOND_ZONE in active) {
        ValueRow(
            label = "Second time zone",
            value = config.secondZoneId,
            onClick = { pickingZone = true },
        )
        TextRow(
            label = "Second zone label",
            value = config.secondZoneLabel,
            placeholder = "LON",
            onChange = { value -> onChange { it.copy(secondZoneLabel = value) } },
        )
    }
    if (ModuleType.COUNTDOWN in active) {
        ValueRow(
            label = "Counting down to",
            value = if (config.countdownEpochDay > 0) {
                LocalDate.ofEpochDay(config.countdownEpochDay).toString()
            } else {
                "Pick a date"
            },
            onClick = { pickingDate = true },
        )
        TextRow(
            label = "Countdown label",
            value = config.countdownLabel,
            placeholder = "Holiday",
            onChange = { value -> onChange { it.copy(countdownLabel = value) } },
        )
    }

    if (active != setOf(ModuleType.NONE)) {
        SettingGroupLabel("Appearance")
        ColorRow("Extras colour", config.moduleColor) { value ->
            onChange { it.copy(moduleColor = value) }
        }
        SliderRow(
            label = "Extras size",
            value = config.moduleScale,
            range = 0.06f..0.35f,
            valueLabel = percentLabel(config.moduleScale),
            onChange = { value -> onChange { it.copy(moduleScale = value) } },
        )
        ChipRow(
            label = "Capitalisation",
            options = TextCase.entries.toList(),
            selected = config.moduleCase,
            labelOf = { it.label },
            onSelect = { value -> onChange { it.copy(moduleCase = value) } },
        )
        SliderRow(
            label = "Letter spacing",
            value = config.moduleLetterSpacing,
            range = 0f..0.4f,
            valueLabel = String.format(Locale.US, "%.2f", config.moduleLetterSpacing),
            onChange = { value -> onChange { it.copy(moduleLetterSpacing = value) } },
        )
    }

    if (pickingZone) {
        ZonePickerDialog(
            current = config.secondZoneId,
            onDismiss = { pickingZone = false },
            onPick = { zone ->
                onChange { it.copy(secondZoneId = zone ?: java.time.ZoneId.systemDefault().id) }
            },
        )
    }
    if (pickingDate) {
        CountdownDatePicker(
            epochDay = config.countdownEpochDay,
            onDismiss = { pickingDate = false },
            onPick = { day -> onChange { it.copy(countdownEpochDay = day) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountdownDatePicker(epochDay: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = if (epochDay > 0L) epochDay * 86_400_000L else null,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis -> onPick(millis / 86_400_000L) }
                    onDismiss()
                },
            ) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

// ------------------------------------------------------------- progress --

@Composable
private fun ProgressSection(config: ClockConfig, onChange: Edit) {
    SwitchRow(
        label = "Show a progress bar",
        checked = config.showProgress,
        onChange = { value -> onChange { it.copy(showProgress = value) } },
    )
    if (!config.showProgress) return

    ChipRow(
        label = "Tracks",
        options = ProgressType.entries.toList(),
        selected = config.progressType,
        labelOf = { it.label },
        onSelect = { value -> onChange { it.copy(progressType = value) } },
    )
    ChipRow(
        label = "Shape",
        options = ProgressStyle.entries.toList(),
        selected = config.progressStyle,
        labelOf = { it.label },
        onSelect = { value -> onChange { it.copy(progressStyle = value) } },
    )
    ColorRow("Filled", config.progressColor) { value ->
        onChange { it.copy(progressColor = value) }
    }
    ColorRow("Track", config.progressTrackColor) { value ->
        onChange { it.copy(progressTrackColor = value) }
    }
    SliderRow(
        label = "Thickness",
        value = config.progressThickness,
        range = 0.01f..0.2f,
        valueLabel = percentLabel(config.progressThickness),
        onChange = { value -> onChange { it.copy(progressThickness = value) } },
    )
}

// --------------------------------------------------------------- analog --

@Composable
private fun AnalogSection(config: ClockConfig, onChange: Edit) {
    ChipRow(
        label = "Dial",
        options = DialStyle.entries.toList(),
        selected = config.dialStyle,
        labelOf = { it.label },
        onSelect = { value -> onChange { it.copy(dialStyle = value) } },
    )
    ChipRow(
        label = "Hands",
        options = HandStyle.entries.toList(),
        selected = config.handStyle,
        labelOf = { it.label },
        onSelect = { value -> onChange { it.copy(handStyle = value) } },
    )
    SliderRow(
        label = "Hand thickness",
        value = config.handWidth,
        range = 0.4f..2f,
        valueLabel = percentLabel(config.handWidth),
        onChange = { value -> onChange { it.copy(handWidth = value) } },
    )
    ColorRow("Hour hand", config.hourHandColor) { value ->
        onChange { it.copy(hourHandColor = value) }
    }
    ColorRow("Minute hand", config.minuteHandColor) { value ->
        onChange { it.copy(minuteHandColor = value) }
    }
    ColorRow("Centre dot", config.centerDotColor) { value ->
        onChange { it.copy(centerDotColor = value) }
    }
    ColorRow("Dial marks", config.dialColor) { value -> onChange { it.copy(dialColor = value) } }
    ColorRow("Numerals", config.numeralColor) { value ->
        onChange { it.copy(numeralColor = value) }
    }
    ColorRow("Face fill", config.analogFillColor) { value ->
        onChange { it.copy(analogFillColor = value) }
    }

    SettingGroupLabel("Outer ring")
    SwitchRow(
        label = "Draw a ring",
        checked = config.analogRing,
        onChange = { value -> onChange { it.copy(analogRing = value) } },
    )
    if (config.analogRing) {
        SliderRow(
            label = "Ring width",
            value = config.analogRingWidth,
            range = 0.005f..0.12f,
            valueLabel = String.format(Locale.US, "%.3f", config.analogRingWidth),
            onChange = { value -> onChange { it.copy(analogRingWidth = value) } },
        )
        ColorRow("Ring colour", config.analogRingColor) { value ->
            onChange { it.copy(analogRingColor = value) }
        }
    }
}

// ---------------------------------------------------------------- words --

@Composable
private fun WordsSection(config: ClockConfig, onChange: Edit) {
    ChipRow(
        label = "Capitalisation",
        options = TextCase.entries.toList(),
        selected = config.wordCase,
        labelOf = { it.label },
        onSelect = { value -> onChange { it.copy(wordCase = value) } },
    )
    if (config.style == ClockStyle.WORDS) {
        SwitchRow(
            label = "Exact minutes",
            subtitle = "\"ten twenty three\" instead of \"twenty past ten\"",
            checked = config.wordExactMinutes,
            onChange = { value -> onChange { it.copy(wordExactMinutes = value) } },
        )
    }
    if (config.style == ClockStyle.WORD_GRID) {
        ColorRow("Unlit letters", config.wordInactiveColor) { value ->
            onChange { it.copy(wordInactiveColor = value) }
        }
    }
}

// --------------------------------------------------------------- binary --

@Composable
private fun BinarySection(config: ClockConfig, onChange: Edit) {
    ChipRow(
        label = "Cells",
        options = BinaryStyle.entries.toList(),
        selected = config.binaryStyle,
        labelOf = { it.label },
        onSelect = { value -> onChange { it.copy(binaryStyle = value) } },
    )
    ColorRow("Off cells", config.binaryOffColor) { value ->
        onChange { it.copy(binaryOffColor = value) }
    }
    SwitchRow(
        label = "Show decimal digits",
        checked = config.binaryShowLabels,
        onChange = { value -> onChange { it.copy(binaryShowLabels = value) } },
    )
}

// ------------------------------------------------------------- behavior --

@Composable
private fun BehaviorSection(config: ClockConfig, onChange: Edit) {
    var pickingPrimary by remember { mutableStateOf(false) }
    var pickingSecondary by remember { mutableStateOf(false) }

    ChipRow(
        label = "Tap the widget",
        options = TapAction.entries.toList(),
        selected = config.tapAction,
        labelOf = { it.label },
        onSelect = { value -> onChange { it.copy(tapAction = value) } },
    )
    if (config.tapAction == TapAction.APP) {
        ValueRow(
            label = "App to open",
            value = config.tapPackage.ifBlank { "Not chosen" },
            onClick = { pickingPrimary = true },
        )
    }
    SettingGroupLabel("Second tap zone")
    Text(
        text = "Choose a different action and the lower third of the widget — " +
            "where the date and extras sit — becomes its own tap target.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ChipRow(
        label = "Tap the bottom",
        options = TapAction.entries.toList(),
        selected = config.tapActionSecondary,
        labelOf = { it.label },
        onSelect = { value -> onChange { it.copy(tapActionSecondary = value) } },
    )
    if (config.tapActionSecondary == TapAction.APP) {
        ValueRow(
            label = "App to open",
            value = config.tapPackageSecondary.ifBlank { "Not chosen" },
            onClick = { pickingSecondary = true },
        )
    }

    if (pickingPrimary) {
        AppPickerDialog(
            current = config.tapPackage,
            onDismiss = { pickingPrimary = false },
            onPick = { app -> onChange { it.copy(tapPackage = app.packageName) } },
        )
    }
    if (pickingSecondary) {
        AppPickerDialog(
            current = config.tapPackageSecondary,
            onDismiss = { pickingSecondary = false },
            onPick = { app -> onChange { it.copy(tapPackageSecondary = app.packageName) } },
        )
    }
}

// ------------------------------------------------------------- advanced --

@Composable
private fun AdvancedSection(config: ClockConfig, onChange: Edit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var naming by remember { mutableStateOf(false) }
    var pasting by remember { mutableStateOf(false) }

    val savedPresets = remember(naming) { ConfigStore.userPresets(context) }

    ValueRow(label = "Copy this config", value = "to clipboard") {
        clipboard.setText(AnnotatedString(ConfigStore.exportJson(config)))
        Toast.makeText(context, "Config copied", Toast.LENGTH_SHORT).show()
    }
    ValueRow(label = "Paste a config", value = "from clipboard or text") { pasting = true }
    ValueRow(label = "Save as my preset", value = "reuse it later") { naming = true }

    if (savedPresets.isNotEmpty()) {
        SettingGroupLabel("My presets")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            savedPresets.forEach { preset ->
                PillButton(
                    text = preset.name,
                    selected = false,
                    onClick = { onChange { preset.config } },
                )
            }
        }
    }

    SettingGroupLabel("Reset")
    ValueRow(label = "Back to preset defaults", value = "discard tweaks") {
        val preset = Presets.byId(config.presetId) ?: Presets.forStyle(config.style)
        onChange { preset.config }
    }

    if (naming) {
        TextPromptDialog(
            title = "Save preset",
            label = "Name",
            initial = config.label.ifBlank { "My clock" },
            onDismiss = { naming = false },
            onConfirm = { name ->
                ConfigStore.saveUserPreset(context, name, config)
                naming = false
                Toast.makeText(context, "Preset saved", Toast.LENGTH_SHORT).show()
            },
        )
    }
    if (pasting) {
        val fromClipboard = clipboard.getText()?.text.orEmpty()
        TextPromptDialog(
            title = "Paste a config",
            label = "WonderClock JSON",
            initial = fromClipboard,
            confirmLabel = "Apply",
            singleLine = false,
            onDismiss = { pasting = false },
            onConfirm = { text ->
                val imported = ConfigStore.importJson(text)
                pasting = false
                if (imported == null) {
                    Toast.makeText(context, "That is not a WonderClock config", Toast.LENGTH_SHORT)
                        .show()
                } else {
                    onChange { imported }
                }
            },
        )
    }
}

// -------------------------------------------------------------- helpers --

@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun timeSummary(config: ClockConfig): String {
    val clock = when (config.hourFormat) {
        HourFormat.AUTO -> "System clock"
        HourFormat.H12 -> "12-hour"
        HourFormat.H24 -> "24-hour"
    }
    val zone = config.timeZoneId?.substringAfterLast('/')
    return if (zone == null) clock else "$clock · $zone"
}

private fun colorSummary(config: ClockConfig): String = buildList {
    if (config.gradient) add("gradient")
    if (config.paintMode != PaintMode.FILL) add("outline")
    if (config.shadow) add("shadow")
    if (config.dynamicColor) add("wallpaper colours")
}.joinToString(", ").ifBlank { "Solid" }

private fun extrasSummary(config: ClockConfig): String = buildList {
    if (config.topModule != ModuleType.NONE) add(config.topModule.label)
    if (config.bottomModule != ModuleType.NONE) add(config.bottomModule.label)
}.joinToString(" · ").ifBlank { "None" }

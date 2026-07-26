package com.manandbeard.wonderclock.data

import kotlinx.serialization.Serializable

/**
 * Everything about one widget instance.
 *
 * Every field has a default, and unknown fields are ignored when reading, so a
 * config written by an older or newer build still loads. Colors are packed
 * ARGB ints. Sizes that scale with the widget are stored as fractions rather
 * than dp so a config looks the same on a 2x2 and a 5x5.
 */
@Serializable
data class ClockConfig(
    val schema: Int = CURRENT_SCHEMA,
    val label: String = "",
    val presetId: String = "",

    // ------------------------------------------------------------- layout --
    val style: ClockStyle = ClockStyle.DIGITAL,
    val hAlign: HAlign = HAlign.CENTER,
    val vAlign: VAlign = VAlign.CENTER,
    /** Inset from the widget edge, as a fraction of min(width, height). */
    val padding: Float = 0.08f,
    /** Vertical gap between rows, as a fraction of content height. */
    val rowGap: Float = 0.06f,

    // --------------------------------------------------------------- time --
    val hourFormat: HourFormat = HourFormat.AUTO,
    val leadingZero: Boolean = false,
    val separator: Separator = Separator.COLON,
    val showAmPm: Boolean = true,
    val amPmPlacement: AmPmPlacement = AmPmPlacement.SUPERSCRIPT,
    val amPmScale: Float = 0.34f,
    val amPmUpperCase: Boolean = true,
    /** Null means "follow the device". */
    val timeZoneId: String? = null,

    // --------------------------------------------------------- typography --
    val font: FontSpec = FontSpec.SANS_THIN,
    val bold: Boolean = false,
    val italic: Boolean = false,
    /** Multiplies the auto-fitted size. 1.0 fills the available box. */
    val timeScale: Float = 1f,
    /** Extra tracking, in em. */
    val letterSpacing: Float = 0f,

    // -------------------------------------------------------- time colors --
    val timeColor: Int = WHITE,
    val splitColors: Boolean = false,
    val hourColor: Int = WHITE,
    val minuteColor: Int = 0xB3FFFFFF.toInt(),
    val separatorColor: Int = 0x80FFFFFF.toInt(),
    val gradient: Boolean = false,
    val gradientColor: Int = 0xFF7DD3FC.toInt(),
    val gradientAngle: Float = 90f,
    val paintMode: PaintMode = PaintMode.FILL,
    /** Outline width as a fraction of the text size. */
    val strokeWidth: Float = 0.03f,
    val strokeColor: Int = WHITE,
    val shadow: Boolean = false,
    /** Blur radius as a fraction of the text size. */
    val shadowRadius: Float = 0.07f,
    val shadowDx: Float = 0f,
    val shadowDy: Float = 0.03f,
    val shadowColor: Int = 0x99000000.toInt(),
    /** Pull the palette from the system wallpaper colors (Android 12+). */
    val dynamicColor: Boolean = false,

    // --------------------------------------------------------- background --
    val backgroundMode: BackgroundMode = BackgroundMode.NONE,
    val backgroundColor: Int = 0x66000000,
    val backgroundColor2: Int = 0x66202040,
    val backgroundAngle: Float = 90f,
    /** Corner radius as a fraction of min(width, height). */
    val cornerRadius: Float = 0.16f,
    /** Border width as a fraction of min(width, height). */
    val borderWidth: Float = 0f,
    val borderColor: Int = 0x33FFFFFF,

    // --------------------------------------------------------------- date --
    val showDate: Boolean = true,
    val datePattern: String = "EEE, MMM d",
    val datePlacement: RowPlacement = RowPlacement.BELOW,
    val dateColor: Int = 0xCCFFFFFF.toInt(),
    /** Row height as a fraction of the content height. */
    val dateScale: Float = 0.22f,
    val dateCase: TextCase = TextCase.UPPER,
    val dateLetterSpacing: Float = 0.12f,
    val dateBold: Boolean = false,
    val dateFont: FontSpec = FontSpec.INHERIT,

    // ------------------------------------------------------------ modules --
    val topModule: ModuleType = ModuleType.NONE,
    val bottomModule: ModuleType = ModuleType.NONE,
    val moduleColor: Int = 0x99FFFFFF.toInt(),
    val moduleScale: Float = 0.17f,
    val moduleCase: TextCase = TextCase.UPPER,
    val moduleLetterSpacing: Float = 0.1f,
    val customText: String = "",
    val secondZoneId: String = "UTC",
    val secondZoneLabel: String = "UTC",
    /** Epoch day of the countdown target; 0 means unset. */
    val countdownEpochDay: Long = 0L,
    val countdownLabel: String = "",

    // ----------------------------------------------------------- progress --
    val showProgress: Boolean = false,
    val progressType: ProgressType = ProgressType.DAY,
    val progressStyle: ProgressStyle = ProgressStyle.BAR,
    val progressColor: Int = 0xFF7DD3FC.toInt(),
    val progressTrackColor: Int = 0x33FFFFFF,
    /** Bar thickness as a fraction of the content height. */
    val progressThickness: Float = 0.05f,

    // ------------------------------------------------------------- analog --
    val dialStyle: DialStyle = DialStyle.TICKS,
    val handStyle: HandStyle = HandStyle.MODERN,
    val dialColor: Int = 0x99FFFFFF.toInt(),
    val numeralColor: Int = 0xCCFFFFFF.toInt(),
    val hourHandColor: Int = WHITE,
    val minuteHandColor: Int = WHITE,
    val centerDotColor: Int = 0xFF7DD3FC.toInt(),
    /** Multiplies the default hand thickness. */
    val handWidth: Float = 1f,
    val analogRing: Boolean = false,
    val analogRingWidth: Float = 0.035f,
    val analogRingColor: Int = 0x33FFFFFF,
    val analogFillColor: Int = 0x00000000,

    // -------------------------------------------------------------- words --
    val wordCase: TextCase = TextCase.UPPER,
    val wordInactiveColor: Int = 0x26FFFFFF,
    val wordExactMinutes: Boolean = false,

    // ------------------------------------------------------------- binary --
    val binaryStyle: BinaryStyle = BinaryStyle.DOTS,
    val binaryOffColor: Int = 0x26FFFFFF,
    val binaryShowLabels: Boolean = false,

    // ----------------------------------------------------------- behavior --
    val tapAction: TapAction = TapAction.CLOCK,
    val tapPackage: String = "",
    val tapActionSecondary: TapAction = TapAction.NONE,
    val tapPackageSecondary: String = "",
) {
    val isAnalog: Boolean get() = style == ClockStyle.ANALOG
    val usesTypography: Boolean get() = style != ClockStyle.ANALOG && style != ClockStyle.BINARY

    /** True when a tap on the lower band should do something different. */
    val hasSecondaryTapZone: Boolean
        get() = tapActionSecondary != TapAction.NONE && tapActionSecondary != tapAction

    companion object {
        const val CURRENT_SCHEMA = 1
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}

// --------------------------------------------------------------------------
// Enums. Each carries its own display label so the UI never needs a lookup
// table that can drift out of sync with the values.
// --------------------------------------------------------------------------

enum class ClockStyle(val label: String, val description: String) {
    DIGITAL("Digital", "One line: 10:24"),
    STACKED("Stacked", "Hours over minutes"),
    ANALOG("Analog", "Dial and hands"),
    WORDS("Words", "Half past ten"),
    WORD_GRID("Letter grid", "A lit-up matrix of letters"),
    BINARY("Binary", "Binary-coded decimal dots"),
}

enum class HourFormat(val label: String) {
    AUTO("Follow system"),
    H12("12-hour"),
    H24("24-hour"),
}

enum class Separator(val label: String, val glyph: String) {
    COLON("Colon", ":"),
    DOT("Dot", "."),
    SPACE("Space", " "),
    THIN_SPACE("Thin space", "\u2009"),
    NONE("None", ""),
}

enum class AmPmPlacement(val label: String) {
    INLINE("Inline"),
    SUPERSCRIPT("Raised"),
    SUBSCRIPT("Lowered"),
    NEW_LINE("Own line"),
}

enum class FontSpec(val label: String, val family: String?) {
    INHERIT("Same as time", null),
    SANS("Sans", "sans-serif"),
    SANS_LIGHT("Sans light", "sans-serif-light"),
    SANS_THIN("Sans thin", "sans-serif-thin"),
    SANS_MEDIUM("Sans medium", "sans-serif-medium"),
    SANS_BLACK("Sans black", "sans-serif-black"),
    CONDENSED("Condensed", "sans-serif-condensed"),
    CONDENSED_LIGHT("Condensed light", "sans-serif-condensed-light"),
    SMALLCAPS("Small caps", "sans-serif-smallcaps"),
    SERIF("Serif", "serif"),
    SERIF_MONO("Serif mono", "serif-monospace"),
    MONOSPACE("Monospace", "monospace"),
    CASUAL("Casual", "casual"),
    CURSIVE("Cursive", "cursive"),
}

enum class PaintMode(val label: String) {
    FILL("Solid"),
    STROKE("Outline"),
    FILL_AND_STROKE("Solid + outline"),
}

enum class BackgroundMode(val label: String) {
    NONE("None"),
    SOLID("Solid"),
    GRADIENT("Gradient"),
}

enum class HAlign(val label: String) { START("Left"), CENTER("Center"), END("Right") }

enum class VAlign(val label: String) { TOP("Top"), CENTER("Middle"), BOTTOM("Bottom") }

enum class RowPlacement(val label: String) { ABOVE("Above time"), BELOW("Below time") }

enum class TextCase(val label: String) {
    NORMAL("As written"),
    UPPER("UPPERCASE"),
    LOWER("lowercase"),
}

enum class ModuleType(val label: String) {
    NONE("None"),
    DATE("Date"),
    WEEKDAY("Weekday"),
    BATTERY("Battery"),
    NEXT_ALARM("Next alarm"),
    WEEK_NUMBER("Week number"),
    SECOND_ZONE("Second time zone"),
    ZONE_LABEL("Time zone label"),
    DAY_PROGRESS("Day progress"),
    YEAR_PROGRESS("Year progress"),
    COUNTDOWN("Countdown"),
    CUSTOM_TEXT("Custom text"),
}

enum class ProgressType(val label: String) {
    HOUR("Hour"),
    DAY("Day"),
    WEEK("Week"),
    MONTH("Month"),
    YEAR("Year"),
    BATTERY("Battery"),
}

enum class ProgressStyle(val label: String) {
    BAR("Bar"),
    DOTS("Dots"),
    SEGMENTS("Segments"),
}

enum class DialStyle(val label: String) {
    NONE("Bare"),
    TICKS("Hour ticks"),
    FINE_TICKS("All ticks"),
    DOTS("Dots"),
    NUMBERS("Numbers"),
    ROMAN("Roman numerals"),
    QUARTERS("Quarters only"),
}

enum class HandStyle(val label: String) {
    CLASSIC("Classic"),
    MODERN("Modern"),
    NEEDLE("Needle"),
    BAR("Bar"),
    ARROW("Arrow"),
}

enum class BinaryStyle(val label: String) {
    DOTS("Dots"),
    SQUARES("Squares"),
    RINGS("Rings"),
}

enum class TapAction(val label: String) {
    NONE("Do nothing"),
    CLOCK("Open clock"),
    ALARMS("Open alarms"),
    TIMER("Open timers"),
    CALENDAR("Open calendar"),
    WIDGET_SETTINGS("Edit this widget"),
    APP("Open an app"),
}

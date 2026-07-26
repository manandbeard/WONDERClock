package com.manandbeard.wonderclock.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.manandbeard.wonderclock.data.AmPmPlacement
import com.manandbeard.wonderclock.data.BinaryStyle
import com.manandbeard.wonderclock.data.ClockConfig
import com.manandbeard.wonderclock.data.VAlign
import java.time.ZonedDateTime
import kotlin.math.min

private const val MAX_WORD_LINES = 4

private fun timePaint(cfg: ClockConfig): Paint = newTextPaint().apply {
    typeface = typefaceFor(cfg.font, cfg.bold, cfg.italic)
    letterSpacing = cfg.letterSpacing
}

/** The text size that makes [segs] fill [box], honoring the user's size dial. */
internal fun fitSegRun(paint: Paint, cfg: ClockConfig, segs: List<Seg>, box: RectF): Float {
    val reference = layoutSegs(paint, segs, REF_SIZE)
    if (reference.isEmpty || box.width() <= 0f || box.height() <= 0f) return 0f
    val fit = min(box.width() / reference.width, box.height() / reference.height)
    return REF_SIZE * fit * cfg.timeScale.coerceIn(0.2f, 2f)
}

/** Lays out and draws one line of coloured runs inside [box]. */
internal fun drawSegRun(
    canvas: Canvas,
    cfg: ClockConfig,
    palette: Palette,
    paint: Paint,
    segs: List<Seg>,
    box: RectF,
    sizeOverride: Float? = null,
) {
    val base = sizeOverride ?: fitSegRun(paint, cfg, segs, box)
    if (base <= 0f) return
    val layout = layoutSegs(paint, segs, base)
    if (layout.isEmpty) return

    val originX = xForAlign(layout.width, box, cfg.hAlign)
    val baseline = when (cfg.vAlign) {
        VAlign.TOP -> box.top - layout.top
        VAlign.CENTER -> box.top + (box.height() - layout.height) / 2f - layout.top
        VAlign.BOTTOM -> box.bottom - layout.bottom
    }
    canvas.drawSegs(segs, layout, paint, cfg, palette, base, originX, baseline)
}

// ------------------------------------------------------------- digital --

internal fun drawDigitalFace(
    canvas: Canvas,
    cfg: ClockConfig,
    palette: Palette,
    env: RenderEnv,
    time: ZonedDateTime,
    box: RectF,
) {
    val paint = timePaint(cfg)
    val segs = ArrayList<Seg>(4)
    segs += Seg(TimeText.hourString(cfg, env, time), 1f, palette.hour)
    val separator = cfg.separator.glyph
    if (separator.isNotEmpty()) segs += Seg(separator, 1f, palette.separator)
    segs += Seg(TimeText.minuteString(env, time), 1f, palette.minute)

    val amPm = TimeText.amPmString(cfg, env, time)
    var ownLine: String? = null
    if (amPm != null) {
        if (cfg.amPmPlacement == AmPmPlacement.NEW_LINE) {
            ownLine = amPm
        } else {
            segs += Seg(
                text = " $amPm",
                scale = cfg.amPmScale.coerceIn(0.1f, 1f),
                color = palette.minute,
                align = when (cfg.amPmPlacement) {
                    AmPmPlacement.SUPERSCRIPT -> SegAlign.RAISED
                    AmPmPlacement.SUBSCRIPT -> SegAlign.LOWERED
                    else -> SegAlign.BASELINE
                },
            )
        }
    }

    if (ownLine == null) {
        drawSegRun(canvas, cfg, palette, paint, segs, box)
        return
    }

    val split = box.height() * 0.76f
    val timeBox = RectF(box.left, box.top, box.right, box.top + split)
    val amPmBox = RectF(box.left, box.top + split, box.right, box.bottom)
    drawSegRun(canvas, cfg, palette, paint, segs, timeBox)
    drawSegRun(
        canvas,
        cfg,
        palette,
        paint,
        listOf(Seg(ownLine, 1f, palette.minute)),
        amPmBox,
    )
}

// ------------------------------------------------------------- stacked --

internal fun drawStackedFace(
    canvas: Canvas,
    cfg: ClockConfig,
    palette: Palette,
    env: RenderEnv,
    time: ZonedDateTime,
    box: RectF,
) {
    val paint = timePaint(cfg)
    val gap = box.height() * 0.03f
    val half = ((box.height() - gap) / 2f).coerceAtLeast(1f)
    val topBox = RectF(box.left, box.top, box.right, box.top + half)
    val bottomBox = RectF(box.left, box.bottom - half, box.right, box.bottom)

    val hourSegs = listOf(Seg(TimeText.hourString(cfg, env, time), 1f, palette.hour))
    val minuteSegs = ArrayList<Seg>(2)
    minuteSegs += Seg(TimeText.minuteString(env, time), 1f, palette.minute)
    TimeText.amPmString(cfg, env, time)?.let { amPm ->
        minuteSegs += Seg(
            text = " $amPm",
            scale = cfg.amPmScale.coerceIn(0.1f, 1f),
            color = palette.minute,
            align = SegAlign.RAISED,
        )
    }

    // One shared size so the two lines read as a single block.
    val base = min(
        fitSegRun(paint, cfg, hourSegs, topBox),
        fitSegRun(paint, cfg, minuteSegs, bottomBox),
    )
    if (base <= 0f) return

    drawSegRun(canvas, cfg, palette, paint, hourSegs, topBox, sizeOverride = base)
    drawSegRun(canvas, cfg, palette, paint, minuteSegs, bottomBox, sizeOverride = base)
}

// --------------------------------------------------------------- words --

internal fun drawWordsFace(
    canvas: Canvas,
    cfg: ClockConfig,
    palette: Palette,
    env: RenderEnv,
    time: ZonedDateTime,
    box: RectF,
) {
    val phrase = WordClock
        .phrase(time.hour, time.minute, cfg.wordExactMinutes)
        .applyCase(cfg.wordCase, env.locale)
    val words = phrase.split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return

    val paint = timePaint(cfg)
    val plan = planWrap(paint, words, box) ?: return

    val lineGap = 0.16f * plan.textSize
    val lineHeight = plan.lineHeight * plan.textSize / REF_SIZE
    val blockHeight = plan.lines.size * lineHeight + (plan.lines.size - 1) * lineGap
    var top = when (cfg.vAlign) {
        VAlign.TOP -> box.top
        VAlign.CENTER -> box.top + (box.height() - blockHeight) / 2f
        VAlign.BOTTOM -> box.bottom - blockHeight
    }

    for (line in plan.lines) {
        val lineBox = RectF(box.left, top, box.right, top + lineHeight)
        drawSegRun(
            canvas,
            cfg,
            palette,
            paint,
            listOf(Seg(line, 1f, palette.time)),
            lineBox,
            sizeOverride = plan.textSize,
        )
        top += lineHeight + lineGap
    }
}

private class WrapPlan(val lines: List<String>, val textSize: Float, val lineHeight: Float)

/**
 * Picks the line breaks that let the phrase be drawn as large as possible.
 *
 * Phrases are at most a handful of words, so this brute-forces every
 * contiguous split for one to [MAX_WORD_LINES] lines and keeps the best.
 */
private fun planWrap(paint: Paint, words: List<String>, box: RectF): WrapPlan? {
    if (box.width() <= 1f || box.height() <= 1f) return null

    paint.textSize = REF_SIZE
    val widths = FloatArray(words.size) { paint.ink(words[it]).width }
    val spaceWidth = paint.measureText(" ")
    val metrics = paint.fontMetrics
    val lineHeight = (metrics.descent - metrics.ascent).coerceAtLeast(1f)

    var best: WrapPlan? = null
    var bestSize = 0f

    for (lineCount in 1..min(MAX_WORD_LINES, words.size)) {
        val split = balancedSplit(widths, spaceWidth, lineCount) ?: continue
        val widest = split.maxOf { range -> lineWidth(widths, spaceWidth, range) }
        if (widest <= 0f) continue
        val totalHeight = lineCount * lineHeight + (lineCount - 1) * 0.16f * REF_SIZE
        val scale = min(box.width() / widest, box.height() / totalHeight)
        val size = REF_SIZE * scale
        if (size > bestSize) {
            bestSize = size
            best = WrapPlan(
                lines = split.map { range ->
                    words.subList(range.first, range.last + 1).joinToString(" ")
                },
                textSize = size,
                lineHeight = lineHeight,
            )
        }
    }
    return best
}

private fun lineWidth(widths: FloatArray, spaceWidth: Float, range: IntRange): Float {
    var total = 0f
    for (index in range) total += widths[index]
    return total + spaceWidth * (range.last - range.first)
}

/** Contiguous split of the words into [lineCount] lines with the narrowest widest line. */
private fun balancedSplit(
    widths: FloatArray,
    spaceWidth: Float,
    lineCount: Int,
): List<IntRange>? {
    val count = widths.size
    if (lineCount > count || lineCount < 1) return null
    if (lineCount == 1) return listOf(0..count - 1)

    var best: List<IntRange>? = null
    var bestWidest = Float.MAX_VALUE

    // Choose lineCount-1 cut positions among the count-1 gaps.
    val cuts = IntArray(lineCount - 1)

    fun recurse(depth: Int, start: Int) {
        if (depth == lineCount - 1) {
            val ranges = ArrayList<IntRange>(lineCount)
            var from = 0
            for (cut in cuts) {
                ranges += from..cut - 1
                from = cut
            }
            ranges += from..count - 1
            val widest = ranges.maxOf { lineWidth(widths, spaceWidth, it) }
            if (widest < bestWidest) {
                bestWidest = widest
                best = ranges
            }
            return
        }
        for (cut in start..count - (lineCount - depth)) {
            cuts[depth] = cut + 1
            recurse(depth + 1, cut + 1)
        }
    }

    recurse(0, 0)
    return best
}

// ----------------------------------------------------------- word grid --

internal fun drawWordGridFace(
    canvas: Canvas,
    cfg: ClockConfig,
    palette: Palette,
    env: RenderEnv,
    time: ZonedDateTime,
    box: RectF,
) {
    val cell = min(box.width() / WordClock.COLS, box.height() / WordClock.ROWS)
    if (cell <= 1f) return

    val gridWidth = cell * WordClock.COLS
    val gridHeight = cell * WordClock.ROWS
    val left = xForAlign(gridWidth, box, cfg.hAlign)
    val top = when (cfg.vAlign) {
        VAlign.TOP -> box.top
        VAlign.CENTER -> box.top + (box.height() - gridHeight) / 2f
        VAlign.BOTTOM -> box.bottom - gridHeight
    }

    val paint = timePaint(cfg)
    paint.letterSpacing = 0f
    paint.textSize = cell * 0.72f
    val metrics = paint.fontMetrics
    val baselineOffset = (cell - (metrics.descent - metrics.ascent)) / 2f - metrics.ascent

    val lit = WordClock.litCells(time.hour, time.minute)
    val litShader = if (cfg.gradient) {
        linearShader(
            RectF(left, top, left + gridWidth, top + gridHeight),
            palette.time,
            palette.gradient,
            cfg.gradientAngle,
        )
    } else {
        null
    }

    for (row in 0 until WordClock.ROWS) {
        val letters = WordClock.GRID[row]
        for (column in 0 until WordClock.COLS) {
            val index = row * WordClock.COLS + column
            val isLit = index in lit
            val glyph = letters[column].toString().applyCase(cfg.wordCase, env.locale)
            paint.shader = if (isLit) litShader else null
            paint.color = if (isLit) palette.time else palette.wordInactive
            val x = left + column * cell + (cell - paint.measureText(glyph)) / 2f
            canvas.drawText(glyph, x, top + row * cell + baselineOffset, paint)
        }
    }
    paint.shader = null
}

// -------------------------------------------------------------- binary --

/** Bits actually needed per BCD column: hour tens, hour ones, minute tens, minute ones. */
private val BINARY_BITS = intArrayOf(2, 4, 3, 4)

internal fun drawBinaryFace(
    canvas: Canvas,
    cfg: ClockConfig,
    palette: Palette,
    env: RenderEnv,
    time: ZonedDateTime,
    box: RectF,
) {
    val hour = if (TimeText.use24Hour(cfg, env)) {
        time.hour
    } else {
        val twelve = time.hour % 12
        if (twelve == 0) 12 else twelve
    }
    val digits = intArrayOf(hour / 10, hour % 10, time.minute / 10, time.minute % 10)

    val labelHeight = if (cfg.binaryShowLabels) box.height() * 0.16f else 0f
    val gridHeight = box.height() - labelHeight
    val cell = min(box.width() / 4f, gridHeight / 4f)
    if (cell <= 1f) return

    val gridWidth = cell * 4f
    val left = xForAlign(gridWidth, box, cfg.hAlign)
    val top = box.top + (gridHeight - cell * 4f) / 2f

    val paint = newFillPaint()
    val radius = cell * 0.32f
    val squareSide = cell * 0.64f

    for (column in 0 until 4) {
        val value = digits[column]
        val bits = BINARY_BITS[column]
        for (row in 0 until 4) {
            // Row 0 is the 8s bit, row 3 the 1s bit.
            val weight = 1 shl (3 - row)
            if (weight > (1 shl bits) - 1) continue
            val on = (value and weight) != 0
            paint.color = if (on) palette.time else palette.binaryOff
            val cx = left + cell * (column + 0.5f)
            val cy = top + cell * (row + 0.5f)
            when (cfg.binaryStyle) {
                BinaryStyle.DOTS -> {
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(cx, cy, radius, paint)
                }
                BinaryStyle.SQUARES -> {
                    paint.style = Paint.Style.FILL
                    canvas.drawRoundRect(
                        RectF(
                            cx - squareSide / 2f,
                            cy - squareSide / 2f,
                            cx + squareSide / 2f,
                            cy + squareSide / 2f,
                        ),
                        cell * 0.14f,
                        cell * 0.14f,
                        paint,
                    )
                }
                BinaryStyle.RINGS -> {
                    if (on) {
                        paint.style = Paint.Style.FILL
                        canvas.drawCircle(cx, cy, radius, paint)
                    } else {
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = cell * 0.07f
                        canvas.drawCircle(cx, cy, radius, paint)
                    }
                }
            }
        }
    }
    paint.style = Paint.Style.FILL

    if (!cfg.binaryShowLabels || labelHeight <= 2f) return
    val labelPaint = timePaint(cfg)
    labelPaint.letterSpacing = 0f
    labelPaint.color = palette.module
    labelPaint.textSize = labelHeight * 0.82f
    val metrics = labelPaint.fontMetrics
    val baseline = box.bottom - metrics.descent
    for (column in 0 until 4) {
        val label = digits[column].toString()
        val cx = left + cell * (column + 0.5f)
        canvas.drawText(label, cx - labelPaint.measureText(label) / 2f, baseline, labelPaint)
    }
}

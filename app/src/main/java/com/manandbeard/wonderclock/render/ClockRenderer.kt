package com.manandbeard.wonderclock.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.manandbeard.wonderclock.data.BackgroundMode
import com.manandbeard.wonderclock.data.ClockConfig
import com.manandbeard.wonderclock.data.ClockStyle
import com.manandbeard.wonderclock.data.FontSpec
import com.manandbeard.wonderclock.data.ModuleType
import com.manandbeard.wonderclock.data.ProgressStyle
import com.manandbeard.wonderclock.data.RowPlacement
import com.manandbeard.wonderclock.data.TextCase
import java.time.ZonedDateTime
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Draws a clock into a bitmap.
 *
 * This is the single source of truth for what a WonderClock looks like: the
 * home screen widget ships the bitmap through RemoteViews and the settings
 * screen draws the same bitmap into a Compose `Image`, so the preview cannot
 * drift from the real thing.
 *
 * Rendering is pure — everything time- or device-dependent arrives in
 * [RenderEnv] — which also makes previews able to show sample data.
 */
object ClockRenderer {

    /** Roughly 6 MB as ARGB_8888, comfortably inside the RemoteViews budget. */
    private const val MAX_PIXELS = 1_600_000
    private const val MAX_DIMENSION = 1_600

    fun render(cfg: ClockConfig, widthPx: Int, heightPx: Int, env: RenderEnv): Bitmap {
        val (width, height) = constrain(widthPx, heightPx)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val palette = Palette(cfg, env)
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())

        drawBackground(canvas, cfg, palette, bounds)

        val inset = cfg.padding.coerceIn(0f, 0.4f) * min(width, height)
        val content = RectF(
            bounds.left + inset,
            bounds.top + inset,
            bounds.right - inset,
            bounds.bottom - inset,
        )
        if (content.width() > 2f && content.height() > 2f) {
            drawRows(canvas, cfg, palette, env, content)
        }
        return bitmap
    }

    /**
     * Keeps the bitmap inside the launcher's per-widget memory budget while
     * preserving the aspect ratio. Oversized widgets scale down rather than
     * being cropped or refused.
     */
    private fun constrain(widthPx: Int, heightPx: Int): Pair<Int, Int> {
        var width = widthPx.coerceAtLeast(1)
        var height = heightPx.coerceAtLeast(1)

        val longest = max(width, height)
        if (longest > MAX_DIMENSION) {
            val factor = MAX_DIMENSION.toFloat() / longest
            width = (width * factor).toInt().coerceAtLeast(1)
            height = (height * factor).toInt().coerceAtLeast(1)
        }

        val pixels = width.toLong() * height.toLong()
        if (pixels > MAX_PIXELS) {
            val factor = sqrt(MAX_PIXELS.toDouble() / pixels).toFloat()
            width = (width * factor).toInt().coerceAtLeast(1)
            height = (height * factor).toInt().coerceAtLeast(1)
        }
        return width to height
    }

    // -------------------------------------------------------- background --

    private fun drawBackground(
        canvas: Canvas,
        cfg: ClockConfig,
        palette: Palette,
        bounds: RectF,
    ) {
        val shortSide = min(bounds.width(), bounds.height())
        val radius = cfg.cornerRadius.coerceIn(0f, 0.5f) * shortSide
        val paint = newFillPaint()

        when (cfg.backgroundMode) {
            BackgroundMode.NONE -> Unit
            BackgroundMode.SOLID -> if (palette.background.isVisible) {
                paint.color = palette.background
                canvas.drawRoundRect(bounds, radius, radius, paint)
            }
            BackgroundMode.GRADIENT -> {
                paint.shader = linearShader(
                    bounds,
                    palette.background,
                    palette.background2,
                    cfg.backgroundAngle,
                )
                canvas.drawRoundRect(bounds, radius, radius, paint)
                paint.shader = null
            }
        }

        val borderWidth = cfg.borderWidth.coerceIn(0f, 0.2f) * shortSide
        if (borderWidth > 0.2f && palette.border.isVisible) {
            val half = borderWidth / 2f
            val inner = RectF(
                bounds.left + half,
                bounds.top + half,
                bounds.right - half,
                bounds.bottom - half,
            )
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = borderWidth
            paint.color = palette.border
            canvas.drawRoundRect(inner, (radius - half).coerceAtLeast(0f), (radius - half).coerceAtLeast(0f), paint)
        }
    }

    // -------------------------------------------------------------- rows --

    /**
     * Stacks the optional rows (modules, date, progress) around the clock face,
     * which absorbs whatever height is left over.
     */
    private fun drawRows(
        canvas: Canvas,
        cfg: ClockConfig,
        palette: Palette,
        env: RenderEnv,
        content: RectF,
    ) {
        val time = TimeText.now(cfg, env)
        val fractions = ArrayList<Float>(6)
        val painters = ArrayList<(RectF) -> Unit>(6)

        fun row(fraction: Float, painter: (RectF) -> Unit) {
            fractions += fraction
            painters += painter
        }

        val moduleStyle = RowStyle(
            color = palette.module,
            font = if (cfg.dateFont == FontSpec.INHERIT) cfg.font else cfg.dateFont,
            bold = cfg.dateBold,
            case = cfg.moduleCase,
            letterSpacing = cfg.moduleLetterSpacing,
        )
        val dateStyle = RowStyle(
            color = palette.date,
            font = if (cfg.dateFont == FontSpec.INHERIT) cfg.font else cfg.dateFont,
            bold = cfg.dateBold,
            case = cfg.dateCase,
            letterSpacing = cfg.dateLetterSpacing,
        )
        val moduleHeight = cfg.moduleScale.coerceIn(0.05f, 0.4f)
        val dateHeight = cfg.dateScale.coerceIn(0.05f, 0.5f)

        TimeText.moduleText(cfg.topModule, cfg, env, time)?.let { text ->
            row(moduleHeight) { box -> drawTextRow(canvas, cfg, env, box, text, moduleStyle) }
        }
        if (cfg.showDate && cfg.datePlacement == RowPlacement.ABOVE) {
            val text = TimeText.dateString(cfg, env, time)
            if (text.isNotBlank()) {
                row(dateHeight) { box -> drawTextRow(canvas, cfg, env, box, text, dateStyle) }
            }
        }

        val faceIndex = fractions.size
        row(FACE_ROW) { box -> drawFace(canvas, cfg, palette, env, time, box) }

        if (cfg.showDate && cfg.datePlacement == RowPlacement.BELOW) {
            val text = TimeText.dateString(cfg, env, time)
            if (text.isNotBlank()) {
                row(dateHeight) { box -> drawTextRow(canvas, cfg, env, box, text, dateStyle) }
            }
        }
        if (cfg.bottomModule != ModuleType.NONE) {
            TimeText.moduleText(cfg.bottomModule, cfg, env, time)?.let { text ->
                row(moduleHeight) { box -> drawTextRow(canvas, cfg, env, box, text, moduleStyle) }
            }
        }
        if (cfg.showProgress) {
            val fraction = TimeText.progressFraction(cfg.progressType, env, time)
            row(cfg.progressThickness.coerceIn(0.01f, 0.3f)) { box ->
                drawProgress(canvas, cfg, palette, box, fraction)
            }
        }

        layoutAndDraw(content, cfg, fractions, painters, faceIndex)
    }

    /** Sentinel: the clock face takes whatever height the other rows leave. */
    private const val FACE_ROW = -1f

    private fun layoutAndDraw(
        content: RectF,
        cfg: ClockConfig,
        fractions: List<Float>,
        painters: List<(RectF) -> Unit>,
        faceIndex: Int,
    ) {
        val rowCount = fractions.size
        val gapFraction = if (rowCount > 1) cfg.rowGap.coerceIn(0f, 0.3f) * (rowCount - 1) else 0f
        val extrasFraction = fractions.sumOf { if (it > 0f) it.toDouble() else 0.0 }.toFloat()

        // Never let the extras crowd the face out completely.
        val budget = 0.78f
        val squeeze = if (extrasFraction + gapFraction > budget) {
            budget / (extrasFraction + gapFraction)
        } else {
            1f
        }

        val gap = cfg.rowGap.coerceIn(0f, 0.3f) * squeeze * content.height()
        val heights = FloatArray(rowCount)
        var used = 0f
        for (index in 0 until rowCount) {
            if (index != faceIndex) {
                heights[index] = fractions[index] * squeeze * content.height()
                used += heights[index]
            }
        }
        heights[faceIndex] =
            (content.height() - used - gap * (rowCount - 1)).coerceAtLeast(content.height() * 0.1f)

        var top = content.top
        for (index in 0 until rowCount) {
            val box = RectF(content.left, top, content.right, top + heights[index])
            if (box.height() > 0.5f) painters[index](box)
            top += heights[index] + gap
        }
    }

    private fun drawFace(
        canvas: Canvas,
        cfg: ClockConfig,
        palette: Palette,
        env: RenderEnv,
        time: ZonedDateTime,
        box: RectF,
    ) {
        when (cfg.style) {
            ClockStyle.DIGITAL -> drawDigitalFace(canvas, cfg, palette, env, time, box)
            ClockStyle.STACKED -> drawStackedFace(canvas, cfg, palette, env, time, box)
            ClockStyle.ANALOG -> drawAnalogFace(canvas, cfg, palette, time, box)
            ClockStyle.WORDS -> drawWordsFace(canvas, cfg, palette, env, time, box)
            ClockStyle.WORD_GRID -> drawWordGridFace(canvas, cfg, palette, env, time, box)
            ClockStyle.BINARY -> drawBinaryFace(canvas, cfg, palette, env, time, box)
        }
    }

    // ---------------------------------------------------------- progress --

    private fun drawProgress(
        canvas: Canvas,
        cfg: ClockConfig,
        palette: Palette,
        box: RectF,
        fraction: Float,
    ) {
        if (box.height() <= 0.5f || box.width() <= 1f) return
        val paint = newFillPaint()
        val value = fraction.coerceIn(0f, 1f)

        when (cfg.progressStyle) {
            ProgressStyle.BAR -> {
                val radius = box.height() / 2f
                paint.color = palette.progressTrack
                canvas.drawRoundRect(box, radius, radius, paint)
                if (value > 0f) {
                    val filled = RectF(
                        box.left,
                        box.top,
                        box.left + box.width() * value,
                        box.bottom,
                    )
                    // A sliver narrower than the corner radius renders as a dot,
                    // which reads better than a clipped rectangle.
                    paint.color = palette.progress
                    canvas.drawRoundRect(
                        filled,
                        min(radius, filled.width() / 2f),
                        radius,
                        paint,
                    )
                }
            }

            ProgressStyle.DOTS -> {
                val count = 12
                val radius = min(box.height() / 2f, box.width() / (count * 2.6f))
                val step = box.width() / count
                val lit = Math.round(value * count)
                for (index in 0 until count) {
                    paint.color = if (index < lit) palette.progress else palette.progressTrack
                    canvas.drawCircle(
                        box.left + step * (index + 0.5f),
                        box.centerY(),
                        radius,
                        paint,
                    )
                }
            }

            ProgressStyle.SEGMENTS -> {
                val count = 20
                val step = box.width() / count
                val width = step * 0.62f
                val lit = Math.round(value * count)
                val radius = min(box.height() / 2f, width / 2f)
                for (index in 0 until count) {
                    paint.color = if (index < lit) palette.progress else palette.progressTrack
                    val left = box.left + step * index + (step - width) / 2f
                    canvas.drawRoundRect(
                        RectF(left, box.top, left + width, box.bottom),
                        radius,
                        radius,
                        paint,
                    )
                }
            }
        }
    }

    // ----------------------------------------------------------- helpers --

    private class RowStyle(
        val color: Int,
        val font: FontSpec,
        val bold: Boolean,
        val case: TextCase,
        val letterSpacing: Float,
    )

    private fun drawTextRow(
        canvas: Canvas,
        cfg: ClockConfig,
        env: RenderEnv,
        box: RectF,
        rawText: String,
        style: RowStyle,
    ) {
        val text = rawText.applyCase(style.case, env.locale)
        if (text.isBlank() || box.height() <= 1f || box.width() <= 1f) return

        val paint = newTextPaint()
        paint.typeface = typefaceFor(style.font, style.bold, cfg.italic, fallback = cfg.font)
        paint.letterSpacing = style.letterSpacing
        paint.textSize = REF_SIZE

        val reference = paint.ink(text)
        if (reference.isEmpty) return
        paint.textSize = REF_SIZE * min(
            box.width() / reference.width,
            box.height() / reference.height,
        )

        val ink = paint.ink(text)
        paint.color = style.color
        canvas.drawText(
            text,
            xForAlign(ink.width, box, cfg.hAlign),
            box.top + (box.height() - ink.height) / 2f - ink.top,
            paint,
        )
    }
}

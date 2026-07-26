package com.manandbeard.wonderclock.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.manandbeard.wonderclock.data.ClockConfig
import com.manandbeard.wonderclock.data.FontSpec
import com.manandbeard.wonderclock.data.HAlign
import com.manandbeard.wonderclock.data.PaintMode
import com.manandbeard.wonderclock.data.TextCase
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Small drawing primitives shared by every clock face.
 *
 * The sizing model throughout: measure once at [REF_SIZE], scale the result.
 * Everything the renderer does with text scales linearly with `textSize`
 * (letter spacing is in em, stroke widths are fractions of the size), so one
 * measurement is enough to land an exact fit — no binary search.
 */
internal const val REF_SIZE = 100f

/** Ink extents of a run of text: the box the glyphs actually cover. */
internal data class Ink(val width: Float, val top: Float, val bottom: Float) {
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val isEmpty: Boolean get() = width <= 0f || height <= 0f
}

private val scratchRect = Rect()

internal fun Paint.ink(text: String): Ink {
    if (text.isEmpty()) return Ink(0f, 0f, 0f)
    synchronized(scratchRect) {
        getTextBounds(text, 0, text.length, scratchRect)
        // Android appends letter spacing after the final glyph too, which would
        // push centred text off to the left; drop it. Tight bounds still catch
        // italic overhang, so take whichever is wider.
        val advance = (measureText(text) - trailingSpacing()).coerceAtLeast(0f)
        val width = max(advance, scratchRect.width().toFloat())
        return Ink(width, scratchRect.top.toFloat(), scratchRect.bottom.toFloat())
    }
}

/** The phantom gap Android adds after the last glyph when tracking is set. */
internal fun Paint.trailingSpacing(): Float = letterSpacing * textSize

internal fun typefaceFor(
    font: FontSpec,
    bold: Boolean,
    italic: Boolean,
    fallback: FontSpec = FontSpec.SANS,
): Typeface {
    val family = font.family ?: fallback.family ?: "sans-serif"
    val style = when {
        bold && italic -> Typeface.BOLD_ITALIC
        bold -> Typeface.BOLD
        italic -> Typeface.ITALIC
        else -> Typeface.NORMAL
    }
    return Typeface.create(family, style)
}

internal fun newTextPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    isSubpixelText = true
    isDither = true
}

internal fun newFillPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    isDither = true
}

internal fun xForAlign(contentWidth: Float, box: RectF, align: HAlign): Float = when (align) {
    HAlign.START -> box.left
    HAlign.CENTER -> box.left + (box.width() - contentWidth) / 2f
    HAlign.END -> box.right - contentWidth
}

/** A gradient that always spans [box] along [angleDeg] (90 = top to bottom). */
internal fun linearShader(box: RectF, from: Int, to: Int, angleDeg: Float): LinearGradient {
    val radians = Math.toRadians(angleDeg.toDouble())
    val dx = cos(radians).toFloat()
    val dy = sin(radians).toFloat()
    val half = 0.5f * (abs(box.width() * dx) + abs(box.height() * dy))
    val cx = box.centerX()
    val cy = box.centerY()
    return LinearGradient(
        cx - dx * half,
        cy - dy * half,
        cx + dx * half,
        cy + dy * half,
        from,
        to,
        Shader.TileMode.CLAMP,
    )
}

/** Keeps [base]'s alpha but takes the RGB of [rgbFrom]. Used for dynamic color. */
internal fun recolor(base: Int, rgbFrom: Int): Int =
    (rgbFrom and 0x00FFFFFF) or (base and 0xFF000000.toInt())

internal fun Int.scaleAlpha(factor: Float): Int {
    val alpha = (Color.alpha(this) * factor).toInt().coerceIn(0, 255)
    return (this and 0x00FFFFFF) or (alpha shl 24)
}

internal fun Int.withAlpha(alpha: Int): Int =
    (this and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

internal val Int.isVisible: Boolean get() = Color.alpha(this) > 0

internal fun String.applyCase(case: TextCase, locale: java.util.Locale): String = when (case) {
    TextCase.NORMAL -> this
    TextCase.UPPER -> uppercase(locale)
    TextCase.LOWER -> lowercase(locale)
}

// ---------------------------------------------------------------- segments --

/** How a segment sits relative to the main run's baseline. */
internal enum class SegAlign { BASELINE, RAISED, LOWERED }

/** One coloured run inside a single line of time, e.g. the minutes or "PM". */
internal class Seg(
    val text: String,
    val scale: Float = 1f,
    val color: Int = Color.WHITE,
    val align: SegAlign = SegAlign.BASELINE,
)

internal class SegLayout(
    val width: Float,
    val top: Float,
    val bottom: Float,
    val xs: FloatArray,
    val shifts: FloatArray,
) {
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val isEmpty: Boolean get() = width <= 0f || height <= 0f
}

/**
 * Places [segs] left to right at the given base text size, returning per-segment
 * offsets plus the union ink box. Called twice per draw: once at [REF_SIZE] to
 * work out the fit, once at the final size to draw.
 */
internal fun layoutSegs(paint: Paint, segs: List<Seg>, base: Float): SegLayout {
    val xs = FloatArray(segs.size)
    val shifts = FloatArray(segs.size)
    var x = 0f
    var top = Float.MAX_VALUE
    var bottom = -Float.MAX_VALUE
    var trailing = 0f

    // Reference ink for the full-size run: raised segments align to its top.
    paint.textSize = base
    val anchor = paint.ink(segs.firstOrNull { it.scale >= 1f && it.text.isNotBlank() }?.text ?: "0")

    segs.forEachIndexed { index, seg ->
        paint.textSize = base * seg.scale
        val segInk = paint.ink(seg.text)
        val shift = when (seg.align) {
            SegAlign.BASELINE -> 0f
            SegAlign.RAISED -> anchor.top - segInk.top
            SegAlign.LOWERED -> 0.16f * base
        }
        xs[index] = x
        shifts[index] = shift
        x += paint.measureText(seg.text)
        if (seg.text.isNotBlank()) {
            top = min(top, shift + segInk.top)
            bottom = max(bottom, shift + segInk.bottom)
            trailing = paint.trailingSpacing()
        }
    }

    if (top > bottom) {
        top = 0f
        bottom = 0f
    }
    return SegLayout((x - trailing).coerceAtLeast(0f), top, bottom, xs, shifts)
}

/**
 * Draws a laid-out run, applying the config's fill/gradient/outline/shadow.
 *
 * [originX] is the left edge of the ink box and [baseline] the baseline of the
 * unshifted segments.
 */
internal fun Canvas.drawSegs(
    segs: List<Seg>,
    layout: SegLayout,
    paint: Paint,
    cfg: ClockConfig,
    palette: Palette,
    base: Float,
    originX: Float,
    baseline: Float,
) {
    if (segs.isEmpty() || base <= 0f) return

    val inkBox = RectF(
        originX,
        baseline + layout.top,
        originX + layout.width,
        baseline + layout.bottom,
    )

    val wantsFill = cfg.paintMode != PaintMode.STROKE
    val wantsStroke = cfg.paintMode != PaintMode.FILL

    if (cfg.shadow && cfg.shadowRadius > 0f) {
        paint.setShadowLayer(
            (cfg.shadowRadius * base).coerceAtLeast(0.1f),
            cfg.shadowDx * base,
            cfg.shadowDy * base,
            palette.shadow,
        )
    } else {
        paint.clearShadowLayer()
    }

    if (wantsFill) {
        paint.style = Paint.Style.FILL
        val shader = if (cfg.gradient) {
            linearShader(inkBox, palette.time, palette.gradient, cfg.gradientAngle)
        } else {
            null
        }
        paint.shader = shader
        segs.forEachIndexed { index, seg ->
            if (seg.text.isEmpty()) return@forEachIndexed
            paint.textSize = base * seg.scale
            paint.color = seg.color
            drawText(seg.text, originX + layout.xs[index], baseline + layout.shifts[index], paint)
        }
        paint.shader = null
    }

    if (wantsStroke) {
        // Over a fill the outline should stay crisp, so the shadow is dropped.
        // On its own it keeps the shadow, which is what makes an outlined face
        // able to glow.
        if (wantsFill) paint.clearShadowLayer()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (cfg.strokeWidth * base).coerceAtLeast(0.5f)
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = palette.stroke
        paint.shader = null
        segs.forEachIndexed { index, seg ->
            if (seg.text.isEmpty()) return@forEachIndexed
            paint.textSize = base * seg.scale
            drawText(seg.text, originX + layout.xs[index], baseline + layout.shifts[index], paint)
        }
    }

    paint.style = Paint.Style.FILL
    paint.clearShadowLayer()
    paint.shader = null
}

package com.manandbeard.wonderclock.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.manandbeard.wonderclock.data.ClockConfig
import com.manandbeard.wonderclock.data.DialStyle
import com.manandbeard.wonderclock.data.HandStyle
import com.manandbeard.wonderclock.data.VAlign
import java.time.ZonedDateTime
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val ROMAN = arrayOf(
    "XII", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI",
)

/** Geometry for a pair of hands, all lengths as a fraction of the radius. */
private class HandSpec(
    val hourLength: Float,
    val hourWidth: Float,
    val minuteLength: Float,
    val minuteWidth: Float,
    val tail: Float,
    val cap: Paint.Cap,
    val arrow: Boolean = false,
)

private fun handSpec(style: HandStyle): HandSpec = when (style) {
    HandStyle.CLASSIC -> HandSpec(0.52f, 0.055f, 0.80f, 0.038f, 0.16f, Paint.Cap.ROUND)
    HandStyle.MODERN -> HandSpec(0.50f, 0.078f, 0.78f, 0.050f, 0.00f, Paint.Cap.ROUND)
    HandStyle.NEEDLE -> HandSpec(0.56f, 0.020f, 0.87f, 0.014f, 0.22f, Paint.Cap.BUTT)
    HandStyle.BAR -> HandSpec(0.50f, 0.088f, 0.78f, 0.056f, 0.12f, Paint.Cap.BUTT)
    HandStyle.ARROW -> HandSpec(0.48f, 0.040f, 0.76f, 0.028f, 0.14f, Paint.Cap.BUTT, arrow = true)
}

internal fun drawAnalogFace(
    canvas: Canvas,
    cfg: ClockConfig,
    palette: Palette,
    time: ZonedDateTime,
    box: RectF,
) {
    val diameter = min(box.width(), box.height())
    if (diameter <= 6f) return

    val centerX = xForAlign(diameter, box, cfg.hAlign) + diameter / 2f
    val centerY = when (cfg.vAlign) {
        VAlign.TOP -> box.top + diameter / 2f
        VAlign.CENTER -> box.centerY()
        VAlign.BOTTOM -> box.bottom - diameter / 2f
    }
    val radius = diameter / 2f
    val paint = newFillPaint()

    if (palette.analogFill.isVisible) {
        paint.color = palette.analogFill
        canvas.drawCircle(centerX, centerY, radius, paint)
    }

    var markRadius = radius
    if (cfg.analogRing && palette.ring.isVisible) {
        val ringWidth = cfg.analogRingWidth.coerceIn(0.002f, 0.2f) * diameter
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = ringWidth
        paint.color = palette.ring
        canvas.drawCircle(centerX, centerY, radius - ringWidth / 2f, paint)
        paint.style = Paint.Style.FILL
        markRadius = radius - ringWidth * 1.35f
    }

    drawDial(canvas, cfg, palette, paint, centerX, centerY, markRadius)
    drawHands(canvas, cfg, palette, paint, centerX, centerY, radius, time)
}

// ---------------------------------------------------------------- dial --

private fun drawDial(
    canvas: Canvas,
    cfg: ClockConfig,
    palette: Palette,
    paint: Paint,
    centerX: Float,
    centerY: Float,
    radius: Float,
) {
    when (cfg.dialStyle) {
        DialStyle.NONE -> Unit

        DialStyle.TICKS -> repeat(12) { index ->
            tick(canvas, paint, centerX, centerY, index * 30f, radius, 0.11f, 0.022f, palette.dial)
        }

        DialStyle.FINE_TICKS -> {
            repeat(60) { index ->
                if (index % 5 != 0) {
                    tick(
                        canvas, paint, centerX, centerY, index * 6f, radius,
                        0.055f, 0.009f, palette.dial.scaleAlpha(0.6f),
                    )
                }
            }
            repeat(12) { index ->
                tick(
                    canvas, paint, centerX, centerY, index * 30f, radius,
                    0.13f, 0.026f, palette.dial,
                )
            }
        }

        DialStyle.QUARTERS -> repeat(4) { index ->
            tick(
                canvas, paint, centerX, centerY, index * 90f, radius,
                0.15f, 0.028f, palette.dial,
            )
        }

        DialStyle.DOTS -> {
            paint.style = Paint.Style.FILL
            paint.color = palette.dial
            repeat(12) { index ->
                val radians = Math.toRadians((index * 30f - 90f).toDouble())
                canvas.drawCircle(
                    centerX + (cos(radians) * radius * 0.87f).toFloat(),
                    centerY + (sin(radians) * radius * 0.87f).toFloat(),
                    radius * 0.032f,
                    paint,
                )
            }
        }

        DialStyle.NUMBERS -> drawNumerals(canvas, cfg, palette, centerX, centerY, radius, false)

        DialStyle.ROMAN -> drawNumerals(canvas, cfg, palette, centerX, centerY, radius, true)
    }
}

private fun tick(
    canvas: Canvas,
    paint: Paint,
    centerX: Float,
    centerY: Float,
    degreesFromTwelve: Float,
    radius: Float,
    length: Float,
    width: Float,
    color: Int,
) {
    if (!color.isVisible) return
    val radians = Math.toRadians((degreesFromTwelve - 90f).toDouble())
    val cosine = cos(radians).toFloat()
    val sine = sin(radians).toFloat()
    val outer = radius * 0.98f
    val inner = outer - radius * length

    paint.style = Paint.Style.STROKE
    paint.strokeCap = Paint.Cap.BUTT
    paint.strokeWidth = radius * width
    paint.color = color
    canvas.drawLine(
        centerX + cosine * inner,
        centerY + sine * inner,
        centerX + cosine * outer,
        centerY + sine * outer,
        paint,
    )
    paint.style = Paint.Style.FILL
}

private fun drawNumerals(
    canvas: Canvas,
    cfg: ClockConfig,
    palette: Palette,
    centerX: Float,
    centerY: Float,
    radius: Float,
    roman: Boolean,
) {
    val paint = newTextPaint()
    paint.typeface = typefaceFor(cfg.font, cfg.bold, cfg.italic)
    paint.color = palette.numeral
    paint.textSize = radius * (if (roman) 0.21f else 0.26f)

    val ringRadius = radius * (if (roman) 0.78f else 0.79f)
    for (hour in 1..12) {
        val label = if (roman) ROMAN[hour % 12] else hour.toString()
        val radians = Math.toRadians((hour * 30f - 90f).toDouble())
        val x = centerX + (cos(radians) * ringRadius).toFloat()
        val y = centerY + (sin(radians) * ringRadius).toFloat()
        val ink = paint.ink(label)
        canvas.drawText(
            label,
            x - paint.measureText(label) / 2f,
            y + ink.height / 2f - ink.bottom,
            paint,
        )
    }
}

// --------------------------------------------------------------- hands --

private fun drawHands(
    canvas: Canvas,
    cfg: ClockConfig,
    palette: Palette,
    paint: Paint,
    centerX: Float,
    centerY: Float,
    radius: Float,
    time: ZonedDateTime,
) {
    val spec = handSpec(cfg.handStyle)
    val thickness = cfg.handWidth.coerceIn(0.2f, 3f)

    // The widget only redraws once a minute, so the minute hand deliberately
    // snaps to the minute instead of creeping with the seconds.
    val minuteAngle = time.minute * 6f
    val hourAngle = (time.hour % 12) * 30f + time.minute * 0.5f

    if (spec.arrow) {
        drawArrowHand(
            canvas, paint, centerX, centerY, radius, hourAngle,
            spec.hourLength, spec.hourWidth * thickness, spec.tail, palette.hourHand,
        )
        drawArrowHand(
            canvas, paint, centerX, centerY, radius, minuteAngle,
            spec.minuteLength, spec.minuteWidth * thickness, spec.tail, palette.minuteHand,
        )
    } else {
        drawLineHand(
            canvas, paint, centerX, centerY, radius, hourAngle,
            spec.hourLength, spec.hourWidth * thickness, spec.tail, spec.cap, palette.hourHand,
        )
        drawLineHand(
            canvas, paint, centerX, centerY, radius, minuteAngle,
            spec.minuteLength, spec.minuteWidth * thickness, spec.tail, spec.cap,
            palette.minuteHand,
        )
    }

    if (palette.centerDot.isVisible) {
        paint.style = Paint.Style.FILL
        paint.color = palette.centerDot
        canvas.drawCircle(centerX, centerY, radius * 0.05f * thickness, paint)
    }
}

private fun drawLineHand(
    canvas: Canvas,
    paint: Paint,
    centerX: Float,
    centerY: Float,
    radius: Float,
    degreesFromTwelve: Float,
    length: Float,
    width: Float,
    tail: Float,
    cap: Paint.Cap,
    color: Int,
) {
    if (!color.isVisible) return
    val radians = Math.toRadians((degreesFromTwelve - 90f).toDouble())
    val cosine = cos(radians).toFloat()
    val sine = sin(radians).toFloat()

    paint.style = Paint.Style.STROKE
    paint.strokeCap = cap
    paint.strokeWidth = (radius * width).coerceAtLeast(1f)
    paint.color = color
    canvas.drawLine(
        centerX - cosine * radius * tail,
        centerY - sine * radius * tail,
        centerX + cosine * radius * length,
        centerY + sine * radius * length,
        paint,
    )
    paint.style = Paint.Style.FILL
}

private fun drawArrowHand(
    canvas: Canvas,
    paint: Paint,
    centerX: Float,
    centerY: Float,
    radius: Float,
    degreesFromTwelve: Float,
    length: Float,
    width: Float,
    tail: Float,
    color: Int,
) {
    if (!color.isVisible) return
    val radians = Math.toRadians((degreesFromTwelve - 90f).toDouble())
    val cosine = cos(radians).toFloat()
    val sine = sin(radians).toFloat()
    // Unit vector at right angles to the hand, for the shaft and head width.
    val perpX = -sine
    val perpY = cosine

    val shaftHalf = radius * width / 2f
    val headHalf = radius * width * 2.2f
    val tipX = centerX + cosine * radius * length
    val tipY = centerY + sine * radius * length
    val neck = radius * (length - width * 5f)
    val neckX = centerX + cosine * neck
    val neckY = centerY + sine * neck
    val backX = centerX - cosine * radius * tail
    val backY = centerY - sine * radius * tail

    val path = Path()
    path.moveTo(backX + perpX * shaftHalf, backY + perpY * shaftHalf)
    path.lineTo(neckX + perpX * shaftHalf, neckY + perpY * shaftHalf)
    path.lineTo(neckX + perpX * headHalf, neckY + perpY * headHalf)
    path.lineTo(tipX, tipY)
    path.lineTo(neckX - perpX * headHalf, neckY - perpY * headHalf)
    path.lineTo(neckX - perpX * shaftHalf, neckY - perpY * shaftHalf)
    path.lineTo(backX - perpX * shaftHalf, backY - perpY * shaftHalf)
    path.close()

    paint.style = Paint.Style.FILL
    paint.color = color
    canvas.drawPath(path, paint)
}

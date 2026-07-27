package com.manandbeard.wonderclock.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.min

/** Grey checkerboard so translucent colours read as translucent. */
fun Modifier.checkerboard(
    cell: Dp,
    light: Color = Color(0xFFE4E4E7),
    dark: Color = Color(0xFFA1A1AA),
): Modifier = drawBehind {
    val step = cell.toPx()
    if (step <= 0.5f) return@drawBehind
    drawRect(light)
    val columns = ceil(size.width / step).toInt()
    val rows = ceil(size.height / step).toInt()
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            if ((row + column) % 2 == 0) continue
            val left = column * step
            val top = row * step
            drawRect(
                color = dark,
                topLeft = Offset(left, top),
                size = Size(
                    min(step, size.width - left).coerceAtLeast(0f),
                    min(step, size.height - top).coerceAtLeast(0f),
                ),
            )
        }
    }
}

private val SWATCHES = listOf(
    0xFFFFFFFF, 0xFFE4E4E7, 0xFF9CA3AF, 0xFF3F3F46, 0xFF111111, 0x00000000,
    0xFF7DD3FC, 0xFF38BDF8, 0xFF2563EB, 0xFF6366F1, 0xFFA855F7, 0xFFF472B6,
    0xFFEF4444, 0xFFF97316, 0xFFF6C744, 0xFF84CC16, 0xFF22C55E, 0xFF14B8A6,
    0x66000000, 0x99000000, 0xCC000000, 0x33FFFFFF, 0x66FFFFFF, 0x99FFFFFF,
).map { it.toInt() }

/**
 * Hue / saturation-value / alpha picker with a hex field and a swatch strip.
 *
 * Alpha matters more here than in most pickers — a widget sits on a wallpaper,
 * so nearly every colour in a good config is partly transparent.
 */
@Composable
fun ColorPickerDialog(
    title: String,
    initial: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val startHsv = remember(initial) {
        FloatArray(3).also { AndroidColor.colorToHSV(initial, it) }
    }
    var hue by remember(initial) { mutableStateOf(startHsv[0]) }
    var saturation by remember(initial) { mutableStateOf(startHsv[1]) }
    var brightness by remember(initial) { mutableStateOf(startHsv[2]) }
    var alpha by remember(initial) { mutableStateOf(AndroidColor.alpha(initial) / 255f) }
    var hexDraft by remember(initial) { mutableStateOf(hexOf(initial).removePrefix("#")) }

    val current = AndroidColor.HSVToColor(
        (alpha * 255f).toInt().coerceIn(0, 255),
        floatArrayOf(hue, saturation, brightness),
    )
    val opaqueHue = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))

    // The hex field is refreshed by the *other* controls only. Driving it off
    // the colour itself would rewrite the field the instant a typed value first
    // parsed, so "FF00" would jump to "FFFF0000" before the user finished.
    fun refreshHex() {
        val packed = AndroidColor.HSVToColor(
            (alpha * 255f).toInt().coerceIn(0, 255),
            floatArrayOf(hue, saturation, brightness),
        )
        hexDraft = hexOf(packed).removePrefix("#")
    }

    fun adopt(color: Int, syncHex: Boolean = true) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(color, hsv)
        hue = hsv[0]
        saturation = hsv[1]
        brightness = hsv[2]
        alpha = AndroidColor.alpha(color) / 255f
        if (syncHex) refreshHex()
    }

    fun setSaturationValue(x: Float, y: Float, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        saturation = (x / width).coerceIn(0f, 1f)
        brightness = 1f - (y / height).coerceIn(0f, 1f)
        refreshHex()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = { TextButton(onClick = { onConfirm(current) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // ---------------------------------------- saturation / value --
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(14.dp)),
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    setSaturationValue(
                                        offset.x, offset.y, size.width, size.height,
                                    )
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        setSaturationValue(
                                            offset.x, offset.y, size.width, size.height,
                                        )
                                    },
                                ) { change, _ ->
                                    setSaturationValue(
                                        change.position.x,
                                        change.position.y,
                                        size.width,
                                        size.height,
                                    )
                                    change.consume()
                                }
                            },
                    ) {
                        drawRect(Brush.horizontalGradient(listOf(Color.White, opaqueHue)))
                        drawRect(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
                        )
                        val markerX = saturation * size.width
                        val markerY = (1f - brightness) * size.height
                        drawCircle(
                            color = Color.White,
                            radius = 9.dp.toPx(),
                            center = Offset(markerX, markerY),
                            style = Stroke(width = 2.5.dp.toPx()),
                        )
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.45f),
                            radius = 11.5.dp.toPx(),
                            center = Offset(markerX, markerY),
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                }

                // ---------------------------------------------------- hue ----
                SliderCanvas(
                    fraction = hue / 360f,
                    onFraction = {
                        hue = (it * 360f).coerceIn(0f, 360f)
                        refreshHex()
                    },
                    brush = Brush.horizontalGradient(HUE_STOPS),
                )

                // -------------------------------------------------- alpha ----
                SliderCanvas(
                    fraction = alpha,
                    onFraction = {
                        alpha = it.coerceIn(0f, 1f)
                        refreshHex()
                    },
                    brush = Brush.horizontalGradient(
                        listOf(opaqueHue.copy(alpha = 0f), opaqueHue),
                    ),
                    checkered = true,
                )

                // ------------------------------------------ hex + preview ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .checkerboard(7.dp)
                            .background(Color(current))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                RoundedCornerShape(14.dp),
                            ),
                    )
                    Spacer(modifier = Modifier.size(14.dp))
                    OutlinedTextField(
                        value = hexDraft,
                        onValueChange = { raw ->
                            val cleaned = raw.trim().removePrefix("#").take(8)
                                .filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
                            hexDraft = cleaned
                            // syncHex = false: the field is the source here, so
                            // rewriting it would fight the user mid-entry.
                            parseHex(cleaned)?.let { adopt(it, syncHex = false) }
                        },
                        label = { Text("AARRGGBB") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                // ------------------------------------------------ swatches ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SWATCHES.forEach { swatch ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .checkerboard(6.dp)
                                .background(Color(swatch))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    CircleShape,
                                )
                                .clickable { adopt(swatch) },
                        )
                    }
                }
            }
        },
    )
}

private val HUE_STOPS = listOf(
    Color(0xFFFF0000),
    Color(0xFFFFFF00),
    Color(0xFF00FF00),
    Color(0xFF00FFFF),
    Color(0xFF0000FF),
    Color(0xFFFF00FF),
    Color(0xFFFF0000),
)

/** A flat draggable track used for both the hue and the alpha channel. */
@Composable
private fun SliderCanvas(
    fraction: Float,
    onFraction: (Float) -> Unit,
    brush: Brush,
    checkered: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .then(if (checkered) Modifier.checkerboard(7.dp) else Modifier),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onFraction((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            onFraction((offset.x / size.width).coerceIn(0f, 1f))
                        },
                    ) { change, _ ->
                        onFraction((change.position.x / size.width).coerceIn(0f, 1f))
                        change.consume()
                    }
                },
        ) {
            drawRect(brush)
            val x = fraction.coerceIn(0f, 1f) * size.width
            drawCircle(
                color = Color.White,
                radius = size.height * 0.32f,
                center = Offset(x, size.height / 2f),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}

private fun parseHex(text: String): Int? {
    val normalized = when (text.length) {
        6 -> "FF$text"
        8 -> text
        else -> return null
    }
    return runCatching { normalized.toLong(16).toInt() }.getOrNull()
}

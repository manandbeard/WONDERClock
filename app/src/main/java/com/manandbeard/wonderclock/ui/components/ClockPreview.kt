package com.manandbeard.wonderclock.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import com.manandbeard.wonderclock.data.ClockConfig
import com.manandbeard.wonderclock.render.ClockRenderer
import com.manandbeard.wonderclock.render.RenderEnv
import kotlinx.coroutines.delay

/**
 * Draws a config exactly the way the home screen will.
 *
 * This is the same [ClockRenderer] call the widget makes, so what the settings
 * screen shows is not an approximation of the widget — it is the widget.
 */
@Composable
fun ClockPreview(
    config: ClockConfig,
    env: RenderEnv,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }
        val heightPx = with(density) { maxHeight.roundToPx() }
        if (widthPx <= 0 || heightPx <= 0) return@BoxWithConstraints

        val bitmap = remember(config, widthPx, heightPx, env) {
            ClockRenderer.render(config, widthPx, heightPx, env)
        }
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * A [RenderEnv] that refreshes on a [periodMillis] boundary.
 *
 * The timestamp is snapped to that boundary so recomposition — and the bitmap
 * cache key with it — only changes when the drawing actually would. Detailed
 * previews tick every second; thumbnail grids tick once a minute.
 */
@Composable
fun rememberLiveEnv(periodMillis: Long = 1_000L): RenderEnv {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current

    var env by remember(periodMillis) {
        mutableStateOf(snapped(RenderEnv.preview(context), periodMillis))
    }

    LaunchedEffect(periodMillis, isPreview) {
        if (isPreview) return@LaunchedEffect
        while (true) {
            val now = System.currentTimeMillis()
            delay((periodMillis - now % periodMillis).coerceAtLeast(50L))
            env = snapped(RenderEnv.preview(context), periodMillis)
        }
    }
    return env
}

private fun snapped(env: RenderEnv, periodMillis: Long): RenderEnv =
    env.copy(nowMillis = env.nowMillis / periodMillis * periodMillis)

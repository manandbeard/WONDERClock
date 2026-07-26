package com.manandbeard.wonderclock.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DD3FC),
    onPrimary = Color(0xFF00344A),
    secondary = Color(0xFFF0ABFC),
    tertiary = Color(0xFFFDBA74),
    background = Color(0xFF101014),
    surface = Color(0xFF15161C),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0369A1),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF9333EA),
    tertiary = Color(0xFFC2410C),
    background = Color(0xFFFBFBFE),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun WonderClockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

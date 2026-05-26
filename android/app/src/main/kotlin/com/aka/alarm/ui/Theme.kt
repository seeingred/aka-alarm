package com.aka.alarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val DawnDarkColors = darkColorScheme(
    primary = Color(0xFFFFB454),
    onPrimary = Color(0xFF1A1100),
    secondary = Color(0xFFFFE6A8),
    background = Color(0xFF0A1740),
    onBackground = Color(0xFFF5F1E6),
    surface = Color(0xFF0A1740),
    onSurface = Color(0xFFF5F1E6),
    surfaceVariant = Color(0xFF1A2A60),
    onSurfaceVariant = Color(0xFFC9C4B5),
)

private val DawnLightColors = lightColorScheme(
    primary = Color(0xFFE38A1A),
    onPrimary = Color.White,
    secondary = Color(0xFFFFB454),
    background = Color(0xFFFFF1E0),
    onBackground = Color(0xFF1A1A2E),
    surface = Color(0xFFFFF1E0),
    onSurface = Color(0xFF1A1A2E),
    surfaceVariant = Color(0xFFFFE8C8),
    onSurfaceVariant = Color(0xFF4A4A66),
)

/**
 * Brand theme. We deliberately *don't* use Material You dynamic colours — the
 * dawn-sky gradient is the app's identity and an alarm clock's UI shouldn't
 * morph to whatever wallpaper the user happens to have. Light vs dark is
 * driven by the system setting and properly swaps both the gradient and the
 * Material colour scheme together.
 */
@Composable
fun AkaAlarmTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (useDarkTheme) DawnDarkColors else DawnLightColors
    MaterialTheme(colorScheme = colorScheme) {
        DawnGradient(useDarkTheme = useDarkTheme) { content() }
    }
}

@Composable
private fun DawnGradient(useDarkTheme: Boolean, content: @Composable () -> Unit) {
    val gradient = if (useDarkTheme) {
        // Indigo → bright dawn-blue. Matches the iOS background and the app icon.
        Brush.verticalGradient(
            0.0f to Color(0xFF0A1740),
            0.55f to Color(0xFF0A3B96),
            1.0f to Color(0xFF1A86FF),
        )
    } else {
        // Warm dawn cream → pale sky. Same "sunrise" feel inverted for daytime.
        Brush.verticalGradient(
            0.0f to Color(0xFFFFF1E0),
            0.55f to Color(0xFFFFE8C8),
            1.0f to Color(0xFFD8E8FF),
        )
    }
    Box(modifier = Modifier.fillMaxSize().background(gradient)) {
        content()
    }
}

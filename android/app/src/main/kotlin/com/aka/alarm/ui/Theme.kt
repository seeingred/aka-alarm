package com.aka.alarm.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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
    background = Color(0xFFFEF7E8),
    onBackground = Color(0xFF1A1A2E),
    surface = Color(0xFFFEF7E8),
    onSurface = Color(0xFF1A1A2E),
)

@Composable
fun AkaAlarmTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        dynamicAvailable && useDarkTheme -> dynamicDarkColorScheme(context)
        dynamicAvailable && !useDarkTheme -> dynamicLightColorScheme(context)
        useDarkTheme -> DawnDarkColors
        else -> DawnLightColors
    }

    MaterialTheme(colorScheme = colorScheme) {
        DawnGradient { content() }
    }
}

/** Vertical indigo → dawn-blue background, matching the iOS look and the icon. */
@Composable
private fun DawnGradient(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0.0f to Color(0xFF0A1740),
                    0.55f to Color(0xFF0A3B96),
                    1.0f to Color(0xFF1A86FF),
                )
            )
    ) {
        content()
    }
}

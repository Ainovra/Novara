package com.novara.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Exact values from static/style.css :root and [data-theme="light"]
private val DarkColors = darkColorScheme(
    background = Color(0xFF0F1115),
    surface = Color(0xFF16191F),
    surfaceVariant = Color(0xFF1B1F27),
    outline = Color(0xFF262B33),
    onBackground = Color(0xFFE8E6E3),
    onSurface = Color(0xFFE8E6E3),
    onSurfaceVariant = Color(0xFF8B92A0),
    primary = Color(0xFFE8A33D),
    onPrimary = Color(0xFF1A1300),
    secondary = Color(0xFF4FB3A9),
    error = Color(0xFFCF6679),
)

private val LightColors = lightColorScheme(
    background = Color(0xFFFAF9F6),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1EFEA),
    outline = Color(0xFFE3E0D9),
    onBackground = Color(0xFF201E1A),
    onSurface = Color(0xFF201E1A),
    onSurfaceVariant = Color(0xFF7A756B),
    primary = Color(0xFFC8791F),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF2E8F84),
    error = Color(0xFFB3261E),
)

@Composable
fun NovaraTheme(
    darkTheme: Boolean = true, // Novara defaults to dark, same as the website
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}

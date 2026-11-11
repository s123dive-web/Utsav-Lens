package com.utsavlens.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFFB3261E), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD4), onPrimaryContainer = Color(0xFF410001),
    secondary = Color(0xFF7B1FA2), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3DAFF), onSecondaryContainer = Color(0xFF2E004E),
    tertiary = Color(0xFF8A5100), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCBD), onTertiaryContainer = Color(0xFF2C1600),
    background = Color(0xFFFFFBF7), onBackground = Color(0xFF201A19),
    surface = Color(0xFFFFFBF7), onSurface = Color(0xFF201A19),
    surfaceVariant = Color(0xFFF5DDDA), onSurfaceVariant = Color(0xFF534341),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB4A9), onPrimary = Color(0xFF690003),
    primaryContainer = Color(0xFF930009), onPrimaryContainer = Color(0xFFFFDAD4),
    secondary = Color(0xFFE2B6FF), onSecondary = Color(0xFF4A007A),
    secondaryContainer = Color(0xFF62188A), onSecondaryContainer = Color(0xFFF3DAFF),
    tertiary = Color(0xFFFFB86C), onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF693C00), onTertiaryContainer = Color(0xFFFFDCBD),
    background = Color(0xFF181211), onBackground = Color(0xFFEDE0DD),
    surface = Color(0xFF181211), onSurface = Color(0xFFEDE0DD),
    surfaceVariant = Color(0xFF534341), onSurfaceVariant = Color(0xFFD8C2BE),
)

@Composable
fun UtsavTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

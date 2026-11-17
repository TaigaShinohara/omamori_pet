package com.omamoripet.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

private val OmamoriWearColorScheme = ColorScheme(
    primary = Color(0xFF8FD3B6),
    onPrimary = Color(0xFF0A261B),
    primaryContainer = Color(0xFF1C3F31),
    onPrimaryContainer = Color(0xFFCFECE0),
    secondary = Color(0xFF9BC4E2),
    onSecondary = Color(0xFF0C2434),
    secondaryContainer = Color(0xFF213C4E),
    onSecondaryContainer = Color(0xFFD4E8F7),
    tertiary = Color(0xFFE8B878),
    onTertiary = Color(0xFF2E1E08),
    tertiaryContainer = Color(0xFF473317),
    onTertiaryContainer = Color(0xFFF7DEC0),
    background = Color(0xFF0F141A),
    onBackground = Color(0xFFECE8DF),
    surfaceContainerLow = Color(0xFF161D25),
    surfaceContainer = Color(0xFF1D2631),
    surfaceContainerHigh = Color(0xFF25303E),
    onSurface = Color(0xFFECE8DF),
    onSurfaceVariant = Color(0xFFB4BCC4),
    outline = Color(0xFF566370),
    outlineVariant = Color(0xFF333E4A)
)

@Composable
fun OmamoriWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OmamoriWearColorScheme,
        content = content
    )
}

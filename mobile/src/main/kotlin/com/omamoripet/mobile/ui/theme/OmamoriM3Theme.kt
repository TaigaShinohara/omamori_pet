package com.omamoripet.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 夜間や不安時にも刺激を抑え、十分なコントラスト (4.5:1 以上) を確保した
 * 和風お守りテイストの Material 3 セマンティックカラースキーム。
 */
private val OmamoriDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8FD3B6),
    onPrimary = Color(0xFF0A261B),
    primaryContainer = Color(0xFF1D4133),
    onPrimaryContainer = Color(0xFFCFECE0),
    secondary = Color(0xFF9BC4E2),
    onSecondary = Color(0xFF0B2435),
    secondaryContainer = Color(0xFF203C4F),
    onSecondaryContainer = Color(0xFFD5E9F8),
    tertiary = Color(0xFFE8B878),
    onTertiary = Color(0xFF2F1F08),
    tertiaryContainer = Color(0xFF4B3517),
    onTertiaryContainer = Color(0xFFF8DFBF),
    background = Color(0xFF0F141A),
    onBackground = Color(0xFFECE8DF),
    surface = Color(0xFF0F141A),
    onSurface = Color(0xFFECE8DF),
    surfaceVariant = Color(0xFF222B35),
    onSurfaceVariant = Color(0xFFB8C0C8),
    surfaceContainerLowest = Color(0xFF0A0E12),
    surfaceContainerLow = Color(0xFF151C24),
    surfaceContainer = Color(0xFF1B232D),
    surfaceContainerHigh = Color(0xFF232D3A),
    surfaceContainerHighest = Color(0xFF2B3746),
    outline = Color(0xFF6B7785),
    outlineVariant = Color(0xFF333F4C),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

private val OmamoriLightColorScheme = lightColorScheme(
    primary = Color(0xFF216B51),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC5EBD9),
    onPrimaryContainer = Color(0xFF072117),
    secondary = Color(0xFF2D6080),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD1E6F6),
    onSecondaryContainer = Color(0xFF091E2D),
    tertiary = Color(0xFF84551B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFBE1BE),
    onTertiaryContainer = Color(0xFF2B1802),
    background = Color(0xFFF7F5F0),
    onBackground = Color(0xFF1A1D21),
    surface = Color(0xFFF7F5F0),
    onSurface = Color(0xFF1A1D21),
    surfaceVariant = Color(0xFFE3E5E0),
    onSurfaceVariant = Color(0xFF454C54),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1EFE9),
    surfaceContainer = Color(0xFFEBE8E1),
    surfaceContainerHigh = Color(0xFFE4E1D9),
    surfaceContainerHighest = Color(0xFFDCD9D0),
    outline = Color(0xFF727982),
    outlineVariant = Color(0xFFC6C9C3),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val OmamoriTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 36.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = "tnum"
    ),
    headlineMedium = TextStyle(
        fontSize = 26.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium
    )
)

private val OmamoriShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
)

@Composable
fun OmamoriM3Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) OmamoriDarkColorScheme else OmamoriLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = OmamoriTypography,
        shapes = OmamoriShapes,
        content = content
    )
}

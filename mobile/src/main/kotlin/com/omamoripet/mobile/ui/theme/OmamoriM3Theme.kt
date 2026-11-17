package com.omamoripet.mobile.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 夜間や不安時にも刺激を抑え、十分なコントラスト (4.5:1 以上) を確保した
 * 和風お守りテイストの Material 3 セマンティックカラースキーム。
 */
private val OmamoriDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8FD3B6),
    onPrimary = Color(0xFF0A261B),
    primaryContainer = Color(0xFF1D4133),
    onPrimaryContainer = Color(0xFFCFECE0),
    secondary = Color(0xFF93C7B5),
    onSecondary = Color(0xFF0B251D),
    secondaryContainer = Color(0xFF213E35),
    onSecondaryContainer = Color(0xFFD1EAE0),
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
    surfaceDim = Color(0xFF0B0F14),
    surfaceBright = Color(0xFF293442),
    surfaceContainerLowest = Color(0xFF0A0E12),
    surfaceContainerLow = Color(0xFF151C24),
    surfaceContainer = Color(0xFF1B232D),
    surfaceContainerHigh = Color(0xFF232D3A),
    surfaceContainerHighest = Color(0xFF2B3746),
    outline = Color(0xFF6B7785),
    outlineVariant = Color(0xFF333F4C),
    inverseSurface = Color(0xFFECE8DF),
    inverseOnSurface = Color(0xFF1A1D21),
    inversePrimary = Color(0xFF216B51),
    scrim = Color(0xFF000000),
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
    secondary = Color(0xFF3A6659),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6EAE0),
    onSecondaryContainer = Color(0xFF0E2A21),
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
    surfaceDim = Color(0xFFDEDBD2),
    surfaceBright = Color(0xFFFBF9F5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1EFE9),
    surfaceContainer = Color(0xFFEBE8E1),
    surfaceContainerHigh = Color(0xFFE4E1D9),
    surfaceContainerHighest = Color(0xFFDCD9D0),
    outline = Color(0xFF727982),
    outlineVariant = Color(0xFFC6C9C3),
    inverseSurface = Color(0xFF22272D),
    inverseOnSurface = Color(0xFFF1EFE9),
    inversePrimary = Color(0xFF8FD3B6),
    scrim = Color(0xFF000000),
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
    headlineSmall = TextStyle(
        fontSize = 22.sp,
        lineHeight = 30.sp,
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
    titleSmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp
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
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium
    )
)

private val OmamoriShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * 穏やかなお守り体験に合わせた Material 3 モーショントークン。
 */
object OmamoriMotion {
    fun <T> effectsDefault(): FiniteAnimationSpec<T> =
        tween(durationMillis = 300, easing = FastOutSlowInEasing)

    fun <T> effectsSlow(): FiniteAnimationSpec<T> =
        tween(durationMillis = 480, easing = FastOutSlowInEasing)

    fun <T> spatialDefault(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
}

/**
 * システムのアニメーション倍率設定（Reduced Motion）をアプリ全体へ伝達する CompositionLocal。
 */
val LocalAnimationsEnabled = compositionLocalOf { true }

@Composable
fun OmamoriM3Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val animationsEnabled by produceState(initialValue = true, context) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f
                ) > 0f
            }.getOrDefault(true)
        }
    }
    val colorScheme = if (darkTheme) OmamoriDarkColorScheme else OmamoriLightColorScheme

    CompositionLocalProvider(LocalAnimationsEnabled provides animationsEnabled) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = OmamoriTypography,
            shapes = OmamoriShapes,
            content = content
        )
    }
}

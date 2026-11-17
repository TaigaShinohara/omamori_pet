package com.omamoripet.wear.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val OmamoriWearColorScheme = ColorScheme(
    primary = Color(0xFF8FD3B6),
    primaryDim = Color(0xFF72B598),
    onPrimary = Color(0xFF0A261B),
    primaryContainer = Color(0xFF1C3F31),
    onPrimaryContainer = Color(0xFFCFECE0),
    secondary = Color(0xFF93C7B5),
    secondaryDim = Color(0xFF76AA98),
    onSecondary = Color(0xFF0B251D),
    secondaryContainer = Color(0xFF213E35),
    onSecondaryContainer = Color(0xFFD1EAE0),
    tertiary = Color(0xFFE8B878),
    tertiaryDim = Color(0xFFC8995C),
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
    outlineVariant = Color(0xFF333E4A),
    error = Color(0xFFF2B8B5),
    errorDim = Color(0xFFD99B97),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

/**
 * システムのアニメーション倍率設定（Reduced Motion）を Wear 画面へ伝達する CompositionLocal。
 */
val LocalWearAnimationsEnabled = compositionLocalOf { true }

@Composable
fun OmamoriWearTheme(content: @Composable () -> Unit) {
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

    CompositionLocalProvider(LocalWearAnimationsEnabled provides animationsEnabled) {
        MaterialTheme(
            colorScheme = OmamoriWearColorScheme,
            content = content
        )
    }
}

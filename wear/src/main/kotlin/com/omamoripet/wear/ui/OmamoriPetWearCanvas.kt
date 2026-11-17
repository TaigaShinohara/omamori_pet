package com.omamoripet.wear.ui

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import com.omamoripet.shared.model.OmamoriState

@Composable
fun OmamoriPetWearAvatar(
    state: OmamoriState,
    contentDescriptionText: String,
    breathProgress: Float = 0.5f,
    size: Dp = 64.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val animationsEnabled = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) > 0f

    val pulseScale = if (animationsEnabled && state != OmamoriState.RESTING) {
        if (state == OmamoriState.BREATHING) {
            0.88f + 0.24f * breathProgress
        } else {
            val transition = rememberInfiniteTransition(label = "pet_pulse")
            val animated by transition.animateFloat(
                initialValue = 0.96f,
                targetValue = 1.04f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 3600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse_scale"
            )
            animated
        }
    } else {
        1f
    }

    val colorScheme = MaterialTheme.colorScheme
    val bodyColor = when (state) {
        OmamoriState.CALM -> colorScheme.primary
        OmamoriState.ELEVATED_AT_REST -> colorScheme.tertiary
        OmamoriState.BREATHING -> colorScheme.secondary
        OmamoriState.RESTING -> colorScheme.outline
    }
    val knotColor = when (state) {
        OmamoriState.CALM -> colorScheme.onPrimaryContainer
        OmamoriState.ELEVATED_AT_REST -> colorScheme.onTertiaryContainer
        OmamoriState.BREATHING -> colorScheme.onSecondaryContainer
        OmamoriState.RESTING -> colorScheme.onSurfaceVariant
    }
    val faceColor = colorScheme.background

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = contentDescriptionText }
    ) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f

        // Aura ring
        drawCircle(
            color = bodyColor.copy(alpha = 0.22f),
            radius = (w * 0.45f) * pulseScale,
            center = Offset(cx, cy)
        )

        if (state == OmamoriState.ELEVATED_AT_REST) {
            drawCircle(
                color = bodyColor.copy(alpha = 0.7f),
                radius = (w * 0.42f) * pulseScale,
                center = Offset(cx, cy),
                style = Stroke(width = w * 0.03f)
            )
        }

        // Omamori body
        val bodyRadius = w * 0.32f * (if (state == OmamoriState.BREATHING) (0.92f + 0.12f * breathProgress) else 1f)
        drawCircle(
            color = bodyColor,
            radius = bodyRadius,
            center = Offset(cx, cy + h * 0.04f)
        )

        // Omamori knot ribbon
        drawRoundRect(
            color = knotColor,
            topLeft = Offset(cx - w * 0.09f, cy - bodyRadius - h * 0.02f),
            size = Size(w * 0.18f, h * 0.09f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )

        // Facial expression (distinct shape per state, not color-only)
        val eyeY = cy
        val eyeOffsetX = bodyRadius * 0.36f
        val strokeW = w * 0.045f

        when (state) {
            OmamoriState.CALM -> {
                // Happy curved eyes
                drawArc(
                    color = faceColor,
                    startAngle = 195f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(cx - eyeOffsetX - w * 0.06f, eyeY - h * 0.03f),
                    size = Size(w * 0.12f, h * 0.09f),
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
                drawArc(
                    color = faceColor,
                    startAngle = 195f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(cx + eyeOffsetX - w * 0.06f, eyeY - h * 0.03f),
                    size = Size(w * 0.12f, h * 0.09f),
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
                drawArc(
                    color = faceColor,
                    startAngle = 20f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(cx - w * 0.055f, eyeY + bodyRadius * 0.24f),
                    size = Size(w * 0.11f, h * 0.065f),
                    style = Stroke(width = strokeW * 0.85f, cap = StrokeCap.Round)
                )
            }
            OmamoriState.ELEVATED_AT_REST -> {
                // Round attentive eyes + small 'O' breathing cue
                drawCircle(color = faceColor, radius = w * 0.038f, center = Offset(cx - eyeOffsetX, eyeY))
                drawCircle(color = faceColor, radius = w * 0.038f, center = Offset(cx + eyeOffsetX, eyeY))
                drawCircle(
                    color = faceColor,
                    radius = w * 0.045f,
                    center = Offset(cx, eyeY + bodyRadius * 0.36f),
                    style = Stroke(width = strokeW * 0.7f)
                )
            }
            OmamoriState.BREATHING -> {
                // Serene closed downward arcs
                drawArc(
                    color = faceColor,
                    startAngle = 15f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(cx - eyeOffsetX - w * 0.06f, eyeY - h * 0.05f),
                    size = Size(w * 0.12f, h * 0.08f),
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
                drawArc(
                    color = faceColor,
                    startAngle = 15f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(cx + eyeOffsetX - w * 0.06f, eyeY - h * 0.05f),
                    size = Size(w * 0.12f, h * 0.08f),
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
            }
            OmamoriState.RESTING -> {
                // Horizontal resting lines
                val lineHalf = w * 0.05f
                drawLine(
                    color = faceColor,
                    start = Offset(cx - eyeOffsetX - lineHalf, eyeY),
                    end = Offset(cx - eyeOffsetX + lineHalf, eyeY),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = faceColor,
                    start = Offset(cx + eyeOffsetX - lineHalf, eyeY),
                    end = Offset(cx + eyeOffsetX + lineHalf, eyeY),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

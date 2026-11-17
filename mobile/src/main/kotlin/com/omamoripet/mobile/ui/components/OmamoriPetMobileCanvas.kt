package com.omamoripet.mobile.ui.components

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omamoripet.shared.model.OmamoriState

@Composable
fun OmamoriPetMobileAvatar(
    state: OmamoriState,
    description: String,
    breathFraction: Float = 0.5f,
    size: Dp = 112.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val animationsEnabled = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) > 0f

    val pulse = if (animationsEnabled && state != OmamoriState.RESTING) {
        if (state == OmamoriState.BREATHING) {
            0.86f + 0.28f * breathFraction
        } else {
            val infinite = rememberInfiniteTransition(label = "mobile_pet_pulse")
            val v by infinite.animateFloat(
                initialValue = 0.96f,
                targetValue = 1.04f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 3800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse_scale"
            )
            v
        }
    } else {
        1f
    }

    val cs = MaterialTheme.colorScheme
    val bodyColor = when (state) {
        OmamoriState.CALM -> cs.primary
        OmamoriState.ELEVATED_AT_REST -> cs.tertiary
        OmamoriState.BREATHING -> cs.secondary
        OmamoriState.RESTING -> cs.outline
    }
    val knotColor = when (state) {
        OmamoriState.CALM -> cs.primaryContainer
        OmamoriState.ELEVATED_AT_REST -> cs.tertiaryContainer
        OmamoriState.BREATHING -> cs.secondaryContainer
        OmamoriState.RESTING -> cs.surfaceContainerHighest
    }
    val faceColor = cs.surface

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = description }
    ) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f

        drawCircle(
            color = bodyColor.copy(alpha = 0.20f),
            radius = w * 0.45f * pulse,
            center = Offset(cx, cy)
        )

        if (state == OmamoriState.ELEVATED_AT_REST) {
            drawCircle(
                color = bodyColor.copy(alpha = 0.65f),
                radius = w * 0.41f * pulse,
                center = Offset(cx, cy),
                style = Stroke(width = w * 0.025f)
            )
        }

        val bodyR = w * 0.31f * (if (state == OmamoriState.BREATHING) (0.90f + 0.14f * breathFraction) else 1f)
        drawCircle(
            color = bodyColor,
            radius = bodyR,
            center = Offset(cx, cy + h * 0.04f)
        )

        // お守り結びモチーフ
        drawRoundRect(
            color = knotColor,
            topLeft = Offset(cx - w * 0.09f, cy - bodyR - h * 0.02f),
            size = Size(w * 0.18f, h * 0.085f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )

        val eyeY = cy
        val eyeOffset = bodyR * 0.36f
        val strokeW = w * 0.04f

        when (state) {
            OmamoriState.CALM -> {
                drawArc(
                    color = faceColor,
                    startAngle = 195f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(cx - eyeOffset - w * 0.055f, eyeY - h * 0.03f),
                    size = Size(w * 0.11f, h * 0.085f),
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
                drawArc(
                    color = faceColor,
                    startAngle = 195f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(cx + eyeOffset - w * 0.055f, eyeY - h * 0.03f),
                    size = Size(w * 0.11f, h * 0.085f),
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
                drawArc(
                    color = faceColor,
                    startAngle = 20f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(cx - w * 0.05f, eyeY + bodyR * 0.24f),
                    size = Size(w * 0.10f, h * 0.06f),
                    style = Stroke(width = strokeW * 0.85f, cap = StrokeCap.Round)
                )
            }
            OmamoriState.ELEVATED_AT_REST -> {
                drawCircle(color = faceColor, radius = w * 0.035f, center = Offset(cx - eyeOffset, eyeY))
                drawCircle(color = faceColor, radius = w * 0.035f, center = Offset(cx + eyeOffset, eyeY))
                drawCircle(
                    color = faceColor,
                    radius = w * 0.042f,
                    center = Offset(cx, eyeY + bodyR * 0.36f),
                    style = Stroke(width = strokeW * 0.7f)
                )
            }
            OmamoriState.BREATHING -> {
                drawArc(
                    color = faceColor,
                    startAngle = 15f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(cx - eyeOffset - w * 0.055f, eyeY - h * 0.04f),
                    size = Size(w * 0.11f, h * 0.08f),
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
                drawArc(
                    color = faceColor,
                    startAngle = 15f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(cx + eyeOffset - w * 0.055f, eyeY - h * 0.04f),
                    size = Size(w * 0.11f, h * 0.08f),
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
            }
            OmamoriState.RESTING -> {
                val half = w * 0.048f
                drawLine(
                    color = faceColor,
                    start = Offset(cx - eyeOffset - half, eyeY),
                    end = Offset(cx - eyeOffset + half, eyeY),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = faceColor,
                    start = Offset(cx + eyeOffset - half, eyeY),
                    end = Offset(cx + eyeOffset + half, eyeY),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

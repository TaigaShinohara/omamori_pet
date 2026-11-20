package com.omamoripet.wear.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import com.omamoripet.shared.model.OmamoriState
import com.omamoripet.wear.R
import kotlinx.coroutines.delay
import kotlin.math.abs

private data class WearEyePose(
    val gazeX: Float,
    val gazeY: Float,
    val leftHeightScale: Float,
    val rightHeightScale: Float,
    val widthScale: Float,
    val cornerFactor: Float
)

/**
 * Wear OS 円形ディスプレイに最適化したお守りボットアイコン。
 * 4状態（CALM・ELEVATED_AT_REST: 緑 / BREATHING: 青磁 / RESTING: 静かなグレー）で表現する。
 */
@Composable
fun OmamoriPetWearAvatar(
    state: OmamoriState,
    contentDescriptionText: String? = null,
    breathProgressProvider: () -> Float = { 0.5f },
    interactive: Boolean = true,
    size: Dp = 64.dp,
    modifier: Modifier = Modifier
) {
    val animationsEnabled = LocalWearAnimationsEnabled.current
    val tapLabel = stringResource(R.string.avatar_tap_action)

    var targetPose by remember {
        mutableStateOf(WearEyePose(0.34f, -0.24f, 1f, 1f, 1f, 0.42f))
    }
    var tapWinkTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(state, animationsEnabled, tapWinkTrigger) {
        if (!animationsEnabled) {
            targetPose = when (state) {
                OmamoriState.CALM -> WearEyePose(0.32f, -0.22f, 1f, 1f, 1f, 0.42f)
                OmamoriState.ELEVATED_AT_REST -> WearEyePose(0f, -0.02f, 0.96f, 0.96f, 1.04f, 0.44f)
                OmamoriState.BREATHING -> WearEyePose(0f, 0.05f, 0.35f, 0.35f, 1.15f, 0.5f)
                OmamoriState.RESTING -> WearEyePose(0f, 0.14f, 0.24f, 0.24f, 1.2f, 0.5f)
            }
            return@LaunchedEffect
        }

        if (tapWinkTrigger > 0 && state != OmamoriState.BREATHING) {
            targetPose = WearEyePose(0.18f, -0.10f, 1.05f, 0.20f, 1.05f, 0.48f)
            delay(360L)
        }

        when (state) {
            OmamoriState.CALM -> {
                val script = listOf(
                    WearEyePose(0.34f, -0.24f, 1.00f, 1.00f, 1.00f, 0.42f) to 2100L,
                    WearEyePose(0.34f, -0.24f, 0.12f, 0.12f, 1.08f, 0.50f) to 140L,
                    WearEyePose(0.00f, -0.04f, 1.00f, 1.00f, 1.05f, 0.38f) to 1800L,
                    WearEyePose(-0.32f, -0.18f, 1.00f, 1.00f, 1.00f, 0.42f) to 1900L,
                    WearEyePose(-0.24f, 0.12f, 1.05f, 0.20f, 1.05f, 0.48f) to 480L,
                    WearEyePose(0.18f, 0.18f, 1.08f, 1.08f, 0.96f, 0.44f) to 1600L,
                    WearEyePose(0.18f, 0.18f, 0.12f, 0.12f, 1.08f, 0.50f) to 130L
                )
                while (true) {
                    for ((pose, waitMs) in script) {
                        targetPose = pose
                        delay(waitMs)
                    }
                }
            }

            OmamoriState.ELEVATED_AT_REST -> {
                val script = listOf(
                    WearEyePose(0.00f, -0.04f, 0.96f, 0.96f, 1.04f, 0.44f) to 1800L,
                    WearEyePose(0.00f, -0.04f, 0.14f, 0.14f, 1.08f, 0.50f) to 150L,
                    WearEyePose(0.08f, -0.02f, 0.94f, 0.94f, 1.04f, 0.44f) to 1600L,
                    WearEyePose(-0.08f, -0.02f, 0.94f, 0.94f, 1.04f, 0.44f) to 1600L,
                    WearEyePose(0.00f, 0.00f, 0.96f, 0.96f, 1.04f, 0.44f) to 1500L
                )
                while (true) {
                    for ((pose, waitMs) in script) {
                        targetPose = pose
                        delay(waitMs)
                    }
                }
            }

            OmamoriState.RESTING -> {
                val script = listOf(
                    WearEyePose(0.00f, 0.16f, 0.22f, 0.22f, 1.22f, 0.50f) to 2800L,
                    WearEyePose(0.12f, 0.12f, 0.32f, 0.32f, 1.15f, 0.48f) to 1400L,
                    WearEyePose(0.00f, 0.18f, 0.20f, 0.20f, 1.22f, 0.50f) to 2600L
                )
                while (true) {
                    for ((pose, waitMs) in script) {
                        targetPose = pose
                        delay(waitMs)
                    }
                }
            }

            OmamoriState.BREATHING -> Unit
        }
    }

    val effectiveTarget = if (state == OmamoriState.BREATHING) {
        WearEyePose(
            gazeX = 0f,
            gazeY = 0.01f,
            leftHeightScale = 0.67f,
            rightHeightScale = 0.67f,
            widthScale = 1.11f,
            cornerFactor = 0.44f
        )
    } else {
        targetPose
    }

    val eyeSpring = remember(animationsEnabled) {
        if (!animationsEnabled) {
            snap()
        } else {
            spring<Float>(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
        }
    }
    val blinkSpec = remember(animationsEnabled) {
        if (!animationsEnabled) {
            snap()
        } else {
            tween<Float>(160, easing = FastOutSlowInEasing)
        }
    }

    val gazeXState = animateFloatAsState(effectiveTarget.gazeX, animationSpec = eyeSpring, label = "wGazeX")
    val gazeYState = animateFloatAsState(effectiveTarget.gazeY, animationSpec = eyeSpring, label = "wGazeY")
    val leftHState = animateFloatAsState(effectiveTarget.leftHeightScale, animationSpec = blinkSpec, label = "wLeftH")
    val rightHState = animateFloatAsState(effectiveTarget.rightHeightScale, animationSpec = blinkSpec, label = "wRightH")
    val widthScaleState = animateFloatAsState(effectiveTarget.widthScale, animationSpec = eyeSpring, label = "wWidthScale")
    val cornerFactorState = animateFloatAsState(effectiveTarget.cornerFactor, animationSpec = eyeSpring, label = "wCorner")

    val colorScheme = MaterialTheme.colorScheme
    val targetBodyColor = when (state) {
        OmamoriState.CALM, OmamoriState.ELEVATED_AT_REST -> colorScheme.primary
        OmamoriState.BREATHING -> colorScheme.secondary
        OmamoriState.RESTING -> colorScheme.outline
    }
    val bodyColorState = animateColorAsState(
        targetValue = targetBodyColor,
        animationSpec = if (animationsEnabled) MaterialTheme.motionScheme.defaultEffectsSpec() else snap(),
        label = "wBodyColor"
    )
    val eyeColor = Color.White
    val interactionSource = remember { MutableInteractionSource() }
    val canTap = interactive && state != OmamoriState.BREATHING && animationsEnabled

    val semanticsModifier = when {
        canTap -> Modifier
            .semantics {
                if (!contentDescriptionText.isNullOrBlank()) {
                    contentDescription = contentDescriptionText
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = tapLabel,
                role = Role.Button,
                onClick = { tapWinkTrigger++ }
            )
        !contentDescriptionText.isNullOrBlank() -> Modifier.semantics { contentDescription = contentDescriptionText }
        else -> Modifier.clearAndSetSemantics {}
    }

    Canvas(
        modifier = modifier
            .size(size)
            .then(semanticsModifier)
    ) {
        val w = this.size.width
        val cx = w / 2f
        val cy = this.size.height / 2f

        val isBreathing = state == OmamoriState.BREATHING
        val breathProgress = if (isBreathing) breathProgressProvider().coerceIn(0f, 1f) else 0.5f

        val gazeX: Float
        val gazeY: Float
        val leftH: Float
        val rightH: Float
        val widthScale: Float
        val cornerFactor: Float
        if (isBreathing) {
            gazeX = 0f
            gazeY = 0.20f - 0.38f * breathProgress
            leftH = 0.28f + 0.78f * breathProgress
            rightH = leftH
            widthScale = 1.20f - 0.18f * breathProgress
            cornerFactor = 0.48f - 0.08f * breathProgress
        } else {
            gazeX = gazeXState.value
            gazeY = gazeYState.value
            leftH = leftHState.value
            rightH = rightHState.value
            widthScale = widthScaleState.value
            cornerFactor = cornerFactorState.value
        }

        val bodyColor = bodyColorState.value
        val haloScale = if (!animationsEnabled) {
            1f
        } else if (isBreathing) {
            0.90f + 0.22f * breathProgress
        } else {
            1.0f + 0.03f * abs(gazeY)
        }

        drawCircle(
            color = bodyColor.copy(alpha = 0.22f),
            radius = w * 0.46f * haloScale,
            center = Offset(cx, cy)
        )

        val bodyRadius = w * 0.36f *
            (if (isBreathing && animationsEnabled) (0.93f + 0.11f * breathProgress) else 1f)
        drawCircle(
            color = bodyColor,
            radius = bodyRadius,
            center = Offset(cx, cy)
        )

        val shiftX = gazeX * bodyRadius * 0.62f
        val shiftY = gazeY * bodyRadius * 0.54f
        val eyeGap = bodyRadius * 0.34f * (1f - abs(gazeX) * 0.22f)
        val baseEyeW = bodyRadius * 0.21f * widthScale
        val baseEyeH = bodyRadius * 0.44f

        val leftPerspX = if (gazeX < 0f) 1f - abs(gazeX) * 0.36f else 1f
        val rightPerspX = if (gazeX > 0f) 1f - abs(gazeX) * 0.36f else 1f
        val leftScale = if (gazeX < 0f) 1f - abs(gazeX) * 0.14f else 1f
        val rightScale = if (gazeX > 0f) 1f - abs(gazeX) * 0.14f else 1f

        val tiltDegrees = gazeX * gazeY * -120f

        val leftCenter = Offset(cx + shiftX - eyeGap, cy + shiftY)
        val rightCenter = Offset(cx + shiftX + eyeGap, cy + shiftY)

        rotate(degrees = tiltDegrees, pivot = Offset(cx + shiftX, cy + shiftY)) {
            val lw = baseEyeW * leftPerspX * leftScale
            val lh = (baseEyeH * leftH * leftScale).coerceAtLeast(w * 0.032f)
            val lCorner = minOf(lw, lh) * cornerFactor
            drawRoundRect(
                color = eyeColor,
                topLeft = Offset(leftCenter.x - lw / 2f, leftCenter.y - lh / 2f),
                size = Size(lw, lh),
                cornerRadius = CornerRadius(lCorner, lCorner)
            )

            val rw = baseEyeW * rightPerspX * rightScale
            val rh = (baseEyeH * rightH * rightScale).coerceAtLeast(w * 0.032f)
            val rCorner = minOf(rw, rh) * cornerFactor
            drawRoundRect(
                color = eyeColor,
                topLeft = Offset(rightCenter.x - rw / 2f, rightCenter.y - rh / 2f),
                size = Size(rw, rh),
                cornerRadius = CornerRadius(rCorner, rCorner)
            )
        }
    }
}

package com.omamoripet.mobile.ui.components

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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omamoripet.mobile.R
import com.omamoripet.mobile.ui.theme.LocalAnimationsEnabled
import com.omamoripet.mobile.ui.theme.OmamoriMotion
import com.omamoripet.shared.model.Feeling
import com.omamoripet.shared.model.OmamoriState
import com.omamoripet.shared.model.PetBodyTone
import com.omamoripet.shared.model.PetExpressionMode
import com.omamoripet.shared.model.PetHeartRateColor
import com.omamoripet.shared.model.PetVisualState
import com.omamoripet.shared.model.resolvePetVisualState
import kotlinx.coroutines.delay
import kotlin.math.abs

private data class EyePose(
    val gazeX: Float,
    val gazeY: Float,
    val leftHeightScale: Float,
    val rightHeightScale: Float,
    val widthScale: Float,
    val cornerFactor: Float,
    val leftTiltDeg: Float = 0f,
    val rightTiltDeg: Float = 0f
)

/**
 * 角丸スクエア／カプセル型の目が立体球面上で穏やかに動くお守りボットアイコン。
 * 体の色は [PetVisualState.tone]（緑 / 黄 / 赤 / 紫、未装着・データ欠損時のみグレー）だけで決まり、
 * 表情は [PetVisualState.mode] と [feeling] に沿って描画する。
 * 気分記録中（[feeling] != null）も体の健康色は維持される。
 */
@Composable
fun OmamoriPetMobileAvatar(
    visualState: PetVisualState,
    feeling: Feeling? = null,
    description: String? = null,
    breathFractionProvider: () -> Float = { 0.5f },
    interactive: Boolean = true,
    size: Dp = 112.dp,
    modifier: Modifier = Modifier
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    val tapLabel = stringResource(R.string.avatar_tap_action)
    val isBreathing = visualState.mode == PetExpressionMode.BREATHING
    val activeFeeling = if (isBreathing) null else feeling

    var targetPose by remember {
        mutableStateOf(
            EyePose(
                gazeX = 0.34f,
                gazeY = -0.24f,
                leftHeightScale = 1f,
                rightHeightScale = 1f,
                widthScale = 1f,
                cornerFactor = 0.42f
            )
        )
    }
    var tapWinkTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(visualState, activeFeeling, animationsEnabled, tapWinkTrigger) {
        if (!animationsEnabled) {
            targetPose = staticEyePose(visualState, activeFeeling)
            return@LaunchedEffect
        }

        if (tapWinkTrigger > 0 && !isBreathing) {
            targetPose = EyePose(0.18f, -0.10f, 1.05f, 0.20f, 1.05f, 0.48f)
            delay(360L)
        }

        if (activeFeeling != null) {
            val script = feelingEyeScript(activeFeeling)
            while (true) {
                for ((pose, waitMs) in script) {
                    targetPose = pose
                    delay(waitMs)
                }
            }
        }

        when (visualState.mode) {
            PetExpressionMode.CALM -> {
                val script = listOf(
                    EyePose(0.34f, -0.24f, 1.00f, 1.00f, 1.00f, 0.42f) to 2100L,
                    EyePose(0.34f, -0.24f, 0.12f, 0.12f, 1.08f, 0.50f) to 140L,
                    EyePose(0.00f, -0.04f, 1.00f, 1.00f, 1.05f, 0.38f) to 1800L,
                    EyePose(-0.32f, -0.18f, 1.00f, 1.00f, 1.00f, 0.42f) to 1900L,
                    EyePose(-0.24f, 0.12f, 1.05f, 0.20f, 1.05f, 0.48f) to 480L,
                    EyePose(0.18f, 0.18f, 1.08f, 1.08f, 0.96f, 0.44f) to 1600L,
                    EyePose(0.18f, 0.18f, 0.12f, 0.12f, 1.08f, 0.50f) to 130L
                )
                while (true) {
                    for ((pose, waitMs) in script) {
                        targetPose = pose
                        delay(waitMs)
                    }
                }
            }

            PetExpressionMode.ELEVATED -> {
                val script = elevatedEyeScript(visualState.tone)
                while (true) {
                    for ((pose, waitMs) in script) {
                        targetPose = pose
                        delay(waitMs)
                    }
                }
            }

            PetExpressionMode.SLEEPING -> {
                val script = listOf(
                    EyePose(0.00f, 0.17f, 0.14f, 0.14f, 1.28f, 0.50f) to 2800L,
                    EyePose(0.00f, 0.19f, 0.17f, 0.17f, 1.26f, 0.50f) to 2400L
                )
                while (true) {
                    for ((pose, waitMs) in script) {
                        targetPose = pose
                        delay(waitMs)
                    }
                }
            }

            PetExpressionMode.BREATHING -> Unit
        }
    }

    val effectiveTarget = if (isBreathing) {
        EyePose(
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

    // Draw フェーズでのみ .value を読み取り、アニメーション中の毎フレーム Recomposition を防ぐ
    val gazeXState = animateFloatAsState(effectiveTarget.gazeX, animationSpec = eyeSpring, label = "gazeX")
    val gazeYState = animateFloatAsState(effectiveTarget.gazeY, animationSpec = eyeSpring, label = "gazeY")
    val leftHState = animateFloatAsState(effectiveTarget.leftHeightScale, animationSpec = blinkSpec, label = "leftH")
    val rightHState = animateFloatAsState(effectiveTarget.rightHeightScale, animationSpec = blinkSpec, label = "rightH")
    val widthScaleState = animateFloatAsState(effectiveTarget.widthScale, animationSpec = eyeSpring, label = "widthScale")
    val cornerFactorState = animateFloatAsState(effectiveTarget.cornerFactor, animationSpec = eyeSpring, label = "corner")
    val leftTiltState = animateFloatAsState(effectiveTarget.leftTiltDeg, animationSpec = eyeSpring, label = "leftTilt")
    val rightTiltState = animateFloatAsState(effectiveTarget.rightTiltDeg, animationSpec = eyeSpring, label = "rightTilt")

    val cs = MaterialTheme.colorScheme
    val targetBodyColor = when (visualState.tone) {
        PetBodyTone.DATA_MISSING -> cs.outline
        PetBodyTone.GREEN -> cs.primary
        PetBodyTone.YELLOW -> Color(0xFFDCCB8D)
        PetBodyTone.RED -> Color(0xFFDDA0A0)
        PetBodyTone.PURPLE -> Color(0xFFBBA4DC)
    }
    val bodyColorState = animateColorAsState(
        targetValue = targetBodyColor,
        animationSpec = if (animationsEnabled) OmamoriMotion.effectsDefault() else snap(),
        label = "bodyColor"
    )
    val eyeColor = Color.White
    val interactionSource = remember { MutableInteractionSource() }
    val canTap = interactive && !isBreathing && animationsEnabled
    val showElevatedRing = activeFeeling == null &&
        visualState.mode == PetExpressionMode.ELEVATED &&
        (visualState.tone == PetBodyTone.RED || visualState.tone == PetBodyTone.PURPLE)

    val semanticsModifier = when {
        canTap -> Modifier
            .semantics {
                if (!description.isNullOrBlank()) {
                    contentDescription = description
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = tapLabel,
                role = Role.Button,
                onClick = { tapWinkTrigger++ }
            )
        !description.isNullOrBlank() -> Modifier.semantics { contentDescription = description }
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

        val breathFraction = if (isBreathing) breathFractionProvider().coerceIn(0f, 1f) else 0.5f

        val gazeX: Float
        val gazeY: Float
        val leftH: Float
        val rightH: Float
        val widthScale: Float
        val cornerFactor: Float
        val leftTilt: Float
        val rightTilt: Float
        if (isBreathing) {
            gazeX = 0f
            gazeY = 0.20f - 0.38f * breathFraction
            leftH = 0.28f + 0.78f * breathFraction
            rightH = leftH
            widthScale = 1.20f - 0.18f * breathFraction
            cornerFactor = 0.48f - 0.08f * breathFraction
            leftTilt = 0f
            rightTilt = 0f
        } else {
            gazeX = gazeXState.value
            gazeY = gazeYState.value
            leftH = leftHState.value
            rightH = rightHState.value
            widthScale = widthScaleState.value
            cornerFactor = cornerFactorState.value
            leftTilt = leftTiltState.value
            rightTilt = rightTiltState.value
        }

        val bodyColor = bodyColorState.value
        val haloScale = if (!animationsEnabled) {
            1f
        } else if (isBreathing) {
            0.90f + 0.22f * breathFraction
        } else {
            1.0f + 0.03f * abs(gazeY)
        }

        // 穏やかな外周オーラ
        drawCircle(
            color = bodyColor.copy(alpha = 0.20f),
            radius = w * 0.46f * haloScale,
            center = Offset(cx, cy)
        )

        // シンプルな球体シルエット
        val bodyRadius = w * 0.36f *
            (if (isBreathing && animationsEnabled) (0.93f + 0.11f * breathFraction) else 1f)

        if (showElevatedRing) {
            drawCircle(
                color = bodyColor.copy(alpha = 0.55f),
                radius = bodyRadius * 1.15f,
                center = Offset(cx, cy),
                style = Stroke(width = w * 0.024f)
            )
        }

        drawCircle(
            color = bodyColor,
            radius = bodyRadius,
            center = Offset(cx, cy)
        )

        // 立体球面上の目の配置と透視縮小
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
            val lh = (baseEyeH * leftH * leftScale).coerceAtLeast(w * 0.028f)
            val lCorner = minOf(lw, lh) * cornerFactor
            rotate(degrees = leftTilt, pivot = leftCenter) {
                drawRoundRect(
                    color = eyeColor,
                    topLeft = Offset(leftCenter.x - lw / 2f, leftCenter.y - lh / 2f),
                    size = Size(lw, lh),
                    cornerRadius = CornerRadius(lCorner, lCorner)
                )
            }

            val rw = baseEyeW * rightPerspX * rightScale
            val rh = (baseEyeH * rightH * rightScale).coerceAtLeast(w * 0.028f)
            val rCorner = minOf(rw, rh) * cornerFactor
            rotate(degrees = rightTilt, pivot = rightCenter) {
                drawRoundRect(
                    color = eyeColor,
                    topLeft = Offset(rightCenter.x - rw / 2f, rightCenter.y - rh / 2f),
                    size = Size(rw, rh),
                    cornerRadius = CornerRadius(rCorner, rCorner)
                )
            }
        }
    }
}

/**
 * 既存の [OmamoriState] ベース呼び出し元（オンボーディング等）向けの後方互換オーバーロード。
 */
@Composable
fun OmamoriPetMobileAvatar(
    state: OmamoriState,
    description: String? = null,
    breathFractionProvider: () -> Float = { 0.5f },
    interactive: Boolean = true,
    size: Dp = 112.dp,
    modifier: Modifier = Modifier,
    petHeartRateColor: PetHeartRateColor? = null,
    isDataMissing: Boolean = (state == OmamoriState.RESTING),
    feeling: Feeling? = null
) {
    OmamoriPetMobileAvatar(
        visualState = resolvePetVisualState(
            state = state,
            petHeartRateColor = petHeartRateColor,
            isDataMissing = isDataMissing
        ),
        feeling = feeling,
        description = description,
        breathFractionProvider = breathFractionProvider,
        interactive = interactive,
        size = size,
        modifier = modifier
    )
}

private fun staticEyePose(visualState: PetVisualState, feeling: Feeling?): EyePose {
    if (feeling != null) {
        return when (feeling) {
            Feeling.ANXIOUS -> EyePose(0f, 0.04f, 0.84f, 0.84f, 1.06f, 0.44f, leftTiltDeg = -14f, rightTiltDeg = 14f)
            Feeling.LOW -> EyePose(0f, 0.14f, 0.42f, 0.42f, 1.18f, 0.48f, leftTiltDeg = -8f, rightTiltDeg = 8f)
            Feeling.IRRITATED -> EyePose(0f, -0.02f, 0.62f, 0.62f, 1.12f, 0.42f, leftTiltDeg = 18f, rightTiltDeg = -18f)
        }
    }
    return when (visualState.mode) {
        PetExpressionMode.CALM -> EyePose(0.32f, -0.22f, 1f, 1f, 1f, 0.42f)
        PetExpressionMode.BREATHING -> EyePose(0f, 0.05f, 0.35f, 0.35f, 1.15f, 0.5f)
        PetExpressionMode.SLEEPING -> EyePose(0f, 0.18f, 0.15f, 0.15f, 1.28f, 0.5f)
        PetExpressionMode.ELEVATED -> when (visualState.tone) {
            PetBodyTone.YELLOW -> EyePose(0f, 0.02f, 0.88f, 0.88f, 1.06f, 0.44f, leftTiltDeg = -10f, rightTiltDeg = 10f)
            PetBodyTone.RED -> EyePose(0f, 0.02f, 0.50f, 0.50f, 1.16f, 0.46f, leftTiltDeg = 14f, rightTiltDeg = -14f)
            PetBodyTone.PURPLE -> EyePose(0f, 0.10f, 0.24f, 0.24f, 1.24f, 0.50f)
            PetBodyTone.GREEN, PetBodyTone.DATA_MISSING -> EyePose(0f, -0.02f, 0.96f, 0.96f, 1.04f, 0.44f)
        }
    }
}

private fun feelingEyeScript(feeling: Feeling): List<Pair<EyePose, Long>> = when (feeling) {
    Feeling.ANXIOUS -> listOf(
        EyePose(0.00f, 0.04f, 0.86f, 0.86f, 1.06f, 0.44f, leftTiltDeg = -14f, rightTiltDeg = 14f) to 1800L,
        EyePose(0.00f, 0.04f, 0.14f, 0.14f, 1.10f, 0.50f, leftTiltDeg = -14f, rightTiltDeg = 14f) to 140L,
        EyePose(-0.08f, 0.06f, 0.82f, 0.82f, 1.06f, 0.44f, leftTiltDeg = -16f, rightTiltDeg = 16f) to 1500L,
        EyePose(0.08f, 0.06f, 0.82f, 0.82f, 1.06f, 0.44f, leftTiltDeg = -16f, rightTiltDeg = 16f) to 1500L
    )
    Feeling.LOW -> listOf(
        EyePose(0.00f, 0.14f, 0.42f, 0.42f, 1.18f, 0.48f, leftTiltDeg = -8f, rightTiltDeg = 8f) to 2400L,
        EyePose(0.00f, 0.16f, 0.14f, 0.14f, 1.22f, 0.50f, leftTiltDeg = -8f, rightTiltDeg = 8f) to 220L,
        EyePose(0.00f, 0.16f, 0.38f, 0.38f, 1.20f, 0.48f, leftTiltDeg = -10f, rightTiltDeg = 10f) to 2200L
    )
    Feeling.IRRITATED -> listOf(
        EyePose(0.00f, -0.02f, 0.62f, 0.62f, 1.12f, 0.42f, leftTiltDeg = 18f, rightTiltDeg = -18f) to 1900L,
        EyePose(0.00f, -0.02f, 0.14f, 0.14f, 1.14f, 0.50f, leftTiltDeg = 18f, rightTiltDeg = -18f) to 140L,
        EyePose(0.06f, -0.02f, 0.58f, 0.58f, 1.12f, 0.42f, leftTiltDeg = 20f, rightTiltDeg = -20f) to 1600L,
        EyePose(-0.06f, -0.02f, 0.58f, 0.58f, 1.12f, 0.42f, leftTiltDeg = 20f, rightTiltDeg = -20f) to 1600L
    )
}

private fun elevatedEyeScript(tone: PetBodyTone): List<Pair<EyePose, Long>> = when (tone) {
    PetBodyTone.YELLOW -> listOf(
        EyePose(0.00f, 0.02f, 0.88f, 0.88f, 1.06f, 0.44f, leftTiltDeg = -10f, rightTiltDeg = 10f) to 1900L,
        EyePose(0.00f, 0.02f, 0.14f, 0.14f, 1.10f, 0.50f, leftTiltDeg = -10f, rightTiltDeg = 10f) to 150L,
        EyePose(0.06f, 0.04f, 0.84f, 0.84f, 1.06f, 0.44f, leftTiltDeg = -12f, rightTiltDeg = 12f) to 1700L,
        EyePose(-0.06f, 0.04f, 0.84f, 0.84f, 1.06f, 0.44f, leftTiltDeg = -12f, rightTiltDeg = 12f) to 1700L
    )
    PetBodyTone.RED -> listOf(
        EyePose(0.00f, 0.02f, 0.50f, 0.50f, 1.16f, 0.46f, leftTiltDeg = 14f, rightTiltDeg = -14f) to 1800L,
        EyePose(0.00f, 0.02f, 0.14f, 0.14f, 1.18f, 0.50f, leftTiltDeg = 14f, rightTiltDeg = -14f) to 150L,
        EyePose(0.00f, 0.04f, 0.46f, 0.46f, 1.18f, 0.46f, leftTiltDeg = 16f, rightTiltDeg = -16f) to 1800L
    )
    PetBodyTone.PURPLE -> listOf(
        EyePose(0.00f, 0.10f, 0.24f, 0.24f, 1.24f, 0.50f) to 2400L,
        EyePose(0.00f, 0.12f, 0.18f, 0.18f, 1.26f, 0.50f) to 2200L
    )
    PetBodyTone.GREEN, PetBodyTone.DATA_MISSING -> listOf(
        EyePose(0.00f, -0.04f, 0.96f, 0.96f, 1.04f, 0.44f) to 1800L,
        EyePose(0.00f, -0.04f, 0.14f, 0.14f, 1.08f, 0.50f) to 150L,
        EyePose(0.08f, -0.02f, 0.94f, 0.94f, 1.04f, 0.44f) to 1600L,
        EyePose(-0.08f, -0.02f, 0.94f, 0.94f, 1.04f, 0.44f) to 1600L,
        EyePose(0.00f, 0.00f, 0.96f, 0.96f, 1.04f, 0.44f) to 1500L
    )
}

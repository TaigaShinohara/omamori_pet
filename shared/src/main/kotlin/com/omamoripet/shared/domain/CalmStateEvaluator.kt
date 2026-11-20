package com.omamoripet.shared.domain

import com.omamoripet.shared.model.AlertPreferences
import com.omamoripet.shared.model.AlertSuppressionReason
import com.omamoripet.shared.model.BiometricSnapshot
import com.omamoripet.shared.model.CalmEvaluation
import com.omamoripet.shared.model.MotionState
import com.omamoripet.shared.model.OmamoriState
import com.omamoripet.shared.model.PetHeartRateColor
import kotlin.math.roundToInt

/**
 * 実測値のみ（現在心拍・安静時ベースライン・静止判定・装着状態）から
 * お守りペットの状態と「平常時なのに心拍が高い」通知可否を決定論的に評価する。
 */
object CalmStateEvaluator {

    fun evaluate(
        snapshot: BiometricSnapshot,
        isBreathingActive: Boolean,
        lastAlertEpochMs: Long,
        currentHourOfDay: Int,
        preferences: AlertPreferences
    ): CalmEvaluation {
        if (!snapshot.isWatchOnBody || snapshot.motionState == MotionState.OFF_BODY || snapshot.currentHeartRateBpm == null) {
            return CalmEvaluation(
                state = OmamoriState.RESTING,
                calmScore = 0,
                deltaFromRestingBpm = null,
                shouldTriggerAlert = false,
                suppressionReason = AlertSuppressionReason.NOT_ON_BODY
            )
        }

        val currentBpm = snapshot.currentHeartRateBpm
        val delta = currentBpm - snapshot.restingBaselineBpm
        val calmScore = computeCalmScore(delta = delta, motionState = snapshot.motionState, isBreathingActive = isBreathingActive)

        if (isBreathingActive) {
            return CalmEvaluation(
                state = OmamoriState.BREATHING,
                calmScore = calmScore,
                deltaFromRestingBpm = delta,
                shouldTriggerAlert = false,
                suppressionReason = AlertSuppressionReason.ALREADY_BREATHING
            )
        }

        if (snapshot.motionState != MotionState.STILL_CONFIRMED) {
            return CalmEvaluation(
                state = OmamoriState.CALM,
                calmScore = calmScore,
                deltaFromRestingBpm = delta,
                shouldTriggerAlert = false,
                suppressionReason = AlertSuppressionReason.RECENT_MOVEMENT
            )
        }

        if (delta < preferences.elevationThresholdBpm) {
            return CalmEvaluation(
                state = OmamoriState.CALM,
                calmScore = calmScore,
                deltaFromRestingBpm = delta,
                shouldTriggerAlert = false,
                suppressionReason = AlertSuppressionReason.BELOW_THRESHOLD
            )
        }

        if (snapshot.sustainedElevationSeconds < preferences.requiredSustainedSeconds) {
            return CalmEvaluation(
                state = OmamoriState.CALM,
                calmScore = calmScore,
                deltaFromRestingBpm = delta,
                shouldTriggerAlert = false,
                suppressionReason = AlertSuppressionReason.DURATION_TOO_SHORT
            )
        }

        val suppression = resolveAlertSuppression(
            nowEpochMs = snapshot.sampledAtEpochMs,
            lastAlertEpochMs = lastAlertEpochMs,
            currentHourOfDay = currentHourOfDay,
            preferences = preferences
        )

        // 静止と持続時間を確認したあと、通知のしきい値を超えた幅でペットの色を選ぶ。
        val bpmAboveThreshold = delta - preferences.elevationThresholdBpm
        val petHeartRateColor = when {
            bpmAboveThreshold >= 25 -> PetHeartRateColor.PURPLE
            bpmAboveThreshold >= 15 -> PetHeartRateColor.RED
            bpmAboveThreshold >= 5 -> PetHeartRateColor.YELLOW
            else -> null
        }
        return CalmEvaluation(
            state = OmamoriState.ELEVATED_AT_REST,
            calmScore = calmScore,
            deltaFromRestingBpm = delta,
            shouldTriggerAlert = suppression == AlertSuppressionReason.NONE,
            suppressionReason = suppression,
            petHeartRateColor = petHeartRateColor
        )
    }

    private fun resolveAlertSuppression(
        nowEpochMs: Long,
        lastAlertEpochMs: Long,
        currentHourOfDay: Int,
        preferences: AlertPreferences
    ): AlertSuppressionReason {
        if (!preferences.alertsEnabled) {
            return AlertSuppressionReason.ALERTS_DISABLED
        }
        if (nowEpochMs < preferences.snoozedUntilEpochMs) {
            return AlertSuppressionReason.SNOOZED
        }
        if (preferences.quietHoursEnabled && isWithinQuietHours(currentHourOfDay, preferences.quietHoursStartHour, preferences.quietHoursEndHour)) {
            return AlertSuppressionReason.QUIET_HOURS
        }
        val cooldownMs = preferences.cooldownMinutes * 60_000L
        if (lastAlertEpochMs > 0L && (nowEpochMs - lastAlertEpochMs) < cooldownMs) {
            return AlertSuppressionReason.IN_COOLDOWN
        }
        return AlertSuppressionReason.NONE
    }

    internal fun isWithinQuietHours(hour: Int, startHour: Int, endHour: Int): Boolean {
        val normalized = ((hour % 24) + 24) % 24
        return if (startHour == endHour) {
            false
        } else if (startHour < endHour) {
            normalized in startHour until endHour
        } else {
            normalized >= startHour || normalized < endHour
        }
    }

    private fun computeCalmScore(
        delta: Int,
        motionState: MotionState,
        isBreathingActive: Boolean
    ): Int {
        // 歩行・活動中の心拍上昇は自然な反応なのでスコアを大きく落とさない
        val effectiveDelta = when {
            isBreathingActive -> (delta * 0.5f).roundToInt().coerceAtLeast(0)
            motionState == MotionState.RECENT_MOVEMENT -> (delta * 0.35f).roundToInt().coerceAtLeast(0)
            else -> delta.coerceAtLeast(0)
        }
        return (100 - effectiveDelta * 2).coerceIn(25, 100)
    }
}

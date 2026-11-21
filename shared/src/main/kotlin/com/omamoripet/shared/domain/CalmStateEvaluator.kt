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
    const val PET_ELEVATION_THRESHOLD_BPM = 7
    private const val PET_YELLOW_THRESHOLD_BPM = 10
    private const val PET_ELEVATION_REQUIRED_SECONDS = 30

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

        val alertElevationReached = delta >= preferences.elevationThresholdBpm &&
            snapshot.sustainedElevationSeconds >= preferences.requiredSustainedSeconds
        val petElevationReached = delta >= PET_ELEVATION_THRESHOLD_BPM &&
            snapshot.sustainedPetElevationSeconds >= PET_ELEVATION_REQUIRED_SECONDS
        val suppression = when {
            delta < preferences.elevationThresholdBpm -> AlertSuppressionReason.BELOW_THRESHOLD
            !alertElevationReached -> AlertSuppressionReason.DURATION_TOO_SHORT
            else -> resolveAlertSuppression(
                nowEpochMs = snapshot.sampledAtEpochMs,
                lastAlertEpochMs = lastAlertEpochMs,
                currentHourOfDay = currentHourOfDay,
                preferences = preferences
            )
        }

        // 小さな心拍差はペットに早めに表示し、赤・紫と通知には従来の判定を使う。
        val bpmAboveThreshold = delta - preferences.elevationThresholdBpm
        val petHeartRateColor = when {
            alertElevationReached && bpmAboveThreshold >= 25 -> PetHeartRateColor.PURPLE
            alertElevationReached && bpmAboveThreshold >= 15 -> PetHeartRateColor.RED
            petElevationReached && delta >= PET_YELLOW_THRESHOLD_BPM -> PetHeartRateColor.YELLOW
            alertElevationReached && bpmAboveThreshold >= 5 -> PetHeartRateColor.YELLOW
            else -> null
        }
        return CalmEvaluation(
            state = if (petElevationReached || alertElevationReached) OmamoriState.ELEVATED_AT_REST else OmamoriState.CALM,
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

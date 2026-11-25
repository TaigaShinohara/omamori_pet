package com.omamoripet.shared.domain

import com.omamoripet.shared.model.BaselineSource
import com.omamoripet.shared.model.MotionState

class SustainedElevationTracker {
    private var startedAtMs: Long? = null

    fun record(aboveThreshold: Boolean, nowMs: Long): Int {
        if (!aboveThreshold) {
            reset()
            return 0
        }
        val start = startedAtMs ?: nowMs.also { startedAtMs = it }
        return ((nowMs - start) / 1_000L).toInt()
    }

    fun reset() {
        startedAtMs = null
    }
}

/**
 * 手首の加速度分散と歩数増分から「直近 N 秒間（デフォルト180秒）連続して動いていない平常時」かを追跡する。
 * 階段を登った直後に立ち止まっても、数分間は心拍が高いまま残るため誤検知を防ぐ。
 */
class MotionStillnessTracker(
    private val requiredStillMs: Long = 180_000L,
    private val accelVarianceThreshold: Double = 0.35
) {
    private var lastMovementEpochMs: Long? = null
    private var firstSampleEpochMs: Long? = null
    private var lastKnownSteps: Long? = null

    fun recordSample(
        epochMs: Long,
        accelMagnitudeVariance: Double,
        cumulativeSteps: Long? = null
    ): MotionState {
        if (firstSampleEpochMs == null) {
            firstSampleEpochMs = epochMs
        }

        val stepsIncreased = if (cumulativeSteps != null) {
            val previous = lastKnownSteps
            lastKnownSteps = cumulativeSteps
            previous != null && cumulativeSteps > previous
        } else {
            false
        }

        if (accelMagnitudeVariance > accelVarianceThreshold || stepsIncreased) {
            lastMovementEpochMs = epochMs
            return MotionState.RECENT_MOVEMENT
        }

        val anchor = lastMovementEpochMs ?: firstSampleEpochMs ?: epochMs
        return if ((epochMs - anchor) >= requiredStillMs) {
            MotionState.STILL_CONFIRMED
        } else {
            MotionState.RECENT_MOVEMENT
        }
    }

    fun reset() {
        lastMovementEpochMs = null
        firstSampleEpochMs = null
        lastKnownSteps = null
    }
}

data class ResolvedBaseline(
    val bpm: Int,
    val source: BaselineSource
)

/**
 * Watch 単体で静止時心拍サンプルから安静時ベースラインを学習し、
 * Health Connect の安静時心拍レコードがあればそれと安全に統合する。
 */
class RestingBaselineLearner(
    private val defaultFallbackBpm: Int = 65,
    private val maxSamples: Int = 60,
    private val minSamplesForLearned: Int = 5
) {
    private val stillHrSamples = ArrayDeque<Int>()
    private var healthConnectBaselineBpm: Int? = null
    private var healthConnectMeasuredAtMs: Long? = null
    private var cachedLearnedBaseline: ResolvedBaseline? = null

    fun loadInitial(samples: List<Int>, healthConnectBpm: Int?, measuredAtEpochMs: Long? = null) {
        stillHrSamples.clear()
        samples.takeLast(maxSamples).forEach { bpm ->
            if (bpm in 38..140) stillHrSamples.addLast(bpm)
        }
        cachedLearnedBaseline = null
        updateHealthConnectBaseline(healthConnectBpm, measuredAtEpochMs)
    }

    fun recordStillSample(bpm: Int) {
        if (bpm !in 38..140) return
        if (stillHrSamples.size >= maxSamples) {
            stillHrSamples.removeFirst()
        }
        stillHrSamples.addLast(bpm)
        cachedLearnedBaseline = null
    }

    fun updateHealthConnectBaseline(bpm: Int?, measuredAtEpochMs: Long? = System.currentTimeMillis()) {
        healthConnectBaselineBpm = bpm?.takeIf { it in 38..120 }
        healthConnectMeasuredAtMs = measuredAtEpochMs.takeIf { healthConnectBaselineBpm != null }
    }

    fun getCurrentSamples(): List<Int> = stillHrSamples.toList()

    fun resolveBaseline(nowEpochMs: Long = System.currentTimeMillis()): ResolvedBaseline {
        val hc = healthConnectBaselineBpm?.takeIf {
            MeasurementFreshness.isFresh(healthConnectMeasuredAtMs, nowEpochMs, MeasurementFreshness.HEALTH_CONNECT_MAX_AGE_MS)
        }
        if (hc != null) {
            return ResolvedBaseline(bpm = hc, source = BaselineSource.HEALTH_CONNECT)
        }
        cachedLearnedBaseline?.let { return it }
        val resolved = if (stillHrSamples.size >= minSamplesForLearned) {
            val sorted = stillHrSamples.sorted()
            // ストレス時の高い値を除いた 30パーセンタイル付近を個人の安静時目安とする
            val index = ((sorted.size - 1) * 0.30).toInt().coerceIn(0, sorted.lastIndex)
            ResolvedBaseline(bpm = sorted[index], source = BaselineSource.WATCH_LEARNED)
        } else {
            ResolvedBaseline(bpm = defaultFallbackBpm, source = BaselineSource.DEFAULT_FALLBACK)
        }
        cachedLearnedBaseline = resolved
        return resolved
    }
}

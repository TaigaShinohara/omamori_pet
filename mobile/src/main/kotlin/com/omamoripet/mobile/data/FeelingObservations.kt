package com.omamoripet.mobile.data

import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.serialization.Serializable

@Serializable
data class FeelingComparison(
    val value: Int,
    val typical: Int? = null,
    val comparisonDays: Int = 0,
    val sampleCount: Int? = null
)

@Serializable
data class FeelingSleep(
    val duration: FeelingComparison,
    val wokeAtEpochMs: Long
)

@Serializable
data class FeelingHealth(
    val sleep: FeelingSleep? = null,
    val steps5: FeelingComparison? = null,
    val steps30: FeelingComparison? = null,
    val heart5: FeelingComparison? = null,
    val heart30: FeelingComparison? = null,
    val periodDay: Int? = null
) {
    internal fun withUpdates(updates: FeelingHealth) = FeelingHealth(
        sleep = updates.sleep?.let {
            it.copy(duration = sleep?.duration.withUpdates(it.duration) ?: it.duration)
        } ?: sleep,
        steps5 = steps5.withUpdates(updates.steps5),
        steps30 = steps30.withUpdates(updates.steps30),
        heart5 = heart5.withUpdates(updates.heart5),
        heart30 = heart30.withUpdates(updates.heart30),
        periodDay = updates.periodDay ?: periodDay
    )
}

private fun FeelingComparison?.withUpdates(update: FeelingComparison?): FeelingComparison? {
    if (update == null) return this
    if (this?.typical != null && update.comparisonDays < comparisonDays) {
        return update.copy(typical = typical, comparisonDays = comparisonDays)
    }
    return update
}

@Serializable
data class FeelingPressure(val hPa: Double, val observedAtEpochMs: Long)

internal data class PendingFeelingActivity(val steps: Boolean = false, val heart: Boolean = false) {
    val any: Boolean get() = steps || heart
}

internal fun Instant.canRefreshFeelingHealth(now: Instant): Boolean =
    this in now.minus(14, ChronoUnit.DAYS)..now

internal fun MobileUiState.pendingFeelingActivity(
    record: FeelingRecord,
    now: Instant = Instant.now()
): PendingFeelingActivity {
    if (!Instant.ofEpochMilli(record.recordedAtEpochMs).canRefreshFeelingHealth(now)) {
        return PendingFeelingActivity()
    }
    return PendingFeelingActivity(
        steps = hasFeelingStepsPermission && record.health?.steps5 == null,
        heart = hasFeelingHeartPermission && record.health?.heart5 == null
    )
}

// USNO's mean lunation and the new moon on 2000-01-06. An approximate, offline value.
internal fun approximateMoonAge(recordedAtEpochMs: Long): Double {
    val referenceNewMoon = Instant.parse("2000-01-06T18:14:00Z").toEpochMilli()
    val synodicDays = 29.530588853
    val elapsedDays = (recordedAtEpochMs - referenceNewMoon).toDouble() / 86_400_000L
    return ((elapsedDays % synodicDays) + synodicDays) % synodicDays
}

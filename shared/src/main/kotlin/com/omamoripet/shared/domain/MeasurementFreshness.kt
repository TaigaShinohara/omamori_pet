package com.omamoripet.shared.domain

object MeasurementFreshness {
    const val HEART_RATE_MAX_AGE_MS = 30_000L
    const val WATCH_STATE_MAX_AGE_MS = 90_000L
    const val HEALTH_CONNECT_MAX_AGE_MS = 14 * 24 * 60 * 60_000L

    fun isFresh(timestampMs: Long?, nowMs: Long, maxAgeMs: Long): Boolean =
        timestampMs != null && timestampMs >= 0L && (nowMs - timestampMs) in 0L..maxAgeMs
}

data class HeartRateReading(val bpm: Int, val measuredAtElapsedMs: Long)

/** Uses sensor timestamps, so a delayed batch or a repeated tick cannot renew a reading. */
class FreshHeartRateTracker {
    private var latest: HeartRateReading? = null
    private var lastLearnedAtElapsedMs: Long? = null

    @Synchronized
    fun record(bpm: Int, measuredAtElapsedMs: Long) {
        if (bpm !in 35..220 || measuredAtElapsedMs < 0L) return
        if (latest?.let { measuredAtElapsedMs <= it.measuredAtElapsedMs } == true) return
        latest = HeartRateReading(bpm, measuredAtElapsedMs)
    }

    @Synchronized
    fun current(nowElapsedMs: Long): HeartRateReading? = latest?.takeIf {
        MeasurementFreshness.isFresh(it.measuredAtElapsedMs, nowElapsedMs, MeasurementFreshness.HEART_RATE_MAX_AGE_MS)
    }

    @Synchronized
    fun takeForLearning(nowElapsedMs: Long): HeartRateReading? {
        val reading = current(nowElapsedMs) ?: return null
        if (reading.measuredAtElapsedMs == lastLearnedAtElapsedMs) return null
        lastLearnedAtElapsedMs = reading.measuredAtElapsedMs
        return reading
    }

    @Synchronized
    fun clear() {
        latest = null
    }
}

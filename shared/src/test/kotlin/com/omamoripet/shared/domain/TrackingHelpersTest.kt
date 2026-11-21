package com.omamoripet.shared.domain

import com.omamoripet.shared.model.BaselineSource
import com.omamoripet.shared.model.MotionState
import com.omamoripet.shared.model.OmamoriState
import com.omamoripet.shared.sync.OmamoriJson
import com.omamoripet.shared.sync.WatchLivePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TrackingHelpersTest {
    @Test
    fun `elevation timers reset independently after a drop or signal loss`() {
        val pet = SustainedElevationTracker()
        val alert = SustainedElevationTracker()
        assertEquals(0, pet.record(true, 1000L))
        assertEquals(0, alert.record(false, 1000L))
        assertEquals(30, pet.record(true, 31_000L))
        assertEquals(0, alert.record(true, 31_000L))
        assertEquals(0, pet.record(false, 40_000L))
        assertEquals(0, pet.record(true, 41_000L))
        assertEquals(10, alert.record(true, 41_000L))
        alert.reset()
        assertEquals(0, alert.record(true, 60_000L))
    }


    @Test
    fun `stillness tracker requires full quiet window after movement`() {
        val tracker = MotionStillnessTracker(requiredStillMs = 180_000L, accelVarianceThreshold = 0.35)
        assertEquals(MotionState.RECENT_MOVEMENT, tracker.recordSample(0L, 0.10, 100L))
        assertEquals(MotionState.RECENT_MOVEMENT, tracker.recordSample(179_000L, 0.10, 100L))
        assertEquals(MotionState.STILL_CONFIRMED, tracker.recordSample(180_000L, 0.10, 100L))

        // Step increase resets the quiet window
        assertEquals(MotionState.RECENT_MOVEMENT, tracker.recordSample(200_000L, 0.10, 102L))
        assertEquals(MotionState.RECENT_MOVEMENT, tracker.recordSample(300_000L, 0.10, 102L))
        assertEquals(MotionState.STILL_CONFIRMED, tracker.recordSample(380_000L, 0.10, 102L))
    }

    @Test
    fun `baseline learner prefers Health Connect when available and falls back to watch samples`() {
        val learner = RestingBaselineLearner(defaultFallbackBpm = 65, minSamplesForLearned = 5)
        assertEquals(BaselineSource.DEFAULT_FALLBACK, learner.resolveBaseline().source)

        listOf(62, 64, 60, 68, 70).forEach { learner.recordStillSample(it) }
        val learned = learner.resolveBaseline()
        assertEquals(BaselineSource.WATCH_LEARNED, learned.source)
        assertEquals(62, learned.bpm)

        learner.updateHealthConnectBaseline(58)
        val hc = learner.resolveBaseline()
        assertEquals(BaselineSource.HEALTH_CONNECT, hc.source)
        assertEquals(58, hc.bpm)
    }

    @Test
    fun `data layer payload round trips cleanly through json`() {
        val payload = WatchLivePayload(
            currentHeartRateBpm = 74,
            restingBaselineBpm = 62,
            baselineSource = BaselineSource.WATCH_LEARNED,
            motionState = MotionState.STILL_CONFIRMED,
            omamoriState = OmamoriState.CALM,
            calmScore = 88,
            isWatchOnBody = true,
            isSimulated = false,
            updatedAtEpochMs = 123456789L
        )
        val encoded = OmamoriJson.encode(payload)
        val decoded = OmamoriJson.decodeOrNull<WatchLivePayload>(encoded)
        assertNotNull(decoded)
        assertEquals(payload, decoded)
    }
}

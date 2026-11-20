package com.omamoripet.shared.domain

import com.omamoripet.shared.model.BaselineSource
import org.junit.Assert.*
import org.junit.Test

class MeasurementFreshnessTest {
    @Test
    fun `missing heart rate expires without another callback`() {
        val tracker = FreshHeartRateTracker()
        tracker.record(100, 1_000L)
        assertEquals(100, tracker.current(1_000L)?.bpm)
        assertNotNull(tracker.current(31_000L))
        assertNull(tracker.current(31_001L))
        assertNull(tracker.current(91_000L))
    }

    @Test
    fun `repeated ticks cannot repeatedly teach the same reading`() {
        val tracker = FreshHeartRateTracker()
        tracker.record(60, 1_000L)
        assertNotNull(tracker.takeForLearning(1_001L))
        assertNull(tracker.takeForLearning(6_000L))
        tracker.record(60, 1_000L)
        assertNull(tracker.takeForLearning(7_000L))
        tracker.record(61, 8_000L)
        assertEquals(61, tracker.takeForLearning(8_001L)?.bpm)
    }

    @Test
    fun `late batches retain original age and cannot replace a newer sample`() {
        val tracker = FreshHeartRateTracker()
        tracker.record(95, 1_000L)
        assertNull(tracker.current(40_000L))
        tracker.record(65, 40_000L)
        tracker.record(95, 2_000L)
        assertEquals(65, tracker.current(40_001L)?.bpm)
        tracker.clear()
        assertNull(tracker.current(40_002L))
    }

    @Test
    fun `watch data and health baselines reject missing expired and future timestamps`() {
        assertFalse(MeasurementFreshness.isFresh(null, 100_000L, 90_000L))
        assertFalse(MeasurementFreshness.isFresh(100_001L, 100_000L, 90_000L))
        assertTrue(MeasurementFreshness.isFresh(10_000L, 100_000L, 90_000L))
        assertFalse(MeasurementFreshness.isFresh(9_999L, 100_000L, 90_000L))
    }

    @Test
    fun `revoked expired and undated health baselines fall back to watch learning`() {
        val learner = RestingBaselineLearner()
        val samples = listOf(60, 61, 62, 63, 64)
        learner.loadInitial(samples, 50, 1_000L)
        assertEquals(BaselineSource.HEALTH_CONNECT, learner.resolveBaseline(1_000L).source)
        assertEquals(BaselineSource.WATCH_LEARNED,
            learner.resolveBaseline(1_001L + MeasurementFreshness.HEALTH_CONNECT_MAX_AGE_MS).source)
        learner.updateHealthConnectBaseline(null)
        assertEquals(61, learner.resolveBaseline().bpm)
        learner.loadInitial(samples, 50)
        assertEquals(BaselineSource.WATCH_LEARNED, learner.resolveBaseline().source)
    }
}

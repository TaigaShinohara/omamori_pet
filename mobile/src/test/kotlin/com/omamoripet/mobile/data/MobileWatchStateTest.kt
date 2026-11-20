package com.omamoripet.mobile.data

import com.omamoripet.shared.model.MotionState
import com.omamoripet.shared.model.OmamoriState
import org.junit.Assert.*
import org.junit.Test

class MobileWatchStateTest {
    @Test
    fun `disconnect and expiry remove all live measurements`() {
        for (state in listOf(OmamoriState.ELEVATED_AT_REST, OmamoriState.BREATHING)) {
            val expired = MobileUiState(
                omamoriState = state, currentHeartRateBpm = 100,
                motionState = MotionState.STILL_CONFIRMED, calmScore = 30, lastWatchSyncMs = 1_000L
            ).withoutWatchMeasurements()
            assertEquals(OmamoriState.RESTING, expired.omamoriState)
            assertNull(expired.currentHeartRateBpm)
            assertNull(expired.lastWatchSyncMs)
            assertEquals(MotionState.OFF_BODY, expired.motionState)
            assertEquals(0, expired.calmScore)
        }
    }

    @Test
    fun `losing the watch connection preserves an explicit phone demo`() {
        val demo = MobileUiState(demoMode = MobileDemoMode.DEMO_CALM, isSimulated = true, currentHeartRateBpm = 66)
        assertEquals(demo, demo.withoutWatchMeasurements())
    }
}

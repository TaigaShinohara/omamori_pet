package com.omamoripet.mobile.data

import com.omamoripet.shared.model.BaselineSource
import com.omamoripet.shared.model.MotionState
import com.omamoripet.shared.model.OmamoriState
import com.omamoripet.shared.model.PetHeartRateColor
import com.omamoripet.shared.sync.WatchLivePayload
import org.junit.Assert.*
import org.junit.Test

class MobileWatchStateTest {
    @Test
    fun `watch color follows the same baseline and state shown on the phone`() {
        val payload = WatchLivePayload(
            currentHeartRateBpm = 100,
            restingBaselineBpm = 65,
            baselineSource = BaselineSource.WATCH_LEARNED,
            motionState = MotionState.STILL_CONFIRMED,
            omamoriState = OmamoriState.ELEVATED_AT_REST,
            calmScore = 30,
            isWatchOnBody = true,
            isSimulated = false,
            updatedAtEpochMs = 1_000L,
            petHeartRateColor = PetHeartRateColor.RED
        )
        assertEquals(PetHeartRateColor.RED, MobileUiState().withWatchMeasurements(payload).petHeartRateColor)
        assertNull(MobileUiState(healthConnectRestingBpm = 75).withWatchMeasurements(payload).petHeartRateColor)
        assertNull(MobileUiState().withWatchMeasurements(payload.copy(omamoriState = OmamoriState.BREATHING)).petHeartRateColor)
    }

    @Test
    fun `disconnect and expiry remove all live measurements`() {
        for (state in listOf(OmamoriState.ELEVATED_AT_REST, OmamoriState.BREATHING)) {
            val expired = MobileUiState(
                omamoriState = state, currentHeartRateBpm = 100,
                motionState = MotionState.STILL_CONFIRMED, calmScore = 30, lastWatchSyncMs = 1_000L,
                petHeartRateColor = PetHeartRateColor.RED
            ).withoutWatchMeasurements()
            assertEquals(OmamoriState.RESTING, expired.omamoriState)
            assertNull(expired.currentHeartRateBpm)
            assertNull(expired.lastWatchSyncMs)
            assertNull(expired.petHeartRateColor)
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

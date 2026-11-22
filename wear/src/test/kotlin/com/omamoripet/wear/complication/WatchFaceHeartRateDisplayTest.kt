package com.omamoripet.wear.complication

import com.omamoripet.shared.model.MotionState
import com.omamoripet.shared.model.OmamoriState
import com.omamoripet.wear.R
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchFaceHeartRateDisplayTest {
    @Test
    fun `cached off body and missing measurements never display a live value or movement`() {
        for (motion in MotionState.entries) {
            for (bpm in listOf(null, 0, -1)) {
                assertEquals(WatchFaceHeartRateDisplay("--", R.string.comp_context_waiting),
                    resolveWatchFaceHeartRateDisplay(bpm, motion, OmamoriState.CALM))
            }
        }
        assertEquals(WatchFaceHeartRateDisplay("--", R.string.comp_context_waiting),
            resolveWatchFaceHeartRateDisplay(72, MotionState.OFF_BODY, OmamoriState.CALM))
    }

    @Test
    fun `movement context takes priority over a cached elevated or breathing state`() {
        for (state in OmamoriState.entries) {
            assertEquals(WatchFaceHeartRateDisplay("123", R.string.comp_context_moving),
                resolveWatchFaceHeartRateDisplay(123, MotionState.RECENT_MOVEMENT, state))
        }
    }

    @Test
    fun `stationary readings separate numeric value from breathing and resting context`() {
        val expectations = mapOf(OmamoriState.CALM to R.string.comp_context_calm,
            OmamoriState.ELEVATED_AT_REST to R.string.comp_context_still,
            OmamoriState.BREATHING to R.string.comp_context_breathing)
        for ((state, label) in expectations) {
            assertEquals(WatchFaceHeartRateDisplay("68", label),
                resolveWatchFaceHeartRateDisplay(68, MotionState.STILL_CONFIRMED, state))
        }
    }
}

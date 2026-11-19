package com.omamoripet.wear.data

import com.omamoripet.shared.model.MotionState
import com.omamoripet.shared.model.OmamoriState
import com.omamoripet.wear.R
import com.omamoripet.wear.complication.resolvePetComplicationCode
import com.omamoripet.wear.complication.resolvePetDrawableRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComplicationUpdateTrackerTest {

    @Test
    fun `initial push succeeds and duplicate tick within 30s is throttled`() {
        val tracker = ComplicationUpdateTracker()
        assertTrue(tracker.shouldPushAndMark(OmamoriState.CALM, MotionState.STILL_CONFIRMED, 68, 94, 1_000L))
        assertFalse(tracker.shouldPushAndMark(OmamoriState.CALM, MotionState.STILL_CONFIRMED, 69, 92, 6_000L))
    }

    @Test
    fun `motion state change pushes immediately even when OmamoriState remains CALM`() {
        val tracker = ComplicationUpdateTracker()
        assertTrue(tracker.shouldPushAndMark(OmamoriState.CALM, MotionState.RECENT_MOVEMENT, 72, 94, 1_000L))
        assertTrue(tracker.shouldPushAndMark(OmamoriState.CALM, MotionState.STILL_CONFIRMED, 72, 86, 6_000L))
    }

    @Test
    fun `state transition pushes immediately even within 30s throttle`() {
        val tracker = ComplicationUpdateTracker()
        assertTrue(tracker.shouldPushAndMark(OmamoriState.CALM, MotionState.STILL_CONFIRMED, 68, 94, 1_000L))
        assertTrue(tracker.shouldPushAndMark(OmamoriState.ELEVATED_AT_REST, MotionState.STILL_CONFIRMED, 86, 58, 6_000L))
        assertTrue(tracker.shouldPushAndMark(OmamoriState.BREATHING, MotionState.STILL_CONFIRMED, 80, 70, 11_000L))
    }

    @Test
    fun `bpm availability change pushes immediately`() {
        val tracker = ComplicationUpdateTracker()
        assertTrue(tracker.shouldPushAndMark(OmamoriState.RESTING, MotionState.RECENT_MOVEMENT, null, 0, 1_000L))
        assertTrue(tracker.shouldPushAndMark(OmamoriState.CALM, MotionState.RECENT_MOVEMENT, 70, 96, 6_000L))
        assertTrue(tracker.shouldPushAndMark(OmamoriState.RESTING, MotionState.OFF_BODY, null, 0, 11_000L))
    }

    @Test
    fun `significant bpm or calm score bucket change pushes after 30s throttle`() {
        val tracker = ComplicationUpdateTracker()
        assertTrue(tracker.shouldPushAndMark(OmamoriState.CALM, MotionState.STILL_CONFIRMED, 68, 94, 1_000L))
        assertFalse(tracker.shouldPushAndMark(OmamoriState.CALM, MotionState.STILL_CONFIRMED, 74, 82, 20_000L))
        assertTrue(tracker.shouldPushAndMark(OmamoriState.CALM, MotionState.STILL_CONFIRMED, 74, 82, 31_000L))
    }

    @Test
    fun `heartbeat pushes after 5 minutes even without metric changes`() {
        val tracker = ComplicationUpdateTracker()
        assertTrue(tracker.shouldPushAndMark(OmamoriState.CALM, MotionState.STILL_CONFIRMED, 68, 94, 1_000L))
        assertFalse(tracker.shouldPushAndMark(OmamoriState.CALM, MotionState.STILL_CONFIRMED, 68, 94, 299_000L))
        assertTrue(tracker.shouldPushAndMark(OmamoriState.CALM, MotionState.STILL_CONFIRMED, 68, 94, 301_000L))
    }

    @Test
    fun `monochromatic and small image pet drawables reflect 4 calm states cleanly`() {
        assertEquals(R.drawable.pet_calm, resolvePetDrawableRes(OmamoriState.CALM, monochromatic = false))
        assertEquals(R.drawable.pet_calm_mono, resolvePetDrawableRes(OmamoriState.CALM, monochromatic = true))
        assertEquals(R.drawable.pet_elevated, resolvePetDrawableRes(OmamoriState.ELEVATED_AT_REST, monochromatic = false))
        assertEquals(R.drawable.pet_elevated_mono, resolvePetDrawableRes(OmamoriState.ELEVATED_AT_REST, monochromatic = true))
        assertEquals(R.drawable.pet_breathing, resolvePetDrawableRes(OmamoriState.BREATHING, monochromatic = false))
        assertEquals(R.drawable.pet_breathing_mono, resolvePetDrawableRes(OmamoriState.BREATHING, monochromatic = true))
        assertEquals(R.drawable.pet_resting, resolvePetDrawableRes(OmamoriState.RESTING, monochromatic = false))
        assertEquals(R.drawable.pet_resting_mono, resolvePetDrawableRes(OmamoriState.RESTING, monochromatic = true))
        assertNotEquals(
            resolvePetDrawableRes(OmamoriState.BREATHING, monochromatic = false),
            resolvePetDrawableRes(OmamoriState.ELEVATED_AT_REST, monochromatic = false)
        )
        assertEquals(0f, resolvePetComplicationCode(OmamoriState.CALM))
        assertEquals(1f, resolvePetComplicationCode(OmamoriState.ELEVATED_AT_REST))
        assertEquals(2f, resolvePetComplicationCode(OmamoriState.RESTING))
        assertEquals(3f, resolvePetComplicationCode(OmamoriState.BREATHING))
    }
}

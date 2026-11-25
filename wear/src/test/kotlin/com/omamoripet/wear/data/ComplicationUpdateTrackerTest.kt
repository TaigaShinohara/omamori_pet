package com.omamoripet.wear.data

import com.omamoripet.shared.model.AlertSuppressionReason
import com.omamoripet.shared.model.CalmEvaluation
import com.omamoripet.shared.model.MotionState
import com.omamoripet.shared.model.OmamoriState
import com.omamoripet.shared.model.PetBodyTone
import com.omamoripet.shared.model.PetExpressionMode
import com.omamoripet.shared.model.PetHeartRateColor
import com.omamoripet.shared.model.PetVisualState
import com.omamoripet.wear.R
import com.omamoripet.wear.complication.resolvePetComplicationCode
import com.omamoripet.wear.ui.resolvePetDrawableRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComplicationUpdateTrackerTest {

    @Test
    fun `color changes refresh complications even when state and metrics are the same`() {
        val tracker = ComplicationUpdateTracker()
        val state = OmamoriState.ELEVATED_AT_REST
        val motion = MotionState.STILL_CONFIRMED
        assertTrue(tracker.shouldPushAndMark(state, motion, 105, 25, 1_000L, PetHeartRateColor.RED))
        assertTrue(tracker.shouldPushAndMark(state, motion, 105, 25, 2_000L, PetHeartRateColor.PURPLE))
        assertFalse(tracker.shouldPushAndMark(state, motion, 105, 25, 3_000L, PetHeartRateColor.PURPLE))
        assertTrue(tracker.shouldPushAndMark(state, motion, 105, 25, 4_000L, null))
    }

    @Test
    fun `elevated colors have distinct images and compatible watch face codes`() {
        val state = OmamoriState.ELEVATED_AT_REST
        val colors = PetHeartRateColor.entries
        assertEquals(
            listOf(R.drawable.pet_strain_yellow, R.drawable.pet_strain_red, R.drawable.pet_strain_purple),
            colors.map { resolvePetDrawableRes(state, petHeartRateColor = it) }
        )
        assertEquals(
            listOf(R.drawable.pet_strain_yellow_mono, R.drawable.pet_strain_red_mono, R.drawable.pet_strain_purple_mono),
            colors.map { resolvePetDrawableRes(state, monochromatic = true, petHeartRateColor = it) }
        )
        assertEquals(listOf(1.125f, 1.25f, 1.375f), colors.map { resolvePetComplicationCode(state, it) })
        assertEquals(R.drawable.pet_calm, resolvePetDrawableRes(OmamoriState.CALM, petHeartRateColor = PetHeartRateColor.PURPLE))
    }

    @Test
    fun `breathing colors have distinct drawables and compatible watch face codes`() {
        val state = OmamoriState.BREATHING
        val colors = PetHeartRateColor.entries
        assertEquals(
            R.drawable.pet_breathing,
            resolvePetDrawableRes(state, petHeartRateColor = null)
        )
        assertEquals(
            listOf(R.drawable.pet_breathing_yellow, R.drawable.pet_breathing_red, R.drawable.pet_breathing_purple),
            colors.map { resolvePetDrawableRes(state, petHeartRateColor = it) }
        )
        assertEquals(
            listOf(R.drawable.pet_breathing_mono, R.drawable.pet_breathing_mono, R.drawable.pet_breathing_mono),
            colors.map { resolvePetDrawableRes(state, monochromatic = true, petHeartRateColor = it) }
        )
        assertEquals(3f, resolvePetComplicationCode(state, null))
        assertEquals(listOf(2.625f, 2.75f, 2.875f), colors.map { resolvePetComplicationCode(state, it) })
    }

    @Test
    fun `watch ui state exposes single source of truth petVisualState across states and colors`() {
        val resting = WatchUiState()
        assertEquals(
            PetVisualState(PetBodyTone.DATA_MISSING, PetExpressionMode.SLEEPING),
            resting.petVisualState
        )

        val restingWhileBreathing = resting.copy(isBreathingActive = true)
        assertEquals(
            PetVisualState(PetBodyTone.DATA_MISSING, PetExpressionMode.BREATHING),
            restingWhileBreathing.petVisualState
        )

        val calm = WatchUiState(
            evaluation = CalmEvaluation(
                state = OmamoriState.CALM,
                calmScore = 92,
                deltaFromRestingBpm = 2,
                shouldTriggerAlert = false,
                suppressionReason = AlertSuppressionReason.BELOW_THRESHOLD
            )
        )
        assertEquals(
            PetVisualState(PetBodyTone.GREEN, PetExpressionMode.CALM),
            calm.petVisualState
        )

        val elevatedPurple = WatchUiState(
            evaluation = CalmEvaluation(
                state = OmamoriState.ELEVATED_AT_REST,
                calmScore = 40,
                deltaFromRestingBpm = 28,
                shouldTriggerAlert = true,
                suppressionReason = AlertSuppressionReason.NONE,
                petHeartRateColor = PetHeartRateColor.PURPLE
            )
        )
        assertEquals(
            PetVisualState(PetBodyTone.PURPLE, PetExpressionMode.ELEVATED),
            elevatedPurple.petVisualState
        )

        val breathingRed = WatchUiState(
            evaluation = CalmEvaluation(
                state = OmamoriState.BREATHING,
                calmScore = 50,
                deltaFromRestingBpm = 18,
                shouldTriggerAlert = false,
                suppressionReason = AlertSuppressionReason.ALREADY_BREATHING,
                petHeartRateColor = PetHeartRateColor.RED
            ),
            isBreathingActive = true
        )
        assertEquals(
            PetVisualState(PetBodyTone.RED, PetExpressionMode.BREATHING),
            breathingRed.petVisualState
        )

        val breathingYellow = breathingRed.copy(
            evaluation = breathingRed.evaluation.copy(petHeartRateColor = PetHeartRateColor.YELLOW)
        )
        assertEquals(
            PetVisualState(PetBodyTone.YELLOW, PetExpressionMode.BREATHING),
            breathingYellow.petVisualState
        )

        val breathingGreen = breathingRed.copy(
            evaluation = breathingRed.evaluation.copy(petHeartRateColor = null)
        )
        assertEquals(
            PetVisualState(PetBodyTone.GREEN, PetExpressionMode.BREATHING),
            breathingGreen.petVisualState
        )
    }

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

package com.omamoripet.shared.domain

import com.omamoripet.shared.model.AlertPreferences
import com.omamoripet.shared.model.AlertSuppressionReason
import com.omamoripet.shared.model.BaselineSource
import com.omamoripet.shared.model.BiometricSnapshot
import com.omamoripet.shared.model.MotionState
import com.omamoripet.shared.model.OmamoriState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalmStateEvaluatorTest {

    private val defaultPrefs = AlertPreferences()

    private fun baseSnapshot(
        bpm: Int? = 66,
        resting: Int = 64,
        motion: MotionState = MotionState.STILL_CONFIRMED,
        onBody: Boolean = true,
        sustainedSeconds: Int = 0,
        sampledAt: Long = 10_000_000L
    ) = BiometricSnapshot(
        currentHeartRateBpm = bpm,
        restingBaselineBpm = resting,
        baselineSource = BaselineSource.WATCH_LEARNED,
        motionState = motion,
        isWatchOnBody = onBody,
        sustainedElevationSeconds = sustainedSeconds,
        sampledAtEpochMs = sampledAt
    )

    @Test
    fun `off body returns RESTING without alert`() {
        val result = CalmStateEvaluator.evaluate(
            snapshot = baseSnapshot(bpm = 95, onBody = false, sustainedSeconds = 180),
            isBreathingActive = false,
            lastAlertEpochMs = 0L,
            currentHourOfDay = 14,
            preferences = defaultPrefs
        )
        assertEquals(OmamoriState.RESTING, result.state)
        assertFalse(result.shouldTriggerAlert)
        assertEquals(AlertSuppressionReason.NOT_ON_BODY, result.suppressionReason)
    }

    @Test
    fun `recent movement prevents elevated alert even with high heart rate`() {
        val result = CalmStateEvaluator.evaluate(
            snapshot = baseSnapshot(bpm = 110, motion = MotionState.RECENT_MOVEMENT, sustainedSeconds = 200),
            isBreathingActive = false,
            lastAlertEpochMs = 0L,
            currentHourOfDay = 14,
            preferences = defaultPrefs
        )
        assertEquals(OmamoriState.CALM, result.state)
        assertFalse(result.shouldTriggerAlert)
        assertEquals(AlertSuppressionReason.RECENT_MOVEMENT, result.suppressionReason)
    }

    @Test
    fun `brief elevation under required duration does not alert`() {
        val result = CalmStateEvaluator.evaluate(
            snapshot = baseSnapshot(bpm = 85, resting = 65, sustainedSeconds = 60),
            isBreathingActive = false,
            lastAlertEpochMs = 0L,
            currentHourOfDay = 14,
            preferences = defaultPrefs
        )
        assertEquals(OmamoriState.CALM, result.state)
        assertFalse(result.shouldTriggerAlert)
        assertEquals(AlertSuppressionReason.DURATION_TOO_SHORT, result.suppressionReason)
    }

    @Test
    fun `sustained elevation while still triggers gentle alert when outside cooldown and quiet hours`() {
        val result = CalmStateEvaluator.evaluate(
            snapshot = baseSnapshot(bpm = 82, resting = 64, sustainedSeconds = 95),
            isBreathingActive = false,
            lastAlertEpochMs = 0L,
            currentHourOfDay = 14,
            preferences = defaultPrefs
        )
        assertEquals(OmamoriState.ELEVATED_AT_REST, result.state)
        assertTrue(result.shouldTriggerAlert)
        assertEquals(18, result.deltaFromRestingBpm)
        assertEquals(AlertSuppressionReason.NONE, result.suppressionReason)
    }

    @Test
    fun `cooldown suppresses repeated alerts while still showing elevated state`() {
        val now = 10_000_000L
        val tenMinutesAgo = now - 10 * 60_000L
        val result = CalmStateEvaluator.evaluate(
            snapshot = baseSnapshot(bpm = 86, resting = 64, sustainedSeconds = 120, sampledAt = now),
            isBreathingActive = false,
            lastAlertEpochMs = tenMinutesAgo,
            currentHourOfDay = 14,
            preferences = defaultPrefs
        )
        assertEquals(OmamoriState.ELEVATED_AT_REST, result.state)
        assertFalse(result.shouldTriggerAlert)
        assertEquals(AlertSuppressionReason.IN_COOLDOWN, result.suppressionReason)
    }

    @Test
    fun `quiet hours wrap across midnight properly`() {
        assertTrue(CalmStateEvaluator.isWithinQuietHours(23, 23, 7))
        assertTrue(CalmStateEvaluator.isWithinQuietHours(2, 23, 7))
        assertFalse(CalmStateEvaluator.isWithinQuietHours(7, 23, 7))
        assertFalse(CalmStateEvaluator.isWithinQuietHours(15, 23, 7))
    }

    @Test
    fun `active breathing switches state to BREATHING and suppresses alerts`() {
        val result = CalmStateEvaluator.evaluate(
            snapshot = baseSnapshot(bpm = 85, resting = 65, sustainedSeconds = 120),
            isBreathingActive = true,
            lastAlertEpochMs = 0L,
            currentHourOfDay = 14,
            preferences = defaultPrefs
        )
        assertEquals(OmamoriState.BREATHING, result.state)
        assertFalse(result.shouldTriggerAlert)
        assertEquals(AlertSuppressionReason.ALREADY_BREATHING, result.suppressionReason)
    }
}

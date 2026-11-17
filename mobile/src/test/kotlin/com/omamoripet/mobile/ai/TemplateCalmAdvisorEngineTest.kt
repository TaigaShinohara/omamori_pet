package com.omamoripet.mobile.ai

import com.omamoripet.shared.model.MotionState
import com.omamoripet.shared.model.OmamoriState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateCalmAdvisorEngineTest {

    private val engine = TemplateCalmAdvisorEngine()

    @Test
    fun `japanese elevated advice reassures gently without alarming words`() = runBlocking {
        val output = engine.generateReflection(
            AdvisorInput(
                omamoriState = OmamoriState.ELEVATED_AT_REST,
                currentBpm = 84,
                restingBaselineBpm = 64,
                motionState = MotionState.STILL_CONFIRMED,
                todayElevatedCount = 1,
                todayBreathingCount = 0,
                lastBreathingDeltaBpm = null,
                isJapanese = true
            )
        )
        assertTrue(output.text.contains("84"))
        assertTrue(output.text.contains("普段より +20"))
        assertFalse(output.text.contains("危険"))
        assertFalse(output.text.contains("異常"))
        assertFalse(output.usedGeminiNano)
    }

    @Test
    fun `japanese post breathing advice uses positive bpm reduction`() = runBlocking {
        val output = engine.generateReflection(
            AdvisorInput(
                omamoriState = OmamoriState.CALM,
                currentBpm = 66,
                restingBaselineBpm = 64,
                motionState = MotionState.STILL_CONFIRMED,
                todayElevatedCount = 1,
                todayBreathingCount = 1,
                lastBreathingDeltaBpm = -6,
                isJapanese = true
            )
        )
        assertTrue(output.text.contains("6 bpm 落ち着きました"))
        assertFalse(output.text.contains("-6 bpm"))
    }

    @Test
    fun `english calm advice reflects post-breathing heart rate reduction`() = runBlocking {
        val output = engine.generateReflection(
            AdvisorInput(
                omamoriState = OmamoriState.CALM,
                currentBpm = 66,
                restingBaselineBpm = 64,
                motionState = MotionState.STILL_CONFIRMED,
                todayElevatedCount = 1,
                todayBreathingCount = 1,
                lastBreathingDeltaBpm = -7,
                isJapanese = false
            )
        )
        assertTrue(output.text.contains("7 bpm"))
        assertFalse(output.usedGeminiNano)
    }
}

package com.omamoripet.shared.model

import com.omamoripet.shared.domain.CalmStateEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PetVisualStateTest {

    @Test
    fun `resting or missing data outside breathing resolves to gray sleeping face`() {
        val resting = resolvePetVisualState(
            state = OmamoriState.RESTING,
            petHeartRateColor = PetHeartRateColor.PURPLE
        )
        assertEquals(PetVisualState(PetBodyTone.DATA_MISSING, PetExpressionMode.SLEEPING), resting)

        val missingCalm = resolvePetVisualState(
            state = OmamoriState.CALM,
            petHeartRateColor = PetHeartRateColor.RED,
            isDataMissing = true
        )
        assertEquals(PetVisualState(PetBodyTone.DATA_MISSING, PetExpressionMode.SLEEPING), missingCalm)

        val missingElevated = resolvePetVisualState(
            state = OmamoriState.ELEVATED_AT_REST,
            petHeartRateColor = PetHeartRateColor.YELLOW,
            isDataMissing = true
        )
        assertEquals(PetVisualState(PetBodyTone.DATA_MISSING, PetExpressionMode.SLEEPING), missingElevated)
    }

    @Test
    fun `missing data during guided breathing resolves to gray breathing face`() {
        val restingWhileBreathing = resolvePetVisualState(
            state = OmamoriState.RESTING,
            petHeartRateColor = PetHeartRateColor.RED,
            isBreathingActive = true
        )
        assertEquals(PetVisualState(PetBodyTone.DATA_MISSING, PetExpressionMode.BREATHING), restingWhileBreathing)

        val breathingWithMissingFlag = resolvePetVisualState(
            state = OmamoriState.BREATHING,
            petHeartRateColor = PetHeartRateColor.PURPLE,
            isDataMissing = true
        )
        assertEquals(PetVisualState(PetBodyTone.DATA_MISSING, PetExpressionMode.BREATHING), breathingWithMissingFlag)

        val calmMissingWhileBreathing = resolvePetVisualState(
            state = OmamoriState.CALM,
            petHeartRateColor = PetHeartRateColor.YELLOW,
            isDataMissing = true,
            isBreathingActive = true
        )
        assertEquals(PetVisualState(PetBodyTone.DATA_MISSING, PetExpressionMode.BREATHING), calmMissingWhileBreathing)
    }

    @Test
    fun `elevated state resolves condition tone with elevated expression`() {
        val cases = listOf(
            null to PetBodyTone.GREEN,
            PetHeartRateColor.YELLOW to PetBodyTone.YELLOW,
            PetHeartRateColor.RED to PetBodyTone.RED,
            PetHeartRateColor.PURPLE to PetBodyTone.PURPLE
        )
        for ((color, expectedTone) in cases) {
            assertEquals(
                PetVisualState(expectedTone, PetExpressionMode.ELEVATED),
                resolvePetVisualState(state = OmamoriState.ELEVATED_AT_REST, petHeartRateColor = color)
            )
        }
    }

    @Test
    fun `breathing state or active breathing flag preserves condition tone with breathing expression`() {
        val cases = listOf(
            null to PetBodyTone.GREEN,
            PetHeartRateColor.YELLOW to PetBodyTone.YELLOW,
            PetHeartRateColor.RED to PetBodyTone.RED,
            PetHeartRateColor.PURPLE to PetBodyTone.PURPLE
        )
        for ((color, expectedTone) in cases) {
            assertEquals(
                PetVisualState(expectedTone, PetExpressionMode.BREATHING),
                resolvePetVisualState(state = OmamoriState.BREATHING, petHeartRateColor = color)
            )
            assertEquals(
                PetVisualState(expectedTone, PetExpressionMode.BREATHING),
                resolvePetVisualState(
                    state = OmamoriState.ELEVATED_AT_REST,
                    petHeartRateColor = color,
                    isBreathingActive = true
                )
            )
        }
    }

    @Test
    fun `calm state resolves condition tone with calm expression`() {
        assertEquals(
            PetVisualState(PetBodyTone.GREEN, PetExpressionMode.CALM),
            resolvePetVisualState(state = OmamoriState.CALM)
        )
        assertEquals(
            PetVisualState(PetBodyTone.YELLOW, PetExpressionMode.CALM),
            resolvePetVisualState(state = OmamoriState.CALM, petHeartRateColor = PetHeartRateColor.YELLOW)
        )
        assertEquals(
            PetVisualState(PetBodyTone.RED, PetExpressionMode.CALM),
            resolvePetVisualState(state = OmamoriState.CALM, petHeartRateColor = PetHeartRateColor.RED)
        )
        assertEquals(
            PetVisualState(PetBodyTone.PURPLE, PetExpressionMode.CALM),
            resolvePetVisualState(state = OmamoriState.CALM, petHeartRateColor = PetHeartRateColor.PURPLE)
        )
    }

    @Test
    fun `public CalmStateEvaluator color helper matches threshold boundaries`() {
        assertNull(
            CalmStateEvaluator.resolvePetHeartRateColor(
                delta = 9,
                alertElevationReached = false,
                petElevationReached = true,
                elevationThresholdBpm = 15
            )
        )
        assertEquals(
            PetHeartRateColor.YELLOW,
            CalmStateEvaluator.resolvePetHeartRateColor(
                delta = 10,
                alertElevationReached = false,
                petElevationReached = true,
                elevationThresholdBpm = 15
            )
        )
        assertEquals(
            PetHeartRateColor.RED,
            CalmStateEvaluator.resolvePetHeartRateColor(
                delta = 30,
                alertElevationReached = true,
                petElevationReached = true,
                elevationThresholdBpm = 15
            )
        )
        assertEquals(
            PetHeartRateColor.PURPLE,
            CalmStateEvaluator.resolvePetHeartRateColor(
                delta = 40,
                alertElevationReached = true,
                petElevationReached = true,
                elevationThresholdBpm = 15
            )
        )
    }
}

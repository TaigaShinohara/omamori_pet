package com.omamoripet.shared.domain

import org.junit.Assert.*
import org.junit.Test

class BreathingTimelineTest {
    @Test
    fun `phases follow four two six real seconds across five cycles`() {
        assertEquals(0, BreathingTimeline.at(3_999L).phaseIndex)
        assertEquals(1, BreathingTimeline.at(4_000L).phaseIndex)
        assertEquals(2, BreathingTimeline.at(6_000L).phaseIndex)
        assertEquals(1, BreathingTimeline.at(11_999L).cycle)
        assertEquals(2, BreathingTimeline.at(12_000L).cycle)
        assertFalse(BreathingTimeline.at(59_999L).completed)
        assertTrue(BreathingTimeline.at(60_000L).completed)
    }

    @Test
    fun `recreating the screen restores the current phase rather than restarting`() {
        val restored = BreathingTimeline.at(40_500L)
        assertEquals(4, restored.cycle)
        assertEquals(1, restored.phaseIndex)
        assertEquals(0.25f, restored.progress, 0.0001f)
        assertTrue(BreathingTimeline.at(90_000L).completed)
    }

    @Test
    fun `slow and fast drawing do not scale the session duration`() {
        for (frameMs in listOf(8L, 16L, 32L, 100L, 1_000L)) {
            var elapsed = 0L
            while (!BreathingTimeline.at(elapsed).completed) elapsed += frameMs
            assertTrue(elapsed >= 60_000L)
            assertTrue(elapsed < 60_000L + frameMs)
        }
    }
}

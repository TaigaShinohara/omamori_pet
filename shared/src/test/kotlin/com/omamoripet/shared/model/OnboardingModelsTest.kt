package com.omamoripet.shared.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingModelsTest {

    @Test
    fun `window dots stay capped at 5 and move active index by 1 each page`() {
        var prevGlobalPos = -1
        for (page in 0 until ONBOARDING_TOTAL_PAGES) {
            val state = computeWindowDotsState(currentPage = page, totalPages = ONBOARDING_TOTAL_PAGES, maxVisibleDots = 5)
            assertEquals(5, state.windowCount)
            assertTrue(state.activeOffsetInWindow in 0..4)
            val currentGlobalPos = state.windowStart + state.activeOffsetInWindow
            assertEquals(page, currentGlobalPos)
            if (prevGlobalPos >= 0) {
                assertEquals(prevGlobalPos + 1, currentGlobalPos)
            }
            prevGlobalPos = currentGlobalPos
        }
    }

    @Test
    fun `window dots edge flags and completion flag match boundaries`() {
        val first = computeWindowDotsState(0, ONBOARDING_TOTAL_PAGES, 5)
        assertEquals(0, first.windowStart)
        assertEquals(0, first.activeOffsetInWindow)
        assertFalse(first.hasMoreBefore)
        assertTrue(first.hasMoreAfter)
        assertFalse(first.isCompletedPage)

        val middle = computeWindowDotsState(5, ONBOARDING_TOTAL_PAGES, 5)
        assertEquals(3, middle.windowStart)
        assertEquals(2, middle.activeOffsetInWindow)
        assertTrue(middle.hasMoreBefore)
        assertTrue(middle.hasMoreAfter)
        assertFalse(middle.isCompletedPage)

        val last = computeWindowDotsState(ONBOARDING_TOTAL_PAGES - 1, ONBOARDING_TOTAL_PAGES, 5)
        assertEquals(ONBOARDING_TOTAL_PAGES - 5, last.windowStart)
        assertEquals(4, last.activeOffsetInWindow)
        assertTrue(last.hasMoreBefore)
        assertFalse(last.hasMoreAfter)
        assertTrue(last.isCompletedPage)
    }

    @Test
    fun `praise selector never repeats the same index consecutively when pool size is 3 or more`() {
        val selector = PraiseSelector()
        for (tier in PraiseTier.entries) {
            var prev = -1
            repeat(8) {
                val next = selector.nextIndex(tier, 3)
                assertTrue(next in 0..2)
                if (prev >= 0) {
                    assertNotEquals(prev, next)
                }
                prev = next
            }
        }
    }

    @Test
    fun `value priority puts phone breathing first for phone-only or calm-down without watch`() {
        assertEquals(
            OnboardingValuePriority.PHONE_BREATH_FIRST,
            resolveValuePriority(OnboardingAnswers(OnboardingConcern.CALM_DOWN, OnboardingDeviceStyle.PHONE_ONLY))
        )
        assertEquals(
            OnboardingValuePriority.WATCH_FILTER_FIRST,
            resolveValuePriority(OnboardingAnswers(OnboardingConcern.CALM_DOWN, OnboardingDeviceStyle.HAS_WATCH))
        )
        assertEquals(
            OnboardingValuePriority.WATCH_FILTER_FIRST,
            resolveValuePriority(OnboardingAnswers(OnboardingConcern.LEARN_BASELINE, OnboardingDeviceStyle.UNDECIDED))
        )
    }

    @Test
    fun `page roles match total onboarding pages count of 15`() {
        assertEquals(15, ONBOARDING_TOTAL_PAGES)
        assertEquals(ONBOARDING_TOTAL_PAGES, OnboardingPageRole.entries.size)
        assertEquals(OnboardingPageRole.HOOK, OnboardingPageRole.entries.first())
        assertEquals(OnboardingPageRole.CONNECT, OnboardingPageRole.entries.last())
    }

    @Test
    fun `analytics tracker records page events without PII`() {
        val tracker = OnboardingAnalyticsTracker()
        tracker.track(OnboardingAnalyticsEvent.PageViewed(0, OnboardingPageRole.HOOK))
        tracker.track(OnboardingAnalyticsEvent.QuestionAnswered(1, OnboardingPageRole.CHOICE_CONCERN))
        tracker.track(OnboardingAnalyticsEvent.Abandoned(5, OnboardingPageRole.VALUE))
        tracker.track(OnboardingAnalyticsEvent.Completed(ONBOARDING_TOTAL_PAGES - 1, OnboardingPageRole.CONNECT, startedBreathingDirectly = true))

        assertEquals(4, tracker.events.size)
        assertEquals(OnboardingPageRole.HOOK, tracker.events[0].role)
        assertEquals(OnboardingPageRole.VALUE, (tracker.events[2] as OnboardingAnalyticsEvent.Abandoned).role)
        assertTrue((tracker.events[3] as OnboardingAnalyticsEvent.Completed).startedBreathingDirectly)
    }
}

package com.omamoripet.shared.model

import kotlinx.serialization.Serializable

const val CURRENT_ONBOARDING_VERSION = 1
const val ONBOARDING_TOTAL_PAGES = 15

@Serializable
enum class OnboardingConcern {
    CALM_DOWN,
    LEARN_BASELINE,
    UNDECIDED
}

@Serializable
enum class OnboardingDeviceStyle {
    HAS_WATCH,
    PHONE_ONLY,
    UNDECIDED
}

@Serializable
enum class OnboardingHapticChoice {
    WITH_HAPTICS,
    VISUAL_ONLY,
    UNDECIDED
}

@Serializable
data class OnboardingAnswers(
    val concern: OnboardingConcern = OnboardingConcern.UNDECIDED,
    val deviceStyle: OnboardingDeviceStyle = OnboardingDeviceStyle.UNDECIDED,
    val hapticChoice: OnboardingHapticChoice = OnboardingHapticChoice.UNDECIDED
)

enum class OnboardingValuePriority {
    WATCH_FILTER_FIRST,
    PHONE_BREATH_FIRST
}

fun resolveValuePriority(answers: OnboardingAnswers): OnboardingValuePriority =
    when {
        answers.deviceStyle == OnboardingDeviceStyle.HAS_WATCH -> OnboardingValuePriority.WATCH_FILTER_FIRST
        answers.deviceStyle == OnboardingDeviceStyle.PHONE_ONLY -> OnboardingValuePriority.PHONE_BREATH_FIRST
        answers.concern == OnboardingConcern.LEARN_BASELINE -> OnboardingValuePriority.WATCH_FILTER_FIRST
        else -> OnboardingValuePriority.PHONE_BREATH_FIRST
    }

data class WindowDotsState(
    val windowStart: Int,
    val windowCount: Int,
    val activeOffsetInWindow: Int,
    val hasMoreBefore: Boolean,
    val hasMoreAfter: Boolean,
    val isCompletedPage: Boolean
)

/**
 * 窓ドット進捗の状態を計算する（最大5ドット表示、総ページ数を直接数えさせない）。
 */
fun computeWindowDotsState(
    currentPage: Int,
    totalPages: Int = ONBOARDING_TOTAL_PAGES,
    maxVisibleDots: Int = 5
): WindowDotsState {
    require(totalPages >= 1) { "totalPages must be >= 1" }
    require(maxVisibleDots >= 1) { "maxVisibleDots must be >= 1" }
    val clampedPage = currentPage.coerceIn(0, totalPages - 1)
    val windowCount = minOf(maxVisibleDots, totalPages)
    val maxStart = (totalPages - windowCount).coerceAtLeast(0)
    val windowStart = (clampedPage - 2).coerceIn(0, maxStart)
    return WindowDotsState(
        windowStart = windowStart,
        windowCount = windowCount,
        activeOffsetInWindow = clampedPage - windowStart,
        hasMoreBefore = windowStart > 0,
        hasMoreAfter = (windowStart + windowCount) < totalPages,
        isCompletedPage = clampedPage == totalPages - 1
    )
}

enum class PraiseTier {
    SMALL,
    MEDIUM,
    LARGE
}

/**
 * 段階別に称賛文を選択し、同じ文の連続表示を防ぐ。
 */
class PraiseSelector {
    private val lastIndexByTier = mutableMapOf<PraiseTier, Int>()

    fun nextIndex(tier: PraiseTier, poolSize: Int): Int {
        require(poolSize >= 1) { "poolSize must be >= 1" }
        if (poolSize == 1) return 0
        val prev = lastIndexByTier[tier]
        val next = if (prev == null) 0 else (prev + 1) % poolSize
        lastIndexByTier[tier] = next
        return next
    }
}

enum class OnboardingPageRole {
    HOOK,
    CHOICE_CONCERN,
    CHOICE_DEVICE,
    MATCH_DECLARATION,
    IDENTITY,
    VALUE,
    CHOICE_HAPTICS,
    HAPTICS_RESULT,
    STEP_1,
    STEP_2,
    STEP_3,
    TRY_IT,
    TRY_RESULT,
    EXPECTATION,
    CONNECT
}

sealed interface OnboardingAnalyticsEvent {
    val pageIndex: Int
    val role: OnboardingPageRole

    data class PageViewed(
        override val pageIndex: Int,
        override val role: OnboardingPageRole
    ) : OnboardingAnalyticsEvent

    data class QuestionAnswered(
        override val pageIndex: Int,
        override val role: OnboardingPageRole
    ) : OnboardingAnalyticsEvent

    data class Skipped(
        override val pageIndex: Int,
        override val role: OnboardingPageRole
    ) : OnboardingAnalyticsEvent

    data class TryLaterSelected(
        override val pageIndex: Int,
        override val role: OnboardingPageRole
    ) : OnboardingAnalyticsEvent

    data class Completed(
        override val pageIndex: Int,
        override val role: OnboardingPageRole,
        val startedBreathingDirectly: Boolean
    ) : OnboardingAnalyticsEvent
}

/**
 * 非 PII のオンボーディング計測トラッカー。
 * 悩みや健康状態を表す回答テキストや端末識別子は記録しない。
 */
class OnboardingAnalyticsTracker(
    private val sink: (OnboardingAnalyticsEvent) -> Unit = {}
) {
    private val _events = mutableListOf<OnboardingAnalyticsEvent>()
    val events: List<OnboardingAnalyticsEvent> get() = _events

    fun track(event: OnboardingAnalyticsEvent) {
        _events += event
        sink(event)
    }
}

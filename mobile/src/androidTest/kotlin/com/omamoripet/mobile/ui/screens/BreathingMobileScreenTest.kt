package com.omamoripet.mobile.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.platform.app.InstrumentationRegistry
import com.omamoripet.mobile.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class BreathingMobileScreenTest {
    private var animationScale = 1f
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor: Float get() = animationScale
    })
    private var nowMs = 0L
    private val completedIds = mutableListOf<String>()

    private fun show(): StateRestorationTester {
        compose.mainClock.autoAdvance = false
        return StateRestorationTester(compose).also { tester ->
            tester.setContent {
                MaterialTheme {
                    BreathingMobileScreen(
                        hapticsEnabled = false,
                        onBack = {},
                        onCompleted = { id, _, _ -> completedIds += id },
                        elapsedRealtimeMs = { nowMs }
                    )
                }
            }
            compose.mainClock.advanceTimeBy(500L)
        }
    }

    private fun advanceTo(timeMs: Long) {
        compose.runOnIdle { nowMs = timeMs }
        compose.mainClock.advanceTimeBy(500L)
        compose.waitForIdle()
    }

    @Test
    fun restoringDuringAndAfterBreathingKeepsProgressAndRecordsOnlyOnce() {
        val tester = show()
        advanceTo(40_500L)
        tester.emulateSavedInstanceStateRestore()
        compose.mainClock.advanceTimeBy(500L)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithContentDescription(context.getString(R.string.breathing_cycle_progress, 4, 5)).assertExists()
        compose.runOnIdle { assertEquals(0, completedIds.size) }
        advanceTo(60_000L)
        compose.runOnIdle { assertEquals(1, completedIds.size) }
        tester.emulateSavedInstanceStateRestore()
        advanceTo(90_000L)
        compose.runOnIdle { assertEquals(1, completedIds.size) }
    }

    @Test
    fun halfSpeedAnimationStillRequiresSixtyRealSeconds() {
        animationScale = 0.5f
        show()
        advanceTo(30_000L)
        compose.runOnIdle { assertEquals(0, completedIds.size) }
        advanceTo(60_000L)
        compose.runOnIdle { assertEquals(1, completedIds.size) }
    }

    @Test
    fun doubleDurationScaleStillFinishesAfterSixtyRealSeconds() {
        animationScale = 2f
        show()
        advanceTo(59_999L)
        compose.runOnIdle { assertEquals(0, completedIds.size) }
        advanceTo(60_000L)
        compose.runOnIdle { assertEquals(1, completedIds.size) }
    }
}

package com.omamoripet.wear.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.omamoripet.shared.model.BaselineSource
import com.omamoripet.shared.model.FEELING_MINUTE_MS
import com.omamoripet.shared.model.FEELING_MINUTES_AFTER
import com.omamoripet.shared.model.FEELING_MINUTES_BEFORE
import com.omamoripet.shared.model.MotionState
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WatchFeelingContextTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store by lazy { PreferenceDataStoreFactory.create(scope = scope) {
        folder.root.resolve("feeling_context.preferences_pb")
    } }
    private var now = 1_700_000_000_000L

    @After fun cancelScope() = scope.cancel()

    @Test
    fun `elapsed time alone cannot expose an unfinished QR and resume retains both sides`() = runBlocking {
        val capture = WatchFeelingContext(store, scope) { now }
        val recordedAt = enableAndRecord(capture)
        val pending = capture.awaitWindow()
        assertFalse(pending.ready)
        now = recordedAt + FEELING_MINUTE_MS
        capture.heartRate(now, 72, now)
        capture.movement(now, MotionState.RECENT_MOVEMENT)
        capture.updateWindows()
        assertFalse(capture.state.value.windows.single().ready)

        // The watch can go straight from one minute after the entry to waking after the deadline.
        now = recordedAt + FEELING_MINUTES_AFTER * FEELING_MINUTE_MS + 2_000
        assertFalse(capture.state.value.windows.single().ready)
        capture.heartRate(now, 80, now)
        capture.updateWindows()
        val finished = capture.awaitWindow(completed = true)
        assertTrue(finished.ready)
        assertEquals(65, finished.context.minutes[FEELING_MINUTES_BEFORE - 1].medianBpm)
        assertEquals(MotionState.STILL_CONFIRMED, finished.context.minutes[FEELING_MINUTES_BEFORE - 1].motion)
        assertEquals(72, finished.context.minutes[FEELING_MINUTES_BEFORE + 1].medianBpm)
        assertEquals(MotionState.RECENT_MOVEMENT, finished.context.minutes[FEELING_MINUTES_BEFORE + 1].motion)
        assertEquals(2, finished.context.minutes.count { it.medianBpm != null })
    }

    @Test
    fun `checkpoint after the record survives a fresh instance with empty sensor buffers`() = runBlocking {
        val capture = WatchFeelingContext(store, scope) { now }
        val recordedAt = enableAndRecord(capture)
        now = recordedAt + 2 * FEELING_MINUTE_MS
        capture.heartRate(now, 76, now)
        capture.movement(now, MotionState.RECENT_MOVEMENT)
        capture.updateWindows(checkpoint = true)
        val restarted = WatchFeelingContext(store, scope) { now }
        assertFalse(restarted.awaitWindow().ready)
        now = recordedAt + FEELING_MINUTES_AFTER * FEELING_MINUTE_MS
        restarted.updateWindows()

        val finished = restarted.awaitWindow(completed = true)
        assertTrue(finished.ready)
        assertEquals(65, finished.context.minutes[FEELING_MINUTES_BEFORE - 1].medianBpm)
        assertEquals(76, finished.context.minutes[FEELING_MINUTES_BEFORE + 2].medianBpm)
        assertEquals(MotionState.RECENT_MOVEMENT, finished.context.minutes[FEELING_MINUTES_BEFORE + 2].motion)
    }

    @Test
    fun `failed final save keeps QR unavailable and can be retried`() = runBlocking {
        val failingStore = object : DataStore<Preferences> by store {
            var fail = false

            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                if (fail) throw IOException("Storage unavailable")
                return store.updateData(transform)
            }
        }
        val capture = WatchFeelingContext(failingStore, scope) { now }
        val recordedAt = enableAndRecord(capture)
        now = recordedAt + FEELING_MINUTES_AFTER * FEELING_MINUTE_MS
        failingStore.fail = true
        capture.updateWindows()
        assertFalse(capture.state.value.windows.single().ready)

        failingStore.fail = false
        capture.updateWindows()
        assertTrue(capture.awaitWindow(completed = true).ready)
    }

    private suspend fun enableAndRecord(capture: WatchFeelingContext): Long {
        capture.setEnabled(true)
        withTimeout(5_000) { capture.state.first { it.enabledSince != null } }
        now += 2 * FEELING_MINUTE_MS
        capture.heartRate(now, 65, now)
        capture.movement(now, MotionState.STILL_CONFIRMED)
        now += FEELING_MINUTE_MS
        capture.startWindow(now, 64, BaselineSource.WATCH_LEARNED)
        capture.awaitWindow()
        return now
    }

    private suspend fun WatchFeelingContext.awaitWindow(completed: Boolean = false): WatchFeelingContext.Window =
        withTimeout(5_000) {
            state.first { it.windows.singleOrNull()?.completed == completed }.windows.single()
        }
}

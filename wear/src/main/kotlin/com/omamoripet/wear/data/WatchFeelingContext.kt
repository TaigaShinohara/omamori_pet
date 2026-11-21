package com.omamoripet.wear.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omamoripet.shared.model.BaselineSource
import com.omamoripet.shared.model.FEELING_MINUTE_COUNT
import com.omamoripet.shared.model.FEELING_MINUTE_MS
import com.omamoripet.shared.model.FEELING_MINUTES_AFTER
import com.omamoripet.shared.model.FEELING_MINUTES_BEFORE
import com.omamoripet.shared.model.FeelingContext
import com.omamoripet.shared.model.FeelingMinute
import com.omamoripet.shared.model.MotionState
import com.omamoripet.shared.sync.OmamoriJson
import java.io.File
import java.io.IOException
import java.util.TreeMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class WatchFeelingContext(context: Context, private val scope: CoroutineScope) {
    private val store = PreferenceDataStoreFactory.create(scope = scope) {
        File(context.noBackupFilesDir, "feeling_context.preferences_pb")
    }
    private val lock = Any()
    private val heart = TreeMap<Long, Int>()
    private val motion = TreeMap<Long, MotionState>()
    private val active = mutableSetOf<Long>()
    val state = store.data.map { preferences ->
        Settings(
            enabledSince = preferences[ENABLED_SINCE],
            windows = readWindows(preferences[WINDOWS])
        )
    }.catch { failure ->
        if (failure is IOException) emit(Settings()) else throw failure
    }.stateIn(scope, SharingStarted.Eagerly, Settings())

    data class Settings(val enabledSince: Long? = null, val windows: List<FeelingContext> = emptyList())

    suspend fun setEnabled(enabled: Boolean) {
        val now = System.currentTimeMillis()
        store.edit { preferences ->
            if (enabled) {
                if (preferences[ENABLED_SINCE] == null) preferences[ENABLED_SINCE] = now
            } else {
                preferences.remove(ENABLED_SINCE)
                preferences.remove(WINDOWS)
            }
        }
        if (!enabled) synchronized(lock) {
            heart.clear()
            motion.clear()
        }
    }

    fun heartRate(measuredAt: Long, bpm: Int, now: Long) {
        val enabledSince = state.value.enabledSince ?: return
        if (measuredAt < enabledSince || measuredAt !in (now - BUFFER_MS)..now) return
        synchronized(lock) {
            heart[measuredAt] = bpm
            heart.headMap(now - BUFFER_MS).clear()
        }
    }

    fun movement(now: Long, observed: MotionState) {
        val enabledSince = state.value.enabledSince ?: return
        if (now < enabledSince) return
        synchronized(lock) {
            motion[now] = observed
            motion.headMap(now - BUFFER_MS).clear()
        }
    }

    fun capture(recordedAt: Long, baselineBpm: Int, baselineSource: BaselineSource) {
        val now = System.currentTimeMillis()
        if (recordedAt !in (now - BUFFER_MS)..(now + CLOCK_TOLERANCE_MS)) return
        if (!synchronized(lock) { active.add(recordedAt) }) return
        scope.launch {
            try {
                val preferences = store.data.first()
                val since = preferences[ENABLED_SINCE] ?: return@launch
                if (recordedAt < since) return@launch
                val end = recordedAt + FEELING_MINUTES_AFTER * FEELING_MINUTE_MS
                save(since, snapshot(recordedAt, baselineBpm, baselineSource, since))
                if (end > now) {
                    delay((end - System.currentTimeMillis()).coerceAtLeast(0L))
                    save(since, snapshot(recordedAt, baselineBpm, baselineSource, since))
                }
            } catch (_: IOException) {
                // A failed local write must not interfere with the live pet or feeling display.
            } finally {
                synchronized(lock) { active.remove(recordedAt) }
            }
        }
    }

    suspend fun pruneExpired(now: Long) {
        synchronized(lock) {
            heart.headMap(now - BUFFER_MS).clear()
            motion.headMap(now - BUFFER_MS).clear()
        }
        if (state.value.windows.none { now - it.recordedAtEpochMs >= RETENTION_MS }) return
        store.edit { preferences ->
            preferences[WINDOWS] = OmamoriJson.encode(
                readWindows(preferences[WINDOWS]).filter { now - it.recordedAtEpochMs < RETENTION_MS }
            )
        }
    }

    private fun snapshot(
        recordedAt: Long,
        baselineBpm: Int,
        baselineSource: BaselineSource,
        enabledSince: Long
    ): FeelingContext {
        val start = recordedAt - FEELING_MINUTES_BEFORE * FEELING_MINUTE_MS
        val minutes = synchronized(lock) {
            List(FEELING_MINUTE_COUNT) { index ->
                val from = start + index * FEELING_MINUTE_MS
                val to = from + FEELING_MINUTE_MS
                val firstAllowed = maxOf(from, enabledSince)
                if (firstAllowed >= to) {
                    FeelingMinute()
                } else {
                    val observedHeart = heart.subMap(firstAllowed, to).values.sorted()
                    val observedMotion = motion.subMap(firstAllowed, to).values
                    summarizeFeelingMinute(observedHeart, observedMotion)
                }
            }
        }
        val validBaseline = baselineBpm.takeIf { it in 35..220 }
        return FeelingContext(recordedAt, validBaseline, baselineSource.takeIf { validBaseline != null }, minutes)
    }

    private suspend fun save(since: Long, snapshot: FeelingContext) {
        if (!snapshot.hasObservations()) return
        store.edit { preferences ->
            if (preferences[ENABLED_SINCE] != since) return@edit
            val windows = readWindows(preferences[WINDOWS])
            val previous = windows.firstOrNull { it.recordedAtEpochMs == snapshot.recordedAtEpochMs }
            val merged = if (previous == null) snapshot else previous.copy(
                minutes = snapshot.minutes.zip(previous.minutes) { current, old ->
                    val heartMinute = if (current.sampleCount >= old.sampleCount) current else old
                    heartMinute.copy(motion = current.motion ?: old.motion)
                }
            )
            val now = System.currentTimeMillis()
            preferences[WINDOWS] = OmamoriJson.encode(
                (listOf(merged) + windows)
                    .distinctBy { it.recordedAtEpochMs }
                    .filter { now - it.recordedAtEpochMs < RETENTION_MS }
                    .sortedByDescending { it.recordedAtEpochMs }
                    .take(MAX_WINDOWS)
            )
        }
    }

    private companion object {
        const val BUFFER_MS = 60 * FEELING_MINUTE_MS
        const val CLOCK_TOLERANCE_MS = 15_000L
        const val RETENTION_MS = 24 * 60 * FEELING_MINUTE_MS
        const val MAX_WINDOWS = 30
        val ENABLED_SINCE = longPreferencesKey("enabled_since")
        val WINDOWS = stringPreferencesKey("windows")

        fun readWindows(json: String?): List<FeelingContext> =
            OmamoriJson.decodeOrNull<List<FeelingContext>>(json) ?: emptyList()
    }
}

internal fun summarizeFeelingMinute(sortedHeart: List<Int>, motion: Collection<MotionState>): FeelingMinute {
    val size = sortedHeart.size
    val median = if (size == 0) null else (sortedHeart[(size - 1) / 2] + sortedHeart[size / 2] + 1) / 2
    val observedMotion = when {
        MotionState.RECENT_MOVEMENT in motion -> MotionState.RECENT_MOVEMENT
        MotionState.STILL_CONFIRMED in motion -> MotionState.STILL_CONFIRMED
        MotionState.OFF_BODY in motion -> MotionState.OFF_BODY
        else -> null
    }
    return FeelingMinute(median, size, observedMotion)
}

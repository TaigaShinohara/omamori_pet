package com.omamoripet.wear.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class WatchFeelingContext(
    private val store: DataStore<Preferences>,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis
) {
    constructor(context: Context, scope: CoroutineScope) : this(
        PreferenceDataStoreFactory.create(scope = scope) {
            File(context.noBackupFilesDir, "feeling_context.preferences_pb")
        },
        scope
    )

    private val lock = Any()
    private val heart = TreeMap<Long, Int>()
    private val motion = TreeMap<Long, MotionState>()
    val state = store.data.map { preferences ->
        val completed = preferences[COMPLETED].orEmpty()
        Settings(
            enabledSince = preferences[ENABLED_SINCE],
            windows = readWindows(preferences[WINDOWS]).map { context ->
                Window(context, context.recordedAtEpochMs.toString() in completed)
            }
        )
    }.catch { failure ->
        if (failure is IOException) emit(Settings()) else throw failure
    }.stateIn(scope, SharingStarted.Eagerly, Settings())

    data class Settings(val enabledSince: Long? = null, val windows: List<Window> = emptyList())

    data class Window(val context: FeelingContext, val completed: Boolean) {
        val recordedAt: Long get() = context.recordedAtEpochMs
        val endsAt: Long get() = recordedAt + FEELING_MINUTES_AFTER * FEELING_MINUTE_MS
        val ready: Boolean get() = completed && context.hasObservations()
    }

    suspend fun setEnabled(enabled: Boolean) {
        val now = clock()
        store.edit { preferences ->
            if (enabled) {
                if (preferences[ENABLED_SINCE] == null) preferences[ENABLED_SINCE] = now
            } else {
                preferences.remove(ENABLED_SINCE)
                preferences.remove(WINDOWS)
                preferences.remove(COMPLETED)
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
        scope.launch { startWindow(recordedAt, baselineBpm, baselineSource) }
    }

    suspend fun startWindow(recordedAt: Long, baselineBpm: Int, baselineSource: BaselineSource) {
        val now = clock()
        if (recordedAt !in (now - BUFFER_MS)..(now + CLOCK_TOLERANCE_MS)) return
        try {
            store.edit { preferences ->
                val since = preferences[ENABLED_SINCE] ?: return@edit
                if (recordedAt < since) return@edit
                val windows = readWindows(preferences[WINDOWS])
                if (windows.any { it.recordedAtEpochMs == recordedAt }) return@edit
                val validBaseline = baselineBpm.takeIf { it in 35..220 }
                val context = FeelingContext(
                    recordedAt, validBaseline, baselineSource.takeIf { validBaseline != null },
                    snapshot(recordedAt, since)
                )
                val completed = preferences[COMPLETED].orEmpty().toMutableSet()
                if (now >= Window(context, false).endsAt) completed.add(recordedAt.toString())
                writeWindows(preferences, listOf(context) + windows, completed, now)
            }
        } catch (_: IOException) {
            // A failed local write must not interfere with the live pet or feeling display.
        }
    }

    suspend fun updateWindows(checkpoint: Boolean = false) {
        val now = clock()
        synchronized(lock) {
            heart.headMap(now - BUFFER_MS).clear()
            motion.headMap(now - BUFFER_MS).clear()
        }
        if (state.value.windows.none {
                now - it.recordedAt >= RETENTION_MS || (!it.completed && (checkpoint || now >= it.endsAt))
            }) return
        try {
            store.edit { preferences ->
                val since = preferences[ENABLED_SINCE] ?: return@edit
                val completed = preferences[COMPLETED].orEmpty().toMutableSet()
                val windows = readWindows(preferences[WINDOWS]).mapNotNull { context ->
                    val windowId = context.recordedAtEpochMs.toString()
                    if (now - context.recordedAtEpochMs >= RETENTION_MS) return@mapNotNull null
                    val due = now >= Window(context, false).endsAt
                    if (windowId in completed || (!due && !checkpoint)) return@mapNotNull context
                    val current = snapshot(context.recordedAtEpochMs, since)
                    val merged = context.copy(minutes = current.zip(context.minutes) { fresh, saved ->
                        val heartMinute = if (fresh.sampleCount >= saved.sampleCount) fresh else saved
                        heartMinute.copy(motion = fresh.motion ?: saved.motion)
                    })
                    if (due) completed.add(windowId)
                    merged
                }
                // The data and the QR eligibility marker become visible in the same transaction.
                writeWindows(preferences, windows, completed, now)
            }
        } catch (_: IOException) {
            // Leave the window pending so a subsequent update can retry the write.
        }
    }

    private fun snapshot(recordedAt: Long, enabledSince: Long): List<FeelingMinute> {
        val start = recordedAt - FEELING_MINUTES_BEFORE * FEELING_MINUTE_MS
        return synchronized(lock) {
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
    }

    private fun writeWindows(
        preferences: MutablePreferences,
        windows: List<FeelingContext>,
        completed: Set<String>,
        now: Long
    ) {
        val retained = windows
            .filter { now - it.recordedAtEpochMs < RETENTION_MS }
            .sortedByDescending { it.recordedAtEpochMs }
            .take(MAX_WINDOWS)
        preferences[WINDOWS] = OmamoriJson.encode(retained)
        preferences[COMPLETED] = retained.map { it.recordedAtEpochMs.toString() }.filter { it in completed }.toSet()
    }

    private companion object {
        const val BUFFER_MS = 60 * FEELING_MINUTE_MS
        const val CLOCK_TOLERANCE_MS = 15_000L
        const val RETENTION_MS = 24 * 60 * FEELING_MINUTE_MS
        const val MAX_WINDOWS = 30
        val ENABLED_SINCE = longPreferencesKey("enabled_since")
        val WINDOWS = stringPreferencesKey("windows")
        val COMPLETED = stringSetPreferencesKey("completed_windows")

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

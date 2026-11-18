package com.omamoripet.wear.data

import android.content.ComponentName
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.omamoripet.shared.domain.CalmStateEvaluator
import com.omamoripet.shared.domain.ResolvedBaseline
import com.omamoripet.shared.domain.RestingBaselineLearner
import com.omamoripet.shared.model.AlertPreferences
import com.omamoripet.shared.model.BaselineSource
import com.omamoripet.shared.model.BiometricSnapshot
import com.omamoripet.shared.model.CalmEvaluation
import com.omamoripet.shared.model.MotionState
import com.omamoripet.shared.model.OmamoriState
import com.omamoripet.shared.sync.OmamoriJson
import com.omamoripet.shared.sync.PhoneBaselinePayload
import com.omamoripet.shared.sync.PreferencesSyncPayload
import com.omamoripet.wear.complication.HeartRateComplicationService
import com.omamoripet.wear.complication.PetStateComplicationService
import com.omamoripet.wear.sync.WatchSyncClient
import com.omamoripet.wear.tile.OmamoriTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime

private val Context.watchDataStore: DataStore<Preferences> by preferencesDataStore(name = "omamori_watch_prefs")

enum class SimulationScenario {
    LIVE_SENSORS,
    CALM_AT_REST,
    ELEVATED_AT_REST,
    POST_WALK_MOVEMENT
}

data class WatchUiState(
    val snapshot: BiometricSnapshot = BiometricSnapshot(
        currentHeartRateBpm = null,
        restingBaselineBpm = 65,
        baselineSource = BaselineSource.DEFAULT_FALLBACK,
        motionState = MotionState.RECENT_MOVEMENT,
        isWatchOnBody = true,
        sustainedElevationSeconds = 0,
        sampledAtEpochMs = System.currentTimeMillis(),
        isSimulated = false
    ),
    val evaluation: CalmEvaluation = CalmEvaluation(
        state = OmamoriState.RESTING,
        calmScore = 0,
        deltaFromRestingBpm = null,
        shouldTriggerAlert = false,
        suppressionReason = com.omamoripet.shared.model.AlertSuppressionReason.NOT_ON_BODY
    ),
    val preferences: AlertPreferences = AlertPreferences(),
    val latestAiAdvice: String? = null,
    val aiAdviceSource: String? = null,
    val isBreathingActive: Boolean = false,
    val simulationScenario: SimulationScenario = SimulationScenario.LIVE_SENSORS,
    val lastAlertEpochMs: Long = 0L,
    val isCalibrating: Boolean = false,
    val calibrationRemainingSeconds: Int = 0
)

class WatchStateRepository(private val context: Context) {

    private object Keys {
        val LEARNED_SAMPLES = stringPreferencesKey("learned_hr_samples")
        val HC_BASELINE_BPM = stringPreferencesKey("hc_baseline_bpm")
        val ALERT_PREFS = stringPreferencesKey("alert_prefs_json")
        val LAST_ALERT_MS = longPreferencesKey("last_alert_epoch_ms")
        val LATEST_AI_ADVICE = stringPreferencesKey("latest_ai_advice")
        val AI_SOURCE_LABEL = stringPreferencesKey("ai_source_label")
    }

    private companion object {
        const val MIN_SAMPLE_PERSIST_INTERVAL_MS = 30_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncClient by lazy { WatchSyncClient(context) }
    private val baselineLock = Any()
    private val baselineLearner = RestingBaselineLearner()

    private val _uiState = MutableStateFlow(WatchUiState())
    val uiState: StateFlow<WatchUiState> = _uiState.asStateFlow()

    @Volatile
    private var samplesDirty = false
    @Volatile
    private var lastSamplesPersistMs = 0L

    private var lastComplicationPushMs: Long = 0L
    private var lastPushedState: OmamoriState? = null
    private var lastPushedBpmBucket: Int? = null

    private val initialLoadJob: Job = scope.launch {
        restorePersistedState()
    }

    suspend fun awaitInitialized() {
        initialLoadJob.join()
    }

    private suspend fun restorePersistedState() {
        val prefs = context.watchDataStore.data.first()
        val samples = OmamoriJson.decodeOrNull<List<Int>>(prefs[Keys.LEARNED_SAMPLES]) ?: emptyList()
        val hcBaseline = prefs[Keys.HC_BASELINE_BPM]?.toIntOrNull()
        val resolved = synchronized(baselineLock) {
            baselineLearner.loadInitial(samples, hcBaseline)
            baselineLearner.resolveBaseline()
        }

        val alertPrefs = OmamoriJson.decodeOrNull<AlertPreferences>(prefs[Keys.ALERT_PREFS]) ?: AlertPreferences()
        val lastAlert = prefs[Keys.LAST_ALERT_MS] ?: 0L
        val advice = prefs[Keys.LATEST_AI_ADVICE]
        val aiSource = prefs[Keys.AI_SOURCE_LABEL]

        _uiState.update { current ->
            val updatedSnap = current.snapshot.copy(
                restingBaselineBpm = resolved.bpm,
                baselineSource = resolved.source
            )
            current.copy(
                snapshot = updatedSnap,
                preferences = alertPrefs,
                lastAlertEpochMs = lastAlert,
                latestAiAdvice = advice,
                aiAdviceSource = aiSource
            )
        }
    }

    fun resolveCurrentBaseline(): ResolvedBaseline = synchronized(baselineLock) {
        baselineLearner.resolveBaseline()
    }

    fun recordStillHeartRateSample(bpm: Int) {
        val now = System.currentTimeMillis()
        val snapshotToPersist: List<Int>? = synchronized(baselineLock) {
            baselineLearner.recordStillSample(bpm)
            samplesDirty = true
            if (now - lastSamplesPersistMs >= MIN_SAMPLE_PERSIST_INTERVAL_MS) {
                lastSamplesPersistMs = now
                samplesDirty = false
                baselineLearner.getCurrentSamples()
            } else {
                null
            }
        }
        if (snapshotToPersist != null) {
            scope.launch {
                context.watchDataStore.edit { it[Keys.LEARNED_SAMPLES] = OmamoriJson.encode(snapshotToPersist) }
            }
        }
    }

    suspend fun flushLearnedSamples() {
        val snapshotToPersist: List<Int>? = synchronized(baselineLock) {
            if (!samplesDirty) return@synchronized null
            samplesDirty = false
            lastSamplesPersistMs = System.currentTimeMillis()
            baselineLearner.getCurrentSamples()
        }
        if (snapshotToPersist != null) {
            context.watchDataStore.edit { it[Keys.LEARNED_SAMPLES] = OmamoriJson.encode(snapshotToPersist) }
        }
    }

    fun updateSensorSnapshot(snapshot: BiometricSnapshot): CalmEvaluation {
        val hour = LocalTime.now().hour
        var evaluated: CalmEvaluation? = null
        _uiState.update { current ->
            val eval = CalmStateEvaluator.evaluate(
                snapshot = snapshot,
                isBreathingActive = current.isBreathingActive,
                lastAlertEpochMs = current.lastAlertEpochMs,
                currentHourOfDay = hour,
                preferences = current.preferences
            )
            evaluated = eval
            current.copy(snapshot = snapshot, evaluation = eval)
        }
        val eval = evaluated!!
        maybeRefreshComplicationsAndTile(eval.state, snapshot.currentHeartRateBpm, snapshot.sampledAtEpochMs)
        return eval
    }

    fun setBreathingActive(active: Boolean) {
        val hour = LocalTime.now().hour
        var stateAfter: OmamoriState? = null
        _uiState.update { state ->
            val eval = CalmStateEvaluator.evaluate(
                snapshot = state.snapshot,
                isBreathingActive = active,
                lastAlertEpochMs = state.lastAlertEpochMs,
                currentHourOfDay = hour,
                preferences = state.preferences
            )
            stateAfter = eval.state
            state.copy(isBreathingActive = active, evaluation = eval)
        }
        val currentState = stateAfter
        if (currentState != null && currentState != lastPushedState) {
            lastPushedState = currentState
            lastComplicationPushMs = System.currentTimeMillis()
            forceRefreshComplicationsAndTile()
        }
    }

    fun markAlertSent(epochMs: Long) {
        _uiState.update { it.copy(lastAlertEpochMs = epochMs) }
        scope.launch {
            context.watchDataStore.edit { it[Keys.LAST_ALERT_MS] = epochMs }
        }
    }

    fun snoozeAlertsForMinutes(minutes: Int, nowEpochMs: Long = System.currentTimeMillis()) {
        val updated = _uiState.value.preferences.copy(snoozedUntilEpochMs = nowEpochMs + minutes * 60_000L)
        updatePreferences(updated)
    }

    fun updatePreferences(preferences: AlertPreferences, syncToPhone: Boolean = true) {
        val hour = LocalTime.now().hour
        _uiState.update { state ->
            val eval = CalmStateEvaluator.evaluate(
                snapshot = state.snapshot,
                isBreathingActive = state.isBreathingActive,
                lastAlertEpochMs = state.lastAlertEpochMs,
                currentHourOfDay = hour,
                preferences = preferences
            )
            state.copy(preferences = preferences, evaluation = eval)
        }
        scope.launch {
            context.watchDataStore.edit { it[Keys.ALERT_PREFS] = OmamoriJson.encode(preferences) }
            if (syncToPhone) {
                syncClient.pushPreferences(
                    com.omamoripet.shared.sync.PreferencesSyncPayload(preferences, System.currentTimeMillis())
                )
            }
        }
    }

    fun setSimulationScenario(scenario: SimulationScenario) {
        val now = System.currentTimeMillis()
        val baseline = resolveCurrentBaseline()
        val current = _uiState.value
        val simulatedSnap = when (scenario) {
            SimulationScenario.LIVE_SENSORS -> current.snapshot.copy(
                currentHeartRateBpm = null,
                restingBaselineBpm = baseline.bpm,
                baselineSource = baseline.source,
                motionState = MotionState.RECENT_MOVEMENT,
                sustainedElevationSeconds = 0,
                sampledAtEpochMs = now,
                isSimulated = false
            )
            SimulationScenario.CALM_AT_REST -> BiometricSnapshot(
                currentHeartRateBpm = baseline.bpm + 2,
                restingBaselineBpm = baseline.bpm,
                baselineSource = baseline.source,
                motionState = MotionState.STILL_CONFIRMED,
                isWatchOnBody = true,
                sustainedElevationSeconds = 0,
                sampledAtEpochMs = now,
                isSimulated = true
            )
            SimulationScenario.ELEVATED_AT_REST -> BiometricSnapshot(
                currentHeartRateBpm = baseline.bpm + current.preferences.elevationThresholdBpm + 6,
                restingBaselineBpm = baseline.bpm,
                baselineSource = baseline.source,
                motionState = MotionState.STILL_CONFIRMED,
                isWatchOnBody = true,
                sustainedElevationSeconds = current.preferences.requiredSustainedSeconds + 15,
                sampledAtEpochMs = now,
                isSimulated = true
            )
            SimulationScenario.POST_WALK_MOVEMENT -> BiometricSnapshot(
                currentHeartRateBpm = baseline.bpm + 26,
                restingBaselineBpm = baseline.bpm,
                baselineSource = baseline.source,
                motionState = MotionState.RECENT_MOVEMENT,
                isWatchOnBody = true,
                sustainedElevationSeconds = 120,
                sampledAtEpochMs = now,
                isSimulated = true
            )
        }
        _uiState.update { it.copy(simulationScenario = scenario) }
        updateSensorSnapshot(simulatedSnap)
    }

    fun setCalibrationProgress(active: Boolean, remainingSeconds: Int) {
        _uiState.update { it.copy(isCalibrating = active, calibrationRemainingSeconds = remainingSeconds) }
    }

    fun applyPhoneBaselineAndAdvice(payload: PhoneBaselinePayload) {
        val resolved = synchronized(baselineLock) {
            baselineLearner.updateHealthConnectBaseline(payload.healthConnectRestingBpm)
            baselineLearner.resolveBaseline()
        }
        val hour = LocalTime.now().hour
        var stateAfter: OmamoriState? = null
        _uiState.update { state ->
            val newSnap = state.snapshot.copy(
                restingBaselineBpm = resolved.bpm,
                baselineSource = resolved.source
            )
            val eval = CalmStateEvaluator.evaluate(
                snapshot = newSnap,
                isBreathingActive = state.isBreathingActive,
                lastAlertEpochMs = state.lastAlertEpochMs,
                currentHourOfDay = hour,
                preferences = state.preferences
            )
            stateAfter = eval.state
            state.copy(
                snapshot = newSnap,
                evaluation = eval,
                latestAiAdvice = payload.latestAiAdviceText ?: state.latestAiAdvice,
                aiAdviceSource = payload.aiSourceLabel ?: state.aiAdviceSource
            )
        }
        scope.launch {
            context.watchDataStore.edit { prefs ->
                val hcBpm = payload.healthConnectRestingBpm
                if (hcBpm != null) {
                    prefs[Keys.HC_BASELINE_BPM] = hcBpm.toString()
                }
                val adviceText = payload.latestAiAdviceText
                if (!adviceText.isNullOrBlank()) {
                    prefs[Keys.LATEST_AI_ADVICE] = adviceText
                }
                val sourceLabel = payload.aiSourceLabel
                if (!sourceLabel.isNullOrBlank()) {
                    prefs[Keys.AI_SOURCE_LABEL] = sourceLabel
                }
            }
        }
        val nextState = stateAfter
        if (nextState != null) {
            lastPushedState = nextState
            lastComplicationPushMs = System.currentTimeMillis()
            forceRefreshComplicationsAndTile()
        }
    }

    /**
     * 毎秒のコンプリケーション更新は Wear OS のスロットリング対象になるため、
     * 状態ランク遷移・3bpm以上の変化・または 5 分経過時のみプッシュする。
     */
    private fun maybeRefreshComplicationsAndTile(state: OmamoriState, bpm: Int?, nowMs: Long) {
        val bpmBucket = bpm?.let { it / 3 }
        val stateChanged = lastPushedState != state
        val significantBpmChange = bpmBucket != null && bpmBucket != lastPushedBpmBucket && (nowMs - lastComplicationPushMs >= 30_000L)
        val fiveMinutesElapsed = (nowMs - lastComplicationPushMs) >= 300_000L
        if (stateChanged || significantBpmChange || fiveMinutesElapsed) {
            lastPushedState = state
            lastPushedBpmBucket = bpmBucket
            lastComplicationPushMs = nowMs
            forceRefreshComplicationsAndTile()
        }
    }

    fun forceRefreshComplicationsAndTile() {
        runCatching {
            ComplicationDataSourceUpdateRequester
                .create(context, ComponentName(context, PetStateComplicationService::class.java))
                .requestUpdateAll()
            ComplicationDataSourceUpdateRequester
                .create(context, ComponentName(context, HeartRateComplicationService::class.java))
                .requestUpdateAll()
            TileService.getUpdater(context).requestUpdate(OmamoriTileService::class.java)
        }
    }
}

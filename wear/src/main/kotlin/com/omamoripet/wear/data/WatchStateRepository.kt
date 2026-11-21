package com.omamoripet.wear.data

import android.content.ComponentName
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.omamoripet.shared.domain.MeasurementFreshness
import com.omamoripet.shared.domain.CalmStateEvaluator
import com.omamoripet.shared.domain.ResolvedBaseline
import com.omamoripet.shared.domain.RestingBaselineLearner
import com.omamoripet.shared.model.AlertPreferences
import com.omamoripet.shared.model.BaselineSource
import com.omamoripet.shared.model.BiometricSnapshot
import com.omamoripet.shared.model.CalmEvaluation
import com.omamoripet.shared.model.MotionState
import com.omamoripet.shared.model.OmamoriState
import com.omamoripet.shared.model.PetHeartRateColor
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

enum class CalibrationResult {
    NONE,
    SUCCESS,
    NO_SIGNAL
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
    val calibrationRemainingSeconds: Int = 0,
    val calibrationResult: CalibrationResult = CalibrationResult.NONE
)

class WatchStateRepository(private val context: Context) {

    private object Keys {
        val LEARNED_SAMPLES = stringPreferencesKey("learned_hr_samples")
        val HC_BASELINE_BPM = stringPreferencesKey("hc_baseline_bpm")
        val HC_MEASURED_AT = longPreferencesKey("hc_measured_at")
        val ALERT_PREFS = stringPreferencesKey("alert_prefs_json")
        val LAST_ALERT_MS = longPreferencesKey("last_alert_epoch_ms")
        val LATEST_AI_ADVICE = stringPreferencesKey("latest_ai_advice")
        val AI_SOURCE_LABEL = stringPreferencesKey("ai_source_label")
        val LAST_LIVE_BPM = intPreferencesKey("last_live_bpm")
        val LAST_MOTION_STATE = stringPreferencesKey("last_motion_state")
        val LAST_ON_BODY = booleanPreferencesKey("last_on_body")
        val LAST_SAMPLE_EPOCH_MS = longPreferencesKey("last_sample_epoch_ms")
        val LAST_SIM_SCENARIO = stringPreferencesKey("last_sim_scenario")
    }

    private companion object {
        const val MIN_SAMPLE_PERSIST_INTERVAL_MS = 30_000L
        const val MIN_SNAPSHOT_PERSIST_INTERVAL_MS = 30_000L
        const val SNAPSHOT_RESTORE_TTL_MS = MeasurementFreshness.HEART_RATE_MAX_AGE_MS
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
    @Volatile
    private var lastSnapshotPersistMs = 0L
    @Volatile
    private var lastPersistedState: OmamoriState? = null
    @Volatile
    private var lastPersistedMotion: MotionState? = null

    private var updateTracker = ComplicationUpdateTracker()

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
            baselineLearner.loadInitial(samples, hcBaseline, prefs[Keys.HC_MEASURED_AT])
            baselineLearner.resolveBaseline()
        }

        val alertPrefs = OmamoriJson.decodeOrNull<AlertPreferences>(prefs[Keys.ALERT_PREFS]) ?: AlertPreferences()
        val lastAlert = prefs[Keys.LAST_ALERT_MS] ?: 0L
        val advice = prefs[Keys.LATEST_AI_ADVICE]
        val aiSource = prefs[Keys.AI_SOURCE_LABEL]

        val savedScenario = prefs[Keys.LAST_SIM_SCENARIO]?.let { name ->
            runCatching { SimulationScenario.valueOf(name) }.getOrNull()
        } ?: SimulationScenario.LIVE_SENSORS
        val savedSampleMs = prefs[Keys.LAST_SAMPLE_EPOCH_MS] ?: 0L
        val nowMs = System.currentTimeMillis()
        val isFreshSnapshot = savedSampleMs > 0L && MeasurementFreshness.isFresh(savedSampleMs, nowMs, SNAPSHOT_RESTORE_TTL_MS)

        val effectiveScenario = if (isFreshSnapshot) savedScenario else SimulationScenario.LIVE_SENSORS
        val restoredBpm = if (isFreshSnapshot) prefs[Keys.LAST_LIVE_BPM] else null
        val restoredMotion = if (isFreshSnapshot) {
            prefs[Keys.LAST_MOTION_STATE]?.let { name ->
                runCatching { MotionState.valueOf(name) }.getOrNull()
            } ?: MotionState.RECENT_MOVEMENT
        } else {
            MotionState.RECENT_MOVEMENT
        }
        val restoredOnBody = if (isFreshSnapshot) (prefs[Keys.LAST_ON_BODY] ?: true) else true

        _uiState.update { current ->
            val updatedSnap = current.snapshot.copy(
                currentHeartRateBpm = restoredBpm,
                restingBaselineBpm = resolved.bpm,
                baselineSource = resolved.source,
                motionState = restoredMotion,
                isWatchOnBody = restoredOnBody,
                sampledAtEpochMs = if (isFreshSnapshot) savedSampleMs else current.snapshot.sampledAtEpochMs,
                isSimulated = effectiveScenario != SimulationScenario.LIVE_SENSORS
            )
            val eval = CalmStateEvaluator.evaluate(
                snapshot = updatedSnap,
                isBreathingActive = current.isBreathingActive,
                lastAlertEpochMs = lastAlert,
                currentHourOfDay = LocalTime.now().hour,
                preferences = alertPrefs
            )
            current.copy(
                snapshot = updatedSnap,
                evaluation = eval,
                preferences = alertPrefs,
                simulationScenario = effectiveScenario,
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
        var scenario = SimulationScenario.LIVE_SENSORS
        _uiState.update { current ->
            scenario = current.simulationScenario
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
        persistSnapshotIfNeeded(snapshot = snapshot, state = eval.state, scenario = scenario)
        maybeRefreshComplicationsAndTile(
            state = eval.state,
            petHeartRateColor = eval.petHeartRateColor,
            motionState = snapshot.motionState,
            bpm = snapshot.currentHeartRateBpm,
            calmScore = eval.calmScore,
            nowMs = snapshot.sampledAtEpochMs
        )
        return eval
    }

    fun setBreathingActive(active: Boolean) {
        val hour = LocalTime.now().hour
        var updatedSnapshot: BiometricSnapshot? = null
        var updatedEval: CalmEvaluation? = null
        _uiState.update { state ->
            val eval = CalmStateEvaluator.evaluate(
                snapshot = state.snapshot,
                isBreathingActive = active,
                lastAlertEpochMs = state.lastAlertEpochMs,
                currentHourOfDay = hour,
                preferences = state.preferences
            )
            updatedSnapshot = state.snapshot
            updatedEval = eval
            state.copy(isBreathingActive = active, evaluation = eval)
        }
        val snap = updatedSnapshot
        val eval = updatedEval
        if (snap != null && eval != null) {
            maybeRefreshComplicationsAndTile(
                state = eval.state,
                petHeartRateColor = eval.petHeartRateColor,
                motionState = snap.motionState,
                bpm = snap.currentHeartRateBpm,
                calmScore = eval.calmScore,
                nowMs = System.currentTimeMillis()
            )
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
        var updatedSnap: BiometricSnapshot? = null
        var updatedEval: CalmEvaluation? = null
        _uiState.update { state ->
            val eval = CalmStateEvaluator.evaluate(
                snapshot = state.snapshot,
                isBreathingActive = state.isBreathingActive,
                lastAlertEpochMs = state.lastAlertEpochMs,
                currentHourOfDay = hour,
                preferences = preferences
            )
            updatedSnap = state.snapshot
            updatedEval = eval
            state.copy(preferences = preferences, evaluation = eval)
        }
        val snap = updatedSnap
        val eval = updatedEval
        if (snap != null && eval != null) {
            maybeRefreshComplicationsAndTile(
                state = eval.state,
                petHeartRateColor = eval.petHeartRateColor,
                motionState = snap.motionState,
                bpm = snap.currentHeartRateBpm,
                calmScore = eval.calmScore,
                nowMs = System.currentTimeMillis()
            )
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
                sustainedPetElevationSeconds = 0,
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
                currentHeartRateBpm = baseline.bpm + current.preferences.elevationThresholdBpm + 3,
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

    fun setCalibrationProgress(
        active: Boolean,
        remainingSeconds: Int,
        result: CalibrationResult = CalibrationResult.NONE
    ) {
        _uiState.update {
            it.copy(
                isCalibrating = active,
                calibrationRemainingSeconds = remainingSeconds,
                calibrationResult = result
            )
        }
    }

    fun clearCalibrationResult() {
        _uiState.update { it.copy(calibrationResult = CalibrationResult.NONE) }
    }

    suspend fun applyPhoneBaselineAndAdvice(payload: PhoneBaselinePayload) {
        awaitInitialized()
        val resolved = synchronized(baselineLock) {
            baselineLearner.updateHealthConnectBaseline(payload.healthConnectRestingBpm, payload.restingMeasuredAtEpochMs)
            baselineLearner.resolveBaseline()
        }
        val hour = LocalTime.now().hour
        var updatedSnap: BiometricSnapshot? = null
        var updatedEval: CalmEvaluation? = null
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
            updatedSnap = newSnap
            updatedEval = eval
            state.copy(
                snapshot = newSnap,
                evaluation = eval,
                latestAiAdvice = payload.latestAiAdviceText ?: state.latestAiAdvice,
                aiAdviceSource = payload.aiSourceLabel ?: state.aiAdviceSource
            )
        }
        context.watchDataStore.edit { prefs ->
            if (resolved.source == BaselineSource.HEALTH_CONNECT) {
                prefs[Keys.HC_BASELINE_BPM] = resolved.bpm.toString()
                prefs[Keys.HC_MEASURED_AT] = payload.restingMeasuredAtEpochMs!!
            } else {
                prefs.remove(Keys.HC_BASELINE_BPM)
                prefs.remove(Keys.HC_MEASURED_AT)
            }
            val adviceText = payload.latestAiAdviceText
            if (!adviceText.isNullOrBlank()) prefs[Keys.LATEST_AI_ADVICE] = adviceText
            val sourceLabel = payload.aiSourceLabel
            if (!sourceLabel.isNullOrBlank()) prefs[Keys.AI_SOURCE_LABEL] = sourceLabel
        }
        val snap = updatedSnap
        val eval = updatedEval
        if (snap != null && eval != null) {
            updateTracker.recordForcedPush(
                state = eval.state,
                petHeartRateColor = eval.petHeartRateColor,
                motionState = snap.motionState,
                bpm = snap.currentHeartRateBpm,
                calmScore = eval.calmScore,
                nowMs = System.currentTimeMillis()
            )
            forceRefreshComplicationsAndTile()
        }
    }

    private fun persistSnapshotIfNeeded(
        snapshot: BiometricSnapshot,
        state: OmamoriState,
        scenario: SimulationScenario
    ) {
        val now = System.currentTimeMillis()
        val changedSignificantly = lastPersistedState != state || lastPersistedMotion != snapshot.motionState
        if (!changedSignificantly && (now - lastSnapshotPersistMs) < MIN_SNAPSHOT_PERSIST_INTERVAL_MS) {
            return
        }
        lastPersistedState = state
        lastPersistedMotion = snapshot.motionState
        lastSnapshotPersistMs = now
        scope.launch {
            context.watchDataStore.edit { prefs ->
                val bpm = snapshot.currentHeartRateBpm
                if (bpm != null) {
                    prefs[Keys.LAST_LIVE_BPM] = bpm
                } else {
                    prefs.remove(Keys.LAST_LIVE_BPM)
                }
                prefs[Keys.LAST_MOTION_STATE] = snapshot.motionState.name
                prefs[Keys.LAST_ON_BODY] = snapshot.isWatchOnBody
                prefs[Keys.LAST_SAMPLE_EPOCH_MS] = snapshot.sampledAtEpochMs
                prefs[Keys.LAST_SIM_SCENARIO] = scenario.name
            }
        }
    }

    /**
     * 毎秒のコンプリケーション更新は Wear OS のスロットリング対象になるため、
     * 状態・移動文脈・計測有無の切り替わりは即時プッシュし、心拍・落ち着きスコア変化は 30 秒以上間隔を空けてプッシュする。
     */
    private fun maybeRefreshComplicationsAndTile(
        state: OmamoriState,
        petHeartRateColor: PetHeartRateColor?,
        motionState: MotionState,
        bpm: Int?,
        calmScore: Int,
        nowMs: Long
    ) {
        if (updateTracker.shouldPushAndMark(state, motionState, bpm, calmScore, nowMs, petHeartRateColor)) {
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

/**
 * コンプリケーション＆タイルの更新スロットリング判定をカプセル化するヘルパー。
 */
internal class ComplicationUpdateTracker {
    private var lastPushedMs: Long = 0L
    private var lastState: OmamoriState? = null
    private var lastPetHeartRateColor: PetHeartRateColor? = null
    private var lastMotionState: MotionState? = null
    private var lastHasBpm: Boolean? = null
    private var lastBpmBucket: Int? = null
    private var lastCalmBucket: Int? = null

    @Synchronized
    fun shouldPushAndMark(
        state: OmamoriState,
        motionState: MotionState,
        bpm: Int?,
        calmScore: Int,
        nowMs: Long,
        petHeartRateColor: PetHeartRateColor? = null
    ): Boolean {
        val hasBpm = bpm != null
        val bpmBucket = bpm?.let { it / 3 }
        val calmBucket = calmScore / 5

        val urgentTransition = lastState != state ||
            lastPetHeartRateColor != petHeartRateColor ||
            lastMotionState != motionState ||
            lastHasBpm != hasBpm
        val elapsed = nowMs - lastPushedMs
        val throttledMetricChange = elapsed >= 30_000L && (
            (bpmBucket != null && bpmBucket != lastBpmBucket) ||
                calmBucket != lastCalmBucket
            )
        val heartbeatElapsed = elapsed >= 300_000L

        if (!urgentTransition && !throttledMetricChange && !heartbeatElapsed) {
            return false
        }

        lastState = state
        lastPetHeartRateColor = petHeartRateColor
        lastMotionState = motionState
        lastHasBpm = hasBpm
        lastBpmBucket = bpmBucket
        lastCalmBucket = calmBucket
        lastPushedMs = nowMs
        return true
    }

    @Synchronized
    fun recordForcedPush(
        state: OmamoriState,
        motionState: MotionState,
        bpm: Int?,
        calmScore: Int,
        nowMs: Long,
        petHeartRateColor: PetHeartRateColor?
    ) {
        lastState = state
        lastPetHeartRateColor = petHeartRateColor
        lastMotionState = motionState
        lastHasBpm = bpm != null
        lastBpmBucket = bpm?.let { it / 3 }
        lastCalmBucket = calmScore / 5
        lastPushedMs = nowMs
    }
}

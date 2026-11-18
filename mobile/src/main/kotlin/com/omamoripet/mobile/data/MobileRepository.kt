package com.omamoripet.mobile.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.omamoripet.mobile.ai.AdvisorInput
import com.omamoripet.mobile.ai.AiBackendStatus
import com.omamoripet.mobile.ai.CalmAdvisorEngine
import com.omamoripet.mobile.ai.GeminiNanoAdvisorEngine
import com.omamoripet.mobile.health.HealthConnectAvailability
import com.omamoripet.mobile.health.HealthConnectManager
import com.omamoripet.mobile.sync.PhoneWearSyncClient
import com.omamoripet.shared.model.AlertPreferences
import com.omamoripet.shared.model.BaselineSource
import com.omamoripet.shared.model.BreathingSessionRecord
import com.omamoripet.shared.model.CURRENT_ONBOARDING_VERSION
import com.omamoripet.shared.model.CalmEventRecord
import com.omamoripet.shared.model.MotionState
import com.omamoripet.shared.model.OmamoriState
import com.omamoripet.shared.model.OnboardingAnalyticsTracker
import com.omamoripet.shared.model.OnboardingAnswers
import com.omamoripet.shared.model.OnboardingHapticChoice
import com.omamoripet.shared.sync.OmamoriJson
import com.omamoripet.shared.sync.PhoneBaselinePayload
import com.omamoripet.shared.sync.PreferencesSyncPayload
import com.omamoripet.shared.sync.WatchLivePayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

private val Context.mobileDataStore: DataStore<Preferences> by preferencesDataStore(name = "omamori_mobile_prefs")

enum class MobileDemoMode {
    LIVE_WATCH,
    DEMO_CALM,
    DEMO_ELEVATED_AT_REST,
    DEMO_MOVING
}

data class MobileUiState(
    val omamoriState: OmamoriState = OmamoriState.RESTING,
    val currentHeartRateBpm: Int? = null,
    val restingBaselineBpm: Int = 65,
    val baselineSource: BaselineSource = BaselineSource.DEFAULT_FALLBACK,
    val motionState: MotionState = MotionState.OFF_BODY,
    val calmScore: Int = 0,
    val isSimulated: Boolean = false,
    val demoMode: MobileDemoMode = MobileDemoMode.LIVE_WATCH,
    val healthConnectAvailability: HealthConnectAvailability = HealthConnectAvailability.UNAVAILABLE,
    val hasHealthConnectPermission: Boolean = false,
    val hcPermissionAttempted: Boolean = false,
    val hcDismissedOnHome: Boolean = false,
    val healthConnectRestingBpm: Int? = null,
    val healthConnectMeasuredAtMs: Long? = null,
    val aiAdviceText: String = "",
    val usedGeminiNano: Boolean = false,
    val aiBackendStatus: AiBackendStatus = AiBackendStatus.FALLBACK_ONLY,
    val isAiRefreshing: Boolean = false,
    val isAiPreparing: Boolean = false,
    val aiPrepareFailed: Boolean = false,
    val preferences: AlertPreferences = AlertPreferences(),
    val calmEvents: List<CalmEventRecord> = emptyList(),
    val breathingSessions: List<BreathingSessionRecord> = emptyList(),
    val lastWatchSyncMs: Long? = null,
    val isWatchConnected: Boolean = false,
    val isOnboardingLoaded: Boolean = false,
    val completedOnboardingVersion: Int = 0,
    val onboardingAnswers: OnboardingAnswers = OnboardingAnswers(),
    val hasCompletedFirstBreathing: Boolean = false,
    val watchGuideDismissedOnHome: Boolean = false
)

class MobileRepository(
    private val context: Context,
    val healthConnectManager: HealthConnectManager = HealthConnectManager(context),
    private val advisorEngine: CalmAdvisorEngine = GeminiNanoAdvisorEngine(),
    private val wearSyncClient: PhoneWearSyncClient = PhoneWearSyncClient(context)
) {
    private object Keys {
        val ALERT_PREFS = stringPreferencesKey("mobile_alert_prefs")
        val CALM_EVENTS = stringPreferencesKey("calm_events_json")
        val BREATH_SESSIONS = stringPreferencesKey("breath_sessions_json")
        val HC_RESTING_BPM = stringPreferencesKey("hc_resting_bpm")
        val HC_MEASURED_AT = stringPreferencesKey("hc_measured_at")
        val HC_ATTEMPTED = booleanPreferencesKey("hc_permission_attempted")
        val HC_DISMISSED_HOME = booleanPreferencesKey("hc_dismissed_home")
        val WATCH_GUIDE_DISMISSED = booleanPreferencesKey("watch_guide_dismissed")
        val ONBOARDING_VERSION = intPreferencesKey("completed_onboarding_version")
        val ONBOARDING_ANSWERS = stringPreferencesKey("onboarding_answers_json")
        val FIRST_BREATH_DONE = booleanPreferencesKey("first_breathing_completed")
    }

    private data class BaselinePushSignature(
        val healthConnectRestingBpm: Int?,
        val restingMeasuredAtEpochMs: Long?,
        val latestAiAdviceText: String?,
        val aiSourceLabel: String?
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _uiState = MutableStateFlow(MobileUiState())
    val uiState: StateFlow<MobileUiState> = _uiState.asStateFlow()

    val onboardingAnalytics = OnboardingAnalyticsTracker()

    private var aiRefreshJob: Job? = null
    private var lastPushedSignature: BaselinePushSignature? = null
    private var lastRealWatchPayload: WatchLivePayload? = null
    private var hasConnectedWearNode: Boolean = false

    init {
        scope.launch {
            restoreFromDisk()
            refreshWatchConnection()
            refreshHealthConnect()
            refreshAiAdvice()
        }
    }

    private suspend fun restoreFromDisk() {
        val prefs = context.mobileDataStore.data.first()
        val alertPrefs = OmamoriJson.decodeOrNull<AlertPreferences>(prefs[Keys.ALERT_PREFS]) ?: AlertPreferences()
        val events = OmamoriJson.decodeOrNull<List<CalmEventRecord>>(prefs[Keys.CALM_EVENTS]) ?: emptyList()
        val sessions = OmamoriJson.decodeOrNull<List<BreathingSessionRecord>>(prefs[Keys.BREATH_SESSIONS]) ?: emptyList()
        val hcBpm = prefs[Keys.HC_RESTING_BPM]?.toIntOrNull()
        val hcTime = prefs[Keys.HC_MEASURED_AT]?.toLongOrNull()
        val hcAttempted = prefs[Keys.HC_ATTEMPTED] ?: false
        val hcDismissed = prefs[Keys.HC_DISMISSED_HOME] ?: false
        val watchGuideDismissed = prefs[Keys.WATCH_GUIDE_DISMISSED] ?: false
        val onboardingVersion = prefs[Keys.ONBOARDING_VERSION] ?: 0
        val onboardingAnswers = OmamoriJson.decodeOrNull<OnboardingAnswers>(prefs[Keys.ONBOARDING_ANSWERS])
            ?: OnboardingAnswers()
        val firstBreathDone = (prefs[Keys.FIRST_BREATH_DONE] ?: false) || sessions.any { !it.isSimulated }

        _uiState.update { state ->
            state.copy(
                preferences = alertPrefs,
                calmEvents = events,
                breathingSessions = sessions,
                healthConnectRestingBpm = hcBpm,
                healthConnectMeasuredAtMs = hcTime,
                restingBaselineBpm = hcBpm ?: state.restingBaselineBpm,
                baselineSource = if (hcBpm != null) BaselineSource.HEALTH_CONNECT else state.baselineSource,
                hcPermissionAttempted = hcAttempted,
                hcDismissedOnHome = hcDismissed,
                watchGuideDismissedOnHome = watchGuideDismissed,
                isOnboardingLoaded = true,
                completedOnboardingVersion = onboardingVersion,
                onboardingAnswers = onboardingAnswers,
                hasCompletedFirstBreathing = firstBreathDone
            )
        }
    }

    suspend fun refreshWatchConnection() {
        hasConnectedWearNode = wearSyncClient.hasConnectedWatch()
        _uiState.update { state ->
            state.copy(
                isWatchConnected = hasConnectedWearNode || lastRealWatchPayload != null
            )
        }
    }

    suspend fun refreshHealthConnect() {
        val availability = healthConnectManager.checkAvailability()
        val hasPerm = healthConnectManager.hasAllPermissions()
        val baseline = if (hasPerm) healthConnectManager.readLatestRestingHeartRate() else null

        _uiState.update { state ->
            state.copy(
                healthConnectAvailability = availability,
                hasHealthConnectPermission = hasPerm,
                healthConnectRestingBpm = baseline?.bpm ?: state.healthConnectRestingBpm,
                healthConnectMeasuredAtMs = baseline?.measuredAtEpochMs ?: state.healthConnectMeasuredAtMs,
                restingBaselineBpm = baseline?.bpm ?: state.restingBaselineBpm,
                baselineSource = if (baseline != null) BaselineSource.HEALTH_CONNECT else state.baselineSource
            )
        }

        if (baseline != null) {
            context.mobileDataStore.edit { prefs ->
                prefs[Keys.HC_RESTING_BPM] = baseline.bpm.toString()
                prefs[Keys.HC_MEASURED_AT] = baseline.measuredAtEpochMs.toString()
            }
            pushBaselineToWatch()
        }
    }

    fun markHealthConnectPermissionAttempted() {
        _uiState.update { it.copy(hcPermissionAttempted = true) }
        scope.launch {
            context.mobileDataStore.edit { it[Keys.HC_ATTEMPTED] = true }
        }
    }

    fun dismissHealthConnectOnHome() {
        _uiState.update { it.copy(hcDismissedOnHome = true) }
        scope.launch {
            context.mobileDataStore.edit { it[Keys.HC_DISMISSED_HOME] = true }
        }
    }

    fun dismissWatchGuideOnHome() {
        _uiState.update { it.copy(watchGuideDismissedOnHome = true) }
        scope.launch {
            context.mobileDataStore.edit { it[Keys.WATCH_GUIDE_DISMISSED] = true }
        }
    }

    fun applyWatchLivePayload(payload: WatchLivePayload) {
        if (!payload.isSimulated) {
            lastRealWatchPayload = payload
        }
        val wasConnected = _uiState.value.isWatchConnected
        if (_uiState.value.demoMode != MobileDemoMode.LIVE_WATCH) {
            if (!wasConnected) {
                _uiState.update { it.copy(isWatchConnected = true) }
            }
            return
        }
        val previous = _uiState.value
        val stateChanged = previous.omamoriState != payload.omamoriState ||
            previous.motionState != payload.motionState ||
            !previous.isWatchConnected
        _uiState.update { state ->
            state.copy(
                omamoriState = payload.omamoriState,
                currentHeartRateBpm = payload.currentHeartRateBpm,
                restingBaselineBpm = state.healthConnectRestingBpm ?: payload.restingBaselineBpm,
                baselineSource = if (state.healthConnectRestingBpm != null) BaselineSource.HEALTH_CONNECT else payload.baselineSource,
                motionState = payload.motionState,
                calmScore = payload.calmScore,
                isSimulated = payload.isSimulated,
                lastWatchSyncMs = payload.updatedAtEpochMs,
                isWatchConnected = true
            )
        }
        if (stateChanged) {
            scheduleAiAdviceRefresh(debounceMs = 300L)
        }
    }

    fun setDemoMode(mode: MobileDemoMode) {
        val base = _uiState.value.healthConnectRestingBpm ?: lastRealWatchPayload?.restingBaselineBpm ?: 64
        _uiState.update { state ->
            when (mode) {
                MobileDemoMode.LIVE_WATCH -> {
                    val real = lastRealWatchPayload
                    if (real != null) {
                        state.copy(
                            demoMode = mode,
                            omamoriState = real.omamoriState,
                            currentHeartRateBpm = real.currentHeartRateBpm,
                            restingBaselineBpm = state.healthConnectRestingBpm ?: real.restingBaselineBpm,
                            baselineSource = if (state.healthConnectRestingBpm != null) BaselineSource.HEALTH_CONNECT else real.baselineSource,
                            motionState = real.motionState,
                            calmScore = real.calmScore,
                            isSimulated = false,
                            lastWatchSyncMs = real.updatedAtEpochMs,
                            isWatchConnected = true
                        )
                    } else {
                        state.copy(
                            demoMode = mode,
                            omamoriState = OmamoriState.RESTING,
                            currentHeartRateBpm = null,
                            restingBaselineBpm = state.healthConnectRestingBpm ?: 65,
                            baselineSource = if (state.healthConnectRestingBpm != null) BaselineSource.HEALTH_CONNECT else BaselineSource.DEFAULT_FALLBACK,
                            motionState = MotionState.OFF_BODY,
                            calmScore = 0,
                            isSimulated = false,
                            lastWatchSyncMs = null,
                            isWatchConnected = hasConnectedWearNode
                        )
                    }
                }
                MobileDemoMode.DEMO_CALM -> state.copy(
                    demoMode = mode,
                    omamoriState = OmamoriState.CALM,
                    currentHeartRateBpm = base + 2,
                    restingBaselineBpm = base,
                    motionState = MotionState.STILL_CONFIRMED,
                    calmScore = 94,
                    isSimulated = true
                )
                MobileDemoMode.DEMO_ELEVATED_AT_REST -> state.copy(
                    demoMode = mode,
                    omamoriState = OmamoriState.ELEVATED_AT_REST,
                    currentHeartRateBpm = base + state.preferences.elevationThresholdBpm + 5,
                    restingBaselineBpm = base,
                    motionState = MotionState.STILL_CONFIRMED,
                    calmScore = 60,
                    isSimulated = true
                )
                MobileDemoMode.DEMO_MOVING -> state.copy(
                    demoMode = mode,
                    omamoriState = OmamoriState.CALM,
                    currentHeartRateBpm = base + 24,
                    restingBaselineBpm = base,
                    motionState = MotionState.RECENT_MOVEMENT,
                    calmScore = 84,
                    isSimulated = true
                )
            }
        }
        scheduleAiAdviceRefresh(debounceMs = 150L)
    }

    private fun scheduleAiAdviceRefresh(debounceMs: Long) {
        aiRefreshJob?.cancel()
        aiRefreshJob = scope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            refreshAiAdviceInternal()
        }
    }

    suspend fun refreshAiAdvice() {
        aiRefreshJob?.cancel()
        refreshAiAdviceInternal()
    }

    private suspend fun refreshAiAdviceInternal() {
        _uiState.update { it.copy(isAiRefreshing = true) }
        val state = _uiState.value
        val todayStart = System.currentTimeMillis() - 24 * 60 * 60_000L
        val todayEvents = state.calmEvents.count { it.timestampEpochMs >= todayStart }
        val todayBreaths = state.breathingSessions.filter { it.startedAtEpochMs >= todayStart }
        val lastDelta = todayBreaths.firstOrNull()?.let { s ->
            if (s.preBpm != null && s.postBpm != null) s.postBpm!! - s.preBpm!! else null
        }
        val output = advisorEngine.generateReflection(
            AdvisorInput(
                omamoriState = state.omamoriState,
                currentBpm = state.currentHeartRateBpm,
                restingBaselineBpm = state.restingBaselineBpm,
                motionState = state.motionState,
                todayElevatedCount = todayEvents,
                todayBreathingCount = todayBreaths.size,
                lastBreathingDeltaBpm = lastDelta,
                isWatchConnected = state.isWatchConnected || state.isSimulated
            )
        )
        _uiState.update {
            it.copy(
                aiAdviceText = output.text,
                usedGeminiNano = output.usedGeminiNano,
                aiBackendStatus = output.backendStatus,
                isAiRefreshing = false
            )
        }
        pushBaselineToWatch()
    }

    suspend fun prepareGeminiNano() {
        _uiState.update { it.copy(isAiPreparing = true, isAiRefreshing = true, aiPrepareFailed = false) }
        val success = advisorEngine.prepareGeminiNano()
        _uiState.update { it.copy(isAiPreparing = false, aiPrepareFailed = !success) }
        refreshAiAdvice()
    }

    fun updatePreferences(updated: AlertPreferences, syncToWatch: Boolean = true) {
        _uiState.update { it.copy(preferences = updated) }
        scope.launch {
            context.mobileDataStore.edit { it[Keys.ALERT_PREFS] = OmamoriJson.encode(updated) }
            if (syncToWatch) {
                wearSyncClient.pushPreferences(PreferencesSyncPayload(updated, System.currentTimeMillis()))
            }
        }
    }

    fun completeOnboarding(answers: OnboardingAnswers, version: Int = CURRENT_ONBOARDING_VERSION) {
        val hapticsEnabled = when (answers.hapticChoice) {
            OnboardingHapticChoice.VISUAL_ONLY -> false
            OnboardingHapticChoice.WITH_HAPTICS, OnboardingHapticChoice.UNDECIDED -> true
        }
        val updatedPrefs = _uiState.value.preferences.copy(breathingHapticsEnabled = hapticsEnabled)
        _uiState.update { state ->
            state.copy(
                completedOnboardingVersion = version,
                onboardingAnswers = answers,
                preferences = updatedPrefs,
                watchGuideDismissedOnHome = false
            )
        }
        scope.launch {
            context.mobileDataStore.edit { prefs ->
                prefs[Keys.ONBOARDING_VERSION] = version
                prefs[Keys.ONBOARDING_ANSWERS] = OmamoriJson.encode(answers)
                prefs[Keys.ALERT_PREFS] = OmamoriJson.encode(updatedPrefs)
                prefs[Keys.WATCH_GUIDE_DISMISSED] = false
            }
            wearSyncClient.pushPreferences(PreferencesSyncPayload(updatedPrefs, System.currentTimeMillis()))
        }
    }

    fun resetOnboardingAndAnswers() {
        val defaultAnswers = OnboardingAnswers()
        val updatedPrefs = _uiState.value.preferences.copy(breathingHapticsEnabled = true)
        _uiState.update { state ->
            state.copy(
                completedOnboardingVersion = 0,
                onboardingAnswers = defaultAnswers,
                hcDismissedOnHome = false,
                watchGuideDismissedOnHome = false,
                preferences = updatedPrefs
            )
        }
        scope.launch {
            context.mobileDataStore.edit { prefs ->
                prefs.remove(Keys.ONBOARDING_VERSION)
                prefs.remove(Keys.ONBOARDING_ANSWERS)
                prefs.remove(Keys.HC_DISMISSED_HOME)
                prefs.remove(Keys.WATCH_GUIDE_DISMISSED)
                prefs[Keys.ALERT_PREFS] = OmamoriJson.encode(updatedPrefs)
            }
            wearSyncClient.pushPreferences(PreferencesSyncPayload(updatedPrefs, System.currentTimeMillis()))
        }
    }

    fun recordBreathingSession(record: BreathingSessionRecord) {
        val updated = (listOf(record) + _uiState.value.breathingSessions).take(50)
        val nowFirstDone = _uiState.value.hasCompletedFirstBreathing || !record.isSimulated
        _uiState.update {
            it.copy(
                breathingSessions = updated,
                hasCompletedFirstBreathing = nowFirstDone
            )
        }
        scope.launch {
            context.mobileDataStore.edit { prefs ->
                prefs[Keys.BREATH_SESSIONS] = OmamoriJson.encode(updated)
                if (!record.isSimulated) {
                    prefs[Keys.FIRST_BREATH_DONE] = true
                }
            }
            scheduleAiAdviceRefresh(debounceMs = 0L)
        }
    }

    fun recordCalmEvent(record: CalmEventRecord) {
        val updated = (listOf(record) + _uiState.value.calmEvents).take(50)
        _uiState.update { it.copy(calmEvents = updated) }
        scope.launch {
            context.mobileDataStore.edit { it[Keys.CALM_EVENTS] = OmamoriJson.encode(updated) }
        }
    }

    fun addLocalBreathingCompletion(durationSec: Int) {
        val state = _uiState.value
        // 憶測なし原則を厳守し、スマホ単体完了時は実測値がない限り postBpm を捏造しない
        val measuredPreBpm = if (!state.isSimulated && state.lastWatchSyncMs != null) state.currentHeartRateBpm else null
        recordBreathingSession(
            BreathingSessionRecord(
                id = UUID.randomUUID().toString(),
                startedAtEpochMs = System.currentTimeMillis() - durationSec * 1000L,
                durationSeconds = durationSec,
                preBpm = measuredPreBpm,
                postBpm = null,
                triggeredByAlert = false,
                isSimulated = state.isSimulated
            )
        )
    }

    fun clearSimulatedHistory() {
        val realEvents = _uiState.value.calmEvents.filterNot { it.isSimulated }
        val realSessions = _uiState.value.breathingSessions.filterNot { it.isSimulated }
        _uiState.update { it.copy(calmEvents = realEvents, breathingSessions = realSessions) }
        scope.launch {
            context.mobileDataStore.edit {
                it[Keys.CALM_EVENTS] = OmamoriJson.encode(realEvents)
                it[Keys.BREATH_SESSIONS] = OmamoriJson.encode(realSessions)
            }
        }
    }

    suspend fun triggerWatchBreathing(): Boolean = wearSyncClient.sendStartBreathingToWatch()

    private suspend fun pushBaselineToWatch() {
        val s = _uiState.value
        val adviceText = s.aiAdviceText.ifBlank { null }
        val sourceLabel = if (s.usedGeminiNano) "Gemini Nano" else "Local"
        val signature = BaselinePushSignature(
            healthConnectRestingBpm = s.healthConnectRestingBpm,
            restingMeasuredAtEpochMs = s.healthConnectMeasuredAtMs,
            latestAiAdviceText = adviceText,
            aiSourceLabel = sourceLabel
        )
        if (signature == lastPushedSignature) return
        lastPushedSignature = signature
        wearSyncClient.pushBaselineAndAdvice(
            PhoneBaselinePayload(
                healthConnectRestingBpm = s.healthConnectRestingBpm,
                restingMeasuredAtEpochMs = s.healthConnectMeasuredAtMs,
                latestAiAdviceText = adviceText,
                aiSourceLabel = sourceLabel,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
    }
}

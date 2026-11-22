package com.omamoripet.mobile.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
import com.omamoripet.mobile.health.readFeelingPressure
import com.omamoripet.mobile.health.RestingHeartRateBaseline
import com.omamoripet.mobile.sync.PhoneWearSyncClient
import com.omamoripet.shared.domain.MeasurementFreshness
import com.omamoripet.shared.model.AlertPreferences
import com.omamoripet.shared.model.BaselineSource
import com.omamoripet.shared.model.BreathingSessionRecord
import com.omamoripet.shared.model.FeelingContext
import com.omamoripet.shared.model.CURRENT_ONBOARDING_VERSION
import com.omamoripet.shared.model.CalmEventRecord
import com.omamoripet.shared.model.Feeling
import com.omamoripet.shared.model.MotionState
import com.omamoripet.shared.model.OmamoriState
import com.omamoripet.shared.model.PetHeartRateColor
import com.omamoripet.shared.model.OnboardingAnalyticsTracker
import com.omamoripet.shared.model.OnboardingAnswers
import com.omamoripet.shared.model.OnboardingHapticChoice
import com.omamoripet.shared.model.visibleForRemainingTime
import com.omamoripet.shared.sync.OmamoriJson
import com.omamoripet.shared.sync.PhoneBaselinePayload
import com.omamoripet.shared.sync.PreferencesSyncPayload
import com.omamoripet.shared.sync.WatchLivePayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
    val feelingHealthPermissionsGranted: Int = 0,
    val hasFeelingStepsPermission: Boolean = false,
    val hasFeelingHeartPermission: Boolean = false,
    val hasPeriodPermission: Boolean = false,
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
    val watchGuideDismissedOnHome: Boolean = false,
    val petHeartRateColor: PetHeartRateColor? = null,
    val feelingRecords: List<FeelingRecord> = emptyList()
)

class MobileRepository(
    private val context: Context,
    val healthConnectManager: HealthConnectManager = HealthConnectManager(context),
    private val advisorEngine: CalmAdvisorEngine = GeminiNanoAdvisorEngine(),
    private val wearSyncClient: PhoneWearSyncClient = PhoneWearSyncClient(context)
) {
    private object Keys {
        val ALERT_PREFS = stringPreferencesKey("mobile_alert_prefs")
        val HC_RESTING_BPM = stringPreferencesKey("hc_resting_bpm")
        val HC_MEASURED_AT = stringPreferencesKey("hc_measured_at")
        val HC_ATTEMPTED = booleanPreferencesKey("hc_permission_attempted")
        val HC_DISMISSED_HOME = booleanPreferencesKey("hc_dismissed_home")
        val WATCH_GUIDE_DISMISSED = booleanPreferencesKey("watch_guide_dismissed")
        val ONBOARDING_VERSION = intPreferencesKey("completed_onboarding_version")
        val ONBOARDING_ANSWERS = stringPreferencesKey("onboarding_answers_json")
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
    private val historyStore = MobileHistoryStore(context.mobileDataStore)
    private val feelingHistory = FeelingHistoryStore(
        PreferenceDataStoreFactory.create(scope = scope) {
            File(context.noBackupFilesDir, "feelings.preferences_pb")
        }
    )
    private val historyMutex = Mutex()
    private val feelingHistoryMutex = Mutex()
    private val feelingHealthMutex = Mutex()
    private val feelingHealthAttempts = linkedMapOf<String, Long>()
    private val healthConnectMutex = Mutex()
    private val baselineSyncMutex = Mutex()
    private val initialLoadJob = scope.launch { restoreFromDisk() }
    private val feelingLoadJob = scope.launch {
        val records = try {
            feelingHistory.records.first()
        } catch (_: IOException) {
            emptyList()
        }
        _uiState.update { it.copy(feelingRecords = records) }
    }

    suspend fun awaitInitialized() = initialLoadJob.join()

    init {
        scope.launch { syncCurrentFeelingFace() }
        scope.launch {
            awaitInitialized()
            refreshWatchConnection()
            refreshHealthConnect()
            refreshAiAdvice()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun syncCurrentFeelingFace() {
        feelingLoadJob.join()
        uiState.map { it.feelingRecords.firstOrNull()?.face }
            .distinctUntilChanged()
            .flatMapLatest { it.visibleForRemainingTime() }
            .conflate()
            .collect { face ->
                wearSyncClient.setPhoneFeelingFace(face?.takeIf { it.remainingMs(System.currentTimeMillis()) > 0 })
            }
    }

    private suspend fun restoreFromDisk() {
        val prefs = context.mobileDataStore.data.first()
        val alertPrefs = OmamoriJson.decodeOrNull<AlertPreferences>(prefs[Keys.ALERT_PREFS]) ?: AlertPreferences()
        val history = MobileHistoryStore.read(prefs)
        val savedHcTime = prefs[Keys.HC_MEASURED_AT]?.toLongOrNull()
        val hcBpm = prefs[Keys.HC_RESTING_BPM]?.toIntOrNull()?.takeIf {
            MeasurementFreshness.isFresh(savedHcTime, System.currentTimeMillis(), MeasurementFreshness.HEALTH_CONNECT_MAX_AGE_MS)
        }
        val hcTime = savedHcTime.takeIf { hcBpm != null }
        val hcAttempted = prefs[Keys.HC_ATTEMPTED] ?: false
        val hcDismissed = prefs[Keys.HC_DISMISSED_HOME] ?: false
        val watchGuideDismissed = prefs[Keys.WATCH_GUIDE_DISMISSED] ?: false
        val onboardingVersion = prefs[Keys.ONBOARDING_VERSION] ?: 0
        val onboardingAnswers = OmamoriJson.decodeOrNull<OnboardingAnswers>(prefs[Keys.ONBOARDING_ANSWERS])
            ?: OnboardingAnswers()

        _uiState.update { state ->
            state.copy(
                preferences = alertPrefs,
                calmEvents = history.events,
                breathingSessions = history.sessions,
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
                hasCompletedFirstBreathing = history.firstBreathingDone
            )
        }
    }

    suspend fun refreshWatchConnection() {
        awaitInitialized()
        hasConnectedWearNode = wearSyncClient.hasConnectedWatch()
        val now = System.currentTimeMillis()
        if (!hasConnectedWearNode || (!_uiState.value.isSimulated && !isFreshWatchPayload(lastRealWatchPayload, now))) {
            invalidateWatchData()
        }
        _uiState.update { it.copy(isWatchConnected = hasConnectedWearNode) }
        wearSyncClient.drainHistory(::recordBreathingSession, ::recordCalmEvent)
    }

    private fun isFreshWatchPayload(payload: WatchLivePayload?, nowMs: Long = System.currentTimeMillis()): Boolean =
        payload != null && MeasurementFreshness.isFresh(
            payload.updatedAtEpochMs, nowMs, MeasurementFreshness.WATCH_STATE_MAX_AGE_MS
        )

    private fun invalidateWatchData() {
        lastRealWatchPayload = null
        val wasLive = _uiState.value.demoMode == MobileDemoMode.LIVE_WATCH
        val hadData = _uiState.value.lastWatchSyncMs != null
        if (wasLive) {
            _uiState.update { it.withoutWatchMeasurements() }
            if (hadData) scheduleAiAdviceRefresh(debounceMs = 0L)
        }
    }

    suspend fun refreshHealthConnect() {
        awaitInitialized()
        healthConnectMutex.withLock {
            val availability = healthConnectManager.checkAvailability()
            val granted = healthConnectManager.grantedPermissions()
            var hasPerm = granted.containsAll(healthConnectManager.requiredPermissions)
            val result = if (hasPerm) healthConnectManager.readLatestRestingHeartRate() else Result.success(null)
            if (result.exceptionOrNull() is SecurityException) hasPerm = false
            val cached = _uiState.value
            val baseline = if (result.isSuccess) result.getOrNull() else {
                if (hasPerm && MeasurementFreshness.isFresh(
                        cached.healthConnectMeasuredAtMs, System.currentTimeMillis(), MeasurementFreshness.HEALTH_CONNECT_MAX_AGE_MS
                    )) cached.healthConnectRestingBpm?.let {
                    RestingHeartRateBaseline(it, cached.healthConnectMeasuredAtMs!!)
                } else null
            }
            val watch = lastRealWatchPayload?.takeIf { isFreshWatchPayload(it) && it.baselineSource != BaselineSource.HEALTH_CONNECT }
            val restingBaselineBpm = baseline?.bpm ?: watch?.restingBaselineBpm ?: 65
            _uiState.update { state ->
                state.copy(
                    healthConnectAvailability = availability,
                    hasHealthConnectPermission = hasPerm,
                    feelingHealthPermissionsGranted = healthConnectManager.feelingPermissions.count { it in granted },
                    hasFeelingStepsPermission = healthConnectManager.feelingStepsPermission in granted,
                    hasFeelingHeartPermission = healthConnectManager.feelingHeartPermission in granted,
                    hasPeriodPermission = granted.containsAll(healthConnectManager.periodPermissions),
                    healthConnectRestingBpm = baseline?.bpm,
                    healthConnectMeasuredAtMs = baseline?.measuredAtEpochMs,
                    restingBaselineBpm = restingBaselineBpm,
                    petHeartRateColor = state.petHeartRateColor.takeIf { state.restingBaselineBpm == restingBaselineBpm },
                    baselineSource = if (baseline != null) BaselineSource.HEALTH_CONNECT else watch?.baselineSource ?: BaselineSource.DEFAULT_FALLBACK
                )
            }
            context.mobileDataStore.edit { prefs ->
                if (baseline != null) {
                    prefs[Keys.HC_RESTING_BPM] = baseline.bpm.toString()
                    prefs[Keys.HC_MEASURED_AT] = baseline.measuredAtEpochMs.toString()
                } else {
                    prefs.remove(Keys.HC_RESTING_BPM)
                    prefs.remove(Keys.HC_MEASURED_AT)
                }
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
        if (!isFreshWatchPayload(payload)) return
        hasConnectedWearNode = true
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
        _uiState.update { it.withWatchMeasurements(payload) }
        if (stateChanged) {
            scheduleAiAdviceRefresh(debounceMs = 300L)
        }
    }

    fun setDemoMode(mode: MobileDemoMode) {
        val base = _uiState.value.healthConnectRestingBpm ?: lastRealWatchPayload?.restingBaselineBpm ?: 64
        _uiState.update { state ->
            when (mode) {
                MobileDemoMode.LIVE_WATCH -> {
                    val real = lastRealWatchPayload?.takeIf { hasConnectedWearNode && isFreshWatchPayload(it) }
                    if (real != null) {
                        state.withWatchMeasurements(real).copy(demoMode = mode)
                    } else {
                        state.copy(
                            demoMode = mode,
                            omamoriState = OmamoriState.RESTING,
                            petHeartRateColor = null,
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
                    petHeartRateColor = null,
                    currentHeartRateBpm = base + 2,
                    restingBaselineBpm = base,
                    motionState = MotionState.STILL_CONFIRMED,
                    calmScore = 94,
                    isSimulated = true
                )
                MobileDemoMode.DEMO_ELEVATED_AT_REST -> state.copy(
                    demoMode = mode,
                    omamoriState = OmamoriState.ELEVATED_AT_REST,
                    petHeartRateColor = null,
                    currentHeartRateBpm = base + state.preferences.elevationThresholdBpm + 3,
                    restingBaselineBpm = base,
                    motionState = MotionState.STILL_CONFIRMED,
                    calmScore = 60,
                    isSimulated = true
                )
                MobileDemoMode.DEMO_MOVING -> state.copy(
                    demoMode = mode,
                    omamoriState = OmamoriState.CALM,
                    petHeartRateColor = null,
                    currentHeartRateBpm = base + 24,
                    restingBaselineBpm = base,
                    motionState = MotionState.RECENT_MOVEMENT,
                    calmScore = 84,
                    isSimulated = true
                )
            }
        }
        if (mode == MobileDemoMode.LIVE_WATCH) {
            scope.launch { wearSyncClient.sendExitDemoToWatch() }
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
        val todayStart = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val todayEvents = state.calmEvents.count { !it.isSimulated && it.timestampEpochMs >= todayStart }
        val todayBreaths = state.breathingSessions.filter { !it.isSimulated && it.startedAtEpochMs >= todayStart }
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
                onboardingAnswers = defaultAnswers,
                hcDismissedOnHome = false,
                watchGuideDismissedOnHome = false,
                preferences = updatedPrefs
            )
        }
        scope.launch {
            context.mobileDataStore.edit { prefs ->
                prefs.remove(Keys.ONBOARDING_ANSWERS)
                prefs.remove(Keys.HC_DISMISSED_HOME)
                prefs.remove(Keys.WATCH_GUIDE_DISMISSED)
                prefs[Keys.ALERT_PREFS] = OmamoriJson.encode(updatedPrefs)
            }
            wearSyncClient.pushPreferences(PreferencesSyncPayload(updatedPrefs, System.currentTimeMillis()))
        }
    }

    suspend fun recordBreathingSession(record: BreathingSessionRecord) {
        awaitInitialized()
        historyMutex.withLock { applyHistory(historyStore.recordSession(record)) }
        scheduleAiAdviceRefresh(debounceMs = 0L)
    }

    suspend fun recordFeeling(feeling: Feeling) {
        feelingLoadJob.join()
        val entry = FeelingRecord(
            id = UUID.randomUUID().toString(),
            feeling = feeling,
            recordedAtEpochMs = System.currentTimeMillis(),
            recordedZoneId = ZoneId.systemDefault().id
        )
        feelingHistoryMutex.withLock {
            val records = feelingHistory.record(entry)
            _uiState.update { it.copy(feelingRecords = records) }
        }
        scope.launch { refreshFeelingHealth(entry.id) }
        scope.launch {
            try {
                val pressure = context.readFeelingPressure() ?: return@launch
                feelingHistoryMutex.withLock {
                    val records = feelingHistory.updatePressure(entry.id, pressure)
                    _uiState.update { it.copy(feelingRecords = records) }
                }
            } catch (_: IOException) {
                // The feeling remains saved if the supplementary observation cannot be stored.
            }
        }
    }

    fun refreshRecentFeelingHealth(force: Boolean = false) {
        scope.launch {
            feelingLoadJob.join()
            uiState.value.feelingRecords.firstOrNull()?.let { refreshFeelingHealth(it.id, force) }
        }
    }

    internal suspend fun watchFeelingHealth(id: String) {
        refreshFeelingHealth(id)
        while (true) {
            val state = uiState.value
            val entry = state.feelingRecords.firstOrNull { it.id == id } ?: return
            if (!state.pendingFeelingActivity(entry).any) return
            delay(30_000L)
            refreshFeelingHealth(id, checkPendingActivity = true)
        }
    }

    private suspend fun refreshFeelingHealth(
        id: String,
        force: Boolean = false,
        checkPendingActivity: Boolean = false
    ) {
        feelingLoadJob.join()
        feelingHealthMutex.withLock {
            val state = uiState.value
            val entry = state.feelingRecords.firstOrNull { it.id == id } ?: return@withLock
            val now = Instant.now()
            val recorded = Instant.ofEpochMilli(entry.recordedAtEpochMs)
            if (!recorded.canRefreshFeelingHealth(now)) return@withLock
            val lastAttempt = feelingHealthAttempts[id]
            val pending = if (checkPendingActivity) {
                state.pendingFeelingActivity(entry, now).takeIf { it.any } ?: return@withLock
            } else {
                if (!force && lastAttempt != null && now.toEpochMilli() - lastAttempt < 120_000L) return@withLock
                null
            }
            val zone = entry.recordedZoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() }
                ?: ZoneId.systemDefault()
            try {
                val health = healthConnectManager.readFeelingHealth(recorded, zone, pending)
                if (pending == null || health != null) {
                    feelingHealthAttempts.remove(id)
                    feelingHealthAttempts[id] = now.toEpochMilli()
                    if (feelingHealthAttempts.size > 32) feelingHealthAttempts.remove(feelingHealthAttempts.keys.first())
                }
                if (health == null) return@withLock
                feelingHistoryMutex.withLock {
                    val records = feelingHistory.updateHealth(id, health)
                    _uiState.update { it.copy(feelingRecords = records) }
                }
            } catch (_: IOException) {
                // Later attempts can retry while the Health Connect data is recent.
            }
        }
    }

    suspend fun updateFeelingContext(id: String, context: FeelingContext?) {
        feelingLoadJob.join()
        feelingHistoryMutex.withLock {
            val records = feelingHistory.updateContext(id, context)
            _uiState.update { it.copy(feelingRecords = records) }
        }
    }

    suspend fun recordCalmEvent(record: CalmEventRecord) {
        awaitInitialized()
        historyMutex.withLock { applyHistory(historyStore.recordEvent(record)) }
        scheduleAiAdviceRefresh(debounceMs = 0L)
    }

    private fun applyHistory(history: MobileHistory) {
        _uiState.update {
            it.copy(
                calmEvents = history.events,
                breathingSessions = history.sessions,
                hasCompletedFirstBreathing = history.firstBreathingDone
            )
        }
    }

    fun addLocalBreathingCompletion(id: String, startedAtEpochMs: Long, durationSec: Int) {
        val state = _uiState.value
        val measuredPreBpm = if (!state.isSimulated && isFreshWatchPayload(lastRealWatchPayload)) state.currentHeartRateBpm else null
        val record = BreathingSessionRecord(
            id = id,
            startedAtEpochMs = startedAtEpochMs,
            durationSeconds = durationSec,
            preBpm = measuredPreBpm,
            postBpm = null,
            triggeredByAlert = false,
            isSimulated = state.isSimulated
        )
        scope.launch { recordBreathingSession(record) }
    }

    fun clearSimulatedHistory() {
        scope.launch {
            awaitInitialized()
            historyMutex.withLock { applyHistory(historyStore.clearSimulated()) }
        }
    }

    suspend fun triggerWatchBreathing(): Boolean = wearSyncClient.sendStartBreathingToWatch()

    private suspend fun pushBaselineToWatch() = baselineSyncMutex.withLock {
        val s = _uiState.value
        val adviceText = s.aiAdviceText.ifBlank { null }
        val sourceLabel = if (s.usedGeminiNano) "Gemini Nano" else "Local"
        val signature = BaselinePushSignature(
            healthConnectRestingBpm = s.healthConnectRestingBpm,
            restingMeasuredAtEpochMs = s.healthConnectMeasuredAtMs,
            latestAiAdviceText = adviceText,
            aiSourceLabel = sourceLabel
        )
        if (signature == lastPushedSignature) return@withLock
        if (wearSyncClient.pushBaselineAndAdvice(
            PhoneBaselinePayload(
                healthConnectRestingBpm = s.healthConnectRestingBpm,
                restingMeasuredAtEpochMs = s.healthConnectMeasuredAtMs,
                latestAiAdviceText = adviceText,
                aiSourceLabel = sourceLabel,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )) lastPushedSignature = signature
    }
}

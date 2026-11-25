package com.omamoripet.wear.sensor

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.omamoripet.shared.domain.CalmStateEvaluator
import com.omamoripet.shared.domain.MotionStillnessTracker
import com.omamoripet.shared.domain.FreshHeartRateTracker
import com.omamoripet.shared.domain.SustainedElevationTracker
import com.omamoripet.shared.model.BiometricSnapshot
import com.omamoripet.shared.model.CalmEventAction
import com.omamoripet.shared.model.CalmEventRecord
import com.omamoripet.shared.model.CalmEvaluation
import com.omamoripet.shared.model.MotionState
import com.omamoripet.shared.model.OmamoriState
import com.omamoripet.shared.model.PetHeartRateColor
import com.omamoripet.shared.sync.toWatchLivePayload
import com.omamoripet.wear.OmamoriWearApplication
import com.omamoripet.wear.R
import com.omamoripet.wear.alert.CalmAlertNotifier
import com.omamoripet.wear.data.SimulationScenario
import com.omamoripet.wear.data.WatchStateRepository
import com.omamoripet.wear.data.WatchUiState
import com.omamoripet.wear.sync.WatchSyncClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalTime
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Wear Health Services の心拍計測と加速度センサーの静止判定を統合し、
 * 「平常時なのに心拍が高い」状態を検知して通知・Data Layer同期を行うフォアグラウンドサービス。
 */
class BiometricMonitorService : LifecycleService(), SensorEventListener {

    companion object {
        const val ACTION_START_CALIBRATION = "com.omamoripet.wear.ACTION_START_CALIBRATION"
        const val PERMISSION_READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"
        private const val ACCEL_BUFFER_SIZE = 30
        private const val ACCEL_BATCH_LATENCY_INTERACTIVE_US = 4_000_000
        private const val ACCEL_BATCH_LATENCY_IDLE_US = 12_000_000
        private const val EVAL_TICK_INTERACTIVE_MS = 5_000L
        private const val EVAL_TICK_IDLE_MS = 15_000L
        private const val EVAL_TICK_OFF_BODY_MS = 60_000L
        private const val EVAL_TICK_PAUSED_MS = 60_000L
        private const val SYNC_MIN_BPM_DELTA_INTERACTIVE_MS = 15_000L
        private const val SYNC_MIN_BPM_DELTA_SCREEN_OFF_MS = 45_000L
        private const val SYNC_BPM_DELTA_INTERACTIVE = 2
        private const val SYNC_BPM_DELTA_SCREEN_OFF = 4
        private const val SYNC_HEARTBEAT_INTERVAL_MS = 60_000L

        fun heartRatePermission(): String =
            if (Build.VERSION.SDK_INT >= 36) PERMISSION_READ_HEART_RATE else Manifest.permission.BODY_SENSORS

        fun hasHeartRatePermission(context: Context): Boolean =
            ActivityCompat.checkSelfPermission(context, heartRatePermission()) == PackageManager.PERMISSION_GRANTED

        fun canStartHealthForegroundService(context: Context): Boolean {
            val hasActivity = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
            return hasActivity && hasHeartRatePermission(context)
        }

        fun start(context: Context, startCalibration: Boolean = false) {
            if (!canStartHealthForegroundService(context)) return
            val intent = Intent(context, BiometricMonitorService::class.java).apply {
                if (startCalibration) action = ACTION_START_CALIBRATION
            }
            runCatching {
                context.startForegroundService(intent)
            }
        }
    }

    private lateinit var repository: WatchStateRepository
    private lateinit var syncClient: WatchSyncClient
    private lateinit var sensorManager: SensorManager
    private var sensorThread: HandlerThread? = null
    private var sensorHandler: Handler? = null

    private val stillnessTracker = MotionStillnessTracker(requiredStillMs = 180_000L, accelVarianceThreshold = 0.35)
    private val accelLock = Any()
    private val accelBuffer = DoubleArray(ACCEL_BUFFER_SIZE)
    private var accelHead = 0
    private var accelCount = 0
    private var lastAccelElapsedMs = 0L

    private val sensorStateLock = Any()
    private val heartRates = FreshHeartRateTracker()
    private val startedBootOffset = System.currentTimeMillis() - SystemClock.elapsedRealtime()
    @Volatile
    private var isWatchOnBody: Boolean = true
    @Volatile
    private var isExplicitlyOffBody: Boolean = false
    @Volatile
    private var isScreenInteractive: Boolean = true
    private var latestMotionState: MotionState = MotionState.RECENT_MOVEMENT
    private val alertElevationTracker = SustainedElevationTracker()
    private val petElevationTracker = SustainedElevationTracker()
    private var accelRegistered = false
    private var currentAccelBatchLatencyUs = ACCEL_BATCH_LATENCY_INTERACTIVE_US
    private var measureRegistered = false
    private var measureRegistering = false
    private var calibrationJob: Job? = null
    private var screenReceiverRegistered = false

    private val evalWakeup = Channel<Unit>(Channel.CONFLATED)

    private var lastSyncPushMs = 0L
    private var lastSyncedState: OmamoriState? = null
    private var lastSyncedPetHeartRateColor: PetHeartRateColor? = null
    private var lastSyncedBaselineBpm: Int? = null
    private var lastSyncedMotion: MotionState? = null
    private var lastSyncedOnBody: Boolean? = null
    private var lastSyncedBpm: Int? = null

    private val measureClient by lazy { HealthServices.getClient(this).measureClient }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    isScreenInteractive = true
                    evalWakeup.trySend(Unit)
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenInteractive = false
                    evalWakeup.trySend(Unit)
                }
            }
        }
    }

    private val hrCallback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
            if (dataType == DataType.HEART_RATE_BPM && availability is DataTypeAvailability) {
                val prevOnBody = isWatchOnBody
                isExplicitlyOffBody = availability == DataTypeAvailability.UNAVAILABLE_DEVICE_OFF_BODY
                isWatchOnBody = availability != DataTypeAvailability.UNAVAILABLE &&
                    availability != DataTypeAvailability.UNAVAILABLE_DEVICE_OFF_BODY
                if (!isWatchOnBody) heartRates.clear()
                if (prevOnBody != isWatchOnBody) {
                    evalWakeup.trySend(Unit)
                }
            }
        }

        override fun onDataReceived(data: DataPointContainer) {
            val points = data.getData(DataType.HEART_RATE_BPM)
            val point = points.maxByOrNull { it.timeDurationFromBoot } ?: return
            if (repository.uiState.value.simulationScenario == SimulationScenario.LIVE_SENSORS &&
                repository.feelingContext.state.value.enabledSince != null
            ) {
                val wallNow = System.currentTimeMillis()
                for (sample in points) {
                    val observedBpm = sample.value.toInt()
                    if (observedBpm in 35..220) {
                        repository.feelingContext.heartRate(
                            startedBootOffset + sample.timeDurationFromBoot.toMillis(), observedBpm, wallNow
                        )
                    }
                }
            }
            val bpm = point.value.toInt()
            if (bpm in 35..220) {
                val wasOffBody = !isWatchOnBody
                heartRates.record(bpm, point.timeDurationFromBoot.toMillis())
                isWatchOnBody = true
                isExplicitlyOffBody = false
                if (wasOffBody) {
                    evalWakeup.trySend(Unit)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val app = application as OmamoriWearApplication
        repository = app.repository
        syncClient = app.syncClient
        sensorManager = getSystemService(SensorManager::class.java)
        isScreenInteractive = getSystemService(PowerManager::class.java)?.isInteractive ?: true

        if (!canStartHealthForegroundService(this)) {
            stopSelf()
            return
        }

        sensorThread = HandlerThread("OmamoriAccel", Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
        sensorHandler = Handler(sensorThread!!.looper)

        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(this, screenStateReceiver, screenFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        screenReceiverRegistered = true

        CalmAlertNotifier.ensureChannels(this)
        val ongoingNotification = NotificationCompat.Builder(this, CalmAlertNotifier.CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.pet_calm)
            .setContentTitle(getString(R.string.notif_monitor_title))
            .setContentText(getString(R.string.notif_monitor_body))
            .setOngoing(true)
            .setSilent(true)
            .build()
        startForeground(CalmAlertNotifier.NOTIF_ID_MONITOR, ongoingNotification)

        startEvaluationLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_START_CALIBRATION) {
            startOneMinuteCalibration()
            evalWakeup.trySend(Unit)
        }
        if (repository.uiState.value.simulationScenario == SimulationScenario.LIVE_SENSORS) {
            evalWakeup.trySend(Unit)
        }
        return START_STICKY
    }

    private fun registerAccelerometer(batchLatencyUs: Int) {
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        val handler = sensorHandler ?: return
        synchronized(sensorStateLock) {
            if (accelRegistered && currentAccelBatchLatencyUs == batchLatencyUs) return
            if (accelRegistered) {
                sensorManager.unregisterListener(this)
                accelRegistered = false
            }
            accelRegistered = sensorManager.registerListener(
                this,
                accel,
                SensorManager.SENSOR_DELAY_NORMAL,
                batchLatencyUs,
                handler
            )
            if (accelRegistered) {
                currentAccelBatchLatencyUs = batchLatencyUs
            }
        }
    }

    private fun unregisterAccelerometer() {
        synchronized(sensorStateLock) {
            if (accelRegistered) {
                sensorManager.unregisterListener(this)
                accelRegistered = false
            }
        }
        synchronized(accelLock) {
            accelCount = 0
            accelHead = 0
            lastAccelElapsedMs = 0L
        }
    }

    private fun registerHeartRateMeasureIfPermitted() {
        if (!hasHeartRatePermission(this)) return
        val shouldStart = synchronized(sensorStateLock) {
            if (measureRegistered || measureRegistering) false
            else {
                measureRegistering = true
                true
            }
        }
        if (!shouldStart) return
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val caps = measureClient.getCapabilitiesAsync().await()
                if (DataType.HEART_RATE_BPM in caps.supportedDataTypesMeasure) {
                    val stillNeeded = synchronized(sensorStateLock) {
                        measureRegistering && !measureRegistered
                    }
                    if (stillNeeded) {
                        measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, hrCallback)
                        val keepRegistered = synchronized(sensorStateLock) {
                            if (measureRegistering) {
                                measureRegistered = true
                                measureRegistering = false
                                true
                            } else {
                                false
                            }
                        }
                        if (!keepRegistered) {
                            runCatching {
                                measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, hrCallback)
                            }
                        }
                    }
                } else {
                    synchronized(sensorStateLock) { measureRegistering = false }
                }
            } catch (_: Exception) {
                synchronized(sensorStateLock) { measureRegistering = false }
            }
        }
    }

    private fun unregisterHeartRateMeasure() {
        val wasRegistered = synchronized(sensorStateLock) {
            measureRegistering = false
            val prev = measureRegistered
            measureRegistered = false
            prev
        }
        if (wasRegistered) {
            runCatching { measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, hrCallback) }
        }
        heartRates.clear()
    }

    private fun unregisterSensors() {
        unregisterHeartRateMeasure()
        stillnessTracker.reset()
        alertElevationTracker.reset()
        petElevationTracker.reset()
        unregisterAccelerometer()
        isExplicitlyOffBody = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0].toDouble()
        val y = event.values[1].toDouble()
        val z = event.values[2].toDouble()
        val mag = sqrt(x * x + y * y + z * z)
        synchronized(accelLock) {
            accelBuffer[accelHead] = mag
            accelHead = (accelHead + 1) % ACCEL_BUFFER_SIZE
            if (accelCount < ACCEL_BUFFER_SIZE) accelCount++
            lastAccelElapsedMs = event.timestamp / 1_000_000L
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun computeAccelVariance(): Double {
        synchronized(accelLock) {
            val count = accelCount
            if (count < 4) return 0.0
            var sum = 0.0
            for (i in 0 until count) {
                sum += accelBuffer[i]
            }
            val mean = sum / count
            var sqDiff = 0.0
            for (i in 0 until count) {
                val d = accelBuffer[i] - mean
                sqDiff += d * d
            }
            return sqDiff / count
        }
    }

    private fun shouldPauseHeartRateMeasure(state: WatchUiState): Boolean {
        if (isScreenInteractive || state.isBreathingActive || state.isCalibrating) return false
        if (state.simulationScenario != SimulationScenario.LIVE_SENSORS) return false
        val prefs = state.preferences
        if (!prefs.alertsEnabled) return true
        return prefs.quietHoursEnabled && isWithinQuietHours(
            hour = LocalTime.now().hour,
            startHour = prefs.quietHoursStartHour,
            endHour = prefs.quietHoursEndHour
        )
    }

    private fun isWithinQuietHours(hour: Int, startHour: Int, endHour: Int): Boolean {
        val normalized = ((hour % 24) + 24) % 24
        return when {
            startHour == endHour -> false
            startHour < endHour -> normalized in startHour until endHour
            else -> normalized >= startHour || normalized < endHour
        }
    }

    private fun startEvaluationLoop() {
        lifecycleScope.launch(Dispatchers.Default) {
            repository.awaitInitialized()
            launch {
                repository.uiState
                    .map {
                        listOf(
                            it.preferences.alertsEnabled,
                            it.preferences.quietHoursEnabled,
                            it.preferences.quietHoursStartHour,
                            it.preferences.quietHoursEndHour,
                            it.isBreathingActive,
                            it.isCalibrating
                        )
                    }
                    .distinctUntilChanged()
                    .collectLatest {
                        evalWakeup.trySend(Unit)
                    }
            }
            repository.uiState
                .map { it.simulationScenario }
                .distinctUntilChanged()
                .collectLatest { scenario ->
                    if (scenario == SimulationScenario.LIVE_SENSORS) {
                        while (isActive) {
                            val nextDelayMs = evaluateLiveTick()
                            withTimeoutOrNull(nextDelayMs) {
                                evalWakeup.receive()
                            }
                        }
                    } else {
                        // デモ中は実センサー・ティックを止めてバッテリーと CPU を節約し、切替時のみ通知・同期
                        // （呼吸開始・終了時は WatchStateRepository.setBreathingActive が即時同期するため重複送信しない）
                        unregisterSensors()
                        pushCurrentDemoSnapshotOnce()
                    }
                }
        }
    }

    private suspend fun pushCurrentDemoSnapshotOnce() {
        val currentState = repository.uiState.value
        val snapshot = currentState.snapshot
        val evaluation = currentState.evaluation
        if (evaluation.shouldTriggerAlert && snapshot.currentHeartRateBpm != null && evaluation.deltaFromRestingBpm != null) {
            CalmAlertNotifier.notifyGentleElevation(
                context = this,
                currentBpm = snapshot.currentHeartRateBpm!!,
                deltaBpm = evaluation.deltaFromRestingBpm!!
            )
            repository.markAlertSent(snapshot.sampledAtEpochMs)
        }
        pushLiveStateIfNeeded(
            snapshot = snapshot,
            evaluation = evaluation,
            forceUrgent = true,
            now = System.currentTimeMillis()
        )
    }

    private suspend fun evaluateLiveTick(): Long {
        val currentState = repository.uiState.value

        if (shouldPauseHeartRateMeasure(currentState)) {
            unregisterHeartRateMeasure()
            unregisterAccelerometer()
            return EVAL_TICK_PAUSED_MS
        }

        registerHeartRateMeasureIfPermitted()

        val now = System.currentTimeMillis()
        val baseline = repository.resolveCurrentBaseline()
        val nowElapsed = SystemClock.elapsedRealtime()

        if (!isWatchOnBody) {
            unregisterAccelerometer()
            stillnessTracker.reset()
            alertElevationTracker.reset()
            petElevationTracker.reset()
            if (isExplicitlyOffBody) {
                repository.feelingContext.movement(now, MotionState.OFF_BODY)
            }
            val prevSnap = currentState.snapshot
            val unchangedOffBody = prevSnap.currentHeartRateBpm == null &&
                prevSnap.restingBaselineBpm == baseline.bpm &&
                prevSnap.baselineSource == baseline.source &&
                prevSnap.motionState == MotionState.OFF_BODY &&
                !prevSnap.isWatchOnBody &&
                prevSnap.sustainedElevationSeconds == 0 &&
                prevSnap.sustainedPetElevationSeconds == 0 &&
                !prevSnap.isSimulated
            val snapshot = BiometricSnapshot(
                currentHeartRateBpm = null,
                restingBaselineBpm = baseline.bpm,
                baselineSource = baseline.source,
                motionState = MotionState.OFF_BODY,
                isWatchOnBody = false,
                sustainedElevationSeconds = 0,
                sustainedPetElevationSeconds = 0,
                sampledAtEpochMs = if (unchangedOffBody) prevSnap.sampledAtEpochMs else now,
                isSimulated = false
            )
            val evaluation = repository.updateSensorSnapshot(snapshot)
            pushLiveStateIfNeeded(
                snapshot = snapshot,
                evaluation = evaluation,
                forceUrgent = false,
                now = now
            )
            return EVAL_TICK_OFF_BODY_MS
        }

        val initialActiveTracking = isScreenInteractive ||
            currentState.isBreathingActive ||
            currentState.isCalibrating ||
            currentState.evaluation.state == OmamoriState.ELEVATED_AT_REST
        registerAccelerometer(
            if (initialActiveTracking) ACCEL_BATCH_LATENCY_INTERACTIVE_US else ACCEL_BATCH_LATENCY_IDLE_US
        )

        latestMotionState = stillnessTracker.recordSample(
            epochMs = now,
            accelMagnitudeVariance = computeAccelVariance()
        )
        val motionMaxAgeMs = (currentAccelBatchLatencyUs / 1_000L) + 6_000L
        val hasRecentMotionMeasurement = synchronized(accelLock) {
            accelCount >= 4 && nowElapsed - lastAccelElapsedMs in 0..motionMaxAgeMs
        }
        when {
            isExplicitlyOffBody -> repository.feelingContext.movement(now, MotionState.OFF_BODY)
            hasRecentMotionMeasurement && isWatchOnBody ->
                repository.feelingContext.movement(now, latestMotionState)
        }
        val reading = heartRates.current(nowElapsed)
        val bpm = reading?.bpm
        if (!currentState.isCalibrating && bpm != null && isWatchOnBody && latestMotionState == MotionState.STILL_CONFIRMED) {
            if (bpm < baseline.bpm + currentState.preferences.elevationThresholdBpm) {
                heartRates.takeForLearning(nowElapsed)?.let { repository.recordStillHeartRateSample(it.bpm) }
            }
        }
        val restingDelta = bpm?.minus(baseline.bpm)?.takeIf {
            isWatchOnBody && latestMotionState == MotionState.STILL_CONFIRMED
        }
        val sustainedSec = alertElevationTracker.record(
            aboveThreshold = restingDelta != null && restingDelta >= currentState.preferences.elevationThresholdBpm,
            nowMs = nowElapsed
        )
        val sustainedPetSec = petElevationTracker.record(
            aboveThreshold = restingDelta != null && restingDelta >= CalmStateEvaluator.PET_ELEVATION_THRESHOLD_BPM,
            nowMs = nowElapsed
        )

        val effectiveMotion = if (isWatchOnBody) latestMotionState else MotionState.OFF_BODY
        val prevSnap = currentState.snapshot
        val sampledAtMs = if (reading != null) {
            startedBootOffset + reading.measuredAtElapsedMs
        } else {
            val unchangedNoReading = prevSnap.currentHeartRateBpm == null &&
                prevSnap.restingBaselineBpm == baseline.bpm &&
                prevSnap.baselineSource == baseline.source &&
                prevSnap.motionState == effectiveMotion &&
                prevSnap.isWatchOnBody == isWatchOnBody &&
                prevSnap.sustainedElevationSeconds == sustainedSec &&
                prevSnap.sustainedPetElevationSeconds == sustainedPetSec &&
                !prevSnap.isSimulated
            if (unchangedNoReading) prevSnap.sampledAtEpochMs else now
        }

        val snapshot = BiometricSnapshot(
            currentHeartRateBpm = bpm,
            restingBaselineBpm = baseline.bpm,
            baselineSource = baseline.source,
            motionState = effectiveMotion,
            isWatchOnBody = isWatchOnBody,
            sustainedElevationSeconds = sustainedSec,
            sustainedPetElevationSeconds = sustainedPetSec,
            sampledAtEpochMs = sampledAtMs,
            isSimulated = false
        )

        val evaluation = repository.updateSensorSnapshot(snapshot)

        if (evaluation.shouldTriggerAlert && snapshot.currentHeartRateBpm != null && evaluation.deltaFromRestingBpm != null) {
            CalmAlertNotifier.notifyGentleElevation(
                context = this,
                currentBpm = snapshot.currentHeartRateBpm!!,
                deltaBpm = evaluation.deltaFromRestingBpm!!
            )
            repository.markAlertSent(now)
            syncClient.sendCalmEvent(
                CalmEventRecord(
                    id = UUID.randomUUID().toString(),
                    timestampEpochMs = now,
                    peakBpm = snapshot.currentHeartRateBpm!!,
                    restingBaselineBpm = snapshot.restingBaselineBpm,
                    sustainedSeconds = snapshot.sustainedElevationSeconds,
                    actionTaken = CalmEventAction.ALERT_SENT,
                    isSimulated = false
                )
            )
        }

        pushLiveStateIfNeeded(
            snapshot = snapshot,
            evaluation = evaluation,
            forceUrgent = evaluation.shouldTriggerAlert,
            now = now
        )

        val stayActive = isScreenInteractive ||
            currentState.isBreathingActive ||
            currentState.isCalibrating ||
            sustainedSec > 0 ||
            sustainedPetSec > 0 ||
            evaluation.state == OmamoriState.ELEVATED_AT_REST
        registerAccelerometer(
            if (stayActive) ACCEL_BATCH_LATENCY_INTERACTIVE_US else ACCEL_BATCH_LATENCY_IDLE_US
        )
        return if (stayActive) EVAL_TICK_INTERACTIVE_MS else EVAL_TICK_IDLE_MS
    }

    private suspend fun pushLiveStateIfNeeded(
        snapshot: BiometricSnapshot,
        evaluation: CalmEvaluation,
        forceUrgent: Boolean,
        now: Long
    ) {
        val stateChanged = lastSyncedState != evaluation.state ||
            lastSyncedPetHeartRateColor != evaluation.petHeartRateColor ||
            ((evaluation.state == OmamoriState.ELEVATED_AT_REST || evaluation.state == OmamoriState.BREATHING) &&
                lastSyncedBaselineBpm != snapshot.restingBaselineBpm) ||
            lastSyncedMotion != snapshot.motionState ||
            lastSyncedOnBody != snapshot.isWatchOnBody
        val bpmDelta = abs((snapshot.currentHeartRateBpm ?: -1) - (lastSyncedBpm ?: -1))
        val elapsed = now - lastSyncPushMs
        val minBpmInterval = if (isScreenInteractive) SYNC_MIN_BPM_DELTA_INTERACTIVE_MS else SYNC_MIN_BPM_DELTA_SCREEN_OFF_MS
        val minBpmDelta = if (isScreenInteractive) SYNC_BPM_DELTA_INTERACTIVE else SYNC_BPM_DELTA_SCREEN_OFF
        val significantBpmChange = bpmDelta >= minBpmDelta && elapsed >= minBpmInterval
        val heartbeatDue = snapshot.isWatchOnBody && elapsed >= SYNC_HEARTBEAT_INTERVAL_MS

        if (forceUrgent || stateChanged || significantBpmChange || heartbeatDue) {
            val urgent = forceUrgent || stateChanged
            val sent = syncClient.pushLiveState(
                payload = snapshot.toWatchLivePayload(
                    state = evaluation.state,
                    calmScore = evaluation.calmScore,
                    publishedAtEpochMs = now,
                    petHeartRateColor = evaluation.petHeartRateColor
                ),
                urgent = urgent
            )
            if (sent) {
                lastSyncPushMs = now
                lastSyncedState = evaluation.state
                lastSyncedPetHeartRateColor = evaluation.petHeartRateColor
                lastSyncedBaselineBpm = snapshot.restingBaselineBpm
                lastSyncedMotion = snapshot.motionState
                lastSyncedOnBody = snapshot.isWatchOnBody
                lastSyncedBpm = snapshot.currentHeartRateBpm
            }
        }
    }

    private fun startOneMinuteCalibration() {
        calibrationJob?.cancel()
        calibrationJob = lifecycleScope.launch(Dispatchers.Default) {
            var recordedSamples = 0
            try {
                for (remaining in 60 downTo 1) {
                    repository.setCalibrationProgress(active = true, remainingSeconds = remaining)
                    if (isWatchOnBody) {
                        heartRates.takeForLearning(SystemClock.elapsedRealtime())?.let {
                            repository.recordStillHeartRateSample(it.bpm)
                            recordedSamples++
                        }
                    }
                    delay(1_000L)
                }
            } finally {
                val result = if (recordedSamples > 0) {
                    com.omamoripet.wear.data.CalibrationResult.SUCCESS
                } else {
                    com.omamoripet.wear.data.CalibrationResult.NO_SIGNAL
                }
                repository.setCalibrationProgress(active = false, remainingSeconds = 0, result = result)
                repository.flushLearnedSamples()
                repository.forceRefreshComplicationsAndTile()
            }
        }
    }

    override fun onDestroy() {
        calibrationJob?.cancel()
        if (screenReceiverRegistered) {
            runCatching { unregisterReceiver(screenStateReceiver) }
            screenReceiverRegistered = false
        }
        unregisterSensors()
        sensorThread?.quitSafely()
        sensorThread = null
        sensorHandler = null
        (application as? OmamoriWearApplication)?.applicationScope?.launch {
            repository.flushLearnedSamples()
        }
        super.onDestroy()
    }
}

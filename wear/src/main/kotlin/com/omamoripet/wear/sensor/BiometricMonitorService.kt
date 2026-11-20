package com.omamoripet.wear.sensor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.omamoripet.shared.domain.MotionStillnessTracker
import com.omamoripet.shared.domain.FreshHeartRateTracker
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
import com.omamoripet.wear.sync.WatchSyncClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
        private const val ACCEL_BATCH_LATENCY_US = 4_000_000
        private const val EVAL_TICK_MS = 5_000L
        private const val SYNC_MIN_BPM_DELTA_INTERVAL_MS = 15_000L
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

    private val stillnessTracker = MotionStillnessTracker(requiredStillMs = 180_000L, accelVarianceThreshold = 0.35)
    private val accelLock = Any()
    private val accelBuffer = DoubleArray(ACCEL_BUFFER_SIZE)
    private var accelHead = 0
    private var accelCount = 0

    private val heartRates = FreshHeartRateTracker()
    @Volatile
    private var isWatchOnBody: Boolean = true
    private var latestMotionState: MotionState = MotionState.RECENT_MOVEMENT
    private var elevatedStartEpochMs: Long? = null
    private var accelRegistered = false
    private var measureRegistered = false
    private var calibrationJob: Job? = null

    private var lastSyncPushMs = 0L
    private var lastSyncedState: OmamoriState? = null
    private var lastSyncedPetHeartRateColor: PetHeartRateColor? = null
    private var lastSyncedBaselineBpm: Int? = null
    private var lastSyncedMotion: MotionState? = null
    private var lastSyncedOnBody: Boolean? = null
    private var lastSyncedBpm: Int? = null

    private val measureClient by lazy { HealthServices.getClient(this).measureClient }

    private val hrCallback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
            if (dataType == DataType.HEART_RATE_BPM && availability is DataTypeAvailability) {
                isWatchOnBody = availability != DataTypeAvailability.UNAVAILABLE &&
                    availability != DataTypeAvailability.UNAVAILABLE_DEVICE_OFF_BODY
                if (!isWatchOnBody) heartRates.clear()
            }
        }

        override fun onDataReceived(data: DataPointContainer) {
            val point = data.getData(DataType.HEART_RATE_BPM)
                .maxByOrNull { it.timeDurationFromBoot } ?: return
            val bpm = point.value.toInt()
            if (bpm in 35..220) {
                heartRates.record(bpm, point.timeDurationFromBoot.toMillis())
                isWatchOnBody = true
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = (application as OmamoriWearApplication).repository
        syncClient = WatchSyncClient(this)
        sensorManager = getSystemService(SensorManager::class.java)

        if (!canStartHealthForegroundService(this)) {
            stopSelf()
            return
        }

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
        }
        if (repository.uiState.value.simulationScenario == SimulationScenario.LIVE_SENSORS) {
            registerSensorsIfPermitted()
        }
        return START_STICKY
    }

    private fun registerSensorsIfPermitted() {
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accel != null && !accelRegistered) {
            // 4秒ハードウェア FIFO バッチングで不要な CPU 起床を抑える
            accelRegistered = sensorManager.registerListener(
                this,
                accel,
                SensorManager.SENSOR_DELAY_NORMAL,
                ACCEL_BATCH_LATENCY_US
            )
        }

        if (hasHeartRatePermission(this) && !measureRegistered) {
            lifecycleScope.launch {
                runCatching {
                    val caps = measureClient.getCapabilitiesAsync().await()
                    if (DataType.HEART_RATE_BPM in caps.supportedDataTypesMeasure && !measureRegistered) {
                        measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, hrCallback)
                        measureRegistered = true
                    }
                }
            }
        }
    }

    private fun unregisterSensors() {
        heartRates.clear()
        stillnessTracker.reset()
        elevatedStartEpochMs = null
        synchronized(accelLock) { accelCount = 0; accelHead = 0 }
        if (accelRegistered) {
            sensorManager.unregisterListener(this)
            accelRegistered = false
        }
        if (measureRegistered) {
            measureRegistered = false
            runCatching { measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, hrCallback) }
        }
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

    private fun startEvaluationLoop() {
        lifecycleScope.launch {
            repository.awaitInitialized()
            repository.uiState
                .map { it.simulationScenario }
                .distinctUntilChanged()
                .collectLatest { scenario ->
                    if (scenario == SimulationScenario.LIVE_SENSORS) {
                        registerSensorsIfPermitted()
                        while (isActive) {
                            evaluateLiveTick()
                            delay(EVAL_TICK_MS)
                        }
                    } else {
                        // デモ中は実センサー・5秒ティックを止めてバッテリーと CPU を節約し、切替や呼吸開始・終了時のみ通知・同期
                        unregisterSensors()
                        repository.uiState
                            .map { it.isBreathingActive }
                            .distinctUntilChanged()
                            .collectLatest {
                                pushCurrentDemoSnapshotOnce()
                            }
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

    private suspend fun evaluateLiveTick() {
        val now = System.currentTimeMillis()
        val currentState = repository.uiState.value
        val baseline = repository.resolveCurrentBaseline()

        latestMotionState = stillnessTracker.recordSample(
            epochMs = now,
            accelMagnitudeVariance = computeAccelVariance()
        )
        val nowElapsed = SystemClock.elapsedRealtime()
        val reading = heartRates.current(nowElapsed)
        val bpm = reading?.bpm
        if (!currentState.isCalibrating && bpm != null && isWatchOnBody && latestMotionState == MotionState.STILL_CONFIRMED) {
            if (bpm < baseline.bpm + currentState.preferences.elevationThresholdBpm) {
                heartRates.takeForLearning(nowElapsed)?.let { repository.recordStillHeartRateSample(it.bpm) }
            }
        }
        val sustainedSec = computeSustainedElevationSeconds(
            bpm = bpm,
            baselineBpm = baseline.bpm,
            threshold = currentState.preferences.elevationThresholdBpm,
            motion = latestMotionState,
            onBody = isWatchOnBody,
            nowMs = now
        )
        val snapshot = BiometricSnapshot(
            currentHeartRateBpm = bpm,
            restingBaselineBpm = baseline.bpm,
            baselineSource = baseline.source,
            motionState = if (isWatchOnBody) latestMotionState else MotionState.OFF_BODY,
            isWatchOnBody = isWatchOnBody,
            sustainedElevationSeconds = sustainedSec,
            sampledAtEpochMs = reading?.let { now - (nowElapsed - it.measuredAtElapsedMs) } ?: now,
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
    }

    private suspend fun pushLiveStateIfNeeded(
        snapshot: BiometricSnapshot,
        evaluation: CalmEvaluation,
        forceUrgent: Boolean,
        now: Long
    ) {
        val stateChanged = lastSyncedState != evaluation.state ||
            lastSyncedPetHeartRateColor != evaluation.petHeartRateColor ||
            (evaluation.state == OmamoriState.ELEVATED_AT_REST && lastSyncedBaselineBpm != snapshot.restingBaselineBpm) ||
            lastSyncedMotion != snapshot.motionState ||
            lastSyncedOnBody != snapshot.isWatchOnBody
        val bpmDelta = abs((snapshot.currentHeartRateBpm ?: -1) - (lastSyncedBpm ?: -1))
        val elapsed = now - lastSyncPushMs
        val significantBpmChange = bpmDelta >= 2 && elapsed >= SYNC_MIN_BPM_DELTA_INTERVAL_MS
        val heartbeatDue = elapsed >= SYNC_HEARTBEAT_INTERVAL_MS

        if (forceUrgent || stateChanged || significantBpmChange || heartbeatDue) {
            val urgent = forceUrgent || stateChanged || heartbeatDue
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

    private fun computeSustainedElevationSeconds(
        bpm: Int?,
        baselineBpm: Int,
        threshold: Int,
        motion: MotionState,
        onBody: Boolean,
        nowMs: Long
    ): Int {
        val elevatedAtRest = onBody && motion == MotionState.STILL_CONFIRMED && bpm != null && (bpm - baselineBpm) >= threshold
        return if (elevatedAtRest) {
            val start = elevatedStartEpochMs ?: nowMs.also { elevatedStartEpochMs = it }
            ((nowMs - start) / 1000L).toInt()
        } else {
            elevatedStartEpochMs = null
            0
        }
    }

    private fun startOneMinuteCalibration() {
        calibrationJob?.cancel()
        calibrationJob = lifecycleScope.launch {
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
        unregisterSensors()
        CoroutineScope(Dispatchers.IO).launch {
            repository.flushLearnedSamples()
        }
        super.onDestroy()
    }
}

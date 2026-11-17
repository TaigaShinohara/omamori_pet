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
import com.omamoripet.shared.model.BiometricSnapshot
import com.omamoripet.shared.model.CalmEventAction
import com.omamoripet.shared.model.CalmEventRecord
import com.omamoripet.shared.model.MotionState
import com.omamoripet.shared.sync.WatchLivePayload
import com.omamoripet.wear.OmamoriWearApplication
import com.omamoripet.wear.R
import com.omamoripet.wear.alert.CalmAlertNotifier
import com.omamoripet.wear.data.SimulationScenario
import com.omamoripet.wear.data.WatchStateRepository
import com.omamoripet.wear.sync.WatchSyncClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.sqrt

/**
 * Wear Health Services の心拍計測と加速度センサーの静止判定を統合し、
 * 「平常時なのに心拍が高い」状態を検知して通知・Data Layer同期を行うフォアグラウンドサービス。
 */
class BiometricMonitorService : LifecycleService(), SensorEventListener {

    companion object {
        const val ACTION_START_CALIBRATION = "com.omamoripet.wear.ACTION_START_CALIBRATION"
        const val PERMISSION_READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"

        fun heartRatePermission(): String =
            if (Build.VERSION.SDK_INT >= 36) PERMISSION_READ_HEART_RATE else Manifest.permission.BODY_SENSORS

        fun hasHeartRatePermission(context: Context): Boolean =
            ActivityCompat.checkSelfPermission(context, heartRatePermission()) == PackageManager.PERMISSION_GRANTED

        fun canStartHealthForegroundService(context: Context): Boolean {
            val hasActivity = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
            val hasHrOn36 = Build.VERSION.SDK_INT >= 36 && hasHeartRatePermission(context)
            return hasActivity || hasHrOn36
        }

        fun start(context: Context, startCalibration: Boolean = false) {
            if (!canStartHealthForegroundService(context)) return
            val intent = Intent(context, BiometricMonitorService::class.java).apply {
                if (startCalibration) action = ACTION_START_CALIBRATION
            }
            context.startForegroundService(intent)
        }
    }

    private lateinit var repository: WatchStateRepository
    private lateinit var syncClient: WatchSyncClient
    private lateinit var sensorManager: SensorManager

    private val stillnessTracker = MotionStillnessTracker(requiredStillMs = 180_000L, accelVarianceThreshold = 0.35)
    private val accelMagnitudes = ArrayDeque<Double>()

    private var latestLiveBpm: Int? = null
    private var isWatchOnBody: Boolean = true
    private var latestMotionState: MotionState = MotionState.RECENT_MOVEMENT
    private var elevatedStartEpochMs: Long? = null
    private var measureRegistered = false
    private var lastSyncPushMs = 0L
    private var calibrationJob: Job? = null

    private val measureClient by lazy { HealthServices.getClient(this).measureClient }

    private val hrCallback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
            if (dataType == DataType.HEART_RATE_BPM && availability is DataTypeAvailability) {
                isWatchOnBody = availability != DataTypeAvailability.UNAVAILABLE &&
                    availability != DataTypeAvailability.UNAVAILABLE_DEVICE_OFF_BODY
            }
        }

        override fun onDataReceived(data: DataPointContainer) {
            val bpm = data.getData(DataType.HEART_RATE_BPM).lastOrNull()?.value?.toInt() ?: return
            if (bpm in 35..220) {
                latestLiveBpm = bpm
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

        registerSensorsIfPermitted()
        startEvaluationLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_START_CALIBRATION) {
            startOneMinuteCalibration()
        }
        registerSensorsIfPermitted()
        return START_STICKY
    }

    private fun registerSensorsIfPermitted() {
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accel != null) {
            sensorManager.unregisterListener(this, accel)
            sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL)
        }

        if (hasHeartRatePermission(this) &&
            !measureRegistered
        ) {
            lifecycleScope.launch {
                runCatching {
                    val caps = measureClient.getCapabilitiesAsync().await()
                    if (DataType.HEART_RATE_BPM in caps.supportedDataTypesMeasure) {
                        measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, hrCallback)
                        measureRegistered = true
                    }
                }
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0].toDouble()
        val y = event.values[1].toDouble()
        val z = event.values[2].toDouble()
        val mag = sqrt(x * x + y * y + z * z)
        synchronized(accelMagnitudes) {
            if (accelMagnitudes.size >= 30) accelMagnitudes.removeFirst()
            accelMagnitudes.addLast(mag)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun computeAccelVariance(): Double {
        val values = synchronized(accelMagnitudes) { accelMagnitudes.toList() }
        if (values.size < 4) return 0.0
        val mean = values.average()
        return values.sumOf { (it - mean) * (it - mean) } / values.size
    }

    private fun startEvaluationLoop() {
        lifecycleScope.launch {
            repository.uiState.collectLatest { /* keep repo hot */ }
        }
        lifecycleScope.launch {
            while (true) {
                evaluateCurrentTick()
                delay(5_000L)
            }
        }
    }

    private suspend fun evaluateCurrentTick() {
        val now = System.currentTimeMillis()
        val currentState = repository.uiState.value
        val baseline = repository.resolveCurrentBaseline()

        val snapshot = when (currentState.simulationScenario) {
            SimulationScenario.LIVE_SENSORS -> {
                latestMotionState = stillnessTracker.recordSample(
                    epochMs = now,
                    accelMagnitudeVariance = computeAccelVariance()
                )
                val bpm = latestLiveBpm
                if (bpm != null && isWatchOnBody && latestMotionState == MotionState.STILL_CONFIRMED) {
                    if (bpm < baseline.bpm + currentState.preferences.elevationThresholdBpm) {
                        repository.recordStillHeartRateSample(bpm)
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
                BiometricSnapshot(
                    currentHeartRateBpm = bpm,
                    restingBaselineBpm = baseline.bpm,
                    baselineSource = baseline.source,
                    motionState = if (isWatchOnBody) latestMotionState else MotionState.OFF_BODY,
                    isWatchOnBody = isWatchOnBody,
                    sustainedElevationSeconds = sustainedSec,
                    sampledAtEpochMs = now,
                    isSimulated = false
                )
            }
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
                currentHeartRateBpm = baseline.bpm + currentState.preferences.elevationThresholdBpm + 6,
                restingBaselineBpm = baseline.bpm,
                baselineSource = baseline.source,
                motionState = MotionState.STILL_CONFIRMED,
                isWatchOnBody = true,
                sustainedElevationSeconds = currentState.preferences.requiredSustainedSeconds + 15,
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
                    actionTaken = CalmEventAction.ALERT_SENT
                )
            )
        }

        if (now - lastSyncPushMs >= 15_000L || evaluation.shouldTriggerAlert) {
            lastSyncPushMs = now
            syncClient.pushLiveState(
                WatchLivePayload(
                    currentHeartRateBpm = snapshot.currentHeartRateBpm,
                    restingBaselineBpm = snapshot.restingBaselineBpm,
                    baselineSource = snapshot.baselineSource,
                    motionState = snapshot.motionState,
                    omamoriState = evaluation.state,
                    calmScore = evaluation.calmScore,
                    isWatchOnBody = snapshot.isWatchOnBody,
                    isSimulated = snapshot.isSimulated,
                    updatedAtEpochMs = now
                )
            )
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
            for (remaining in 60 downTo 1) {
                repository.setCalibrationProgress(active = true, remainingSeconds = remaining)
                val bpm = latestLiveBpm
                if (bpm != null && isWatchOnBody) {
                    repository.recordStillHeartRateSample(bpm)
                }
                delay(1_000L)
            }
            repository.setCalibrationProgress(active = false, remainingSeconds = 0)
            repository.forceRefreshComplicationsAndTile()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        if (measureRegistered) {
            runCatching { measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, hrCallback) }
        }
    }
}

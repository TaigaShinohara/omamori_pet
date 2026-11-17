package com.omamoripet.wear.breathing

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import com.omamoripet.shared.model.BreathingSessionRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class BreathPhase {
    INHALE,
    HOLD,
    EXHALE,
    COMPLETED
}

data class BreathingUiState(
    val isRunning: Boolean = false,
    val phase: BreathPhase = BreathPhase.INHALE,
    val phaseProgress: Float = 0f,
    val cycleIndex: Int = 1,
    val totalCycles: Int = 5,
    val preBpm: Int? = null,
    val postBpm: Int? = null,
    val completedRecord: BreathingSessionRecord? = null
)

/**
 * 4-2-6 呼吸（4秒吸う / 2秒止める / 6秒吐く × 5セット = 60秒）を触覚振動と同期してガイドする。
 */
class HapticBreathingController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val readCurrentBpm: () -> Int?,
    private val onActiveChanged: (Boolean) -> Unit,
    private val onSessionCompleted: suspend (BreathingSessionRecord) -> Unit
) {
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private val _state = MutableStateFlow(BreathingUiState())
    val state: StateFlow<BreathingUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun start(triggeredByAlert: Boolean, totalCycles: Int = 5) {
        job?.cancel()
        val startedAt = System.currentTimeMillis()
        val preBpm = readCurrentBpm()
        onActiveChanged(true)

        job = scope.launch {
            for (cycle in 1..totalCycles) {
                runPhase(BreathPhase.INHALE, 4_000L, cycle, totalCycles, preBpm, pulseAtStart = true)
                runPhase(BreathPhase.HOLD, 2_000L, cycle, totalCycles, preBpm, pulseAtStart = false)
                runPhase(BreathPhase.EXHALE, 6_000L, cycle, totalCycles, preBpm, pulseAtStart = true)
            }

            val postBpm = readCurrentBpm()
            vibrateCompletion()
            val record = BreathingSessionRecord(
                id = UUID.randomUUID().toString(),
                startedAtEpochMs = startedAt,
                durationSeconds = totalCycles * 12,
                preBpm = preBpm,
                postBpm = postBpm,
                triggeredByAlert = triggeredByAlert
            )
            onActiveChanged(false)
            _state.value = BreathingUiState(
                isRunning = false,
                phase = BreathPhase.COMPLETED,
                phaseProgress = 1f,
                cycleIndex = totalCycles,
                totalCycles = totalCycles,
                preBpm = preBpm,
                postBpm = postBpm,
                completedRecord = record
            )
            onSessionCompleted(record)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        vibrator?.cancel()
        onActiveChanged(false)
        _state.value = BreathingUiState()
    }

    private suspend fun runPhase(
        phase: BreathPhase,
        durationMs: Long,
        cycle: Int,
        totalCycles: Int,
        preBpm: Int?,
        pulseAtStart: Boolean
    ) {
        if (pulseAtStart) {
            vibratePhaseCue(phase)
        }
        val stepMs = 50L
        var elapsed = 0L
        while (elapsed <= durationMs) {
            _state.value = BreathingUiState(
                isRunning = true,
                phase = phase,
                phaseProgress = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f),
                cycleIndex = cycle,
                totalCycles = totalCycles,
                preBpm = preBpm
            )
            delay(stepMs)
            elapsed += stepMs
        }
    }

    private fun vibratePhaseCue(phase: BreathPhase) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = when (phase) {
            BreathPhase.INHALE -> VibrationEffect.createWaveform(longArrayOf(0, 70, 90, 90), intArrayOf(0, 45, 0, 75), -1)
            BreathPhase.EXHALE -> VibrationEffect.createWaveform(longArrayOf(0, 120, 120, 60), intArrayOf(0, 70, 0, 35), -1)
            BreathPhase.HOLD, BreathPhase.COMPLETED -> return
        }
        v.vibrate(effect)
    }

    private fun vibrateCompletion() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(110L, 65))
    }
}

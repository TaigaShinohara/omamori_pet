package com.omamoripet.shared.domain

data class BreathingPosition(
    val cycle: Int,
    val phaseIndex: Int,
    val phaseDurationMs: Int,
    val progress: Float,
    val completed: Boolean
)

/** A real-time 4-2-6 timeline; independent of animation scale and UI recreation. */
object BreathingTimeline {
    const val TOTAL_CYCLES = 5
    const val DURATION_SECONDS = 60
    private const val CYCLE_MS = 12_000L

    fun at(elapsedMs: Long): BreathingPosition {
        val elapsed = elapsedMs.coerceAtLeast(0L)
        if (elapsed >= DURATION_SECONDS * 1_000L) {
            return BreathingPosition(TOTAL_CYCLES, 2, 6_000, 1f, true)
        }
        val cycleTime = elapsed % CYCLE_MS
        val phase = when {
            cycleTime < 4_000L -> 0
            cycleTime < 6_000L -> 1
            else -> 2
        }
        val start = when (phase) { 0 -> 0L; 1 -> 4_000L; else -> 6_000L }
        val duration = when (phase) { 0 -> 4_000; 1 -> 2_000; else -> 6_000 }
        return BreathingPosition(
            cycle = (elapsed / CYCLE_MS).toInt() + 1,
            phaseIndex = phase,
            phaseDurationMs = duration,
            progress = (cycleTime - start).toFloat() / duration,
            completed = false
        )
    }
}

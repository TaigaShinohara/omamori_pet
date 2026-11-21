package com.omamoripet.shared.model

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable

private const val FACE_DURATION_MS = 3 * 60_000L

@Serializable
enum class Feeling {
    ANXIOUS,
    LOW,
    IRRITATED
}

@Serializable
data class FeelingFace(val feeling: Feeling, val recordedAtEpochMs: Long) {
    fun remainingMs(nowEpochMs: Long, futureToleranceMs: Long = 0L): Long {
        val ageMs = nowEpochMs - recordedAtEpochMs
        return if (ageMs in -futureToleranceMs until FACE_DURATION_MS) {
            FACE_DURATION_MS - ageMs.coerceAtLeast(0L)
        } else {
            0L
        }
    }
}

/** Collecting again after resume recomputes the remaining time from the original recording. */
fun FeelingFace?.visibleForRemainingTime(futureToleranceMs: Long = 0L): Flow<FeelingFace?> = flow {
    val face = this@visibleForRemainingTime
    val remaining = face?.remainingMs(System.currentTimeMillis(), futureToleranceMs) ?: 0L
    emit(face.takeIf { remaining > 0 })
    if (remaining > 0) {
        delay(remaining)
        emit(null)
    }
}

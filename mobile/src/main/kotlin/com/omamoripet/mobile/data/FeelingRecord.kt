package com.omamoripet.mobile.data

import kotlinx.serialization.Serializable

private const val FACE_DURATION_MS = 3 * 60_000L

@Serializable
enum class Feeling {
    ANXIOUS,
    LOW,
    IRRITATED
}

@Serializable
data class FeelingRecord(val id: String, val feeling: Feeling, val recordedAtEpochMs: Long) {
    internal fun faceRemainingMs(nowEpochMs: Long): Long {
        val ageMs = nowEpochMs - recordedAtEpochMs
        return if (ageMs in 0 until FACE_DURATION_MS) FACE_DURATION_MS - ageMs else 0
    }
}

package com.omamoripet.shared.model

import java.nio.ByteBuffer
import java.util.Base64
import kotlinx.serialization.Serializable

const val FEELING_MINUTE_MS = 60_000L
const val FEELING_MINUTES_BEFORE = 30
const val FEELING_MINUTES_AFTER = 15
const val FEELING_MINUTE_COUNT = FEELING_MINUTES_BEFORE + FEELING_MINUTES_AFTER

@Serializable
data class FeelingMinute(
    val medianBpm: Int? = null,
    val sampleCount: Int = 0,
    val motion: MotionState? = null
)

@Serializable
data class FeelingContext(
    val recordedAtEpochMs: Long,
    val baselineBpm: Int?,
    val baselineSource: BaselineSource?,
    val minutes: List<FeelingMinute>
) {
    fun hasObservations(): Boolean = minutes.any { it.sampleCount > 0 || it.motion != null }
}

/** Compact, versioned offline import format. No feeling label or device identifiers are required. */
object FeelingContextQr {
    private const val PREFIX = "OMAMORI1:"
    private const val PACKET_SIZE = 10 + 3 * FEELING_MINUTE_COUNT
    private const val ENCODED_LENGTH = (PACKET_SIZE * 4 + 2) / 3

    fun encode(context: FeelingContext): String {
        require(context.minutes.size == FEELING_MINUTE_COUNT)
        val bytes = ByteBuffer.allocate(PACKET_SIZE)
        bytes.putLong(context.recordedAtEpochMs)
        bytes.put((context.baselineBpm ?: 0).toByte())
        bytes.put(when (context.baselineSource) {
            null -> 0
            BaselineSource.DEFAULT_FALLBACK -> 1
            BaselineSource.WATCH_LEARNED -> 2
            BaselineSource.HEALTH_CONNECT -> 3
        }.toByte())
        for (minute in context.minutes) {
            bytes.put((minute.medianBpm ?: 0).toByte())
            bytes.put(minute.sampleCount.coerceAtMost(255).toByte())
            bytes.put(when (minute.motion) {
                null -> 0
                MotionState.STILL_CONFIRMED -> 1
                MotionState.RECENT_MOVEMENT -> 2
                MotionState.OFF_BODY -> 3
            }.toByte())
        }
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.array())
    }

    fun decode(text: String, expectedRecordedAtMs: Long): FeelingContext? {
        if (!text.startsWith(PREFIX) || text.length != PREFIX.length + ENCODED_LENGTH) return null
        val raw = try {
            Base64.getUrlDecoder().decode(text.substring(PREFIX.length))
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (raw.size != PACKET_SIZE) return null
        val bytes = ByteBuffer.wrap(raw)
        val recordedAt = bytes.long
        if (recordedAt != expectedRecordedAtMs) return null
        val baseline = bytes.get().unsigned()
        val source = when (bytes.get().unsigned()) {
            0 -> null
            1 -> BaselineSource.DEFAULT_FALLBACK
            2 -> BaselineSource.WATCH_LEARNED
            3 -> BaselineSource.HEALTH_CONNECT
            else -> return null
        }
        if ((baseline == 0) != (source == null) || (baseline != 0 && baseline !in 35..220)) return null
        val minutes = List(FEELING_MINUTE_COUNT) {
            val bpm = bytes.get().unsigned()
            val count = bytes.get().unsigned()
            if ((bpm == 0) != (count == 0) || (bpm != 0 && bpm !in 35..220)) return null
            val motion = when (bytes.get().unsigned()) {
                0 -> null
                1 -> MotionState.STILL_CONFIRMED
                2 -> MotionState.RECENT_MOVEMENT
                3 -> MotionState.OFF_BODY
                else -> return null
            }
            FeelingMinute(bpm.takeIf { it != 0 }, count, motion)
        }
        return FeelingContext(recordedAt, baseline.takeIf { it != 0 }, source, minutes)
            .takeIf { it.hasObservations() }
    }

    private fun Byte.unsigned(): Int = toInt() and 0xff
}

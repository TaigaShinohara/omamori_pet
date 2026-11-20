package com.omamoripet.shared.sync

import com.omamoripet.shared.domain.MeasurementFreshness
import com.omamoripet.shared.model.BaselineSource
import com.omamoripet.shared.model.BiometricSnapshot
import com.omamoripet.shared.model.MotionState
import com.omamoripet.shared.model.OmamoriState
import com.omamoripet.shared.model.PetHeartRateColor
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class WatchLivePayloadTest {
    private val snapshot = BiometricSnapshot(80, 65, BaselineSource.WATCH_LEARNED,
        MotionState.STILL_CONFIRMED, true, 100, 1_000L)

    @Test
    fun `watch sync preserves color and accepts older payloads without a color`() {
        val payload = snapshot.toWatchLivePayload(
            OmamoriState.ELEVATED_AT_REST, 40, 2_000L, PetHeartRateColor.RED
        )
        val serialized = OmamoriJson.encode(payload)
        assertEquals(payload, OmamoriJson.decodeOrNull<WatchLivePayload>(serialized))

        val fields = OmamoriJson.codec.parseToJsonElement(serialized).jsonObject
        val legacy = OmamoriJson.decodeOrNull<WatchLivePayload>(JsonObject(fields - "petHeartRateColor").toString())
        assertEquals(payload.copy(petHeartRateColor = null), legacy)
    }

    @Test
    fun `long running demo breathing transitions are fresh when published`() {
        for (state in listOf(OmamoriState.BREATHING, OmamoriState.CALM)) {
            val payload = snapshot.copy(isSimulated = true).toWatchLivePayload(state, 80, 200_000L)
            assertEquals(200_000L, payload.updatedAtEpochMs)
            assertEquals(state, payload.omamoriState)
            assertTrue(MeasurementFreshness.isFresh(payload.updatedAtEpochMs, 200_001L,
                MeasurementFreshness.WATCH_STATE_MAX_AGE_MS))
        }
    }

    @Test
    fun `publishing real data never makes an old heart rate fresh`() {
        val payload = snapshot.toWatchLivePayload(OmamoriState.CALM, 80, 200_000L)
        assertEquals(1_000L, payload.updatedAtEpochMs)
        assertFalse(MeasurementFreshness.isFresh(payload.updatedAtEpochMs, 200_001L,
            MeasurementFreshness.WATCH_STATE_MAX_AGE_MS))
    }
}

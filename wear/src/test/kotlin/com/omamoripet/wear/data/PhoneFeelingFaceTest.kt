package com.omamoripet.wear.data

import com.omamoripet.shared.model.Feeling
import com.omamoripet.shared.model.FeelingFace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneFeelingFaceTest {
    @Test
    fun `the newest unexpired recording wins even if a cached update arrives later`() {
        val latest = FeelingFace(Feeling.IRRITATED, 10_000L)
        val older = FeelingFace(Feeling.LOW, 9_000L)
        val state = WatchUiState().withPhoneFeelingFace(older, 11_000L)
            .withPhoneFeelingFace(latest, 11_001L)
            .withPhoneFeelingFace(older, 11_002L)
        assertEquals(latest, state.phoneFeelingFace)
    }

    @Test
    fun `an expired sync is dropped while normal clock skew is tolerated`() {
        val face = FeelingFace(Feeling.ANXIOUS, 10_000L)
        assertNull(WatchUiState().withPhoneFeelingFace(face, 190_000L).phoneFeelingFace)
        assertEquals(face, WatchUiState().withPhoneFeelingFace(face, 9_000L).phoneFeelingFace)
        assertNull(WatchUiState().withPhoneFeelingFace(face, 4_999L).phoneFeelingFace)
    }
}

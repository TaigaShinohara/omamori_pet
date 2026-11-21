package com.omamoripet.shared.model

import com.omamoripet.shared.sync.OmamoriJson
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FeelingFaceTest {
    @Test
    fun `display time follows the recording even after late delivery`() {
        val face = FeelingFace(Feeling.ANXIOUS, 1_000L)
        assertEquals(0L, face.remainingMs(999L))
        assertEquals(180_000L, face.remainingMs(1_000L))
        assertEquals(1_000L, face.remainingMs(180_000L))
        assertEquals(0L, face.remainingMs(181_000L))
    }

    @Test
    fun `the synchronized face carries only the feeling and recording time`() {
        val face = FeelingFace(Feeling.LOW, 1_000L)
        val payload = OmamoriJson.encode(face)
        assertEquals("""{"feeling":"LOW","recordedAtEpochMs":1000}""", payload)
        assertEquals(face, OmamoriJson.decodeOrNull<FeelingFace>(payload))
    }

    @Test
    fun `opening during an expired recording leaves the normal face in place`() = runBlocking {
        assertEquals(listOf<FeelingFace?>(null), FeelingFace(Feeling.IRRITATED, 1_000L).visibleForRemainingTime().toList())
    }
}

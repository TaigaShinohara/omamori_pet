package com.omamoripet.mobile.health

import androidx.health.connect.client.records.HeartRateRecord
import com.omamoripet.mobile.data.FeelingComparison
import com.omamoripet.mobile.data.approximateMoonAge
import java.time.Instant
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeelingHealthReaderTest {
    @Test
    fun `previous night uses the recorded local date and ignores the afternoon nap and unfinished sleep`() {
        val recordedAt = ZonedDateTime.parse("2026-11-22T17:05:00+09:00[Asia/Tokyo]")
        val fullNight = SleepInterval(
            Instant.parse("2026-11-21T14:30:00Z"), Instant.parse("2026-11-21T22:00:00Z")
        )
        val nap = SleepInterval(Instant.parse("2026-11-22T04:00:00Z"), Instant.parse("2026-11-22T05:00:00Z"))
        val unfinished = SleepInterval(Instant.parse("2026-11-22T02:00:00Z"), Instant.parse("2026-11-22T09:00:00Z"))
        assertEquals(fullNight, previousNight(listOf(nap, unfinished, fullNight), recordedAt))
        assertNull(previousNight(listOf(fullNight), recordedAt.minusHours(13)))
    }

    @Test
    fun `comparison uses observed days and preserves missing data`() {
        assertNull(compareFeelingValue(null, listOf(2, 3, 4)))
        assertEquals(FeelingComparison(0, comparisonDays = 2), compareFeelingValue(0, listOf(0, 20)))
        assertEquals(
            FeelingComparison(72, typical = 64, comparisonDays = 4, sampleCount = 3),
            compareFeelingValue(72, listOf(130, 64, 6, 63), samples = 3)
        )
    }

    @Test
    fun `heart rate filters individual measurements when a record spans multiple windows`() {
        val end = Instant.parse("2026-11-22T01:00:00Z")
        val recent = HeartRateRecord.Sample(end.minusSeconds(90), 84)
        val observations = listOf(
            HeartRateRecord.Sample(end.minusSeconds(780), 70),
            recent,
            recent,
            HeartRateRecord.Sample(end, 180)
        )
        assertEquals(HeartSummary(84, 1), summarizeHeartSamples(observations, end.minusSeconds(300), end))
        assertEquals(HeartSummary(77, 2), summarizeHeartSamples(observations, end.minusSeconds(1800), end))
    }

    @Test
    fun `approximate moon age uses the instant across dates`() {
        val newMoon = Instant.parse("2000-01-06T18:14:00Z").toEpochMilli()
        assertEquals(0.0, approximateMoonAge(newMoon), 0.01)
        assertEquals(7.0, approximateMoonAge(newMoon + 7 * 86_400_000L), 0.01)
        assertEquals(28.53, approximateMoonAge(newMoon - 86_400_000L), 0.01)
    }
}

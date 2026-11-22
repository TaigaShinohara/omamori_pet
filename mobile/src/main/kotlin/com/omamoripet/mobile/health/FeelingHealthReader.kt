package com.omamoripet.mobile.health

import android.os.RemoteException
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.omamoripet.mobile.data.FeelingComparison
import com.omamoripet.mobile.data.FeelingHealth
import com.omamoripet.mobile.data.FeelingSleep
import com.omamoripet.mobile.data.PendingFeelingActivity
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt
import kotlin.reflect.KClass

private const val COMPARISON_DAYS = 14

internal suspend fun HealthConnectClient.collectFeelingHealth(
    recordedAt: ZonedDateTime,
    granted: Set<String>,
    pendingActivity: PendingFeelingActivity?
): FeelingHealth? {
    val anchor = recordedAt.toInstant()
    val comparisonTimes = (1L..COMPARISON_DAYS.toLong()).map(recordedAt::minusDays)
    val canReadSteps = HealthPermission.getReadPermission(StepsRecord::class) in granted
    val canReadHeart = HealthPermission.getReadPermission(HeartRateRecord::class) in granted
    val canReadSleep = HealthPermission.getReadPermission(SleepSessionRecord::class) in granted
    val canReadPeriod = HealthPermission.getReadPermission(MenstruationPeriodRecord::class) in granted

    val current = readActivityWindow(anchor, canReadSteps, canReadHeart)
    if (pendingActivity != null) {
        val stepsArrived = pendingActivity.steps && current.steps5 != null
        val heartArrived = pendingActivity.heart && current.heart5 != null
        if (!stepsArrived && !heartArrived) return null
    }

    val previous = if (current == ActivityWindow()) emptyList() else comparisonTimes.map {
        readActivityWindow(it.toInstant(), canReadSteps, canReadHeart)
    }
    val sleep = if (canReadSleep) optionalHealthRead {
        val sessions = readAll(
            SleepSessionRecord::class,
            comparisonTimes.last().toLocalDate().minusDays(1).atStartOfDay(recordedAt.zone).toInstant(),
            anchor
        )
        val intervals = sessions.map { SleepInterval(it.startTime, it.endTime) }
        previousNight(intervals, recordedAt)?.let { night ->
            FeelingSleep(
                duration = compareFeelingValue(
                    night.minutes, comparisonTimes.mapNotNull { previousNight(intervals, it)?.minutes }
                )!!,
                wokeAtEpochMs = night.end.toEpochMilli()
            )
        }
    } else null
    val periodDay = if (canReadPeriod) optionalHealthRead {
        readAll(MenstruationPeriodRecord::class, anchor.minus(30, ChronoUnit.DAYS), anchor)
            .map { it.startTime }
            .filter { !it.isAfter(anchor) }
            .maxOrNull()
            ?.let { start ->
                ChronoUnit.DAYS.between(
                    start.atZone(recordedAt.zone).toLocalDate(), recordedAt.toLocalDate()
                ).toInt() + 1
            }
    } else null
    return FeelingHealth(
        sleep = sleep,
        steps5 = compareFeelingValue(current.steps5, previous.mapNotNull { it.steps5 }),
        steps30 = compareFeelingValue(current.steps30, previous.mapNotNull { it.steps30 }),
        heart5 = compareFeelingValue(
            current.heart5?.average, previous.mapNotNull { it.heart5?.average }, current.heart5?.samples
        ),
        heart30 = compareFeelingValue(
            current.heart30?.average, previous.mapNotNull { it.heart30?.average }, current.heart30?.samples
        ),
        periodDay = periodDay
    ).takeUnless { it == FeelingHealth() }
}

private data class ActivityWindow(
    val steps5: Int? = null,
    val steps30: Int? = null,
    val heart5: HeartSummary? = null,
    val heart30: HeartSummary? = null
)

internal data class HeartSummary(val average: Int, val samples: Int)

private suspend fun HealthConnectClient.readActivityWindow(
    end: Instant, steps: Boolean, heart: Boolean
): ActivityWindow {
    suspend fun readSteps(minutes: Long): Int? = if (steps) optionalHealthRead {
        aggregate(
            AggregateRequest(
                setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(end.minus(minutes, ChronoUnit.MINUTES), end)
            )
        )[StepsRecord.COUNT_TOTAL]?.toInt()
    } else null
    val heartSamples = if (heart) optionalHealthRead {
        readAll(HeartRateRecord::class, end.minus(30, ChronoUnit.MINUTES), end).flatMap { it.samples }
    }.orEmpty() else emptyList()
    return ActivityWindow(
        steps5 = readSteps(5),
        steps30 = readSteps(30),
        heart5 = summarizeHeartSamples(heartSamples, end.minus(5, ChronoUnit.MINUTES), end),
        heart30 = summarizeHeartSamples(heartSamples, end.minus(30, ChronoUnit.MINUTES), end)
    )
}

internal fun summarizeHeartSamples(
    samples: List<HeartRateRecord.Sample>, start: Instant, end: Instant
): HeartSummary? {
    val observed = samples.filter { it.time >= start && it.time < end }
        .distinctBy { it.time to it.beatsPerMinute }
    return if (observed.isEmpty()) null else {
        HeartSummary(observed.map { it.beatsPerMinute }.average().roundToInt(), observed.size)
    }
}

internal fun compareFeelingValue(value: Int?, previous: List<Int>, samples: Int? = null): FeelingComparison? {
    if (value == null) return null
    val sorted = previous.sorted()
    val typical = when {
        sorted.size < 3 -> null
        sorted.size % 2 == 1 -> sorted[sorted.size / 2]
        else -> ((sorted[sorted.size / 2 - 1].toDouble() + sorted[sorted.size / 2]) / 2).roundToInt()
    }
    return FeelingComparison(value, typical, sorted.size, samples)
}

internal data class SleepInterval(val start: Instant, val end: Instant) {
    val minutes: Int get() = Duration.between(start, end).toMinutes().toInt()
}

internal fun previousNight(sessions: List<SleepInterval>, recordedAt: ZonedDateTime): SleepInterval? {
    val date = recordedAt.toLocalDate()
    val start = date.minusDays(1).atTime(18, 0).atZone(recordedAt.zone).toInstant()
    val noon = date.atTime(12, 0).atZone(recordedAt.zone).toInstant()
    val end = recordedAt.toInstant()
    return sessions.asSequence()
        .filter { it.end > start && it.start < noon && it.end <= end && it.minutes > 0 }
        .maxWithOrNull(compareBy(SleepInterval::minutes, SleepInterval::end))
}

private suspend fun <T : Record> HealthConnectClient.readAll(type: KClass<T>, start: Instant, end: Instant): List<T> {
    val records = mutableListOf<T>()
    var pageToken: String? = null
    do {
        val page = readRecords(ReadRecordsRequest(type, TimeRangeFilter.between(start, end), pageToken = pageToken))
        records.addAll(page.records)
        pageToken = page.pageToken?.takeIf { it.isNotEmpty() }
    } while (pageToken != null)
    return records
}

private suspend fun <T> optionalHealthRead(read: suspend () -> T): T? =
    try {
        read()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: SecurityException) {
        null
    } catch (_: IOException) {
        null
    } catch (_: IllegalStateException) {
        null
    } catch (_: UnsupportedOperationException) {
        null
    } catch (_: RemoteException) {
        null
    }

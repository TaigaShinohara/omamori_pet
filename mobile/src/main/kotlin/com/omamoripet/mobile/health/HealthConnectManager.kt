package com.omamoripet.mobile.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import com.omamoripet.mobile.data.FeelingHealth
import com.omamoripet.mobile.data.PendingFeelingActivity

enum class HealthConnectAvailability {
    AVAILABLE,
    UPDATE_REQUIRED,
    UNAVAILABLE
}

data class RestingHeartRateBaseline(
    val bpm: Int,
    val measuredAtEpochMs: Long
)

class HealthConnectManager(private val context: Context) {

    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(RestingHeartRateRecord::class)
    )
    internal val feelingStepsPermission = HealthPermission.getReadPermission(StepsRecord::class)
    internal val feelingHeartPermission = HealthPermission.getReadPermission(HeartRateRecord::class)
    val feelingPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        feelingStepsPermission,
        feelingHeartPermission
    )
    val periodPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(MenstruationPeriodRecord::class)
    )

    fun checkAvailability(): HealthConnectAvailability {
        return when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.UPDATE_REQUIRED
            else -> HealthConnectAvailability.UNAVAILABLE
        }
    }

    suspend fun hasAllPermissions(): Boolean {
        return grantedPermissions().containsAll(requiredPermissions)
    }

    suspend fun grantedPermissions(): Set<String> {
        if (checkAvailability() != HealthConnectAvailability.AVAILABLE) return emptySet()
        return runCatching {
            val client = HealthConnectClient.getOrCreate(context)
            client.permissionController.getGrantedPermissions()
        }.onFailure { if (it is CancellationException) throw it }.getOrDefault(emptySet())
    }

    internal suspend fun readFeelingHealth(
        recordedAt: Instant,
        zone: ZoneId,
        pendingActivity: PendingFeelingActivity?
    ): FeelingHealth? {
        val granted = grantedPermissions()
        if (granted.none { it in feelingPermissions || it in periodPermissions }) return null
        val client = runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull() ?: return null
        return client.collectFeelingHealth(recordedAt.atZone(zone), granted, pendingActivity)
    }

    /**
     * 過去14日分の RestingHeartRateRecord から最新の個人安静時心拍を取得する（憶測なしの実測のみ）。
     */
    suspend fun readLatestRestingHeartRate(): Result<RestingHeartRateBaseline?> {
        if (!hasAllPermissions()) return Result.success(null)
        return runCatching {
            val client = HealthConnectClient.getOrCreate(context)
            val now = Instant.now()
            val start = now.minus(14, ChronoUnit.DAYS)
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = RestingHeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, now),
                    ascendingOrder = false,
                    pageSize = 1
                )
            )
            val latest = response.records.maxByOrNull { it.time } ?: return@runCatching null
            RestingHeartRateBaseline(
                bpm = latest.beatsPerMinute.toInt(),
                measuredAtEpochMs = latest.time.toEpochMilli()
            )
        }.onFailure { if (it is CancellationException) throw it }
    }
}

package com.omamoripet.mobile.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.temporal.ChronoUnit

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

    fun checkAvailability(): HealthConnectAvailability {
        return when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.UPDATE_REQUIRED
            else -> HealthConnectAvailability.UNAVAILABLE
        }
    }

    suspend fun hasAllPermissions(): Boolean {
        if (checkAvailability() != HealthConnectAvailability.AVAILABLE) return false
        return runCatching {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            granted.containsAll(requiredPermissions)
        }.onFailure { if (it is CancellationException) throw it }.getOrDefault(false)
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

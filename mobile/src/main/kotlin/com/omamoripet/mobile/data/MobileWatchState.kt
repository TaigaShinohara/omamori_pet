package com.omamoripet.mobile.data

import com.omamoripet.shared.model.BaselineSource
import com.omamoripet.shared.model.MotionState
import com.omamoripet.shared.model.OmamoriState
import com.omamoripet.shared.sync.WatchLivePayload

internal fun MobileUiState.withWatchMeasurements(payload: WatchLivePayload): MobileUiState {
    val baselineBpm = healthConnectRestingBpm ?: payload.restingBaselineBpm
    return copy(
        omamoriState = payload.omamoriState,
        petHeartRateColor = payload.petHeartRateColor.takeIf {
            (payload.omamoriState == OmamoriState.ELEVATED_AT_REST ||
                payload.omamoriState == OmamoriState.BREATHING) &&
                baselineBpm == payload.restingBaselineBpm
        },
        currentHeartRateBpm = payload.currentHeartRateBpm,
        restingBaselineBpm = baselineBpm,
        baselineSource = if (healthConnectRestingBpm != null) BaselineSource.HEALTH_CONNECT else payload.baselineSource,
        motionState = payload.motionState,
        calmScore = payload.calmScore,
        isSimulated = payload.isSimulated,
        lastWatchSyncMs = payload.updatedAtEpochMs,
        isWatchConnected = true
    )
}

internal fun MobileUiState.withoutWatchMeasurements(): MobileUiState =
    if (demoMode != MobileDemoMode.LIVE_WATCH) this else copy(
        omamoriState = OmamoriState.RESTING,
        petHeartRateColor = null,
        currentHeartRateBpm = null,
        motionState = MotionState.OFF_BODY,
        calmScore = 0,
        isSimulated = false,
        lastWatchSyncMs = null
    )

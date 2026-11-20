package com.omamoripet.mobile.data

import com.omamoripet.shared.model.MotionState
import com.omamoripet.shared.model.OmamoriState

internal fun MobileUiState.withoutWatchMeasurements(): MobileUiState =
    if (demoMode != MobileDemoMode.LIVE_WATCH) this else copy(
        omamoriState = OmamoriState.RESTING,
        currentHeartRateBpm = null,
        motionState = MotionState.OFF_BODY,
        calmScore = 0,
        isSimulated = false,
        lastWatchSyncMs = null
    )

package com.omamoripet.wear.complication

import com.omamoripet.shared.model.MotionState
import com.omamoripet.shared.model.OmamoriState
import com.omamoripet.wear.R

internal data class WatchFaceHeartRateDisplay(val bpmText: String, val contextLabelRes: Int)

internal fun resolveWatchFaceHeartRateDisplay(
    bpm: Int?,
    motion: MotionState,
    state: OmamoriState
): WatchFaceHeartRateDisplay {
    // Off-body readings can be cached; never show them as a current measurement.
    if (bpm == null || bpm <= 0 || motion == MotionState.OFF_BODY) {
        return WatchFaceHeartRateDisplay("--", R.string.comp_context_waiting)
    }
    val label = when {
        motion == MotionState.RECENT_MOVEMENT -> R.string.comp_context_moving
        state == OmamoriState.BREATHING -> R.string.comp_context_breathing
        state == OmamoriState.ELEVATED_AT_REST -> R.string.comp_context_still
        else -> R.string.comp_context_calm
    }
    return WatchFaceHeartRateDisplay(bpm.toString(), label)
}

package com.omamoripet.mobile.data

import com.omamoripet.shared.model.Feeling
import com.omamoripet.shared.model.FeelingFace
import kotlinx.serialization.Serializable

@Serializable
data class FeelingRecord(val id: String, val feeling: Feeling, val recordedAtEpochMs: Long) {
    internal val face: FeelingFace get() = FeelingFace(feeling, recordedAtEpochMs)
}

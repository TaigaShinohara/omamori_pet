package com.omamoripet.mobile.data

import com.omamoripet.shared.model.Feeling
import com.omamoripet.shared.model.FeelingContext
import com.omamoripet.shared.model.FeelingFace
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class FeelingRecord(
    val id: String,
    val feeling: Feeling,
    val recordedAtEpochMs: Long,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val context: FeelingContext? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val recordedZoneId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val health: FeelingHealth? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val pressure: FeelingPressure? = null
) {
    internal val face: FeelingFace get() = FeelingFace(feeling, recordedAtEpochMs)
}

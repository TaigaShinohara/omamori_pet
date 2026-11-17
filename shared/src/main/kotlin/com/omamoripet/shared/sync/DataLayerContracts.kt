package com.omamoripet.shared.sync

import com.omamoripet.shared.model.AlertPreferences
import com.omamoripet.shared.model.BaselineSource
import com.omamoripet.shared.model.BreathingSessionRecord
import com.omamoripet.shared.model.CalmEventRecord
import com.omamoripet.shared.model.MotionState
import com.omamoripet.shared.model.OmamoriState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object DataLayerPaths {
    const val WATCH_LIVE_STATE = "/omamori/watch_live_state"
    const val PHONE_BASELINE_AND_ADVICE = "/omamori/phone_baseline_and_advice"
    const val BREATHING_SESSION = "/omamori/breathing_session"
    const val CALM_EVENT = "/omamori/calm_event"
    const val ALERT_PREFERENCES = "/omamori/alert_preferences"
    const val COMMAND_START_BREATHING = "/omamori/command/start_breathing"
    const val KEY_PAYLOAD = "payload_json"
}

@Serializable
data class WatchLivePayload(
    val currentHeartRateBpm: Int?,
    val restingBaselineBpm: Int,
    val baselineSource: BaselineSource,
    val motionState: MotionState,
    val omamoriState: OmamoriState,
    val calmScore: Int,
    val isWatchOnBody: Boolean,
    val isSimulated: Boolean,
    val updatedAtEpochMs: Long
)

@Serializable
data class PhoneBaselinePayload(
    val healthConnectRestingBpm: Int?,
    val restingMeasuredAtEpochMs: Long?,
    val latestAiAdviceText: String?,
    val aiSourceLabel: String?,
    val updatedAtEpochMs: Long
)

@Serializable
data class PreferencesSyncPayload(
    val preferences: AlertPreferences,
    val updatedAtEpochMs: Long
)

object OmamoriJson {
    val codec: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    inline fun <reified T> encode(value: T): String = codec.encodeToString(value)

    inline fun <reified T> decodeOrNull(raw: String?): T? {
        if (raw.isNullOrBlank()) return null
        return runCatching { codec.decodeFromString<T>(raw) }.getOrNull()
    }
}

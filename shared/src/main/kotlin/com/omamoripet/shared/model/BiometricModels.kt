package com.omamoripet.shared.model

import kotlinx.serialization.Serializable

/**
 * お守りペットの現在状態。不安を煽らない非断定的な4状態に絞る。
 */
@Serializable
enum class OmamoriState {
    /** 落ち着いている・日常の安定した状態 */
    CALM,
    /** 静止中（平常時）に心拍ベースラインより高止まりしている気づき状態 */
    ELEVATED_AT_REST,
    /** 4-2-6 触覚呼吸ガイドを実施中 */
    BREATHING,
    /** 未装着・センサー準備中・待機中 */
    RESTING
}

@Serializable
enum class BaselineSource {
    /** 初期目安値（まだ学習・同期データが不足） */
    DEFAULT_FALLBACK,
    /** ウォッチの静止時心拍から自動学習した個人基準値 */
    WATCH_LEARNED,
    /** Health Connect の安静時心拍レコードと連携した基準値 */
    HEALTH_CONNECT
}

@Serializable
enum class MotionState {
    /** 規定時間（例: 3分）連続で歩行・体動がない真の平常時 */
    STILL_CONFIRMED,
    /** 直近で移動・体動があった（階段・歩行の余韻による心拍上昇を除外） */
    RECENT_MOVEMENT,
    /** 時計が外されている */
    OFF_BODY
}

@Serializable
enum class AlertSuppressionReason {
    NONE,
    NOT_ON_BODY,
    RECENT_MOVEMENT,
    BELOW_THRESHOLD,
    DURATION_TOO_SHORT,
    IN_COOLDOWN,
    QUIET_HOURS,
    SNOOZED,
    ALERTS_DISABLED,
    ALREADY_BREATHING
}

@Serializable
data class BiometricSnapshot(
    val currentHeartRateBpm: Int?,
    val restingBaselineBpm: Int,
    val baselineSource: BaselineSource,
    val motionState: MotionState,
    val isWatchOnBody: Boolean,
    val sustainedElevationSeconds: Int,
    val sampledAtEpochMs: Long,
    val isSimulated: Boolean = false
)

@Serializable
data class CalmEvaluation(
    val state: OmamoriState,
    val calmScore: Int,
    val deltaFromRestingBpm: Int?,
    val shouldTriggerAlert: Boolean,
    val suppressionReason: AlertSuppressionReason
)

@Serializable
data class AlertPreferences(
    val alertsEnabled: Boolean = true,
    val elevationThresholdBpm: Int = 15,
    val requiredStillSeconds: Int = 180,
    val requiredSustainedSeconds: Int = 90,
    val cooldownMinutes: Int = 30,
    val quietHoursEnabled: Boolean = true,
    val quietHoursStartHour: Int = 23,
    val quietHoursEndHour: Int = 7,
    val snoozedUntilEpochMs: Long = 0L,
    val breathingHapticsEnabled: Boolean = true
)

@Serializable
enum class CalmEventAction {
    ALERT_SENT,
    BREATHING_COMPLETED,
    SNOOZED,
    RESOLVED_NATURALLY
}

@Serializable
data class CalmEventRecord(
    val id: String,
    val timestampEpochMs: Long,
    val peakBpm: Int,
    val restingBaselineBpm: Int,
    val sustainedSeconds: Int,
    val actionTaken: CalmEventAction,
    val isSimulated: Boolean = false
)

@Serializable
data class BreathingSessionRecord(
    val id: String,
    val startedAtEpochMs: Long,
    val durationSeconds: Int,
    val preBpm: Int?,
    val postBpm: Int?,
    val triggeredByAlert: Boolean,
    val isSimulated: Boolean = false
)

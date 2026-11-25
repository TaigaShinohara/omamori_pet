package com.omamoripet.shared.model

/**
 * お守りペットの体色（心拍・データ状態軸）。
 * 装着中かつデータ取得済みの間は GREEN / YELLOW / RED / PURPLE のいずれかになり、
 * 未装着・データ欠損時のみ DATA_MISSING（グレー）になる。
 */
enum class PetBodyTone {
    DATA_MISSING,
    GREEN,
    YELLOW,
    RED,
    PURPLE
}

/**
 * お守りペットの表情・アニメーション軸。
 */
enum class PetExpressionMode {
    SLEEPING,
    CALM,
    ELEVATED,
    BREATHING
}

/**
 * お守りペットの見た目を「体色（心拍・データ状態）」と「表情（モード）」の2軸で表す正本モデル。
 */
data class PetVisualState(
    val tone: PetBodyTone,
    val mode: PetExpressionMode
)

/**
 * [OmamoriState] と心拍色・データ欠損・呼吸実施状態から、2軸の [PetVisualState] を決定論的に解決する。
 */
fun resolvePetVisualState(
    state: OmamoriState,
    petHeartRateColor: PetHeartRateColor? = null,
    isDataMissing: Boolean = false,
    isBreathingActive: Boolean = false
): PetVisualState {
    val missing = isDataMissing || state == OmamoriState.RESTING
    val tone = if (missing) {
        PetBodyTone.DATA_MISSING
    } else {
        when (petHeartRateColor) {
            null -> PetBodyTone.GREEN
            PetHeartRateColor.YELLOW -> PetBodyTone.YELLOW
            PetHeartRateColor.RED -> PetBodyTone.RED
            PetHeartRateColor.PURPLE -> PetBodyTone.PURPLE
        }
    }
    val mode = when {
        isBreathingActive || state == OmamoriState.BREATHING -> PetExpressionMode.BREATHING
        missing -> PetExpressionMode.SLEEPING
        state == OmamoriState.ELEVATED_AT_REST -> PetExpressionMode.ELEVATED
        else -> PetExpressionMode.CALM
    }
    return PetVisualState(tone = tone, mode = mode)
}

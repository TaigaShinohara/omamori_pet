package com.omamoripet.wear.ui

import androidx.annotation.StringRes
import com.omamoripet.shared.model.OmamoriState
import com.omamoripet.shared.model.PetHeartRateColor
import com.omamoripet.wear.R

@StringRes
internal fun resolvePetStateLabelRes(
    state: OmamoriState,
    petHeartRateColor: PetHeartRateColor? = null
): Int = when (state) {
    OmamoriState.CALM -> R.string.pet_state_calm
    OmamoriState.ELEVATED_AT_REST -> when (petHeartRateColor) {
        null -> R.string.pet_state_slightly_high
        PetHeartRateColor.YELLOW -> R.string.pet_state_high
        PetHeartRateColor.RED -> R.string.pet_state_very_high
        PetHeartRateColor.PURPLE -> R.string.pet_state_extremely_high
    }
    OmamoriState.BREATHING -> R.string.pet_state_breathing
    OmamoriState.RESTING -> R.string.pet_state_resting
}

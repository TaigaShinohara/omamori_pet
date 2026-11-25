package com.omamoripet.wear.ui

import androidx.annotation.DrawableRes
import com.omamoripet.shared.model.OmamoriState
import com.omamoripet.shared.model.PetHeartRateColor
import com.omamoripet.wear.R

@DrawableRes
internal fun resolvePetDrawableRes(
    state: OmamoriState,
    monochromatic: Boolean = false,
    petHeartRateColor: PetHeartRateColor? = null
): Int = when (state) {
    OmamoriState.CALM -> if (monochromatic) R.drawable.pet_calm_mono else R.drawable.pet_calm
    OmamoriState.BREATHING -> if (monochromatic) {
        R.drawable.pet_breathing_mono
    } else {
        when (petHeartRateColor) {
            PetHeartRateColor.YELLOW -> R.drawable.pet_breathing_yellow
            PetHeartRateColor.RED -> R.drawable.pet_breathing_red
            PetHeartRateColor.PURPLE -> R.drawable.pet_breathing_purple
            null -> R.drawable.pet_breathing
        }
    }
    OmamoriState.RESTING -> if (monochromatic) R.drawable.pet_resting_mono else R.drawable.pet_resting
    OmamoriState.ELEVATED_AT_REST -> when (petHeartRateColor) {
        PetHeartRateColor.YELLOW -> if (monochromatic) R.drawable.pet_strain_yellow_mono else R.drawable.pet_strain_yellow
        PetHeartRateColor.RED -> if (monochromatic) R.drawable.pet_strain_red_mono else R.drawable.pet_strain_red
        PetHeartRateColor.PURPLE -> if (monochromatic) R.drawable.pet_strain_purple_mono else R.drawable.pet_strain_purple
        null -> if (monochromatic) R.drawable.pet_elevated_mono else R.drawable.pet_elevated
    }
}

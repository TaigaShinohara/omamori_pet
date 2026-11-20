package com.omamoripet.wear.complication

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.omamoripet.shared.model.MotionState
import com.omamoripet.shared.model.OmamoriState
import com.omamoripet.shared.model.PetHeartRateColor
import com.omamoripet.wear.MainActivity
import com.omamoripet.wear.OmamoriWearApplication
import com.omamoripet.wear.R
import com.omamoripet.wear.ui.resolvePetDrawableRes

private fun launchWatchAppPendingIntent(context: Context, openBreathing: Boolean): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(MainActivity.EXTRA_OPEN_BREATHING, openBreathing)
    }
    return PendingIntent.getActivity(
        context,
        if (openBreathing) 12 else 11,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

internal fun resolvePetComplicationCode(
    state: OmamoriState,
    petHeartRateColor: PetHeartRateColor? = null
): Float = when (state) {
    OmamoriState.CALM -> 0f
    // Older watch faces render all values in this band as the green elevated face.
    OmamoriState.ELEVATED_AT_REST -> when (petHeartRateColor) {
        PetHeartRateColor.YELLOW -> 1.125f
        PetHeartRateColor.RED -> 1.25f
        PetHeartRateColor.PURPLE -> 1.375f
        null -> 1f
    }
    OmamoriState.RESTING -> 2f
    OmamoriState.BREATHING -> 3f
}

internal fun resolveHeartRateDisplayText(
    context: Context,
    bpm: Int?,
    delta: Int?,
    motion: MotionState,
    state: OmamoriState
): String = when {
    bpm == null || motion == MotionState.OFF_BODY -> "-- bpm"
    motion == MotionState.RECENT_MOVEMENT ->
        context.getString(R.string.comp_moving_bpm, bpm)
    state == OmamoriState.ELEVATED_AT_REST && delta != null ->
        context.getString(R.string.comp_still_elevated_bpm, bpm, delta)
    delta != null -> {
        val sign = if (delta >= 0) "+" else ""
        context.getString(R.string.comp_calm_bpm, bpm, "$sign$delta")
    }
    else -> "$bpm bpm"
}

class PetStateComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val repository = (application as OmamoriWearApplication).repository
        repository.awaitInitialized()
        val evaluation = repository.uiState.value.evaluation
        val state = evaluation.state
        val petHeartRateColor = evaluation.petHeartRateColor

        val shortLabel = when (state) {
            OmamoriState.CALM -> getString(R.string.state_calm_short)
            OmamoriState.ELEVATED_AT_REST -> getString(
                if (petHeartRateColor == null) R.string.state_elevated_short else R.string.state_heart_rate_high_short
            )
            OmamoriState.BREATHING -> getString(R.string.state_breathing_short)
            OmamoriState.RESTING -> getString(R.string.state_resting_short)
        }
        val tapAction = launchWatchAppPendingIntent(
            context = this,
            openBreathing = false
        )
        val contentDescription = PlainComplicationText.Builder(shortLabel).build()
        val colorIcon = Icon.createWithResource(
            this,
            resolvePetDrawableRes(state = state, petHeartRateColor = petHeartRateColor)
        )
        val monoIcon = Icon.createWithResource(
            this,
            resolvePetDrawableRes(state = state, monochromatic = true, petHeartRateColor = petHeartRateColor)
        )

        return when (request.complicationType) {
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = resolvePetComplicationCode(state, petHeartRateColor),
                min = 0f,
                max = 3f,
                contentDescription = contentDescription
            )
                .setSmallImage(SmallImage.Builder(colorIcon, SmallImageType.ICON).build())
                .setMonochromaticImage(MonochromaticImage.Builder(monoIcon).build())
                .setText(PlainComplicationText.Builder(shortLabel).build())
                .setTapAction(tapAction)
                .build()

            ComplicationType.SMALL_IMAGE -> SmallImageComplicationData.Builder(
                smallImage = SmallImage.Builder(colorIcon, SmallImageType.ICON).build(),
                contentDescription = contentDescription
            )
                .setTapAction(tapAction)
                .build()

            ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(
                monochromaticImage = MonochromaticImage.Builder(monoIcon).build(),
                contentDescription = contentDescription
            )
                .setTapAction(tapAction)
                .build()

            else -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(shortLabel).build(),
                contentDescription = contentDescription
            )
                .setTapAction(tapAction)
                .build()
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData {
        val label = getString(R.string.state_calm_short)
        val desc = PlainComplicationText.Builder(label).build()
        val colorIcon = Icon.createWithResource(this, R.drawable.pet_calm)
        val monoIcon = Icon.createWithResource(this, R.drawable.pet_calm_mono)
        return when (type) {
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = resolvePetComplicationCode(OmamoriState.CALM),
                min = 0f,
                max = 3f,
                contentDescription = desc
            )
                .setSmallImage(SmallImage.Builder(colorIcon, SmallImageType.ICON).build())
                .setMonochromaticImage(MonochromaticImage.Builder(monoIcon).build())
                .setText(PlainComplicationText.Builder(label).build())
                .build()

            ComplicationType.SMALL_IMAGE -> SmallImageComplicationData.Builder(
                smallImage = SmallImage.Builder(colorIcon, SmallImageType.ICON).build(),
                contentDescription = desc
            ).build()

            ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(
                monochromaticImage = MonochromaticImage.Builder(monoIcon).build(),
                contentDescription = desc
            ).build()

            else -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(label).build(),
                contentDescription = desc
            ).build()
        }
    }
}

class HeartRateComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val repository = (application as OmamoriWearApplication).repository
        repository.awaitInitialized()
        val uiState = repository.uiState.value
        val bpm = uiState.snapshot.currentHeartRateBpm
        val delta = uiState.evaluation.deltaFromRestingBpm
        val calmScore = uiState.evaluation.calmScore.toFloat().coerceIn(0f, 100f)
        val motion = uiState.snapshot.motionState
        val state = uiState.evaluation.state

        val display = resolveHeartRateDisplayText(
            context = this,
            bpm = bpm,
            delta = delta,
            motion = motion,
            state = state
        )
        val tapAction = launchWatchAppPendingIntent(
            context = this,
            openBreathing = false
        )
        val text = PlainComplicationText.Builder(display).build()

        return when (request.complicationType) {
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = calmScore,
                min = 0f,
                max = 100f,
                contentDescription = text
            )
                .setText(text)
                .setTapAction(tapAction)
                .build()

            else -> ShortTextComplicationData.Builder(
                text = text,
                contentDescription = text
            )
                .setTapAction(tapAction)
                .build()
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData {
        val previewLabel = getString(R.string.comp_calm_bpm, 68, "+2")
        val text = PlainComplicationText.Builder(previewLabel).build()
        return when (type) {
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = 88f,
                min = 0f,
                max = 100f,
                contentDescription = text
            ).setText(text).build()
            else -> ShortTextComplicationData.Builder(
                text = text,
                contentDescription = text
            ).build()
        }
    }
}

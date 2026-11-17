package com.omamoripet.wear.complication

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.omamoripet.shared.model.OmamoriState
import com.omamoripet.wear.MainActivity
import com.omamoripet.wear.OmamoriWearApplication
import com.omamoripet.wear.R

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

class PetStateComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val state = (application as OmamoriWearApplication).repository.uiState.value.evaluation.state
        val iconRes = when (state) {
            OmamoriState.CALM -> R.drawable.pet_calm
            OmamoriState.ELEVATED_AT_REST -> R.drawable.pet_elevated
            OmamoriState.BREATHING -> R.drawable.pet_breathing
            OmamoriState.RESTING -> R.drawable.pet_resting
        }
        val shortLabel = when (state) {
            OmamoriState.CALM -> getString(R.string.state_calm_short)
            OmamoriState.ELEVATED_AT_REST -> getString(R.string.state_elevated_short)
            OmamoriState.BREATHING -> getString(R.string.state_breathing_short)
            OmamoriState.RESTING -> getString(R.string.state_resting_short)
        }
        val tapAction = launchWatchAppPendingIntent(
            context = this,
            openBreathing = state == OmamoriState.ELEVATED_AT_REST
        )
        val contentDescription = PlainComplicationText.Builder(shortLabel).build()
        val icon = Icon.createWithResource(this, iconRes)

        return when (request.complicationType) {
            ComplicationType.SMALL_IMAGE -> SmallImageComplicationData.Builder(
                smallImage = SmallImage.Builder(icon, SmallImageType.ICON).build(),
                contentDescription = contentDescription
            )
                .setTapAction(tapAction)
                .build()

            ComplicationType.MONOCHROMATIC_IMAGE -> androidx.wear.watchface.complications.data.MonochromaticImageComplicationData.Builder(
                monochromaticImage = MonochromaticImage.Builder(icon).build(),
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
        val icon = Icon.createWithResource(this, R.drawable.pet_calm)
        return when (type) {
            ComplicationType.SMALL_IMAGE -> SmallImageComplicationData.Builder(
                smallImage = SmallImage.Builder(icon, SmallImageType.ICON).build(),
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
        val uiState = (application as OmamoriWearApplication).repository.uiState.value
        val bpm = uiState.snapshot.currentHeartRateBpm
        val delta = uiState.evaluation.deltaFromRestingBpm
        val calmScore = uiState.evaluation.calmScore.toFloat().coerceIn(0f, 100f)

        val display = if (bpm != null && delta != null) {
            val sign = if (delta >= 0) "+" else ""
            "♥ $bpm ($sign$delta)"
        } else {
            "♥ --"
        }
        val tapAction = launchWatchAppPendingIntent(
            context = this,
            openBreathing = uiState.evaluation.state == OmamoriState.ELEVATED_AT_REST
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
        val text = PlainComplicationText.Builder("♥ 68 (+2)").build()
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

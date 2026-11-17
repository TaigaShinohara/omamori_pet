package com.omamoripet.wear.tile

import android.content.ComponentName
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.omamoripet.shared.model.OmamoriState
import com.omamoripet.wear.MainActivity
import com.omamoripet.wear.OmamoriWearApplication
import com.omamoripet.wear.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future

private const val RESOURCES_VERSION = "1"
private const val ID_PET_CALM = "pet_calm"
private const val ID_PET_ELEVATED = "pet_elevated"
private const val ID_PET_BREATHING = "pet_breathing"
private const val ID_PET_RESTING = "pet_resting"

class OmamoriTileService : TileService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        serviceScope.future {
            val uiState = (application as OmamoriWearApplication).repository.uiState.value
            val state = uiState.evaluation.state
            val bpm = uiState.snapshot.currentHeartRateBpm
            val delta = uiState.evaluation.deltaFromRestingBpm

            val stateTitle = when (state) {
                OmamoriState.CALM -> getString(R.string.state_calm)
                OmamoriState.ELEVATED_AT_REST -> getString(R.string.state_elevated)
                OmamoriState.BREATHING -> getString(R.string.state_breathing)
                OmamoriState.RESTING -> getString(R.string.state_resting)
            }
            val bpmText = if (bpm != null && delta != null) {
                val sign = if (delta >= 0) "+" else ""
                "$bpm bpm ($sign$delta)"
            } else {
                "-- bpm"
            }
            val motionLabel = when (uiState.snapshot.motionState) {
                com.omamoripet.shared.model.MotionState.STILL_CONFIRMED -> getString(R.string.still_confirmed)
                com.omamoripet.shared.model.MotionState.RECENT_MOVEMENT -> getString(R.string.recent_movement)
                com.omamoripet.shared.model.MotionState.OFF_BODY -> getString(R.string.off_body)
            }
            val petResourceId = when (state) {
                OmamoriState.CALM -> ID_PET_CALM
                OmamoriState.ELEVATED_AT_REST -> ID_PET_ELEVATED
                OmamoriState.BREATHING -> ID_PET_BREATHING
                OmamoriState.RESTING -> ID_PET_RESTING
            }

            val openBreathingAction = ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setClassName(MainActivity::class.java.name)
                        .setPackageName(packageName)
                        .addKeyToExtraMapping(
                            MainActivity.EXTRA_OPEN_BREATHING,
                            ActionBuilders.booleanExtra(true)
                        )
                        .build()
                )
                .build()

            val primaryColor = when (state) {
                OmamoriState.ELEVATED_AT_REST -> 0xFFE8B878.toInt()
                OmamoriState.BREATHING -> 0xFF93C7B5.toInt()
                OmamoriState.CALM, OmamoriState.RESTING -> 0xFF8FD3B6.toInt()
            }
            val onPrimaryColor = when (state) {
                OmamoriState.ELEVATED_AT_REST -> 0xFF2E1E08.toInt()
                OmamoriState.BREATHING -> 0xFF0B251D.toInt()
                OmamoriState.CALM, OmamoriState.RESTING -> 0xFF0A261B.toInt()
            }
            val materialColors = Colors(
                primaryColor,
                onPrimaryColor,
                0xFF1D2631.toInt(),
                0xFFECE8DF.toInt()
            )

            val content = LayoutElementBuilders.Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(
                    LayoutElementBuilders.Image.Builder()
                        .setResourceId(petResourceId)
                        .setWidth(DimensionBuilders.dp(56f))
                        .setHeight(DimensionBuilders.dp(56f))
                        .build()
                )
                .addContent(
                    Text.Builder(this@OmamoriTileService, stateTitle)
                        .setTypography(Typography.TYPOGRAPHY_TITLE3)
                        .setColor(ColorBuilders.argb(0xFFECE8DF.toInt()))
                        .build()
                )
                .addContent(
                    Text.Builder(this@OmamoriTileService, bpmText)
                        .setTypography(Typography.TYPOGRAPHY_BODY1)
                        .setColor(ColorBuilders.argb(primaryColor))
                        .build()
                )
                .addContent(
                    Text.Builder(this@OmamoriTileService, motionLabel)
                        .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                        .setColor(ColorBuilders.argb(0xFFB4BCC4.toInt()))
                        .build()
                )
                .build()

            val chip = CompactChip.Builder(
                this@OmamoriTileService,
                getString(R.string.action_breathe_1m),
                ModifiersBuilders.Clickable.Builder()
                    .setOnClick(openBreathingAction)
                    .build(),
                requestParams.deviceConfiguration
            )
                .setChipColors(ChipColors.primaryChipColors(materialColors))
                .build()

            val layout = PrimaryLayout.Builder(requestParams.deviceConfiguration)
                .setResponsiveContentInsetEnabled(true)
                .setContent(content)
                .setPrimaryChipContent(chip)
                .build()

            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setFreshnessIntervalMillis(300_000L)
                .setTileTimeline(
                    TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(
                            TimelineBuilders.TimelineEntry.Builder()
                                .setLayout(
                                    LayoutElementBuilders.Layout.Builder()
                                        .setRoot(layout)
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build()
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> = serviceScope.future {
        ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .addIdToImageMapping(
                ID_PET_CALM,
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(R.drawable.pet_calm)
                            .build()
                    ).build()
            )
            .addIdToImageMapping(
                ID_PET_ELEVATED,
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(R.drawable.pet_elevated)
                            .build()
                    ).build()
            )
            .addIdToImageMapping(
                ID_PET_BREATHING,
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(R.drawable.pet_breathing)
                            .build()
                    ).build()
            )
            .addIdToImageMapping(
                ID_PET_RESTING,
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(R.drawable.pet_resting)
                            .build()
                    ).build()
            )
            .build()
    }
}

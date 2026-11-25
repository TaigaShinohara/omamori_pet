package com.omamoripet.wear.tile

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
import com.omamoripet.shared.model.MotionState
import com.omamoripet.wear.MainActivity
import com.omamoripet.wear.OmamoriWearApplication
import com.omamoripet.wear.R
import com.omamoripet.wear.ui.resolvePetDrawableRes
import com.omamoripet.wear.ui.resolvePetStateLabelRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future

private const val RESOURCES_VERSION = "5"
private val petDrawables = intArrayOf(
    R.drawable.pet_calm,
    R.drawable.pet_elevated,
    R.drawable.pet_strain_yellow,
    R.drawable.pet_strain_red,
    R.drawable.pet_strain_purple,
    R.drawable.pet_breathing,
    R.drawable.pet_breathing_yellow,
    R.drawable.pet_breathing_red,
    R.drawable.pet_breathing_purple,
    R.drawable.pet_resting
)

class OmamoriTileService : TileService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Default)

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        serviceScope.future {
            val repository = (application as OmamoriWearApplication).repository
            repository.awaitInitialized()
            val uiState = repository.uiState.value
            val state = uiState.evaluation.state
            val petHeartRateColor = uiState.evaluation.petHeartRateColor
            val bpm = uiState.snapshot.currentHeartRateBpm
            val delta = uiState.evaluation.deltaFromRestingBpm

            val stateTitle = getString(resolvePetStateLabelRes(state, petHeartRateColor))
            val bpmText = if (bpm != null && delta != null) {
                val sign = if (delta >= 0) "+" else ""
                "$bpm bpm ($sign$delta)"
            } else {
                null
            }
            val motionLabel = when {
                uiState.snapshot.motionState == MotionState.OFF_BODY -> getString(R.string.off_body)
                bpm == null -> getString(R.string.waiting_for_reading)
                uiState.snapshot.motionState == MotionState.STILL_CONFIRMED -> getString(R.string.still_confirmed)
                else -> getString(R.string.recent_movement)
            }
            val petResourceId = resources.getResourceEntryName(
                resolvePetDrawableRes(state, petHeartRateColor = petHeartRateColor)
            )

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

            val primaryColor = 0xFF8FD3B6.toInt()
            val onPrimaryColor = 0xFF0A261B.toInt()
            val materialColors = Colors(
                primaryColor,
                onPrimaryColor,
                0xFF1D2631.toInt(),
                0xFFECE8DF.toInt()
            )

            val contentBuilder = LayoutElementBuilders.Column.Builder()
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
            if (bpmText != null) {
                contentBuilder.addContent(
                    Text.Builder(this@OmamoriTileService, bpmText)
                        .setTypography(Typography.TYPOGRAPHY_BODY1)
                        .setColor(ColorBuilders.argb(primaryColor))
                        .build()
                )
            }
            val content = contentBuilder
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
                .setFreshnessIntervalMillis(1_800_000L)
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
        val tileResources = ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION)
        for (drawable in petDrawables) {
            tileResources.addIdToImageMapping(
                resources.getResourceEntryName(drawable),
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(drawable)
                            .build()
                    ).build()
            )
        }
        tileResources.build()
    }
}

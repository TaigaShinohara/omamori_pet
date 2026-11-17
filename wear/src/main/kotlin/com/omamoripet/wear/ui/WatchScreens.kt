package com.omamoripet.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.LinearProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.omamoripet.shared.model.MotionState
import com.omamoripet.shared.model.OmamoriState
import com.omamoripet.wear.R
import com.omamoripet.wear.breathing.BreathPhase
import com.omamoripet.wear.breathing.BreathingUiState
import com.omamoripet.wear.data.SimulationScenario
import com.omamoripet.wear.data.WatchUiState

@Composable
fun HomeWatchScreen(
    uiState: WatchUiState,
    hasSensorPermission: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onStartBreathing: () -> Unit,
    onSnooze1h: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = listState,
        timeText = { TimeText() }
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                val state = uiState.evaluation.state
                val stateTitle = when (state) {
                    OmamoriState.CALM -> stringResource(R.string.state_calm)
                    OmamoriState.ELEVATED_AT_REST -> stringResource(R.string.state_elevated)
                    OmamoriState.BREATHING -> stringResource(R.string.state_breathing)
                    OmamoriState.RESTING -> stringResource(R.string.state_resting)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 4.dp)
                ) {
                    OmamoriPetWearAvatar(
                        state = state,
                        contentDescriptionText = stateTitle,
                        size = 64.dp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (uiState.snapshot.isSimulated) "$stateTitle (${stringResource(R.string.demo_badge)})" else stateTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (!hasSensorPermission && uiState.simulationScenario == SimulationScenario.LIVE_SENSORS) {
                item {
                    Card(
                        onClick = onRequestPermissions,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ),
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                    ) {
                        Text(
                            text = stringResource(R.string.permission_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = stringResource(R.string.permission_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.action_grant_permissions),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                item {
                    OutlinedButton(
                        onClick = onOpenAppSettings,
                        label = { Text(stringResource(R.string.action_open_settings)) },
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                    )
                }
            }

            // 純粋な実測表示パネル（非インタラクティブ：誤タップで即呼吸開始させない）
            item {
                val bpm = uiState.snapshot.currentHeartRateBpm
                val delta = uiState.evaluation.deltaFromRestingBpm
                val bpmLine = if (bpm != null && delta != null) {
                    val sign = if (delta >= 0) "+" else ""
                    "$bpm bpm ($sign$delta)"
                } else {
                    "-- bpm"
                }
                val accentColor = if (uiState.evaluation.state == OmamoriState.ELEVATED_AT_REST) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                }
                val motionText = when (uiState.snapshot.motionState) {
                    MotionState.STILL_CONFIRMED -> stringResource(R.string.still_confirmed)
                    MotionState.RECENT_MOVEMENT -> stringResource(R.string.recent_movement)
                    MotionState.OFF_BODY -> stringResource(R.string.off_body)
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = bpmLine,
                            style = MaterialTheme.typography.titleMedium,
                            color = accentColor
                        )
                    }
                    Text(
                        text = motionText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.baseline_label, uiState.snapshot.restingBaselineBpm),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 1分触覚呼吸ボタン
            item {
                Button(
                    onClick = onStartBreathing,
                    colors = if (uiState.evaluation.state == OmamoriState.ELEVATED_AT_REST) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                    label = { Text(stringResource(R.string.action_breathe_1m)) },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                )
            }

            // 静止中の心拍上昇時は、やらない選択（1h休止）も1タップで出せるようにする
            if (uiState.evaluation.state == OmamoriState.ELEVATED_AT_REST) {
                item {
                    FilledTonalButton(
                        onClick = onSnooze1h,
                        label = { Text(stringResource(R.string.action_snooze_1h)) },
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = onOpenSettings,
                    label = { Text(stringResource(R.string.action_demo_settings)) },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                )
            }

            item {
                Text(
                    text = stringResource(R.string.non_medical_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun BreathingWatchScreen(
    breathingState: BreathingUiState,
    onStop: () -> Unit,
    onDoneBack: () -> Unit
) {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    ScreenScaffold(timeText = {}) { contentPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 16.dp)
        ) {
            if (breathingState.phase == BreathPhase.COMPLETED) {
                OmamoriPetWearAvatar(
                    state = OmamoriState.CALM,
                    contentDescriptionText = stringResource(R.string.breathing_done_title),
                    size = 54.dp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.breathing_done_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                val pre = breathingState.preBpm
                val post = breathingState.postBpm
                val subtitle = when {
                    pre != null && post != null && post < pre ->
                        stringResource(R.string.breathing_done_reduced, pre, post, pre - post)
                    post != null ->
                        stringResource(R.string.breathing_done_steady, post)
                    else ->
                        stringResource(R.string.breathing_done_summary)
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onDoneBack,
                    label = { Text(stringResource(R.string.breathing_back)) }
                )
            } else {
                val breathFraction = when (breathingState.phase) {
                    BreathPhase.INHALE -> breathingState.phaseProgress
                    BreathPhase.HOLD -> 1f
                    BreathPhase.EXHALE -> 1f - breathingState.phaseProgress
                    BreathPhase.COMPLETED -> 0.5f
                }
                val cueText = when (breathingState.phase) {
                    BreathPhase.INHALE -> stringResource(R.string.breathing_inhale)
                    BreathPhase.HOLD -> stringResource(R.string.breathing_hold)
                    BreathPhase.EXHALE -> stringResource(R.string.breathing_exhale)
                    BreathPhase.COMPLETED -> ""
                }
                OmamoriPetWearAvatar(
                    state = OmamoriState.BREATHING,
                    contentDescriptionText = cueText,
                    breathProgress = breathFraction,
                    size = 70.dp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = cueText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "${stringResource(R.string.breathing_seconds_left, breathingState.remainingSecondsInPhase)} · ${breathingState.cycleIndex}/${breathingState.totalCycles}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { breathingState.phaseProgress },
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(4.dp)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onStop,
                    label = { Text(stringResource(R.string.breathing_stop)) }
                )
            }
        }
    }
}

@Composable
fun DemoSettingsWatchScreen(
    uiState: WatchUiState,
    hasSensorPermission: Boolean,
    onSelectScenario: (SimulationScenario) -> Unit,
    onToggleAlerts: () -> Unit,
    onSnooze1h: () -> Unit,
    onStartCalibration: () -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    var snoozedFeedback by remember { mutableStateOf(false) }

    ScreenScaffold(
        scrollState = listState,
        timeText = { TimeText() }
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings_section_alerts),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            item {
                OutlinedButton(
                    onClick = onToggleAlerts,
                    label = {
                        Text(
                            if (uiState.preferences.alertsEnabled) {
                                stringResource(R.string.settings_alerts_on)
                            } else {
                                stringResource(R.string.settings_alerts_off)
                            }
                        )
                    },
                    transformation = SurfaceTransformation(spec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, spec)
                )
            }
            item {
                OutlinedButton(
                    onClick = {
                        snoozedFeedback = true
                        onSnooze1h()
                    },
                    label = {
                        Text(
                            if (snoozedFeedback) {
                                stringResource(R.string.settings_snoozed_confirm)
                            } else {
                                stringResource(R.string.action_snooze_1h)
                            }
                        )
                    },
                    transformation = SurfaceTransformation(spec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, spec)
                )
            }
            item {
                val canCalibrate = hasSensorPermission && uiState.simulationScenario == SimulationScenario.LIVE_SENSORS && !uiState.isCalibrating
                FilledTonalButton(
                    onClick = onStartCalibration,
                    enabled = canCalibrate,
                    label = {
                        Text(
                            when {
                                uiState.isCalibrating -> "${uiState.calibrationRemainingSeconds}s..."
                                !hasSensorPermission || uiState.simulationScenario != SimulationScenario.LIVE_SENSORS ->
                                    stringResource(R.string.calibration_requires_sensors)
                                else -> stringResource(R.string.action_calibrate)
                            }
                        )
                    },
                    transformation = SurfaceTransformation(spec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, spec)
                )
            }

            item {
                Text(
                    text = stringResource(R.string.settings_section_demo),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            val scenarios = listOf(
                SimulationScenario.LIVE_SENSORS to R.string.demo_live,
                SimulationScenario.CALM_AT_REST to R.string.demo_calm,
                SimulationScenario.ELEVATED_AT_REST to R.string.demo_elevated,
                SimulationScenario.POST_WALK_MOVEMENT to R.string.demo_moving
            )
            scenarios.forEach { (scenario, labelRes) ->
                item {
                    val selected = uiState.simulationScenario == scenario
                    val label = stringResource(labelRes)
                    FilledTonalButton(
                        onClick = { onSelectScenario(scenario) },
                        colors = if (selected) {
                            ButtonDefaults.buttonColors()
                        } else {
                            ButtonDefaults.filledTonalButtonColors()
                        },
                        label = { Text(if (selected) "✓ $label" else label) },
                        transformation = SurfaceTransformation(spec),
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, spec)
                    )
                }
            }
        }
    }
}

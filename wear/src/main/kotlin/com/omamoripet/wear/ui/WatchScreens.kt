package com.omamoripet.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
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
    onStartCalibration: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(scrollState = listState) { contentPadding ->
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
                            text = stringResource(R.string.permission_denied_recovery),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
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

            // 実測心拍と静止判定カード
            item {
                val bpm = uiState.snapshot.currentHeartRateBpm
                val delta = uiState.evaluation.deltaFromRestingBpm
                val bpmLine = if (bpm != null && delta != null) {
                    val sign = if (delta >= 0) "+" else ""
                    "♥ $bpm bpm ($sign$delta)"
                } else {
                    "♥ -- bpm"
                }
                val motionText = when (uiState.snapshot.motionState) {
                    MotionState.STILL_CONFIRMED -> stringResource(R.string.still_confirmed)
                    MotionState.RECENT_MOVEMENT -> stringResource(R.string.recent_movement)
                    MotionState.OFF_BODY -> stringResource(R.string.off_body)
                }
                Card(
                    onClick = onStartBreathing,
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                ) {
                    Text(
                        text = bpmLine,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (uiState.evaluation.state == OmamoriState.ELEVATED_AT_REST) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
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

            // Phone側 AI の一言（あれば表示）
            if (!uiState.latestAiAdvice.isNullOrBlank()) {
                item {
                    Card(
                        onClick = {},
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                    ) {
                        Text(
                            text = uiState.latestAiAdvice,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            item {
                FilledTonalButton(
                    onClick = onStartCalibration,
                    enabled = !uiState.isCalibrating,
                    label = {
                        Text(
                            if (uiState.isCalibrating) {
                                "${uiState.calibrationRemainingSeconds}s..."
                            } else {
                                stringResource(R.string.action_calibrate)
                            }
                        )
                    },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                )
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
    ScreenScaffold { contentPadding ->
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
                    size = 56.dp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.breathing_done_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                val pre = breathingState.preBpm
                val post = breathingState.postBpm
                val subtitle = if (pre != null && post != null) {
                    stringResource(R.string.breathing_done_delta, pre, post)
                } else {
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
                    size = 76.dp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = cueText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "${breathingState.cycleIndex} / ${breathingState.totalCycles}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.Center) {
                    OutlinedButton(
                        onClick = onStop,
                        label = { Text(stringResource(R.string.breathing_stop)) }
                    )
                }
            }
        }
    }
}

@Composable
fun DemoSettingsWatchScreen(
    currentScenario: SimulationScenario,
    alertsEnabled: Boolean,
    onSelectScenario: (SimulationScenario) -> Unit,
    onToggleAlerts: () -> Unit,
    onSnooze1h: () -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text(
                    text = stringResource(R.string.action_demo_settings),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            val scenarios = listOf(
                SimulationScenario.LIVE_SENSORS to "Live Sensors",
                SimulationScenario.CALM_AT_REST to "Demo: Calm",
                SimulationScenario.ELEVATED_AT_REST to "Demo: Elevated HR",
                SimulationScenario.POST_WALK_MOVEMENT to "Demo: Moving (Guard)"
            )
            scenarios.forEach { (scenario, label) ->
                item {
                    val selected = currentScenario == scenario
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
            item {
                OutlinedButton(
                    onClick = onToggleAlerts,
                    label = { Text(if (alertsEnabled) "Alerts: ON" else "Alerts: OFF") },
                    transformation = SurfaceTransformation(spec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, spec)
                )
            }
            item {
                OutlinedButton(
                    onClick = onSnooze1h,
                    label = { Text(stringResource(R.string.action_snooze_1h)) },
                    transformation = SurfaceTransformation(spec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, spec)
                )
            }
        }
    }
}

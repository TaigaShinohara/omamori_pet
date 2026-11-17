package com.omamoripet.wear.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
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
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
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
import kotlinx.coroutines.delay

@Composable
fun HomeWatchScreen(
    uiState: WatchUiState,
    hasSensorPermission: Boolean,
    permissionDeniedBefore: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onStartBreathing: () -> Unit,
    onSnooze1h: () -> Unit,
    onExitDemo: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val animationsEnabled = LocalWearAnimationsEnabled.current

    val state = uiState.evaluation.state
    val isElevated = state == OmamoriState.ELEVATED_AT_REST
    val needsPermission = !hasSensorPermission && uiState.simulationScenario == SimulationScenario.LIVE_SENSORS
    val bpm = uiState.snapshot.currentHeartRateBpm
    val delta = uiState.evaluation.deltaFromRestingBpm
    val motionState = uiState.snapshot.motionState
    val baselineBpm = uiState.snapshot.restingBaselineBpm
    val isSimulated = uiState.snapshot.isSimulated

    var wasCalibrating by remember { mutableStateOf(false) }
    var showCalibrationDone by remember { mutableStateOf(false) }
    var snoozedFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isCalibrating) {
        if (uiState.isCalibrating) {
            wasCalibrating = true
            showCalibrationDone = false
        } else if (wasCalibrating) {
            wasCalibrating = false
            showCalibrationDone = true
            delay(4_000L)
            showCalibrationDone = false
        }
    }

    LaunchedEffect(snoozedFeedback) {
        if (snoozedFeedback) {
            delay(2_500L)
            snoozedFeedback = false
        }
    }

    val snoozedUntil = uiState.preferences.snoozedUntilEpochMs
    val isCurrentlySnoozed = snoozedFeedback || remember(snoozedUntil) {
        snoozedUntil > System.currentTimeMillis()
    }

    ScreenScaffold(
        scrollState = listState,
        timeText = { TimeText() }
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "pet_header") {
                WatchPetHeader(
                    state = state,
                    isSimulated = isSimulated,
                    animationsEnabled = animationsEnabled,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (uiState.isCalibrating) {
                item(key = "calibration_progress") {
                    val transformation = remember(this, transformationSpec) { SurfaceTransformation(transformationSpec) }
                    Card(
                        onClick = {},
                        enabled = false,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        transformation = transformation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                    ) {
                        Text(
                            text = stringResource(R.string.calibration_running, uiState.calibrationRemainingSeconds),
                            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = stringResource(R.string.calibration_running_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            } else if (showCalibrationDone) {
                item(key = "calibration_done") {
                    val transformation = remember(this, transformationSpec) { SurfaceTransformation(transformationSpec) }
                    Card(
                        onClick = { showCalibrationDone = false },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        transformation = transformation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                    ) {
                        Text(
                            text = stringResource(R.string.calibration_done, baselineBpm),
                            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (needsPermission) {
                item(key = "permission_card") {
                    val transformation = remember(this, transformationSpec) { SurfaceTransformation(transformationSpec) }
                    Card(
                        onClick = if (permissionDeniedBefore) onOpenAppSettings else onRequestPermissions,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 18.dp,
                            vertical = 12.dp
                        ),
                        transformation = transformation,
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
                            text = stringResource(
                                if (permissionDeniedBefore) R.string.permission_denied_recovery else R.string.permission_body
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                if (permissionDeniedBefore) R.string.action_open_settings else R.string.action_grant_permissions
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 純粋な実測表示パネル（非インタラクティブ + Wear M3 SurfaceTransformation 準拠）
            item(key = "biometric_panel") {
                val transformation = remember(this, transformationSpec) { SurfaceTransformation(transformationSpec) }
                BiometricSummaryPanel(
                    bpm = bpm,
                    delta = delta,
                    isElevated = isElevated,
                    motionState = motionState,
                    baselineBpm = baselineBpm,
                    animationsEnabled = animationsEnabled,
                    transformation = transformation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                )
            }

            // 1分触覚呼吸ボタン
            item(key = "breathe_cta") {
                val transformation = remember(this, transformationSpec) { SurfaceTransformation(transformationSpec) }
                WatchBreathingCtaButton(
                    isElevated = isElevated,
                    onClick = onStartBreathing,
                    transformation = transformation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                )
            }

            if (isSimulated) {
                item(key = "exit_demo_cta") {
                    val transformation = remember(this, transformationSpec) { SurfaceTransformation(transformationSpec) }
                    OutlinedButton(
                        onClick = onExitDemo,
                        label = { Text(stringResource(R.string.action_exit_demo)) },
                        transformation = transformation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                    )
                }
            }

            // 静止中の心拍上昇時は、やらない選択（1h休止）も1タップで出せるようにする
            if (isElevated) {
                item(key = "snooze_cta") {
                    val transformation = remember(this, transformationSpec) { SurfaceTransformation(transformationSpec) }
                    FilledTonalButton(
                        onClick = {
                            snoozedFeedback = true
                            onSnooze1h()
                        },
                        enabled = !isCurrentlySnoozed,
                        label = {
                            Text(
                                stringResource(
                                    if (isCurrentlySnoozed) R.string.settings_snoozed_confirm else R.string.action_snooze_1h
                                )
                            )
                        },
                        transformation = transformation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                    )
                }
            }

            item(key = "settings_btn") {
                val transformation = remember(this, transformationSpec) { SurfaceTransformation(transformationSpec) }
                OutlinedButton(
                    onClick = onOpenSettings,
                    label = { Text(stringResource(R.string.action_demo_settings)) },
                    transformation = transformation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                )
            }
        }
    }
}

@Composable
private fun WatchPetHeader(
    state: OmamoriState,
    isSimulated: Boolean,
    animationsEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val stateTitle = when (state) {
        OmamoriState.CALM -> stringResource(R.string.state_calm)
        OmamoriState.ELEVATED_AT_REST -> stringResource(R.string.state_elevated)
        OmamoriState.BREATHING -> stringResource(R.string.state_breathing)
        OmamoriState.RESTING -> stringResource(R.string.state_resting)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(top = 2.dp, bottom = 2.dp)
    ) {
        OmamoriPetWearAvatar(
            state = state,
            contentDescriptionText = stateTitle,
            size = 48.dp
        )
        Spacer(Modifier.height(2.dp))
        AnimatedContent(
            targetState = stateTitle,
            transitionSpec = {
                if (animationsEnabled) {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                } else {
                    EnterTransition.None togetherWith ExitTransition.None
                }
            },
            label = "watchStateTitle"
        ) { title ->
            Text(
                text = if (isSimulated) "$title (${stringResource(R.string.demo_badge)})" else title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun BiometricSummaryPanel(
    bpm: Int?,
    delta: Int?,
    isElevated: Boolean,
    motionState: MotionState,
    baselineBpm: Int,
    animationsEnabled: Boolean,
    transformation: SurfaceTransformation,
    modifier: Modifier = Modifier
) {
    val hasLiveReading = bpm != null && motionState != MotionState.OFF_BODY
    val bpmLine = if (hasLiveReading && delta != null) {
        val sign = if (delta >= 0) "+" else ""
        "$bpm bpm ($sign$delta)"
    } else if (hasLiveReading) {
        "$bpm bpm"
    } else {
        null
    }
    val targetAccent = if (isElevated) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }
    val accentColor by animateColorAsState(
        targetValue = targetAccent,
        animationSpec = if (animationsEnabled) MaterialTheme.motionScheme.defaultEffectsSpec() else snap(),
        label = "watchAccentColor"
    )
    val motionText = when (motionState) {
        MotionState.STILL_CONFIRMED -> stringResource(R.string.still_confirmed)
        MotionState.RECENT_MOVEMENT -> stringResource(R.string.recent_movement)
        MotionState.OFF_BODY -> stringResource(R.string.off_body)
    }

    val containerBgColor = MaterialTheme.colorScheme.surfaceContainer
    val containerShape = CardDefaults.shape
    val containerPainter = remember(transformation, containerBgColor, containerShape) {
        transformation.createContainerPainter(
            painter = ColorPainter(containerBgColor),
            shape = containerShape
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .graphicsLayer { with(transformation) { applyContainerTransformation() } }
            .paint(containerPainter, contentScale = ContentScale.FillBounds)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        if (bpmLine != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.graphicsLayer { with(transformation) { applyContentTransformation() } }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_favorite_rounded),
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = bpmLine,
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    color = accentColor
                )
            }
            Text(
                text = "$motionText · ${stringResource(R.string.baseline_label, baselineBpm)}",
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { with(transformation) { applyContentTransformation() } }
            )
        } else {
            Text(
                text = motionText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { with(transformation) { applyContentTransformation() } }
            )
        }
    }
}

@Composable
private fun WatchBreathingCtaButton(
    isElevated: Boolean,
    onClick: () -> Unit,
    transformation: SurfaceTransformation,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = if (isElevated) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            )
        } else {
            ButtonDefaults.buttonColors()
        },
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_spa_rounded),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
        },
        label = { Text(stringResource(R.string.action_breathe_1m)) },
        transformation = transformation,
        modifier = modifier
    )
}

@Composable
fun BreathingWatchScreen(
    breathingState: BreathingUiState,
    onStop: () -> Unit,
    onDoneBack: () -> Unit
) {
    val view = LocalView.current
    val animationsEnabled = LocalWearAnimationsEnabled.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // 1フェーズ単位で Animatable を駆動し、Canvas と ProgressBar にはラムダ渡しで Recomposition を 1秒1回に抑制
    val phaseProgressAnim = remember { Animatable(0f) }
    val latestPhase by rememberUpdatedState(breathingState.phase)

    LaunchedEffect(
        breathingState.isRunning,
        breathingState.phase,
        breathingState.cycleIndex,
        breathingState.phaseDurationMs,
        animationsEnabled
    ) {
        if (!breathingState.isRunning || breathingState.phase == BreathPhase.COMPLETED) {
            return@LaunchedEffect
        }
        if (!animationsEnabled) {
            phaseProgressAnim.snapTo(0.5f)
        } else {
            phaseProgressAnim.snapTo(0f)
            phaseProgressAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = breathingState.phaseDurationMs.coerceAtLeast(100),
                    easing = LinearEasing
                )
            )
        }
    }

    val breathFractionProvider = remember {
        {
            val p = phaseProgressAnim.value.coerceIn(0f, 1f)
            when (latestPhase) {
                BreathPhase.INHALE -> p
                BreathPhase.HOLD -> 1f
                BreathPhase.EXHALE -> 1f - p
                BreathPhase.COMPLETED -> 0.5f
            }
        }
    }
    val linearProgressProvider = remember {
        { phaseProgressAnim.value.coerceIn(0f, 1f) }
    }

    ScreenScaffold(timeText = {}) { contentPadding ->
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
        ) {
            AnimatedContent(
                targetState = breathingState.phase == BreathPhase.COMPLETED,
                transitionSpec = {
                    if (animationsEnabled) {
                        fadeIn(tween(240)) togetherWith fadeOut(tween(180))
                    } else {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                label = "watchBreathingComplete"
            ) { isCompleted ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (isCompleted) {
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
                        val cueText = when (breathingState.phase) {
                            BreathPhase.INHALE -> stringResource(R.string.breathing_inhale)
                            BreathPhase.HOLD -> stringResource(R.string.breathing_hold)
                            BreathPhase.EXHALE -> stringResource(R.string.breathing_exhale)
                            BreathPhase.COMPLETED -> ""
                        }
                        OmamoriPetWearAvatar(
                            state = OmamoriState.BREATHING,
                            contentDescriptionText = cueText,
                            breathProgressProvider = breathFractionProvider,
                            size = 70.dp
                        )
                        Spacer(Modifier.height(6.dp))
                        AnimatedContent(
                            targetState = cueText,
                            transitionSpec = {
                                if (animationsEnabled) {
                                    fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                                } else {
                                    EnterTransition.None togetherWith ExitTransition.None
                                }
                            },
                            label = "watchBreathCue"
                        ) { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                        }
                        Text(
                            text = "${stringResource(R.string.breathing_seconds_left, breathingState.remainingSecondsInPhase)} · ${stringResource(R.string.breathing_cycle_label, breathingState.cycleIndex, breathingState.totalCycles)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        WearBreathProgressBar(
                            progressProvider = linearProgressProvider,
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .height(4.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onStop,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            label = { Text(stringResource(R.string.breathing_stop)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WearBreathProgressBar(
    progressProvider: () -> Float,
    modifier: Modifier = Modifier
) {
    // Wear M3 LinearProgressIndicator 内部の snapshotFlow + Animatable 再起動を避け、Draw フェーズのみで描画する
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val indicatorColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val radius = size.height / 2f
        val corner = CornerRadius(radius, radius)
        drawRoundRect(
            color = trackColor,
            size = size,
            cornerRadius = corner
        )
        val fraction = progressProvider().coerceIn(0f, 1f)
        val fillWidth = size.width * fraction
        if (fillWidth > 0f) {
            drawRoundRect(
                color = indicatorColor,
                size = Size(width = fillWidth, height = size.height),
                cornerRadius = corner
            )
        }
    }
}

@Composable
fun DemoSettingsWatchScreen(
    uiState: WatchUiState,
    hasSensorPermission: Boolean,
    hasNotificationPermission: Boolean,
    onRequestOrOpenNotificationSettings: () -> Unit,
    onSelectScenario: (SimulationScenario) -> Unit,
    onToggleAlerts: () -> Unit,
    onSnooze1h: () -> Unit,
    onStartCalibration: () -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    var snoozedFeedback by remember { mutableStateOf(false) }

    val scenarios = remember {
        listOf(
            SimulationScenario.LIVE_SENSORS to R.string.demo_live,
            SimulationScenario.CALM_AT_REST to R.string.demo_calm,
            SimulationScenario.ELEVATED_AT_REST to R.string.demo_elevated,
            SimulationScenario.POST_WALK_MOVEMENT to R.string.demo_moving
        )
    }

    LaunchedEffect(snoozedFeedback) {
        if (snoozedFeedback) {
            delay(2_500L)
            snoozedFeedback = false
        }
    }

    val snoozedUntil = uiState.preferences.snoozedUntilEpochMs
    val isCurrentlySnoozed = snoozedFeedback || remember(snoozedUntil) {
        snoozedUntil > System.currentTimeMillis()
    }

    ScreenScaffold(
        scrollState = listState,
        timeText = { TimeText() }
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "section_alerts") {
                val transformation = remember(this, spec) { SurfaceTransformation(spec) }
                ListSubHeader(
                    transformation = transformation,
                    modifier = Modifier.transformedHeight(this, spec)
                ) {
                    Text(stringResource(R.string.settings_section_alerts))
                }
            }
            item(key = "toggle_alerts") {
                val transformation = remember(this, spec) { SurfaceTransformation(spec) }
                SwitchButton(
                    checked = uiState.preferences.alertsEnabled,
                    onCheckedChange = { onToggleAlerts() },
                    label = { Text(stringResource(R.string.settings_alerts_label)) },
                    secondaryLabel = { Text(stringResource(R.string.settings_alerts_secondary)) },
                    transformation = transformation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, spec)
                )
            }
            if (uiState.preferences.alertsEnabled && !hasNotificationPermission) {
                item(key = "notif_perm_recovery") {
                    val transformation = remember(this, spec) { SurfaceTransformation(spec) }
                    OutlinedButton(
                        onClick = onRequestOrOpenNotificationSettings,
                        label = { Text(stringResource(R.string.notif_permission_off)) },
                        secondaryLabel = { Text(stringResource(R.string.notif_permission_off_sub)) },
                        transformation = transformation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, spec)
                    )
                }
            }
            item(key = "snooze_1h") {
                val transformation = remember(this, spec) { SurfaceTransformation(spec) }
                OutlinedButton(
                    onClick = {
                        snoozedFeedback = true
                        onSnooze1h()
                    },
                    label = {
                        Text(
                            if (isCurrentlySnoozed) {
                                stringResource(R.string.settings_snoozed_confirm)
                            } else {
                                stringResource(R.string.action_snooze_1h)
                            }
                        )
                    },
                    transformation = transformation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, spec)
                )
            }
            item(key = "calibrate") {
                val transformation = remember(this, spec) { SurfaceTransformation(spec) }
                WatchCalibrationButton(
                    hasSensorPermission = hasSensorPermission,
                    isLiveMode = uiState.simulationScenario == SimulationScenario.LIVE_SENSORS,
                    isCalibrating = uiState.isCalibrating,
                    remainingSeconds = uiState.calibrationRemainingSeconds,
                    onClick = onStartCalibration,
                    transformation = transformation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, spec)
                )
            }

            item(key = "section_demo") {
                val transformation = remember(this, spec) { SurfaceTransformation(spec) }
                ListSubHeader(
                    transformation = transformation,
                    modifier = Modifier.transformedHeight(this, spec)
                ) {
                    Text(stringResource(R.string.settings_section_demo))
                }
            }
            scenarios.forEach { (scenario, labelRes) ->
                item(key = "scenario_${scenario.name}") {
                    val transformation = remember(this, spec) { SurfaceTransformation(spec) }
                    val selected = uiState.simulationScenario == scenario
                    RadioButton(
                        selected = selected,
                        onSelect = { onSelectScenario(scenario) },
                        label = { Text(stringResource(labelRes)) },
                        transformation = transformation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, spec)
                    )
                }
            }
            item(key = "disclaimer") {
                Text(
                    text = stringResource(R.string.non_medical_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun WatchCalibrationButton(
    hasSensorPermission: Boolean,
    isLiveMode: Boolean,
    isCalibrating: Boolean,
    remainingSeconds: Int,
    onClick: () -> Unit,
    transformation: SurfaceTransformation,
    modifier: Modifier = Modifier
) {
    val canCalibrate = hasSensorPermission && isLiveMode && !isCalibrating
    FilledTonalButton(
        onClick = onClick,
        enabled = canCalibrate,
        label = {
            Text(
                when {
                    isCalibrating -> stringResource(R.string.calibration_running, remainingSeconds)
                    !hasSensorPermission || !isLiveMode ->
                        stringResource(R.string.calibration_requires_sensors)
                    else -> stringResource(R.string.action_calibrate)
                }
            )
        },
        secondaryLabel = if (canCalibrate) {
            { Text(stringResource(R.string.action_calibrate_desc)) }
        } else {
            null
        },
        transformation = transformation,
        modifier = modifier
    )
}

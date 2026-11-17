package com.omamoripet.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omamoripet.mobile.R
import com.omamoripet.mobile.ai.AiBackendStatus
import com.omamoripet.mobile.data.MobileDemoMode
import com.omamoripet.mobile.data.MobileUiState
import com.omamoripet.mobile.health.HealthConnectAvailability
import com.omamoripet.mobile.ui.components.OmamoriPetMobileAvatar
import com.omamoripet.shared.model.BaselineSource
import com.omamoripet.shared.model.MotionState
import com.omamoripet.shared.model.OmamoriState
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun HomeMobileScreen(
    uiState: MobileUiState,
    onOpenBreathing: () -> Unit,
    onTriggerWatchBreathing: () -> Unit,
    onRefreshAi: () -> Unit,
    onPrepareGeminiNano: () -> Unit,
    onRequestHealthConnect: () -> Unit,
    onSelectDemoMode: (MobileDemoMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            HeroStatusCard(
                uiState = uiState,
                onOpenBreathing = onOpenBreathing,
                onTriggerWatchBreathing = onTriggerWatchBreathing
            )

            AiReflectionCard(
                uiState = uiState,
                onRefreshAi = onRefreshAi,
                onPrepareGeminiNano = onPrepareGeminiNano
            )

            HealthConnectSection(
                uiState = uiState,
                onRequestHealthConnect = onRequestHealthConnect
            )

            DemoModeSelector(
                current = uiState.demoMode,
                onSelect = onSelectDemoMode
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HeroStatusCard(
    uiState: MobileUiState,
    onOpenBreathing: () -> Unit,
    onTriggerWatchBreathing: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val containerColor = when (uiState.omamoriState) {
        OmamoriState.CALM -> cs.primaryContainer
        OmamoriState.ELEVATED_AT_REST -> cs.tertiaryContainer
        OmamoriState.BREATHING -> cs.secondaryContainer
        OmamoriState.RESTING -> cs.surfaceContainerHigh
    }
    val contentColor = when (uiState.omamoriState) {
        OmamoriState.CALM -> cs.onPrimaryContainer
        OmamoriState.ELEVATED_AT_REST -> cs.onTertiaryContainer
        OmamoriState.BREATHING -> cs.onSecondaryContainer
        OmamoriState.RESTING -> cs.onSurface
    }

    val delta = uiState.currentHeartRateBpm?.minus(uiState.restingBaselineBpm)
    val stateTitle = when (uiState.omamoriState) {
        OmamoriState.CALM -> stringResource(R.string.state_calm_title)
        OmamoriState.ELEVATED_AT_REST -> stringResource(R.string.state_elevated_title)
        OmamoriState.BREATHING -> stringResource(R.string.state_breathing_title)
        OmamoriState.RESTING -> stringResource(R.string.state_resting_title)
    }
    val stateDesc = when (uiState.omamoriState) {
        OmamoriState.CALM -> stringResource(R.string.state_calm_desc)
        OmamoriState.ELEVATED_AT_REST -> stringResource(R.string.state_elevated_desc, delta ?: 0)
        OmamoriState.BREATHING -> stringResource(R.string.state_breathing_desc)
        OmamoriState.RESTING -> stringResource(R.string.state_resting_desc)
    }
    val motionIcon = when (uiState.motionState) {
        MotionState.STILL_CONFIRMED -> Icons.Rounded.SelfImprovement
        MotionState.RECENT_MOVEMENT -> Icons.AutoMirrored.Rounded.DirectionsWalk
        MotionState.OFF_BODY -> Icons.Rounded.Watch
    }
    val motionText = when (uiState.motionState) {
        MotionState.STILL_CONFIRMED -> stringResource(R.string.motion_still)
        MotionState.RECENT_MOVEMENT -> stringResource(R.string.motion_moving)
        MotionState.OFF_BODY -> stringResource(R.string.motion_off)
    }

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OmamoriPetMobileAvatar(
                    state = uiState.omamoriState,
                    description = stateTitle,
                    size = 96.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stateTitle,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = uiState.currentHeartRateBpm?.toString() ?: "--",
                            style = MaterialTheme.typography.displaySmall
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = buildString {
                                append(stringResource(R.string.heart_rate_unit))
                                if (delta != null) {
                                    val sign = if (delta >= 0) "+" else ""
                                    append(" ($sign$delta)")
                                }
                            },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
            }

            Text(
                text = stateDesc,
                style = MaterialTheme.typography.bodyLarge
            )

            // 非カラー依存の状態ラベル（アイコン＋テキスト）
            Surface(
                color = cs.surface.copy(alpha = 0.45f),
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = motionIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = motionText,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onOpenBreathing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.omamoriState == OmamoriState.ELEVATED_AT_REST) cs.tertiary else cs.primary,
                        contentColor = if (uiState.omamoriState == OmamoriState.ELEVATED_AT_REST) cs.onTertiary else cs.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Rounded.Spa, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.cta_breathe_phone), maxLines = 1)
                }
                OutlinedButton(
                    onClick = onTriggerWatchBreathing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Rounded.Watch, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.cta_breathe_watch), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun AiReflectionCard(
    uiState: MobileUiState,
    onRefreshAi: () -> Unit,
    onPrepareGeminiNano: () -> Unit
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.ai_card_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    text = if (uiState.usedGeminiNano) {
                        stringResource(R.string.ai_source_nano)
                    } else {
                        stringResource(R.string.ai_source_local)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (uiState.isAiRefreshing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = uiState.aiAdviceText.ifBlank { "..." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = uiState.aiAdviceText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = onRefreshAi,
                    enabled = !uiState.isAiRefreshing,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ai_refresh))
                }

                if (uiState.aiBackendStatus == AiBackendStatus.GEMINI_NANO_DOWNLOADABLE) {
                    FilledTonalButton(
                        onClick = onPrepareGeminiNano,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(stringResource(R.string.ai_download_btn))
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthConnectSection(
    uiState: MobileUiState,
    onRequestHealthConnect: () -> Unit
) {
    val sourceLabel = when (uiState.baselineSource) {
        BaselineSource.HEALTH_CONNECT -> stringResource(R.string.baseline_source_hc)
        BaselineSource.WATCH_LEARNED -> stringResource(R.string.baseline_source_watch)
        BaselineSource.DEFAULT_FALLBACK -> stringResource(R.string.baseline_source_default)
    }

    OutlinedCard(
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.HealthAndSafety,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = stringResource(R.string.hc_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Text(
                text = stringResource(R.string.baseline_format, uiState.restingBaselineBpm, sourceLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            when {
                uiState.healthConnectAvailability != HealthConnectAvailability.AVAILABLE -> {
                    Text(
                        text = stringResource(R.string.hc_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                !uiState.hasHealthConnectPermission -> {
                    Text(
                        text = stringResource(R.string.hc_missing_perm),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilledTonalButton(
                        onClick = onRequestHealthConnect,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(stringResource(R.string.hc_grant_btn))
                    }
                }
                uiState.healthConnectRestingBpm != null -> {
                    Text(
                        text = stringResource(R.string.hc_ready, uiState.healthConnectRestingBpm),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun DemoModeSelector(
    current: MobileDemoMode,
    onSelect: (MobileDemoMode) -> Unit
) {
    val options = listOf(
        MobileDemoMode.LIVE_WATCH to "Live",
        MobileDemoMode.DEMO_CALM to "Calm",
        MobileDemoMode.DEMO_ELEVATED_AT_REST to "Elevated",
        MobileDemoMode.DEMO_MOVING to "Moving"
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.settings_demo_section),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = current == mode,
                    onClick = { onSelect(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    label = { Text(label, maxLines = 1) }
                )
            }
        }
    }
}

@Composable
fun BreathingMobileScreen(
    onBack: () -> Unit,
    onCompleted: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var running by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    var phaseIndex by remember { mutableIntStateOf(0) } // 0=Inhale(4s), 1=Hold(2s), 2=Exhale(6s)
    var progress by remember { mutableFloatStateOf(0f) }
    var cycle by remember { mutableIntStateOf(1) }
    val totalCycles = 5

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        completed = false
        val durations = listOf(4_000L, 2_000L, 6_000L)
        for (c in 1..totalCycles) {
            cycle = c
            for (p in 0..2) {
                phaseIndex = p
                val dur = durations[p]
                var elapsed = 0L
                while (elapsed <= dur) {
                    progress = (elapsed.toFloat() / dur).coerceIn(0f, 1f)
                    delay(50L)
                    elapsed += 50L
                }
            }
        }
        running = false
        completed = true
        onCompleted(totalCycles * 12)
    }

    val breathFraction = when {
        !running -> 0.5f
        phaseIndex == 0 -> progress
        phaseIndex == 1 -> 1f
        else -> 1f - progress
    }
    val cueText = when {
        completed -> stringResource(R.string.breathing_complete)
        !running -> stringResource(R.string.state_breathing_desc)
        phaseIndex == 0 -> stringResource(R.string.breathing_inhale)
        phaseIndex == 1 -> stringResource(R.string.breathing_hold)
        else -> stringResource(R.string.breathing_exhale)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            OmamoriPetMobileAvatar(
                state = if (running) OmamoriState.BREATHING else OmamoriState.CALM,
                description = cueText,
                breathFraction = breathFraction,
                size = 160.dp
            )
            Text(
                text = cueText,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            if (running) {
                Text(
                    text = "$cycle / $totalCycles",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        running = false
                        onBack()
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.back))
                }
                Button(
                    onClick = { running = !running },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(
                        if (running) stringResource(R.string.breathing_stop) else stringResource(R.string.breathing_start)
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryMobileScreen(
    uiState: MobileUiState,
    modifier: Modifier = Modifier
) {
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    val hasItems = uiState.breathingSessions.isNotEmpty() || uiState.calmEvents.isNotEmpty()

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        if (!hasItems) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxSize()
                    .padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Spa,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.history_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.history_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
            ) {
                items(uiState.breathingSessions, key = { "b_${it.id}" }) { session ->
                    val timeLabel = formatter.format(Date(session.startedAtEpochMs))
                    val deltaStr = if (session.preBpm != null && session.postBpm != null) {
                        stringResource(R.string.history_breath_delta, session.preBpm!!, session.postBpm!!)
                    } else {
                        "${session.durationSeconds}s"
                    }
                    ListItem(
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Spa,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        headlineContent = {
                            Text(stringResource(R.string.history_breath_item, deltaStr))
                        },
                        supportingContent = { Text(timeLabel) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                items(uiState.calmEvents, key = { "e_${it.id}" }) { event ->
                    val timeLabel = formatter.format(Date(event.timestampEpochMs))
                    ListItem(
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.FavoriteBorder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        },
                        headlineContent = {
                            Text(stringResource(R.string.history_event_item, event.peakBpm, event.restingBaselineBpm))
                        },
                        supportingContent = { Text(timeLabel) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
fun SettingsMobileScreen(
    uiState: MobileUiState,
    onUpdatePreferences: (com.omamoripet.shared.model.AlertPreferences) -> Unit,
    onRequestHealthConnect: () -> Unit,
    onSelectDemoMode: (MobileDemoMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs = uiState.preferences
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_alerts_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            ListItem(
                leadingContent = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_alerts_toggle)) },
                supportingContent = { Text(stringResource(R.string.settings_alerts_subtitle)) },
                trailingContent = {
                    Switch(
                        checked = prefs.alertsEnabled,
                        onCheckedChange = { onUpdatePreferences(prefs.copy(alertsEnabled = it)) }
                    )
                }
            )
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.settings_threshold_label, prefs.elevationThresholdBpm),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = prefs.elevationThresholdBpm.toFloat(),
                    onValueChange = {
                        onUpdatePreferences(prefs.copy(elevationThresholdBpm = it.roundToInt()))
                    },
                    valueRange = 10f..25f,
                    steps = 2
                )
            }
            ListItem(
                leadingContent = { Icon(Icons.Rounded.Hotel, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_quiet_hours)) },
                trailingContent = {
                    Switch(
                        checked = prefs.quietHoursEnabled,
                        onCheckedChange = { onUpdatePreferences(prefs.copy(quietHoursEnabled = it)) }
                    )
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            ListItem(
                leadingContent = { Icon(Icons.Rounded.Favorite, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.hc_title)) },
                supportingContent = {
                    Text(
                        if (uiState.hasHealthConnectPermission && uiState.healthConnectRestingBpm != null) {
                            stringResource(R.string.hc_ready, uiState.healthConnectRestingBpm)
                        } else {
                            stringResource(R.string.hc_missing_perm)
                        }
                    )
                },
                trailingContent = {
                    if (uiState.healthConnectAvailability == HealthConnectAvailability.AVAILABLE) {
                        TextButton(onClick = onRequestHealthConnect) {
                            Text(stringResource(R.string.hc_grant_btn))
                        }
                    }
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(modifier = Modifier.padding(16.dp)) {
                DemoModeSelector(
                    current = uiState.demoMode,
                    onSelect = onSelectDemoMode
                )
            }

            OutlinedCard(
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_disclaimer_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(R.string.settings_disclaimer_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

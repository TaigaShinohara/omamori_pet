package com.omamoripet.mobile.ui.screens

import android.os.VibrationEffect
import android.os.Vibrator
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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsPaused
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omamoripet.mobile.R
import com.omamoripet.mobile.ai.AiBackendStatus
import com.omamoripet.mobile.data.MobileDemoMode
import com.omamoripet.mobile.data.MobileUiState
import com.omamoripet.mobile.health.HealthConnectAvailability
import com.omamoripet.mobile.ui.components.OmamoriPetMobileAvatar
import com.omamoripet.shared.model.AlertPreferences
import com.omamoripet.shared.model.BaselineSource
import com.omamoripet.shared.model.BreathingSessionRecord
import com.omamoripet.shared.model.CalmEventAction
import com.omamoripet.shared.model.CalmEventRecord
import com.omamoripet.shared.model.MotionState
import com.omamoripet.shared.model.OmamoriState
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
fun HomeMobileScreen(
    uiState: MobileUiState,
    onOpenBreathing: () -> Unit,
    onTriggerWatchBreathing: () -> Unit,
    onRefreshAi: () -> Unit,
    onPrepareGeminiNano: () -> Unit,
    onRequestHealthConnect: () -> Unit,
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

            // 連携済みの日常利用時は Hero 内に1行統合し、権限未許可または要更新時だけ復旧案内を出す
            val needsHealthConnectAction =
                uiState.healthConnectAvailability != HealthConnectAvailability.UNAVAILABLE &&
                    !uiState.hasHealthConnectPermission
            if (needsHealthConnectAction) {
                HealthConnectRecoveryCard(
                    onRequestHealthConnect = onRequestHealthConnect
                )
            }

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
    val baselineSourceLabel = when (uiState.baselineSource) {
        BaselineSource.HEALTH_CONNECT -> stringResource(R.string.baseline_source_hc)
        BaselineSource.WATCH_LEARNED -> stringResource(R.string.baseline_source_watch)
        BaselineSource.DEFAULT_FALLBACK -> stringResource(R.string.baseline_source_default)
    }
    val timeFormatter = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val syncStatusText = when {
        uiState.isSimulated -> stringResource(R.string.watch_badge_demo)
        uiState.lastWatchSyncMs != null ->
            stringResource(R.string.watch_badge_connected, timeFormatter.format(Date(uiState.lastWatchSyncMs)))
        else -> stringResource(R.string.watch_badge_disconnected)
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
            Text(
                text = syncStatusText,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.8f)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OmamoriPetMobileAvatar(
                    state = uiState.omamoriState,
                    description = stateTitle,
                    size = 92.dp
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

            Text(
                text = stringResource(R.string.baseline_format, uiState.restingBaselineBpm, baselineSourceLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.85f)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
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
                TextButton(
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

            if (uiState.aiBackendStatus == AiBackendStatus.GEMINI_NANO_DOWNLOADABLE) {
                Text(
                    text = stringResource(R.string.ai_status_downloadable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun HealthConnectRecoveryCard(
    onRequestHealthConnect: () -> Unit
) {
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
    val context = LocalContext.current
    val view = LocalView.current
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }

    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            vibrator?.cancel()
        }
    }

    var sessionId by remember { mutableIntStateOf(1) }
    var running by remember { mutableStateOf(true) } // 開いたら即座に呼吸ガイド開始（2回タップの摩擦を排除）
    var completed by remember { mutableStateOf(false) }
    var phaseIndex by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var remainingSec by remember { mutableIntStateOf(4) }
    var cycle by remember { mutableIntStateOf(1) }
    val totalCycles = 5

    LaunchedEffect(running, sessionId) {
        if (!running) return@LaunchedEffect
        completed = false
        val durations = listOf(4_000L, 2_000L, 6_000L)
        for (c in 1..totalCycles) {
            cycle = c
            for (p in 0..2) {
                phaseIndex = p
                vibrateMobileBreathCue(vibrator, p)
                val dur = durations[p]
                var elapsed = 0L
                while (elapsed <= dur) {
                    progress = (elapsed.toFloat() / dur).coerceIn(0f, 1f)
                    remainingSec = ceil(((dur - elapsed).coerceAtLeast(0L)) / 1000.0).toInt().coerceAtLeast(1)
                    delay(50L)
                    elapsed += 50L
                }
            }
        }
        vibrateMobileCompletion(vibrator)
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
                    text = "${stringResource(R.string.breathing_seconds_left, remainingSec)} · $cycle / $totalCycles",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(6.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (completed) {
                    OutlinedButton(
                        onClick = {
                            sessionId++
                            running = true
                        },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(stringResource(R.string.breathing_restart))
                    }
                    Button(
                        onClick = onBack,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(stringResource(R.string.breathing_done_back))
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            running = false
                            onBack()
                        },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.breathing_stop))
                    }
                }
            }
        }
    }
}

private fun vibrateMobileBreathCue(vibrator: Vibrator?, phaseIndex: Int) {
    if (vibrator == null || !vibrator.hasVibrator()) return
    val effect = when (phaseIndex) {
        0 -> VibrationEffect.createWaveform(longArrayOf(0, 50, 70, 70), intArrayOf(0, 40, 0, 65), -1)
        1 -> VibrationEffect.createOneShot(30L, 30)
        else -> VibrationEffect.createWaveform(longArrayOf(0, 90, 90, 50), intArrayOf(0, 60, 0, 35), -1)
    }
    vibrator.vibrate(effect)
}

private fun vibrateMobileCompletion(vibrator: Vibrator?) {
    if (vibrator == null || !vibrator.hasVibrator()) return
    vibrator.vibrate(VibrationEffect.createOneShot(100L, 60))
}

private sealed interface HistoryTimelineItem {
    val key: String
    val timestampEpochMs: Long
    val isSimulated: Boolean

    data class Breath(val record: BreathingSessionRecord) : HistoryTimelineItem {
        override val key: String = "b_${record.id}"
        override val timestampEpochMs: Long = record.startedAtEpochMs
        override val isSimulated: Boolean = record.isSimulated
    }

    data class Event(val record: CalmEventRecord) : HistoryTimelineItem {
        override val key: String = "e_${record.id}"
        override val timestampEpochMs: Long = record.timestampEpochMs
        override val isSimulated: Boolean = record.isSimulated
    }
}

@Composable
fun HistoryMobileScreen(
    uiState: MobileUiState,
    onTryBreathing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    val timeline = remember(uiState.breathingSessions, uiState.calmEvents) {
        (uiState.breathingSessions.map { HistoryTimelineItem.Breath(it) } +
            uiState.calmEvents.map { HistoryTimelineItem.Event(it) })
            .sortedByDescending { it.timestampEpochMs }
    }
    val todayCutoff = remember { System.currentTimeMillis() - 24 * 60 * 60_000L }
    val todayBreaths = uiState.breathingSessions.count { !it.isSimulated && it.startedAtEpochMs >= todayCutoff }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        if (timeline.isEmpty()) {
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
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = onTryBreathing,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(stringResource(R.string.history_try_breathing))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
            ) {
                item(key = "summary_header") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Spa,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.history_today_summary, todayBreaths),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }

                items(timeline, key = { it.key }) { item ->
                    val timeLabel = formatter.format(Date(item.timestampEpochMs))
                    val demoSuffix = if (item.isSimulated) " · ${stringResource(R.string.history_demo_tag)}" else ""
                    when (item) {
                        is HistoryTimelineItem.Breath -> {
                            val s = item.record
                            val detail = if (s.preBpm != null && s.postBpm != null) {
                                stringResource(R.string.history_breath_delta, s.preBpm!!, s.postBpm!!)
                            } else {
                                stringResource(R.string.history_breath_duration, s.durationSeconds)
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
                                    Text(stringResource(R.string.history_breath_item, detail))
                                },
                                supportingContent = { Text("$timeLabel$demoSuffix") }
                            )
                        }
                        is HistoryTimelineItem.Event -> {
                            val e = item.record
                            val isSnoozed = e.actionTaken == CalmEventAction.SNOOZED
                            ListItem(
                                leadingContent = {
                                    Icon(
                                        imageVector = if (isSnoozed) Icons.Rounded.NotificationsPaused else Icons.Rounded.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (isSnoozed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary
                                    )
                                },
                                headlineContent = {
                                    Text(
                                        if (isSnoozed) {
                                            stringResource(R.string.history_event_snoozed)
                                        } else {
                                            stringResource(R.string.history_event_item, e.peakBpm, e.restingBaselineBpm)
                                        }
                                    )
                                },
                                supportingContent = { Text("$timeLabel$demoSuffix") }
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
fun SettingsMobileScreen(
    uiState: MobileUiState,
    onUpdatePreferences: (AlertPreferences) -> Unit,
    onRequestHealthConnect: () -> Unit,
    onSelectDemoMode: (MobileDemoMode) -> Unit,
    onClearDemoHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs = uiState.preferences
    val hasDemoRecords = uiState.breathingSessions.any { it.isSimulated } || uiState.calmEvents.any { it.isSimulated }

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
                        when {
                            uiState.healthConnectAvailability != HealthConnectAvailability.AVAILABLE ->
                                stringResource(R.string.hc_unavailable)
                            uiState.hasHealthConnectPermission && uiState.healthConnectRestingBpm != null ->
                                stringResource(R.string.hc_ready, uiState.healthConnectRestingBpm)
                            else ->
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

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                DemoModeSelector(
                    current = uiState.demoMode,
                    onSelect = onSelectDemoMode
                )
                if (hasDemoRecords) {
                    TextButton(
                        onClick = onClearDemoHistory,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_clear_demo))
                    }
                }
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

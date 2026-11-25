package com.omamoripet.mobile.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.omamoripet.mobile.R
import com.omamoripet.mobile.data.FeelingRecord
import com.omamoripet.mobile.data.PendingFeelingActivity
import com.omamoripet.mobile.ui.theme.LocalAnimationsEnabled
import com.omamoripet.shared.model.BreathingSessionRecord
import com.omamoripet.shared.model.FEELING_MINUTE_COUNT
import com.omamoripet.shared.model.FEELING_MINUTE_MS
import com.omamoripet.shared.model.FEELING_MINUTES_BEFORE
import com.omamoripet.shared.model.FeelingContext
import com.omamoripet.shared.model.FeelingContextQr
import com.omamoripet.shared.model.MotionState
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeelingDetailScreen(
    record: FeelingRecord?,
    pendingActivity: PendingFeelingActivity,
    breathingSessions: List<BreathingSessionRecord>,
    onBack: () -> Unit,
    onSaveContext: suspend (String, FeelingContext?) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feeling_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back_rounded),
                            contentDescription = stringResource(R.string.feeling_detail_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (record != null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                FeelingDetail(record, pendingActivity, breathingSessions, onSaveContext)
            }
        }
    }
}

@Composable
private fun FeelingDetail(
    record: FeelingRecord,
    pendingActivity: PendingFeelingActivity,
    breathingSessions: List<BreathingSessionRecord>,
    onSaveContext: suspend (String, FeelingContext?) -> Unit
) {
    val scope = rememberCoroutineScope()
    val animationsEnabled = LocalAnimationsEnabled.current
    var showSecondary by remember { mutableStateOf(!animationsEnabled) }
    LaunchedEffect(animationsEnabled) {
        if (animationsEnabled && !showSecondary) {
            delay(60L)
        }
        showSecondary = true
    }

    var saving by remember { mutableStateOf(false) }
    var feedback by rememberSaveable { mutableStateOf<Int?>(null) }
    var watchImportExpanded by rememberSaveable { mutableStateOf(false) }
    var showMinutes by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val expandedLabel = stringResource(R.string.section_expanded)
    val collapsedLabel = stringResource(R.string.section_collapsed)
    val watchImportArrowRotation by animateFloatAsState(
        targetValue = if (watchImportExpanded) 90f else 0f,
        animationSpec = if (animationsEnabled) tween(180) else snap(),
        label = "watchImportArrowRotation"
    )
    val minutesArrowRotation by animateFloatAsState(
        targetValue = if (showMinutes) 90f else 0f,
        animationSpec = if (animationsEnabled) tween(180) else snap(),
        label = "minutesArrowRotation"
    )

    fun save(context: FeelingContext?) {
        if (saving) return
        saving = true
        feedback = null
        scope.launch {
            try {
                onSaveContext(record.id, context)
            } catch (_: IOException) {
                feedback = R.string.feeling_save_failed
            } finally {
                saving = false
            }
        }
    }

    val scanPrompt = stringResource(R.string.feeling_detail_scan_prompt)
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents
        if (text != null) {
            val context = FeelingContextQr.decode(text, record.recordedAtEpochMs)
            if (context != null) save(context) else feedback = R.string.feeling_detail_wrong_code
        } else if (result.originalIntent?.getBooleanExtra(Intents.Scan.MISSING_CAMERA_PERMISSION, false) == true) {
            feedback = R.string.feeling_detail_camera_permission
        }
    }

    val start = record.recordedAtEpochMs - FEELING_MINUTES_BEFORE * FEELING_MINUTE_MS
    val end = start + FEELING_MINUTE_COUNT * FEELING_MINUTE_MS
    val breaths = remember(start, end, breathingSessions) {
        breathingSessions.filter {
            !it.isSimulated && it.startedAtEpochMs < end &&
                it.startedAtEpochMs + it.durationSeconds * 1_000L > start
        }.sortedBy { it.startedAtEpochMs }
    }
    val context = record.context
    val dateFormatter = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    val timeFormatter = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

    LazyColumn(
        modifier = Modifier.widthIn(max = 580.dp).fillMaxWidth().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(painterResource(record.feeling.faceRes), contentDescription = null, Modifier.size(52.dp))
                Column {
                    Text(stringResource(record.feeling.labelRes), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = dateFormatter.format(Date(record.recordedAtEpochMs)),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item(key = "observations") {
            FeelingObservations(record, pendingActivity)
        }
        item(key = "secondary") {
            AnimatedVisibility(
                visible = showSecondary,
                enter = if (animationsEnabled) fadeIn(tween(200)) else EnterTransition.None
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (context != null || breaths.isNotEmpty()) {
                        FeelingTimeline(context, start, breaths)
                    }
                    if (context != null) {
                        val minutesMeasured = context.minutes.count { it.medianBpm != null }
                        val summaryText = context.baselineBpm?.let { bpm ->
                            stringResource(
                                R.string.feeling_detail_summary_with_baseline,
                                minutesMeasured,
                                FEELING_MINUTE_COUNT,
                                bpm
                            )
                        } ?: stringResource(
                            R.string.feeling_detail_coverage,
                            minutesMeasured,
                            FEELING_MINUTE_COUNT
                        )
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .semantics {
                                    stateDescription = if (showMinutes) expandedLabel else collapsedLabel
                                }
                                .clickable(role = Role.Button) { showMinutes = !showMinutes }
                        ) {
                            Text(
                                text = stringResource(R.string.feeling_detail_minutes_toggle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                painter = painterResource(R.drawable.ic_keyboard_arrow_right_rounded),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.rotate(minutesArrowRotation)
                            )
                        }
                        AnimatedVisibility(
                            visible = showMinutes,
                            enter = if (animationsEnabled) fadeIn() + expandVertically() else EnterTransition.None,
                            exit = if (animationsEnabled) fadeOut() + shrinkVertically() else ExitTransition.None
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                context.minutes.forEachIndexed { index, minute ->
                                    val pulse = minute.medianBpm?.let { stringResource(R.string.feeling_detail_bpm, it) }
                                        ?: stringResource(R.string.feeling_detail_missing)
                                    val motion = when (minute.motion) {
                                        MotionState.STILL_CONFIRMED -> R.string.feeling_detail_still
                                        MotionState.RECENT_MOVEMENT -> R.string.feeling_detail_moving
                                        MotionState.OFF_BODY -> R.string.feeling_detail_off_body
                                        null -> R.string.feeling_detail_missing
                                    }
                                    Text(
                                        text = stringResource(
                                            R.string.feeling_detail_minute,
                                            timeFormatter.format(Date(start + index * FEELING_MINUTE_MS)),
                                            pulse,
                                            stringResource(motion)
                                        ),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
                                    )
                                }
                            }
                        }
                    }
                    if (breaths.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.feeling_detail_breathing),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { heading() }
                            )
                            breaths.forEach { breath ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_spa_rounded),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.feeling_detail_breath_session,
                                            timeFormatter.format(Date(breath.startedAtEpochMs)),
                                            breath.durationSeconds
                                        ),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
                                    )
                                }
                            }
                        }
                    }
                    if (context == null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                        .semantics {
                                            stateDescription = if (watchImportExpanded) expandedLabel else collapsedLabel
                                        }
                                        .clickable(role = Role.Button) { watchImportExpanded = !watchImportExpanded }
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_watch_rounded),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = stringResource(R.string.feeling_detail_import_title),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        painter = painterResource(R.drawable.ic_keyboard_arrow_right_rounded),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.rotate(watchImportArrowRotation)
                                    )
                                }
                                AnimatedVisibility(
                                    visible = watchImportExpanded,
                                    enter = if (animationsEnabled) fadeIn() + expandVertically() else EnterTransition.None,
                                    exit = if (animationsEnabled) fadeOut() + shrinkVertically() else ExitTransition.None
                                ) {
                                    Column(
                                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.feeling_detail_import_help),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        OutlinedButton(
                                            enabled = !saving,
                                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                                            onClick = {
                                                feedback = null
                                                scanner.launch(
                                                    ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                                        .setPrompt(scanPrompt)
                                                        .setBarcodeImageEnabled(false)
                                                        .setBeepEnabled(false)
                                                        .setOrientationLocked(false)
                                                )
                                            }
                                        ) {
                                            Text(stringResource(R.string.feeling_detail_scan))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        TextButton(
                            enabled = !saving,
                            onClick = { confirmDelete = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(stringResource(R.string.feeling_detail_remove_watch_data))
                        }
                    }
                    feedback?.let { message ->
                        Text(stringResource(message), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        item(key = "bottom_spacer") { Box(Modifier.height(16.dp)) }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.feeling_detail_remove_watch_data)) },
            text = { Text(stringResource(R.string.feeling_detail_remove_help)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        save(null)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.feeling_detail_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(android.R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun FeelingTimeline(context: FeelingContext?, start: Long, breaths: List<BreathingSessionRecord>) {
    val colors = MaterialTheme.colorScheme
    val timeFormatter = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val end = start + FEELING_MINUTE_COUNT * FEELING_MINUTE_MS
    val minutes = context?.minutes.orEmpty()
    val minutesMeasured = minutes.count { it.medianBpm != null }
    val bpmValues = minutes.mapNotNull { it.medianBpm } + listOfNotNull(context?.baselineBpm)
    val graphMax = (bpmValues.maxOrNull() ?: 90) + 8
    val graphMin = (bpmValues.minOrNull() ?: 55) - 8
    val startLabel = timeFormatter.format(Date(start))
    val endLabel = timeFormatter.format(Date(end))
    val timelineDescription = if (context != null) {
        stringResource(
            R.string.feeling_detail_timeline_a11y_with_watch,
            startLabel,
            endLabel,
            minutesMeasured,
            FEELING_MINUTE_COUNT,
            breaths.size
        )
    } else {
        stringResource(
            R.string.feeling_detail_timeline_a11y_breaths_only,
            startLabel,
            endLabel,
            breaths.size
        )
    }
    val timeLabelStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .semantics { contentDescription = timelineDescription }
        ) {
            val minuteWidth = size.width / FEELING_MINUTE_COUNT
            val pulseBottom = size.height - 22.dp.toPx()
            val recordX = FEELING_MINUTES_BEFORE * minuteWidth
            fun pulseY(bpm: Int): Float = pulseBottom * (graphMax - bpm) / (graphMax - graphMin)
            drawLine(colors.outline, Offset(recordX, 0f), Offset(recordX, size.height), 1.dp.toPx())
            if (minutes.any { it.medianBpm != null }) {
                context?.baselineBpm?.let { bpm ->
                    drawLine(
                        colors.outlineVariant, Offset(0f, pulseY(bpm)), Offset(size.width, pulseY(bpm)),
                        1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
                    )
                }
            }
            for ((index, minute) in minutes.withIndex()) {
                val x = (index + 0.5f) * minuteWidth
                minute.medianBpm?.let { bpm ->
                    val centre = Offset(x, pulseY(bpm))
                    minutes.getOrNull(index - 1)?.medianBpm?.let { previous ->
                        drawLine(colors.primary, Offset(x - minuteWidth, pulseY(previous)), centre, 1.5.dp.toPx(), StrokeCap.Round)
                    }
                    drawCircle(colors.primary, radius = 1.5.dp.toPx(), center = centre)
                }
                val movementColor = when (minute.motion) {
                    MotionState.RECENT_MOVEMENT -> colors.tertiary
                    MotionState.STILL_CONFIRMED -> colors.tertiary.copy(alpha = 0.35f)
                    MotionState.OFF_BODY -> colors.outlineVariant
                    null -> null
                }
                movementColor?.let { color ->
                    drawRect(
                        color, Offset(index * minuteWidth, pulseBottom + 5.dp.toPx()),
                        Size((minuteWidth - 1.dp.toPx()).coerceAtLeast(0f), 6.dp.toPx())
                    )
                }
            }
            for (breath in breaths) {
                val begin = (breath.startedAtEpochMs.coerceAtLeast(start) - start).toFloat() / (end - start) * size.width
                val finish = ((breath.startedAtEpochMs + breath.durationSeconds * 1_000L).coerceAtMost(end) - start)
                    .toFloat() / (end - start) * size.width
                val y = size.height - 4.dp.toPx()
                drawLine(colors.secondary, Offset(begin, y), Offset(finish, y), 4.dp.toPx(), StrokeCap.Round)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(startLabel, Modifier.weight(2f), style = timeLabelStyle, color = colors.onSurfaceVariant)
            Text(stringResource(R.string.feeling_detail_recorded), style = timeLabelStyle, color = colors.onSurfaceVariant)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Text(endLabel, style = timeLabelStyle, color = colors.onSurfaceVariant)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (context != null) {
                TimelineLegendDot(colors.primary, stringResource(R.string.feeling_detail_legend_heart))
                TimelineLegendDot(colors.tertiary, stringResource(R.string.feeling_detail_legend_movement))
            }
            if (context != null || breaths.isNotEmpty()) {
                TimelineLegendDot(colors.secondary, stringResource(R.string.feeling_detail_legend_breathing))
            }
        }
    }
}

@Composable
private fun TimelineLegendDot(color: Color, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

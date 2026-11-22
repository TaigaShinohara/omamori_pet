package com.omamoripet.mobile.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.omamoripet.mobile.R
import com.omamoripet.mobile.data.FeelingRecord
import com.omamoripet.shared.model.BaselineSource
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeelingDetailScreen(
    record: FeelingRecord?,
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
                FeelingDetail(record, breathingSessions, onSaveContext)
            }
        }
    }
}

@Composable
private fun FeelingDetail(
    record: FeelingRecord,
    breathingSessions: List<BreathingSessionRecord>,
    onSaveContext: suspend (String, FeelingContext?) -> Unit
) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var feedback by rememberSaveable { mutableStateOf<Int?>(null) }
    var showMinutes by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
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
    val dateFormatter = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM) }
    val timeFormatter = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    LazyColumn(
        modifier = Modifier.widthIn(max = 580.dp).fillMaxWidth().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(painterResource(record.feeling.faceRes), contentDescription = null, Modifier.size(52.dp))
                Column {
                    Text(stringResource(record.feeling.labelRes), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        dateFormatter.format(Date(record.recordedAtEpochMs)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item { FeelingObservations(record) }
        if (context != null || breaths.isNotEmpty()) {
            item { FeelingTimeline(context, start, breaths) }
        }
        if (context == null) {
            item {
                Text(
                    stringResource(R.string.feeling_detail_no_watch_data),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Text(stringResource(R.string.feeling_detail_import_help), style = MaterialTheme.typography.bodyMedium)
            }
            item {
                OutlinedButton(enabled = !saving, onClick = {
                    feedback = null
                    scanner.launch(
                        ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt(scanPrompt)
                            .setBarcodeImageEnabled(false).setBeepEnabled(false).setOrientationLocked(false)
                    )
                }) {
                    Text(stringResource(R.string.feeling_detail_scan))
                }
            }
        } else {
            item {
                val minutesMeasured = context.minutes.count { it.medianBpm != null }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.feeling_detail_coverage, minutesMeasured, FEELING_MINUTE_COUNT))
                    context.baselineBpm?.let { bpm ->
                        val label = if (context.baselineSource == BaselineSource.DEFAULT_FALLBACK) {
                            R.string.feeling_detail_baseline_default
                        } else {
                            R.string.feeling_detail_baseline
                        }
                        Text(stringResource(label, bpm), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                TextButton(onClick = { showMinutes = !showMinutes }) {
                    Text(stringResource(if (showMinutes) R.string.feeling_detail_hide_minutes else R.string.feeling_detail_show_minutes))
                }
            }
            if (showMinutes) {
                items(context.minutes.size, key = { index -> index }) { index ->
                    val minute = context.minutes[index]
                    val pulse = minute.medianBpm?.let { stringResource(R.string.feeling_detail_bpm, it) }
                        ?: stringResource(R.string.feeling_detail_missing)
                    val motion = when (minute.motion) {
                        MotionState.STILL_CONFIRMED -> R.string.feeling_detail_still
                        MotionState.RECENT_MOVEMENT -> R.string.feeling_detail_moving
                        MotionState.OFF_BODY -> R.string.feeling_detail_off_body
                        null -> R.string.feeling_detail_missing
                    }
                    Text(
                        stringResource(
                            R.string.feeling_detail_minute,
                            timeFormatter.format(Date(start + index * FEELING_MINUTE_MS)),
                            pulse,
                            stringResource(motion)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        item { Text(stringResource(R.string.feeling_detail_breathing), style = MaterialTheme.typography.titleMedium) }
        if (breaths.isEmpty()) {
            item { Text(stringResource(R.string.feeling_detail_no_breathing), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(breaths, key = { "breath-${it.id}" }) { breath ->
            Text(
                stringResource(
                    R.string.feeling_detail_breath_session,
                    timeFormatter.format(Date(breath.startedAtEpochMs)),
                    breath.durationSeconds
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (context != null) {
            item {
                TextButton(enabled = !saving, onClick = { confirmDelete = true }) {
                    Text(stringResource(R.string.feeling_detail_remove_watch_data))
                }
            }
        }
        feedback?.let { message ->
            item { Text(stringResource(message), color = MaterialTheme.colorScheme.error) }
        }
        item { Box(Modifier.height(16.dp)) }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.feeling_detail_remove_watch_data)) },
            text = { Text(stringResource(R.string.feeling_detail_remove_help)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    save(null)
                }) {
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
    val bpmValues = minutes.mapNotNull { it.medianBpm } + listOfNotNull(context?.baselineBpm)
    val graphMax = (bpmValues.maxOrNull() ?: 90) + 8
    val graphMin = (bpmValues.minOrNull() ?: 55) - 8
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (minutes.any { it.medianBpm != null }) {
            Text(stringResource(R.string.feeling_detail_heart), style = MaterialTheme.typography.labelMedium)
        }
        Canvas(Modifier.fillMaxWidth().height(112.dp)) {
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
            Text(timeFormatter.format(Date(start)), Modifier.weight(2f), style = MaterialTheme.typography.labelSmall)
            Text(stringResource(R.string.feeling_detail_recorded), style = MaterialTheme.typography.labelSmall)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Text(timeFormatter.format(Date(end)), style = MaterialTheme.typography.labelSmall)
            }
        }
        if (context != null) {
            Text(stringResource(R.string.feeling_detail_timeline_legend), style = MaterialTheme.typography.bodySmall)
        }
    }
}

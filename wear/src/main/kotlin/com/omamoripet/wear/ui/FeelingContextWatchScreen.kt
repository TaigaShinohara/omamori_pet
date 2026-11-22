package com.omamoripet.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.omamoripet.shared.model.FeelingContextQr
import com.omamoripet.wear.R
import com.omamoripet.wear.data.WatchFeelingContext
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.floor

@Composable
internal fun FeelingContextWatchScreen(
    settings: WatchFeelingContext.Settings,
    onSetEnabled: suspend (Boolean) -> Unit,
    onUpdateWindows: suspend () -> Unit,
    onShowCode: (Boolean) -> Unit
) {
    var selectedTime by rememberSaveable { mutableStateOf<Long?>(null) }
    var updateFailed by remember { mutableStateOf(false) }
    var changing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(settings.windows) {
        while (true) {
            now = System.currentTimeMillis()
            if (settings.windows.any { !it.completed && now >= it.endsAt }) onUpdateWindows()
            if (settings.windows.none { !it.completed }) break
            delay(5_000)
        }
    }
    val selected = settings.windows.firstOrNull { it.recordedAt == selectedTime && it.ready }?.context
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM) }
    val qr = remember(selected) {
        selected?.let { QRCodeWriter().encode(FeelingContextQr.encode(it), BarcodeFormat.QR_CODE, 0, 0) }
    }
    val settingsListState = rememberTransformingLazyColumnState()
    val codeListState = rememberTransformingLazyColumnState()
    val listState = if (qr == null) settingsListState else codeListState

    if (qr != null) {
        val view = LocalView.current
        DisposableEffect(view) {
            onShowCode(true)
            view.keepScreenOn = true
            onDispose {
                view.keepScreenOn = false
                onShowCode(false)
            }
        }
    }
    ScreenScaffold(scrollState = listState, timeText = { if (qr == null) TimeText() }) { padding ->
        TransformingLazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            if (qr != null) {
                item {
                    val label = stringResource(R.string.feeling_context_qr)
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(137.dp).semantics { contentDescription = label }) {
                            val moduleSize = floor(size.minDimension / qr.width)
                            val inset = (size.minDimension - moduleSize * qr.width) / 2
                            drawRect(Color.White)
                            for (y in 0 until qr.height) {
                                for (x in 0 until qr.width) {
                                    if (qr[x, y]) {
                                        drawRect(
                                            color = Color.Black,
                                            topLeft = Offset(inset + x * moduleSize, inset + y * moduleSize),
                                            size = Size(moduleSize, moduleSize)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { selectedTime = null },
                        label = { Text(stringResource(android.R.string.cancel)) }
                    )
                }
            } else {
                item {
                    SwitchButton(
                        checked = settings.enabledSince != null,
                        enabled = !changing,
                        onCheckedChange = { enabled ->
                            changing = true
                            updateFailed = false
                            scope.launch {
                                try {
                                    onSetEnabled(enabled)
                                } catch (_: IOException) {
                                    updateFailed = true
                                } finally {
                                    changing = false
                                }
                            }
                        },
                        label = { Text(stringResource(R.string.feeling_context_title)) },
                        secondaryLabel = { Text(stringResource(R.string.feeling_context_setting)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (updateFailed) item { Text(stringResource(R.string.feeling_context_save_failed)) }
                if (settings.enabledSince != null) {
                    item {
                        Text(
                            stringResource(R.string.feeling_context_instructions),
                            modifier = Modifier.padding(horizontal = 8.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (settings.windows.isEmpty()) {
                        item { Text(stringResource(R.string.feeling_context_empty), Modifier.padding(horizontal = 8.dp)) }
                    }
                    for (window in settings.windows) {
                        val recordedAt = window.recordedAt
                        val status = when {
                            window.ready -> R.string.feeling_context_qr
                            window.completed -> R.string.feeling_context_no_data
                            now >= window.endsAt -> R.string.feeling_context_saving
                            else -> R.string.feeling_context_collecting
                        }
                        item(key = recordedAt) {
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = window.ready,
                                onClick = { selectedTime = recordedAt },
                                label = { Text(formatter.format(Date(recordedAt))) },
                                secondaryLabel = {
                                    Text(stringResource(status))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

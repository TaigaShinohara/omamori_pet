package com.omamoripet.wear.ui

import android.graphics.Bitmap
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.roundToInt

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
    val lifecycleOwner = LocalLifecycleOwner.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(settings.windows, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val current = System.currentTimeMillis()
                now = current
                if (settings.windows.any { !it.completed && current >= it.endsAt }) {
                    onUpdateWindows()
                }
                val nextEndsAt = settings.windows
                    .asSequence()
                    .filter { !it.completed && it.endsAt > current }
                    .minOfOrNull { it.endsAt }
                    ?: break
                delay((nextEndsAt - current).coerceAtLeast(100L))
            }
        }
    }
    val selected = settings.windows.firstOrNull { it.recordedAt == selectedTime && it.ready }?.context
    val showQr = selected != null
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    val qrBitmap by produceState<ImageBitmap?>(initialValue = null, selected) {
        value = if (selected == null) {
            null
        } else {
            withContext(Dispatchers.Default) {
                val matrix = QRCodeWriter().encode(
                    FeelingContextQr.encode(selected),
                    BarcodeFormat.QR_CODE,
                    0,
                    0
                )
                val w = matrix.width
                val h = matrix.height
                val pixels = IntArray(w * h)
                val black = android.graphics.Color.BLACK
                val white = android.graphics.Color.WHITE
                for (y in 0 until h) {
                    val rowOffset = y * w
                    for (x in 0 until w) {
                        pixels[rowOffset + x] = if (matrix[x, y]) black else white
                    }
                }
                Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
            }
        }
    }
    val settingsListState = rememberTransformingLazyColumnState()
    val codeListState = rememberTransformingLazyColumnState()
    val listState = if (!showQr) settingsListState else codeListState

    if (showQr) {
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
    ScreenScaffold(scrollState = listState, timeText = { if (!showQr) TimeText() }) { padding ->
        TransformingLazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            if (showQr) {
                item {
                    val label = stringResource(R.string.feeling_context_qr)
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(137.dp).semantics { contentDescription = label }) {
                            drawRect(Color.White)
                            val bmp = qrBitmap ?: return@Canvas
                            val moduleSize = floor(size.minDimension / bmp.width).toInt().coerceAtLeast(1)
                            val drawSide = moduleSize * bmp.width
                            val inset = ((size.minDimension - drawSide) / 2f).roundToInt()
                            drawImage(
                                image = bmp,
                                dstOffset = IntOffset(inset, inset),
                                dstSize = IntSize(drawSide, drawSide),
                                filterQuality = FilterQuality.None
                            )
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
                    if (settings.windows.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.feeling_context_instructions),
                                modifier = Modifier.padding(horizontal = 8.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
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

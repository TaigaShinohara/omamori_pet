package com.omamoripet.mobile.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.omamoripet.mobile.R
import com.omamoripet.mobile.data.Feeling
import com.omamoripet.mobile.data.FeelingRecord
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@get:StringRes
internal val Feeling.labelRes: Int get() = when (this) {
    Feeling.ANXIOUS -> R.string.feeling_anxious
    Feeling.LOW -> R.string.feeling_low
    Feeling.IRRITATED -> R.string.feeling_irritated
}

@get:DrawableRes
internal val Feeling.faceRes: Int get() = when (this) {
    Feeling.ANXIOUS -> R.drawable.pet_feeling_anxious
    Feeling.LOW -> R.drawable.pet_feeling_low
    Feeling.IRRITATED -> R.drawable.pet_feeling_irritated
}

@Composable
internal fun activeFeelingFace(latest: FeelingRecord?): Feeling? {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember(latest) {
        mutableStateOf(latest?.takeIf { it.faceRemainingMs(System.currentTimeMillis()) > 0 }?.feeling)
    }
    LaunchedEffect(latest, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val remaining = latest?.faceRemainingMs(System.currentTimeMillis()) ?: 0L
            active = latest?.feeling.takeIf { remaining > 0 }
            if (remaining > 0) {
                delay(remaining)
                active = null
            }
        }
    }
    return active
}

@Composable
internal fun FeelingRecordDialog(onDismiss: () -> Unit, onRecord: suspend (Feeling) -> Unit) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.feeling_record_action)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                for (feeling in Feeling.entries) {
                    ListItem(
                        leadingContent = {
                            Image(painterResource(feeling.faceRes), contentDescription = null, modifier = Modifier.size(36.dp))
                        },
                        headlineContent = { Text(stringResource(feeling.labelRes)) },
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !saving, role = Role.Button) {
                            saving = true
                            failed = false
                            scope.launch {
                                try {
                                    onRecord(feeling)
                                    onDismiss()
                                } catch (_: IOException) {
                                    failed = true
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    )
                }
                if (failed) {
                    Text(stringResource(R.string.feeling_save_failed), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

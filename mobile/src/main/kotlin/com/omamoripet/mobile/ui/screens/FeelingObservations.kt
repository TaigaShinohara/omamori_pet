package com.omamoripet.mobile.ui.screens

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.omamoripet.mobile.R
import com.omamoripet.mobile.data.FeelingComparison
import com.omamoripet.mobile.data.FeelingRecord
import com.omamoripet.mobile.data.PendingFeelingActivity
import com.omamoripet.mobile.data.approximateMoonAge
import com.omamoripet.mobile.ui.theme.LocalAnimationsEnabled
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

@Composable
internal fun FeelingObservations(record: FeelingRecord, pendingActivity: PendingFeelingActivity) {
    val health = record.health
    val timeFormatter = remember(record.recordedZoneId) {
        DateFormat.getTimeInstance(DateFormat.SHORT).apply {
            record.recordedZoneId?.let { timeZone = TimeZone.getTimeZone(it) }
        }
    }
    val moonAge = remember(record.recordedAtEpochMs) { approximateMoonAge(record.recordedAtEpochMs) }
    val animationsEnabled = LocalAnimationsEnabled.current
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (detailsExpanded) 90f else 0f,
        animationSpec = if (animationsEnabled) tween(180) else snap(),
        label = "detailsArrowRotation"
    )
    val expandedLabel = stringResource(R.string.section_expanded)
    val collapsedLabel = stringResource(R.string.section_collapsed)

    val hasPrimaryData = pendingActivity.any ||
        health?.sleep != null ||
        health?.heart5 != null ||
        health?.steps5 != null ||
        health?.periodDay != null
    val hasAnyHealthConnect = pendingActivity.any ||
        health?.sleep != null ||
        health?.steps5 != null ||
        health?.steps30 != null ||
        health?.heart5 != null ||
        health?.heart30 != null
    val anyPrimaryMissingTypical = listOfNotNull(
        health?.sleep?.duration,
        health?.heart5,
        health?.steps5
    ).any { it.typical == null }
    val anySecondaryMissingTypical = listOfNotNull(
        health?.heart30,
        health?.steps30
    ).any { it.typical == null }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (hasPrimaryData) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.feeling_health_detail_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                    health?.sleep?.let { sleep ->
                        val wakeText = stringResource(
                            R.string.feeling_health_wake,
                            timeFormatter.format(Date(sleep.wokeAtEpochMs))
                        )
                        ComparisonRow(
                            label = R.string.feeling_health_sleep,
                            metric = sleep.duration,
                            trailingNote = wakeText
                        ) { duration ->
                            if (duration < 60) stringResource(R.string.feeling_health_minutes, duration)
                            else stringResource(R.string.feeling_health_sleep_duration, duration / 60, duration % 60)
                        }
                    }
                    if (pendingActivity.heart || health?.heart5 != null) {
                        ComparisonRow(R.string.feeling_health_heart_5, health?.heart5) { value ->
                            stringResource(R.string.feeling_detail_bpm, value)
                        }
                    }
                    if (pendingActivity.steps || health?.steps5 != null) {
                        ComparisonRow(R.string.feeling_health_steps_5, health?.steps5) { value ->
                            stringResource(R.string.feeling_health_steps, value)
                        }
                    }
                    health?.periodDay?.let {
                        Text(
                            text = stringResource(R.string.feeling_health_period_day, it),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
                        )
                    }
                    if (anyPrimaryMissingTypical) {
                        Text(
                            text = stringResource(R.string.feeling_health_comparison_missing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .semantics {
                        stateDescription = if (detailsExpanded) expandedLabel else collapsedLabel
                    }
                    .clickable(role = Role.Button) { detailsExpanded = !detailsExpanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.feeling_health_details_toggle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    painter = painterResource(R.drawable.ic_keyboard_arrow_right_rounded),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(arrowRotation)
                )
            }

            AnimatedVisibility(
                visible = detailsExpanded,
                enter = if (animationsEnabled) fadeIn() + expandVertically() else EnterTransition.None,
                exit = if (animationsEnabled) fadeOut() + shrinkVertically() else ExitTransition.None
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!hasAnyHealthConnect) {
                        Text(
                            text = stringResource(R.string.feeling_health_detail_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    health?.heart30?.let {
                        ComparisonRow(R.string.feeling_health_heart_30, it) { value ->
                            stringResource(R.string.feeling_detail_bpm, value)
                        }
                    }
                    health?.steps30?.let {
                        ComparisonRow(R.string.feeling_health_steps_30, it) { value ->
                            stringResource(R.string.feeling_health_steps, value)
                        }
                    }
                    if (!anyPrimaryMissingTypical && anySecondaryMissingTypical) {
                        Text(
                            text = stringResource(R.string.feeling_health_comparison_missing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    record.pressure?.let {
                        Text(
                            text = stringResource(
                                R.string.feeling_health_pressure,
                                it.hPa,
                                timeFormatter.format(Date(it.observedAtEpochMs))
                            ),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
                        )
                    }
                    Text(
                        text = stringResource(R.string.feeling_health_moon, moonAge),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparisonRow(
    label: Int,
    metric: FeelingComparison?,
    trailingNote: String? = null,
    format: @Composable (Int) -> String
) {
    Column(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val baseLine = if (metric == null) {
            stringResource(label)
        } else {
            stringResource(R.string.feeling_health_measurement, stringResource(label), format(metric.value))
        }
        val headline = if (trailingNote != null) "$baseLine · $trailingNote" else baseLine
        Text(
            text = headline,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
        )
        val typical = metric?.typical
        val detail = when {
            metric == null -> stringResource(R.string.feeling_health_sync_pending)
            typical != null -> {
                val difference = metric.value - typical
                val differenceText = when {
                    difference > 0 -> stringResource(R.string.feeling_health_more, format(difference))
                    difference < 0 -> stringResource(R.string.feeling_health_less, format(-difference))
                    else -> stringResource(R.string.feeling_health_same)
                }
                stringResource(R.string.feeling_health_typical, format(typical), differenceText)
            }
            else -> null
        }
        detail?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

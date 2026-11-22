package com.omamoripet.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omamoripet.mobile.R
import com.omamoripet.mobile.data.FeelingComparison
import com.omamoripet.mobile.data.FeelingRecord
import com.omamoripet.mobile.data.approximateMoonAge
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

@Composable
internal fun FeelingObservations(record: FeelingRecord) {
    val health = record.health
    val timeFormatter = remember(record.recordedZoneId) {
        DateFormat.getTimeInstance(DateFormat.SHORT).apply {
            record.recordedZoneId?.let { timeZone = TimeZone.getTimeZone(it) }
        }
    }
    val moonAge = remember(record.recordedAtEpochMs) { approximateMoonAge(record.recordedAtEpochMs) }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.feeling_health_detail_title), style = MaterialTheme.typography.titleMedium)
            if (health?.sleep == null && health?.steps5 == null && health?.steps30 == null &&
                health?.heart5 == null && health?.heart30 == null
            ) {
                Text(
                    stringResource(R.string.feeling_health_detail_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    stringResource(R.string.feeling_health_comparison_scope),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                health?.sleep?.let { sleep ->
                    ComparisonRow(R.string.feeling_health_sleep, sleep.duration) { duration ->
                        if (duration < 60) stringResource(R.string.feeling_health_minutes, duration)
                        else stringResource(R.string.feeling_health_sleep_duration, duration / 60, duration % 60)
                    }
                    Text(
                        stringResource(R.string.feeling_health_wake, timeFormatter.format(Date(sleep.wokeAtEpochMs))),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                health?.steps5?.let { ComparisonRow(R.string.feeling_health_steps_5, it) { value ->
                    stringResource(R.string.feeling_health_steps, value)
                } }
                health?.steps30?.let { ComparisonRow(R.string.feeling_health_steps_30, it) { value ->
                    stringResource(R.string.feeling_health_steps, value)
                } }
                health?.heart5?.let { ComparisonRow(R.string.feeling_health_heart_5, it) { value ->
                    stringResource(R.string.feeling_detail_bpm, value)
                } }
                health?.heart30?.let { ComparisonRow(R.string.feeling_health_heart_30, it) { value ->
                    stringResource(R.string.feeling_detail_bpm, value)
                } }
            }
            health?.periodDay?.let {
                Text(stringResource(R.string.feeling_health_period_day, it), style = MaterialTheme.typography.bodyMedium)
            }
            record.pressure?.let {
                Text(
                    stringResource(
                        R.string.feeling_health_pressure, it.hPa,
                        timeFormatter.format(Date(it.observedAtEpochMs))
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(stringResource(R.string.feeling_health_moon, moonAge), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ComparisonRow(label: Int, metric: FeelingComparison, format: @Composable (Int) -> String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(R.string.feeling_health_measurement, stringResource(label), format(metric.value)),
            style = MaterialTheme.typography.bodyMedium
        )
        val typical = metric.typical
        val detail = if (typical != null) {
            val difference = metric.value - typical
            val differenceText = when {
                difference > 0 -> stringResource(R.string.feeling_health_more, format(difference))
                difference < 0 -> stringResource(R.string.feeling_health_less, format(-difference))
                else -> stringResource(R.string.feeling_health_same)
            }
            stringResource(R.string.feeling_health_typical, format(typical), metric.comparisonDays, differenceText)
        } else {
            stringResource(R.string.feeling_health_comparison_missing)
        }
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

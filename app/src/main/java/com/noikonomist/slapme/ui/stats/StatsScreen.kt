package com.noikonomist.slapme.ui.stats

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.noikonomist.slapme.R
import com.noikonomist.slapme.ui.appContainer
import com.noikonomist.slapme.ui.components.CardColumn
import com.noikonomist.slapme.ui.components.ScreenScaffold
import com.noikonomist.slapme.ui.components.durationText
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun StatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val history = appContainer().historyRepository
    val zone = remember { ZoneId.systemDefault() }
    val locale = LocalConfiguration.current.locales[0]
    var weeksBack by rememberSaveable { mutableIntStateOf(0) }
    var metric by rememberSaveable { mutableStateOf(ChartMetric.SLAPS) }

    val end = remember(weeksBack) { LocalDate.now(zone).minusWeeks(weeksBack.toLong()) }
    val start = end.minusDays(6)
    val fromMillis = start.atStartOfDay(zone).toInstant().toEpochMilli()
    val toMillis = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val usage by remember(end) { history.dailyUsage(start, end) }.collectAsState(initial = emptyList())
    val slaps by remember(end) { history.slaps(fromMillis, toMillis) }.collectAsState(initial = emptyList())
    val overrides by remember(end) { history.overrides(fromMillis, toMillis) }.collectAsState(initial = emptyList())
    val week = WeekStatsCalculator.compute(end, usage, slaps, overrides, zone)

    ScreenScaffold(title = stringResource(R.string.stats_title), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Week picker
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { weeksBack++ }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.stats_previous_week))
                }
                Text(
                    DateUtils.formatDateRange(context, fromMillis, toMillis - 1, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { weeksBack-- }, enabled = weeksBack > 0) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.stats_next_week))
                }
            }

            // Chart
            Card(Modifier.fillMaxWidth()) {
                CardColumn {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(metric == ChartMetric.SLAPS, onClick = { metric = ChartMetric.SLAPS }, label = { Text(stringResource(R.string.stats_metric_slaps)) })
                        FilterChip(metric == ChartMetric.TIME_USED, onClick = { metric = ChartMetric.TIME_USED }, label = { Text(stringResource(R.string.stats_metric_time)) })
                    }
                    val values = week.days.map(metric::value)
                    val labels = week.days.map { it.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) }
                    val minutesUnit = stringResource(R.string.stats_minutes_short)
                    val description = when (metric) {
                        ChartMetric.SLAPS -> stringResource(
                            R.string.stats_chart_description_slaps,
                            labels.zip(week.days).joinToString(", ") { (label, day) -> "$label ${day.slaps}" },
                        )
                        ChartMetric.TIME_USED -> stringResource(
                            R.string.stats_chart_description_time,
                            labels.zip(values).joinToString(", ") { (label, v) -> "$label ${v.roundToLong()} $minutesUnit" },
                        )
                    }
                    BarChart(
                        values = values,
                        labels = labels,
                        valueText = { v -> v.roundToLong().toString() },
                        highlightIndex = if (weeksBack == 0) week.days.lastIndex else null,
                        description = description,
                    )
                    if (metric == ChartMetric.TIME_USED) {
                        Text(stringResource(R.string.stats_time_axis_note), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Week summary
            Card(Modifier.fillMaxWidth()) {
                CardColumn {
                    Text(
                        stringResource(if (weeksBack == 0) R.string.stats_week_title else R.string.stats_week_title_past),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    SummaryRow(stringResource(R.string.stats_total_slaps), week.totalSlaps.toString())
                    week.busiestDay?.let {
                        SummaryRow(
                            stringResource(R.string.stats_busiest_day),
                            pluralStringResource(R.plurals.stats_slaps_on, it.slaps, it.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale), it.slaps),
                        )
                    }
                    SummaryRow(stringResource(R.string.stats_longest_focus), durationText(week.longestFocusMillis.milliseconds))
                    SummaryRow(stringResource(R.string.stats_time_used), durationText(week.usedMillis.milliseconds))
                    SummaryRow(stringResource(R.string.stats_snoozes), week.snoozes.toString())
                    SummaryRow(stringResource(R.string.stats_friction), week.frictionOverrides.toString())
                    SummaryRow(stringResource(R.string.stats_switch_offs), week.switchOffs.toString())
                }
            }

            // Per day, newest first
            Card(Modifier.fillMaxWidth()) {
                CardColumn {
                    Text(stringResource(R.string.stats_days_title), style = MaterialTheme.typography.titleLarge)
                    week.days.asReversed().forEachIndexed { i, day ->
                        if (i > 0) HorizontalDivider()
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(
                                DateUtils.formatDateTime(
                                    context,
                                    day.date.atStartOfDay(zone).toInstant().toEpochMilli(),
                                    DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL,
                                ),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                stringResource(
                                    R.string.stats_day_line,
                                    pluralStringResource(R.plurals.stats_slaps, day.slaps, day.slaps),
                                    durationText(day.usedMillis.milliseconds),
                                    durationText(day.longestFocusMillis.milliseconds),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

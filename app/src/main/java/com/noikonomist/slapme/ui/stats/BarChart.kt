package com.noikonomist.slapme.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/**
 * A simple bar chart drawn with Canvas: one bar per value, a label under each, the value above each
 * non-zero bar, and gridlines at 0, half and the (rounded) top. [description] is read by TalkBack.
 */
@Composable
fun BarChart(
    values: List<Double>,
    labels: List<String>,
    valueText: (Double) -> String,
    highlightIndex: Int?,
    description: String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val barColor = MaterialTheme.colorScheme.primary
    val highlightColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = textColor)
    val valueStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    val axisMax = ChartScale.niceMax(values.maxOrNull() ?: 0.0)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(220.dp)
            .semantics { contentDescription = description },
    ) {
        val labelArea = 24.dp.toPx()
        val top = 22.dp.toPx()
        val chartHeight = size.height - labelArea - top
        val slot = size.width / values.size.coerceAtLeast(1)
        val barWidth = slot * 0.56f

        for (line in ChartScale.gridlines(axisMax)) {
            val y = top + chartHeight * (1 - (line / axisMax)).toFloat()
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }

        values.forEachIndexed { i, value ->
            val centerX = slot * i + slot / 2
            val barHeight = (chartHeight * (value / axisMax)).toFloat()
            val barTop = top + chartHeight - barHeight
            if (value > 0) {
                drawRoundRect(
                    color = if (i == highlightIndex) highlightColor else barColor,
                    topLeft = Offset(centerX - barWidth / 2, barTop),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                )
                val text = measurer.measure(valueText(value), valueStyle)
                drawText(text, topLeft = Offset(centerX - text.size.width / 2f, barTop - text.size.height - 2.dp.toPx()))
            }
            labels.getOrNull(i)?.let { label ->
                val text = measurer.measure(label, labelStyle)
                drawText(text, topLeft = Offset(centerX - text.size.width / 2f, top + chartHeight + 6.dp.toPx()))
            }
        }
    }
}

package com.example.mattshealthtracker

import android.annotation.SuppressLint
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Addchart
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StatisticsScreen(openedDay: String) {
    val context = LocalContext.current
    val viewModel: StatisticsViewModel = viewModel(factory = StatisticsViewModelFactory(context))
    val healthData = viewModel.healthData.value
    val selectedTimeframe = viewModel.selectedTimeframe.value

    // Define all available metrics here:
    val allMetrics = listOf(
        "Malaise" to { it: HealthData -> it.malaise },
        "Stress Level" to { it: HealthData -> it.stressLevel },
        "Sleep Quality" to { it: HealthData -> it.sleepQuality },
        "Illness Impact" to { it: HealthData -> it.illnessImpact },
        "Depression" to { it: HealthData -> it.depression },
        "Hopelessness" to { it: HealthData -> it.hopelessness },
        "Sore Throat" to { it: HealthData -> it.soreThroat },
        "Sleep Length" to { it: HealthData -> it.sleepLength },
        "Lymphadenopathy" to { it: HealthData -> it.lymphadenopathy },
        "Exercise Level" to { it: HealthData -> it.exerciseLevel },
        "Sleep Readiness" to { it: HealthData -> it.sleepReadiness }
    )

    // Keep track of selected metrics in state, initialized with some defaults
    val selectedMetrics = remember { mutableStateListOf("Malaise", "Stress Level", "Sleep Quality") }

    // State to control expansion
    var showAllMetrics by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Health Statistics") }) }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(0.dp))
            TimeframeSelector(selectedTimeframe) { viewModel.updateTimeframe(it) }
            Spacer(modifier = Modifier.height(16.dp))

            // --- AVERAGES SECTION ---
            if (healthData.isEmpty()) {
                EmptyDataInfo()
            } else {
                HealthMetricsSummary(healthData) // This is your averages section
                Spacer(modifier = Modifier.height(24.dp))
            }

            // --- TOGGLES SECTION (Now with Expand/Collapse) ---
            Column(modifier = Modifier.animateContentSize()) { // Apply animateContentSize here
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAllMetrics = !showAllMetrics } // Toggle on row click
                        .padding(vertical = 2.dp), // Add some padding for click area
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Select Metrics to Display", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { showAllMetrics = !showAllMetrics }) {
                        Icon(
                            imageVector = if (showAllMetrics) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (showAllMetrics) "Collapse metrics" else "Expand metrics"
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))

                // Conditionally display chips
                if (showAllMetrics) {
                    // Expanded view: Show all metrics in FlowRow
                    FlowRow {
                        allMetrics.forEach { (label, _) ->
                            FilterChip(
                                selected = selectedMetrics.contains(label),
                                onClick = {
                                    if (selectedMetrics.contains(label)) {
                                        selectedMetrics.remove(label)
                                    } else {
                                        selectedMetrics.add(label)
                                    }
                                },
                                label = { Text(label) },
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                } else {
                    // Collapsed view: Show only selected metrics (or a default few if none selected)
                    // Take up to 3 selected metrics, or fallback to first 3 allMetrics if selected is empty.
                    val displayedMetrics = if (selectedMetrics.isNotEmpty()) {
                        selectedMetrics.take(3)
                    } else {
                        allMetrics.map { it.first }.take(1) // Display first 3 if no selected metrics
                    }

                    FlowRow {
                        displayedMetrics.forEach { label ->
                            // Find the full metric object to pass to FilterChip
                            val metric = allMetrics.firstOrNull { it.first == label }
                            metric?.let {
                                FilterChip(
                                    selected = selectedMetrics.contains(label),
                                    onClick = {
                                        // On click, expand and then handle selection
                                        showAllMetrics = true
                                        if (selectedMetrics.contains(label)) {
                                            selectedMetrics.remove(label)
                                        } else {
                                            selectedMetrics.add(label)
                                        }
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        // Optionally add a "..." chip if more metrics are hidden
                        if (allMetrics.size > displayedMetrics.size && selectedMetrics.size > 3 || (selectedMetrics.isEmpty() && allMetrics.size > 3)) {
                            FilterChip(
                                selected = false,
                                onClick = { showAllMetrics = true },
                                label = { Text("...") },
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp)) // Space after toggles section

            // --- GRAPHS SECTION ---
            if (healthData.isNotEmpty()) {
                selectedMetrics.forEach { metricLabel ->
                    val extractor = allMetrics.firstOrNull { it.first == metricLabel }?.second
                    extractor?.let {
                        HealthLineChart(
                            chartTitle = metricLabel,
                            dataPoints = healthData.map(it),
                            labels = healthData.map { it.currentDate }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun EmptyDataInfo() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No data available for the selected timeframe.", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Start tracking your health to see statistics here!", style = MaterialTheme.typography.bodyMedium)
        Icon(Icons.Default.Addchart, contentDescription = "Information", modifier = Modifier.size(48.dp))
    }
}

@Composable
fun HealthMetricChart(title: String, dataPoints: List<Float>, labels: List<String>, chartTitle: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(8.dp))
    HealthLineChart(
        dataPoints = dataPoints,
        labels = labels,
        chartTitle = chartTitle
    )
    Spacer(modifier = Modifier.height(24.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeframeSelector(
    selectedTimeframe: Timeframe,
    onTimeframeSelected: (Timeframe) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Timeframe.entries.forEach { timeframe ->
            FilterChip(
                selected = selectedTimeframe == timeframe,
                onClick = { onTimeframeSelected(timeframe) },
                label = { Text(timeframe.label) },
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun HealthMetricsSummary(healthData: List<HealthData>) {
    fun List<Float>.avgOrZero() = average().toFloat().takeIf { !it.isNaN() } ?: 0f

    val stats = listOf(
        "Average Malaise:" to healthData.map { it.malaise }.avgOrZero(),
        "Average Sore Throat:" to healthData.map { it.soreThroat }.avgOrZero(),
        "Average Lymphadenopathy:" to healthData.map { it.lymphadenopathy }.avgOrZero(),
        "Average Exercise Level:" to healthData.map { it.exerciseLevel }.avgOrZero(),
        "Average Stress Level:" to healthData.map { it.stressLevel }.avgOrZero(),
        "Average Illness Impact:" to healthData.map { it.illnessImpact }.avgOrZero(),
        "Average Depression:" to healthData.map { it.depression }.avgOrZero(),
        "Average Hopelessness:" to healthData.map { it.hopelessness }.avgOrZero(),
        "Average Sleep Quality:" to healthData.map { it.sleepQuality }.avgOrZero(),
        "Average Sleep Length (hours):" to healthData.map { it.sleepLength }.avgOrZero(),
        "Average Sleep Readiness:" to healthData.map { it.sleepReadiness }.avgOrZero(),
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Summary Statistics", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            stats.forEach { (label, value) ->
                StatisticRow(label, String.format("%.1f", value))
            }
        }
    }
}

@Composable
fun StatisticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun calculatePointSpacing(count: Int): Dp {
    val maxSpacing = 14f  // max spacing for very few points
    val minSpacing = 2f   // min spacing for many points
    val maxCount = 180f   // above this, spacing stays at min

    val spacing = if (count >= maxCount) {
        minSpacing
    } else {
        maxSpacing - ((count / maxCount) * (maxSpacing - minSpacing))
    }

    return spacing.dp
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun HealthLineChart(
    dataPoints: List<Float>,
    labels: List<String>, // Expected ISO date strings "YYYY-MM-DD"
    chartTitle: String,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    if (dataPoints.isEmpty()) {
        Text("No data to display for $chartTitle.", style = MaterialTheme.typography.bodySmall)
        return
    }

    val pointSpacing = calculatePointSpacing(dataPoints.size)
    val totalWidth = (dataPoints.size - 1) * pointSpacing.value

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val minRequiredChartWidth = with(density) {
        val screenWidthDp = configuration.screenWidthDp.dp  // Convert Int to Dp
        screenWidthDp // this is already Dp, no need to convert again
    }
    val contentModifier = if (dataPoints.size > 1) {
        Modifier.width(totalWidth.dp.coerceAtLeast(minRequiredChartWidth))
    } else {
        Modifier.fillMaxWidth()
    }

    val minVal = dataPoints.minOrNull() ?: 0f
    val maxVal = (dataPoints.maxOrNull() ?: 10f) + 0.1f
    val valueRange = maxVal - minVal
    val normalizedData = dataPoints.map { if (valueRange == 0f) 0.5f else (it - minVal) / valueRange }

    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    val dates = labels.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }

    val weeklyTickIndices = dates.mapIndexedNotNull { index, date ->
        if (date.dayOfWeek == DayOfWeek.MONDAY) index else null
    }

    val monthlyLabelIndices = dates.mapIndexedNotNull { index, date ->
        if (date.dayOfMonth == 1 || (index == 0 && dates.isNotEmpty())) index else null
    }

    val monthFormatter = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .horizontalScroll(scrollState)
        ) {
            Canvas(
                modifier = contentModifier.fillMaxHeight()
            ) {
                val lineWidth = 2.dp.toPx()
                val xStep = pointSpacing.toPx()
                val tickLength = 8.dp.toPx()

                // Draw Y-axis grid lines
                val numYLabels = 5
                for (i in 0 until numYLabels) {
                    val y = size.height - (i.toFloat() / (numYLabels - 1)) * size.height
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // --- Draw Smoothed Line ---
                if (dataPoints.size > 1) {
                    val path = Path()
                    val firstX = 0f
                    val firstY = size.height - normalizedData[0] * size.height
                    path.moveTo(firstX, firstY)

                    for (i in 0 until dataPoints.size - 1) {
                        val currentX = i * xStep
                        val currentY = size.height - normalizedData[i] * size.height

                        val nextX = (i + 1) * xStep
                        val nextY = size.height - normalizedData[i + 1] * size.height

                        val tension = 0.3f

                        val controlX1 = currentX + (nextX - currentX) * tension
                        val controlY1 = currentY

                        val controlX2 = nextX - (nextX - currentX) * tension
                        val controlY2 = nextY

                        path.cubicTo(controlX1, controlY1, controlX2, controlY2, nextX, nextY)
                    }

                    // FIX: Added style = Stroke(width = lineWidth)
                    drawPath(
                        path = path,
                        color = primaryColor,
                        style = Stroke(width = lineWidth) // <--- THIS IS THE FIX
                    )
                }
                // --- END Draw Smoothed Line ---

                // Draw Weekly Ticks (Mondays only)
                weeklyTickIndices.forEach { index ->
                    val x = index * xStep
                    drawLine(
                        color = tickColor,
                        start = Offset(x, size.height),
                        end = Offset(x, size.height - tickLength),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }

                // Draw Monthly Bars (Vertical lines for months)
                monthlyLabelIndices.forEach { index ->
                    val x = index * xStep
                    drawLine(
                        color = labelColor.copy(alpha = 0.3f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Monthly labels
        Box(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .then(contentModifier)
                .fillMaxHeight(0.05f)
        ) {
            monthlyLabelIndices.forEach { index ->
                val date = dates.getOrNull(index)
                val rawText = date?.format(monthFormatter) ?: ""
                val labelText = rawText.replaceFirstChar { it.uppercaseChar() } // Capitalize first letter

                val xPosition = (index * pointSpacing.value).dp

                if (labelText.isNotEmpty()) {
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.labelMedium.copy( // Smaller text size (labelMedium)
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Normal // Normal font weight
                        ),
                        modifier = Modifier
                            .offset(x = xPosition - (pointSpacing / 2))
                            .wrapContentWidth(align = Alignment.CenterHorizontally)
                            .align(Alignment.CenterStart),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
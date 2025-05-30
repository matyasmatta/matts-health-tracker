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
import androidx.compose.material.icons.filled.CalendarToday
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Applies a Savitzky-Golay filter to smooth a list of data points.
 *
 * @param data The input list of Float data points.
 * @param windowSize The size of the smoothing window (must be an odd number).
 * @param polynomialOrder The order of the polynomial to fit (e.g., 2 for quadratic).
 * @return A new list of smoothed Float data points.
 */
fun applySavitzkyGolayFilter(data: List<Float>, windowSize: Int, polynomialOrder: Int): List<Float> {
    if (data.size < windowSize) return data // Cannot smooth if data is smaller than window

    require(windowSize % 2 == 1) { "Window size must be an odd number." }
    require(polynomialOrder < windowSize) { "Polynomial order must be less than window size." }
    require(windowSize >= 3) { "Window size must be at least 3 for smoothing." }

    val halfWindow = windowSize / 2
    val smoothedData = data.toMutableList()

    // Pre-calculated coefficients for common window sizes and polynomial order 2 (quadratic).
    val coefficients: Map<Int, List<Float>> = mapOf(
        // For window 3, a simple 3-point average effectively provides some smoothing.
        3 to listOf(1f/3f, 1f/3f, 1f/3f), // Simple 3-point moving average
        5 to listOf(-3f/35f, 12f/35f, 17f/35f, 12f/35f, -3f/35f), // SG(5,2)
        7 to listOf(-2f/21f, 3f/21f, 6f/21f, 7f/21f, 6f/21f, 3f/21f, -2f/21f) // SG(7,2)
    )

    val currentCoefficients = coefficients[windowSize]
        ?: throw IllegalArgumentException("No pre-calculated coefficients for window size $windowSize and polynomial order $polynomialOrder.")

    for (i in data.indices) {
        var sum = 0f
        var effectiveCoeffSum = 0f

        // Apply convolution, handling edges by only including points within bounds.
        for (j in -halfWindow..halfWindow) {
            val dataIndex = i + j
            val coeffIndex = j + halfWindow

            if (dataIndex >= 0 && dataIndex < data.size) {
                sum += data[dataIndex] * currentCoefficients[coeffIndex]
                effectiveCoeffSum += currentCoefficients[coeffIndex]
            }
        }
        // Normalize the sum if the window is truncated at the edges.
        smoothedData[i] = if (effectiveCoeffSum != 0f) sum / effectiveCoeffSum else sum
    }

    return smoothedData
}


@Composable
fun calculatePointSpacing(count: Int): Dp {
    // Get the screen width in Dp
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.toFloat() // Convert to float for calculations

    // Define the scale factor to reduce the effective width
    val scaleFactor = 0.95f // Makes the chart 5% smaller

    // Constants for the logic
    val daysForOneMonth = 30f // Approximately one month
    val daysForSixMonths = 180f // Approximately six months
    val targetWidthMultiplierForSixMonths = 3f // 300% of screen width

    val spacing: Float

    // Calculate the effective screen width after applying the scale factor
    val effectiveScreenWidthDp = screenWidthDp * scaleFactor

    if (count <= daysForOneMonth) {
        // Up to one month: fill the effective screen width
        // Ensure count is at least 1 to avoid division by zero
        spacing = effectiveScreenWidthDp / (count.coerceAtLeast(1).toFloat())
    } else {
        // More than one month: dynamic scaling based on the effective screen width
        // Calculate 'a' and 'b' for the linear function M = aN + b
        // M = targetWidthMultiplier
        // N = count
        // (x1, y1) = (daysForOneMonth, 1f) -> M = 1 at 30 days
        // (x2, y2) = (daysForSixMonths, targetWidthMultiplierForSixMonths) -> M = 3 at 180 days

        val a = (targetWidthMultiplierForSixMonths - 1f) / (daysForSixMonths - daysForOneMonth)
        val b = 1f - (a * daysForOneMonth)

        // Calculate the target width multiplier based on the current count
        val targetWidthMultiplier = a * count + b

        // Calculate the spacing
        // totalWidth = targetWidthMultiplier * effectiveScreenWidthDp
        // totalWidth = count * spacing
        // spacing = (targetWidthMultiplier * effectiveScreenWidthDp) / count
        spacing = (targetWidthMultiplier * effectiveScreenWidthDp) / count
    }

    // Add a minimum spacing to prevent points from overlapping excessively
    // This is a practical safeguard, adjust as needed
    val minPracticalSpacing = 2.dp.value
    return spacing.dp.coerceAtLeast(minPracticalSpacing.dp)
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun HealthLineChart(
    dataPoints: List<Float>,
    labels: List<String>, // Expected ISO date strings "YYYY-MM-DD"
    chartTitle: String,
    smoothingWindowSize: Int = 5, // Default smoothing window size
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    if (dataPoints.isEmpty()) {
        Text("No data to display for $chartTitle.", style = MaterialTheme.typography.bodySmall)
        return
    }

    // Apply Savitzky-Golay filter here before drawing
    val smoothedDataPoints = remember(dataPoints, smoothingWindowSize) {
        applySavitzkyGolayFilter(dataPoints, smoothingWindowSize, 2) // Using polynomial order 2
    }

    val pointSpacing = calculatePointSpacing(smoothedDataPoints.size) // Use smoothed data size for spacing
    val totalWidth = (smoothedDataPoints.size - 1) * pointSpacing.value

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val minRequiredChartWidth = with(density) {
        configuration.screenWidthDp.dp
    }

    val contentModifier = if (smoothedDataPoints.size > 1) {
        Modifier.width(totalWidth.dp.coerceAtLeast(minRequiredChartWidth))
    } else {
        Modifier.fillMaxWidth()
    }

    // --- MODIFICATION START ---
    // Define the fixed range for your data (0 to 4)
    val dataMin = 0f
    val dataMax = 4f
    val dataRange = dataMax - dataMin

    // This function will map your data points (0-4) to the canvas height (0 to size.height)
    // A value of 0 should be at the bottom (size.height)
    // A value of 4 should be at the top (0f)
    fun mapValueToY(value: Float, canvasHeight: Float): Float {
        // First, normalize the value within the 0-4 range to 0-1
        val normalizedValue = (value - dataMin) / dataRange
        // Then, invert it (so 0 is high, 1 is low for Y-axis) and scale to canvas height
        return canvasHeight * (1f - normalizedValue)
    }
    // --- MODIFICATION END ---

    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    val dates = labels.mapNotNull { dateString: String -> runCatching { LocalDate.parse(dateString) }.getOrNull() }

    val weeklyTickIndices = dates.mapIndexedNotNull { index, date ->
        if (date.dayOfWeek == DayOfWeek.MONDAY) index else null
    }

    val monthlyLabelIndices = dates.mapIndexedNotNull { index, date ->
        if (date.dayOfMonth == 1 || (index == 0 && dates.isNotEmpty())) index else null
    }

    val monthFormatter = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())

    Column(modifier = modifier.fillMaxWidth()) {
        Text(chartTitle, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(modifier = Modifier.height(8.dp))

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

                // Draw Y-axis grid lines and labels (now based on 0-4 range)
                val numYLabels = 5 // For 0, 1, 2, 3, 4
                for (i in 0 until numYLabels) {
                    val value = dataMin + (i.toFloat() / (numYLabels - 1)) * dataRange // Calculate value for this label
                    val y = mapValueToY(value, size.height) // Map value to Y position on canvas

                    // Draw grid line
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )

                    // Draw text label for Y-axis (e.g., "0", "1", "2", "3", "4")
                    drawContext.canvas.nativeCanvas.apply {
                        val textPaint = android.graphics.Paint().apply {
                            color = labelColor.toArgb()
                            textSize = 12.sp.toPx() // Adjust text size as needed
                            textAlign = android.graphics.Paint.Align.RIGHT
                        }
                        drawText(
                            value.toInt().toString(), // Display as integer
                            -8.dp.toPx(), // Offset slightly to the left of the chart area
                            y + textPaint.textSize / 3, // Adjust Y position for centering
                            textPaint
                        )
                    }
                }


                // --- Draw Smoothed Line ---
                if (smoothedDataPoints.size > 1) {
                    val path = Path()
                    val firstX = 0f
                    val firstY = mapValueToY(smoothedDataPoints[0], size.height) // Use mapValueToY
                    path.moveTo(firstX, firstY)

                    for (i in 0 until smoothedDataPoints.size - 1) {
                        val currentX = i * xStep
                        val currentY = mapValueToY(smoothedDataPoints[i], size.height) // Use mapValueToY

                        val nextX = (i + 1) * xStep
                        val nextY = mapValueToY(smoothedDataPoints[i + 1], size.height) // Use mapValueToY

                        val tension = 0.4f // Adjust tension for the curve's appearance

                        val controlX1 = currentX + (nextX - currentX) * tension
                        val controlY1 = currentY

                        val controlX2 = nextX - (nextX - currentX) * tension
                        val controlY2 = nextY

                        path.cubicTo(controlX1, controlY1, controlX2, controlY2, nextX, nextY)
                    }

                    drawPath(
                        path = path,
                        color = primaryColor,
                        style = Stroke(width = lineWidth)
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
                val labelText = rawText.replaceFirstChar { it.uppercaseChar() }

                val xPosition = (index * pointSpacing.value).dp

                if (labelText.isNotEmpty()) {
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Normal
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StatisticsScreen(openedDay: String) {
    val context = LocalContext.current
    val viewModel: StatisticsViewModel = viewModel(factory = StatisticsViewModelFactory(context, openedDay))
    val healthData = viewModel.healthData.value
    val selectedTimeframe = viewModel.selectedTimeframe.value
    val currentChartStartDate by viewModel.currentStartDate.collectAsState()
    val summarySentence by viewModel.summarySentence
    val metricDifferences by viewModel.metricDifferences

    // NEW: Collect correlation states
    val correlations by viewModel.correlations.collectAsState()
    val isCalculatingCorrelations by viewModel.isCalculatingCorrelations.collectAsState()
    val coroutineScope = rememberCoroutineScope()


    LaunchedEffect(openedDay) {
        viewModel.updateOpenedDay(openedDay)
    }

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

    val allMetricsWithGoodBadFlagUI = listOf(
        MetricInfo("Malaise", { it.malaise }, isHigherBetter = false),
        MetricInfo("Stress Level", { it.stressLevel }, isHigherBetter = false),
        MetricInfo("Sleep Quality", { it.sleepQuality }, isHigherBetter = true),
        MetricInfo("Illness Impact", { it.illnessImpact }, isHigherBetter = false),
        MetricInfo("Depression", { it.depression }, isHigherBetter = false),
        MetricInfo("Hopelessness", { it.hopelessness }, isHigherBetter = false),
        MetricInfo("Sore Throat", { it.soreThroat }, isHigherBetter = false),
        MetricInfo("Sleep Length", { it.sleepLength }, isHigherBetter = true),
        MetricInfo("Lymphadenopathy", { it.lymphadenopathy }, isHigherBetter = false),
        MetricInfo("Exercise Level", { it.exerciseLevel }, isHigherBetter = true),
        MetricInfo("Sleep Readiness", { it.sleepReadiness }, isHigherBetter = true)
    )

    val selectedMetrics = remember { mutableStateListOf<String>() }
    var showAllMetrics by remember { mutableStateOf(false) }
    var smoothingWindowSize by remember { mutableStateOf(5) }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Health Statistics") },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CalendarToday,
                            contentDescription = "Interval Start Date",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "$currentChartStartDate to $openedDay",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
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
            Spacer(modifier = Modifier.height(8.dp))

            if (healthData.isEmpty()) {
                EmptyDataInfo()
            } else {
                HealthMetricsSummary(
                    healthData = healthData,
                    summarySentence = summarySentence,
                    metricDifferences = metricDifferences,
                    allMetricsWithGoodBadFlag = allMetricsWithGoodBadFlagUI
                )
                Spacer(modifier = Modifier.height(12.dp)) // Spacer after Summary Statistics

                // MOVED: Correlation Section is now right below Summary Statistics
                CorrelationSection(
                    correlations = correlations,
                    isCalculatingCorrelations = isCalculatingCorrelations,
                    onCalculateCorrelationsClick = { coroutineScope.launch { viewModel.calculateCorrelations() } },
                    onUpdatePreference = { id, delta -> viewModel.updateCorrelationPreference(id, delta) }
                )
                Spacer(modifier = Modifier.height(12.dp)) // Spacer after Correlation Section
            }

            Column(modifier = Modifier.animateContentSize()) { // This is "Select Metrics to Display"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAllMetrics = !showAllMetrics }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("📈  Select Metrics to Display", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { showAllMetrics = !showAllMetrics }) {
                        Icon(
                            imageVector = if (showAllMetrics) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (showAllMetrics) "Collapse metrics"
                            else "Expand metrics"
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))

                if (showAllMetrics) {
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
                    if (selectedMetrics.isEmpty() && healthData.isNotEmpty()) {
                        FlowRow {
                            FilterChip(
                                selected = false,
                                onClick = {
                                    selectedMetrics.add("Malaise")
                                    showAllMetrics = true
                                },
                                label = { Text("Malaise") },
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                            FilterChip(
                                selected = false,
                                onClick = { showAllMetrics = true },
                                label = { Text("...") },
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        val displayedMetrics = if (selectedMetrics.isNotEmpty()) {
                            selectedMetrics.take(3)
                        } else {
                            allMetrics.map { it.first }.take(3)
                        }

                        FlowRow {
                            displayedMetrics.forEach { label ->
                                val metric = allMetrics.firstOrNull { it.first == label }
                                metric?.let {
                                    FilterChip(
                                        selected = selectedMetrics.contains(label),
                                        onClick = {
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
                            if (allMetrics.size > displayedMetrics.size || (selectedMetrics.isNotEmpty() && selectedMetrics.size > 3)) {
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
            }
            Spacer(modifier = Modifier.height(24.dp)) // Spacer before the charts

            if (healthData.isNotEmpty() && selectedMetrics.isNotEmpty()) {
                selectedMetrics.forEach { metricLabel ->
                    val extractor = allMetrics.firstOrNull { it.first == metricLabel }?.second
                    extractor?.let {
                        HealthLineChart(
                            chartTitle = metricLabel,
                            dataPoints = healthData.map(it),
                            labels = healthData.map { it.currentDate },
                            smoothingWindowSize = smoothingWindowSize
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            // Removed old Spacer and CorrelationSection from here
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthMetricsSummary(
    healthData: List<HealthData>,
    summarySentence: String,
    metricDifferences: Map<String, Float>,
    allMetricsWithGoodBadFlag: List<MetricInfo>
) {
    var expanded by remember { mutableStateOf(false) }

    fun List<Float>.avgOrZero() = average().toFloat().takeIf { !it.isNaN() } ?: 0f

    val stats = listOf(
        "Malaise" to healthData.map { it.malaise }.avgOrZero(),
        "Stress Level" to healthData.map { it.stressLevel }.avgOrZero(),
        "Sleep Quality" to healthData.map { it.sleepQuality }.avgOrZero(),
        "Illness Impact" to healthData.map { it.illnessImpact }.avgOrZero(),
        "Depression" to healthData.map { it.depression }.avgOrZero(),
        "Hopelessness" to healthData.map { it.hopelessness }.avgOrZero(),
        "Sore Throat" to healthData.map { it.soreThroat }.avgOrZero(),
        "Sleep Length" to healthData.map { it.sleepLength }.avgOrZero(),
        "Lymphadenopathy" to healthData.map { it.lymphadenopathy }.avgOrZero(),
        "Exercise Level" to healthData.map { it.exerciseLevel }.avgOrZero(),
        "Sleep Readiness" to healthData.map { it.sleepReadiness }.avgOrZero(),
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(top = 6.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
                .animateContentSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("📌  Summary Statistics", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse summary" else "Expand summary"
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (expanded) {
                stats.forEach { (label, value) ->
                    val change = metricDifferences[label]
                    val metricInfo = allMetricsWithGoodBadFlag.firstOrNull { it.name == label }

                    StatisticRow(
                        label = label,
                        value = String.format("%.1f", value),
                        change = change,
                        isHigherBetter = metricInfo?.isHigherBetter
                    )
                }
            } else {
                Text(
                    text = summarySentence,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun StatisticRow(label: String, value: String, change: Float? = null, isHigherBetter: Boolean? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            change?.let { diff ->
                val changeColor = when {
                    isHigherBetter == true && diff > 0 -> Color.Green // Positive change is good
                    isHigherBetter == false && diff < 0 -> Color.Green // Negative change is good
                    isHigherBetter == true && diff < 0 -> Color.Red // Negative change is bad
                    isHigherBetter == false && diff > 0 -> Color.Red // Positive change is bad
                    else -> MaterialTheme.colorScheme.onSurfaceVariant // No significant change, or zero, or unknown
                }

                val formattedDiff = String.format(Locale.getDefault(), "%+.1f", diff)

                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "($formattedDiff)",
                    style = MaterialTheme.typography.labelSmall,
                    color = changeColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorrelationSection(
    correlations: List<Correlation>,
    isCalculatingCorrelations: Boolean,
    onCalculateCorrelationsClick: () -> Unit,
    onUpdatePreference: (Long, Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(top = 6.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
                .animateContentSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("🔗  Correlations", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse correlations" else "Expand correlations"
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (expanded) {
                OutlinedButton(
                    onClick = onCalculateCorrelationsClick,
                    enabled = !isCalculatingCorrelations,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isCalculatingCorrelations) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Calculating...")
                    } else {
                        Text("Calculate Correlations")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (correlations.isEmpty() && !isCalculatingCorrelations) {
                    Text(
                        "No correlations found. Click 'Calculate Correlations' to analyze your data.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                } else {
                    correlations.forEachIndexed { index, correlation ->
                        CorrelationItem(correlation, onUpdatePreference)
                        if (index < correlations.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            } else {
                Text(
                    text = if (correlations.isNotEmpty()) "Tap to view ${correlations.size} discovered relationships" else "Tap to calculate health correlations",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun CorrelationItem(
    correlation: Correlation,
    onUpdatePreference: (Long, Int) -> Unit
) {
    // Define the range for the Preference Bar for visual representation
    val MAX_PREFERENCE_BAR_VALUE = 5
    val MIN_PREFERENCE_BAR_VALUE = -5
    val PREFERENCE_BAR_RANGE = (MAX_PREFERENCE_BAR_VALUE - MIN_PREFERENCE_BAR_VALUE).toFloat()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Main correlation description: "Symptom A increases/decreases with Symptom B"
            Text(
                text = "${correlation.getDisplayNameA()} ${
                    when {
                        correlation.confidence > 0 -> "increases with"
                        correlation.confidence < 0 -> "decreases with"
                        else -> "is unrelated to"
                    }
                } ${correlation.getDisplayNameB()}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(4.dp)) // Space between main text and bars

            // --- Strength Bar ---
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Strength:", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(70.dp))
                LinearProgressIndicator(
                    progress = abs(correlation.confidence),
                    modifier = Modifier.weight(1f),
                    color = if (correlation.confidence > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Text(" ${String.format("%.2f", correlation.confidence)}", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.height(2.dp))

            // --- Preference Bar ---
            val normalizedPreference = (correlation.preferenceScore.toFloat() - MIN_PREFERENCE_BAR_VALUE) / PREFERENCE_BAR_RANGE
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Preference:", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(70.dp))
                LinearProgressIndicator(
                    progress = normalizedPreference.coerceIn(0f, 1f),
                    modifier = Modifier.weight(1f),
                    color = when {
                        correlation.preferenceScore > 0 -> MaterialTheme.colorScheme.tertiary
                        correlation.preferenceScore < 0 -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    }
                )
                Text(" ${correlation.preferenceScore}", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.height(2.dp))

            // --- Delay and Average Information (on one line, only if applicable) ---
            val delayText = if (correlation.lag > 0) "Delay: ${correlation.lag} day${if (correlation.lag != 1) "s" else ""}" else ""
            val averageText = correlation.getAverageInfo() // This now returns empty if no averaging

            val combinedInfo = when {
                delayText.isNotEmpty() && averageText.isNotEmpty() -> "$delayText | $averageText"
                delayText.isNotEmpty() -> delayText
                averageText.isNotEmpty() -> averageText
                else -> "" // No delay or averaging information to display
            }

            if (combinedInfo.isNotEmpty()) {
                Text(
                    text = combinedInfo,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // --- Preference Buttons ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onUpdatePreference(correlation.id, 1) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Increase preference")
            }
            IconButton(
                onClick = { onUpdatePreference(correlation.id, -1) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Decrease preference")
            }
        }
    }
}
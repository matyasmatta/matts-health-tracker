package com.example.mattshealthtracker

import android.content.Context
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mattshealthtracker.HealthData
import com.example.mattshealthtracker.HealthDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale // <-- Ensure this is imported for DateTimeFormatter.ofPattern

// Enum to define available timeframes
enum class Timeframe(val label: String) {
    TWO_WEEKS("2 Weeks"), // <-- ADD THIS LINE
    ONE_MONTH("1 Month"),
    THREE_MONTHS("3 Months"),
    SIX_MONTHS("6 Months")
}

class StatisticsViewModel(applicationContext: Context, private val initialOpenedDay: String) : ViewModel() {
    private val dbHelper = HealthDatabaseHelper(applicationContext)

    private val _currentOpenedDay = MutableStateFlow(initialOpenedDay)
    val currentOpenedDay: StateFlow<String> = _currentOpenedDay.asStateFlow()

    private val _currentStartDate = MutableStateFlow(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
    val currentStartDate: StateFlow<String> = _currentStartDate.asStateFlow()

    private val _healthData = mutableStateOf<List<HealthData>>(emptyList())
    val healthData: State<List<HealthData>> = _healthData

    private val _previousIntervalHealthData = mutableStateOf<List<HealthData>>(emptyList())

    private val _summarySentence = mutableStateOf("")
    val summarySentence: State<String> = _summarySentence

    // --- NEW: State for metric differences ---
    private val _metricDifferences = mutableStateOf<Map<String, Float>>(emptyMap())
    val metricDifferences: State<Map<String, Float>> = _metricDifferences
    // --- END NEW ADDITION ---

    private val _selectedTimeframe = mutableStateOf(Timeframe.ONE_MONTH)
    val selectedTimeframe: State<Timeframe> = _selectedTimeframe

    private var referenceDate: LocalDate = LocalDate.parse(initialOpenedDay)

    // Define all metrics and their extractors, now also with a `isHigherBetter` flag
    // This makes the logic for coloring the change clearer
    private val allMetricsWithGoodBadFlag = listOf(
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

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _currentOpenedDay.collectLatest { dayString ->
                try {
                    referenceDate = LocalDate.parse(dayString)
                    fetchHealthDataForSelectedTimeframe()
                } catch (e: Exception) {
                    Log.e("StatisticsViewModel", "Error parsing openedDay: $dayString", e)
                }
            }
        }
    }

    fun updateTimeframe(timeframe: Timeframe) {
        _selectedTimeframe.value = timeframe
        fetchHealthDataForSelectedTimeframe()
    }

    fun updateOpenedDay(newOpenedDay: String) {
        if (_currentOpenedDay.value != newOpenedDay) {
            _currentOpenedDay.value = newOpenedDay
        }
    }

    private fun fetchHealthDataForSelectedTimeframe() {
        viewModelScope.launch(Dispatchers.IO) {
            val endDate = referenceDate
            val startDate = when (_selectedTimeframe.value) {
                Timeframe.TWO_WEEKS -> endDate.minusWeeks(2).plusDays(1) // <-- ADD THIS CASE
                Timeframe.ONE_MONTH -> endDate.minusMonths(1).plusDays(1)
                Timeframe.THREE_MONTHS -> endDate.minusMonths(3).plusDays(1)
                Timeframe.SIX_MONTHS -> endDate.minusMonths(6).plusDays(1)
            }

            val prevEndDate = startDate.minusDays(1)
            val prevStartDate = when (_selectedTimeframe.value) {
                Timeframe.TWO_WEEKS -> prevEndDate.minusWeeks(2).plusDays(1) // <-- ADD THIS CASE
                Timeframe.ONE_MONTH -> prevEndDate.minusMonths(1).plusDays(1)
                Timeframe.THREE_MONTHS -> prevEndDate.minusMonths(3).plusDays(1)
                Timeframe.SIX_MONTHS -> prevEndDate.minusMonths(6).plusDays(1)
            }

            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

            _currentStartDate.value = startDate.format(formatter)

            val currentIntervalData = dbHelper.fetchDataInDateRange(startDate.format(formatter), endDate.format(formatter))
            _healthData.value = currentIntervalData

            val previousIntervalData = dbHelper.fetchDataInDateRange(prevStartDate.format(formatter), prevEndDate.format(formatter))
            _previousIntervalHealthData.value = previousIntervalData

            val (summary, diffs) = generateSummaryAndDifferences(currentIntervalData, previousIntervalData)
            _summarySentence.value = summary
            _metricDifferences.value = diffs
        }
    }

    private fun List<HealthData>.avgOrZero(extractor: (HealthData) -> Float): Float {
        return this.map(extractor).average().toFloat().takeIf { !it.isNaN() } ?: 0f
    }

    // --- MODIFIED FUNCTION ---
    private fun generateSummaryAndDifferences(
        currentData: List<HealthData>,
        previousData: List<HealthData>
    ): Pair<String, Map<String, Float>> {
        if (currentData.isEmpty() || previousData.isEmpty()) {
            _metricDifferences.value = emptyMap() // Clear differences if not enough data
            return "Not enough data to compare with the previous period." to emptyMap()
        }

        val threshold = 0.3f
        val differences = mutableListOf<Pair<String, Float>>()
        val diffMap = mutableMapOf<String, Float>() // To store differences for direct access

        for (metricInfo in allMetricsWithGoodBadFlag) {
            val currentAvg = currentData.avgOrZero(metricInfo.extractor)
            val previousAvg = previousData.avgOrZero(metricInfo.extractor)
            val diff = currentAvg - previousAvg
            differences.add(metricInfo.name to diff)
            diffMap[metricInfo.name] = diff
        }

        _metricDifferences.value = diffMap // Update the state with the calculated differences

        val allStable = differences.all { Math.abs(it.second) <= threshold }

        var summary: String
        if (allStable) {
            summary = "You're stable! 😊"
        } else {
            val mostTrendingMetric = differences.maxByOrNull { Math.abs(it.second) }

            mostTrendingMetric?.let { (metricName, diff) ->
                val metricInfo = allMetricsWithGoodBadFlag.first { it.name == metricName }
                val trend = when {
                    diff > 0 && metricInfo.isHigherBetter -> "increased \uD83D\uDE4F" // e.g., Sleep Length increased
                    diff < 0 && !metricInfo.isHigherBetter -> "improved \uD83D\uDE4F" // e.g., Malaise decreased
                    diff > 0 && !metricInfo.isHigherBetter -> "worsened \uD83E\uDEE4" // e.g., Stress Level increased
                    diff < 0 && metricInfo.isHigherBetter -> "decreased \uD83E\uDEE4" // e.g., Sleep Quality decreased
                    else -> "changed" // Fallback, though should be covered
                }
                summary = "$metricName has $trend"
            } ?: run {
                summary = "Could not determine trend."
            }
        }
        return summary to diffMap
    }
}

// --- NEW DATA CLASS FOR METRIC INFO ---
data class MetricInfo(
    val name: String,
    val extractor: (HealthData) -> Float,
    val isHigherBetter: Boolean // Flag to determine if higher value is better or worse
)

// Factory for ViewModel to pass context safely
class StatisticsViewModelFactory(private val context: Context, private val openedDay: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StatisticsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StatisticsViewModel(context.applicationContext, openedDay) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
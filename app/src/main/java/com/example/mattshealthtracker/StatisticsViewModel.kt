package com.example.mattshealthtracker

import android.content.Context
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

// Enum to define available timeframes
enum class Timeframe(val label: String) {
    ONE_WEEK("1 Week"),
    TWO_WEEKS("2 Weeks"),
    ONE_MONTH("1 Month"),
    THREE_MONTHS("3 Months"),
    SIX_MONTHS("6 Months"),
    ONE_YEAR("1 Year")
}

class StatisticsViewModel(applicationContext: Context, private val initialOpenedDay: String) : ViewModel() {
    private val healthDatabaseHelper = HealthDatabaseHelper(applicationContext)
    private val correlationRepository = CorrelationRepository(applicationContext)

    private val _currentOpenedDay = MutableStateFlow(initialOpenedDay)
    val currentOpenedDay: StateFlow<String> = _currentOpenedDay.asStateFlow()

    private val _currentStartDate = MutableStateFlow(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
    val currentStartDate: StateFlow<String> = _currentStartDate.asStateFlow()

    private val _healthData = mutableStateOf<List<HealthData>>(emptyList())
    val healthData: State<List<HealthData>> = _healthData

    private val _previousIntervalHealthData = mutableStateOf<List<HealthData>>(emptyList())

    private val _summarySentence = mutableStateOf("")
    val summarySentence: State<String> = _summarySentence

    private val _metricDifferences = mutableStateOf<Map<String, Float>>(emptyMap())
    val metricDifferences: State<Map<String, Float>> = _metricDifferences

    private val _selectedTimeframe = mutableStateOf(Timeframe.ONE_MONTH)
    val selectedTimeframe: State<Timeframe> = _selectedTimeframe

    private var referenceDate: LocalDate = LocalDate.parse(initialOpenedDay)

    // Define all metrics and their extractors, now also with a `isHigherBetter` flag
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

    // State for Correlations
    private val _correlations = MutableStateFlow<List<Correlation>>(emptyList())
    val correlations: StateFlow<List<Correlation>> = _correlations.asStateFlow()

    private val _isCalculatingCorrelations = MutableStateFlow(false)
    val isCalculatingCorrelations: StateFlow<Boolean> = _isCalculatingCorrelations.asStateFlow()

    // State for the one-line correlation overview sentence
    private val _correlationOverviewSentence = MutableStateFlow("Load more data to discover correlations! 📈")
    val correlationOverviewSentence: StateFlow<String> = _correlationOverviewSentence.asStateFlow()


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
        loadCorrelations() // Load existing correlations on ViewModel initialization
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
                Timeframe.ONE_WEEK -> endDate.minusWeeks(1).plusDays(1)
                Timeframe.TWO_WEEKS -> endDate.minusWeeks(2).plusDays(1)
                Timeframe.ONE_MONTH -> endDate.minusMonths(1).plusDays(1)
                Timeframe.THREE_MONTHS -> endDate.minusMonths(3).plusDays(1)
                Timeframe.SIX_MONTHS -> endDate.minusMonths(6).plusDays(1)
                Timeframe.ONE_YEAR -> endDate.minusYears(1).plusDays(1)
            }

            val prevEndDate = startDate.minusDays(1)
            val prevStartDate = when (_selectedTimeframe.value) {
                Timeframe.ONE_WEEK -> prevEndDate.minusWeeks(1).plusDays(1)
                Timeframe.TWO_WEEKS -> prevEndDate.minusWeeks(2).plusDays(1)
                Timeframe.ONE_MONTH -> prevEndDate.minusMonths(1).plusDays(1)
                Timeframe.THREE_MONTHS -> prevEndDate.minusMonths(3).plusDays(1)
                Timeframe.SIX_MONTHS -> prevEndDate.minusMonths(6).plusDays(1)
                Timeframe.ONE_YEAR -> prevEndDate.minusYears(1).plusDays(1)
            }

            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

            _currentStartDate.value = startDate.format(formatter)

            val currentIntervalData = healthDatabaseHelper.fetchDataInDateRange(startDate.format(formatter), endDate.format(formatter))
            _healthData.value = currentIntervalData

            val previousIntervalData = healthDatabaseHelper.fetchDataInDateRange(prevStartDate.format(formatter), prevEndDate.format(formatter))
            _previousIntervalHealthData.value = previousIntervalData

            val (summary, diffs) = generateSummaryAndDifferences(currentIntervalData, previousIntervalData)
            _summarySentence.value = summary
            _metricDifferences.value = diffs
        }
    }

    private fun List<HealthData>.avgOrZero(extractor: (HealthData) -> Float): Float {
        return this.map(extractor).average().toFloat().takeIf { !it.isNaN() } ?: 0f
    }

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

        val allStable = differences.all { abs(it.second) <= threshold }

        var summary: String
        if (allStable) {
            summary = "You're stable! 😊"
        } else {
            val mostTrendingMetric = differences.maxByOrNull { abs(it.second) }

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

    // Function to load existing correlations
    fun loadCorrelations() {
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = correlationRepository.getCorrelationsAboveRating()
            _correlations.value = loaded // _correlations now holds all relevant correlations
            Log.d("StatisticsViewModel", "Loaded ${loaded.size} correlations.")

            // Initial overview sentence selection (from top 3)
            randomizeCorrelationOverview(fromTopN = 3) // Use the new function for initial pick
        }
    }

    // Function to pick a random correlation from top N and update the overview sentence
    fun randomizeCorrelationOverview(fromTopN: Int = 10) { // Default to top 10 for explicit randomize button
        viewModelScope.launch(Dispatchers.IO) {
            val availableCorrelations = _correlations.value

            if (availableCorrelations.isNotEmpty()) {
                val relevantCorrelations = availableCorrelations.take(fromTopN) // Take top N for randomization
                val randomCorrelation = relevantCorrelations.randomOrNull()

                if (randomCorrelation != null) {
                    _correlationOverviewSentence.value = generateCorrelationOverviewSentence(randomCorrelation)
                } else {
                    _correlationOverviewSentence.value = "Hmm, couldn't find a new insight. Maybe log more data! 🧐"
                }
            } else {
                _correlationOverviewSentence.value = "No correlations found yet. Log more data to find insights! 📊"
            }
        }
    }

    // Helper function to generate a nicely worded, fun, emoji-rich correlation overview sentence
    private fun generateCorrelationOverviewSentence(correlation: Correlation): String {
        val symptomA = correlation.getDisplayNameA()
        val symptomB = correlation.getDisplayNameB()
        val confidence = correlation.confidence
        val lag = correlation.lag
        val absConfidence = abs(confidence)

        // Changed to adverbs to fit "seems to [adverb] [verb]" structure
        val strengthAdverb = when {
            absConfidence >= 0.7f -> "very strongly"
            absConfidence >= 0.5f -> "clearly"
            absConfidence >= 0.3f -> "noticeably"
            else -> "slightly" // Unlikely to be picked if rating > 0.5 but good for robustness
        }

        val directionPhrase = when {
            confidence > 0 -> "boost" // Changed to base verb form
            confidence < 0 -> "put a damper on" // Changed to base verb form
            else -> "be surprisingly unrelated to" // Adjusted for new sentence structure
        }

        val lagPhrase = if (lag > 0) " about $lag day${if (lag != 1) "s" else ""} later" else ""

        val sentenceBuilder = StringBuilder()
        // New sentence structure: "Symptom A seems to [strength] [direction] Symptom B [lag]!"
        sentenceBuilder.append("${symptomA} seems to ${strengthAdverb} ${directionPhrase} ${symptomB}${lagPhrase}!")

        // Add emojis based on keywords in symptom names
        val emojis = mutableSetOf<String>() // Use a set to avoid duplicate emojis

        // Emojis for specific symptoms/categories (ordered by general relevance/preference)
        if (symptomA.contains("Sleep", ignoreCase = true) || symptomB.contains("Sleep", ignoreCase = true) ||
            (symptomA.contains("Quality", ignoreCase = true) && (symptomA.contains("Sleep", ignoreCase = true) || symptomB.contains("Sleep", ignoreCase = true))) ||
            (symptomB.contains("Quality", ignoreCase = true) && (symptomA.contains("Sleep", ignoreCase = true) || symptomB.contains("Sleep", ignoreCase = true)))
        ) {
            emojis.add("😴")
        }
        if (symptomA.contains("Exercise", ignoreCase = true) || symptomB.contains("Exercise", ignoreCase = true)) {
            emojis.add("💪")
        }
        if (symptomA.contains("Stress", ignoreCase = true) || symptomB.contains("Stress", ignoreCase = true)) {
            emojis.add("😬")
        }
        if (symptomA.contains("Illness", ignoreCase = true) || symptomB.contains("Illness", ignoreCase = true) ||
            symptomA.contains("Malaise", ignoreCase = true) || symptomB.contains("Malaise", ignoreCase = true) ||
            symptomA.contains("Throat", ignoreCase = true) || symptomB.contains("Throat", ignoreCase = true) ||
            symptomA.contains("Lymphadenopathy", ignoreCase = true) || symptomB.contains("Lymphadenopathy", ignoreCase = true)) {
            emojis.add("🤒")
        }
        if (symptomA.contains("Depression", ignoreCase = true) || symptomB.contains("Depression", ignoreCase = true) ||
            symptomA.contains("Hopelessness", ignoreCase = true) || symptomB.contains("Hopelessness", ignoreCase = true)) {
            emojis.add("😔")
        }

        // Emojis for correlation direction (these are prioritized if general ones aren't enough)
        if (confidence > 0) {
            emojis.add("✨") // Positive correlation
        } else if (confidence < 0) {
            emojis.add("📉") // Negative correlation
        }

        // Append collected emojis, taking at most 2
        if (emojis.isNotEmpty()) {
            sentenceBuilder.append(" ${emojis.take(2).joinToString(" ")}") // Limit to at most 2 emojis
        }

        return sentenceBuilder.toString()
    }


    // Function to trigger correlation calculation
    fun calculateCorrelations() {
        viewModelScope.launch(Dispatchers.IO) {
            _isCalculatingCorrelations.value = true
            Log.d("StatisticsViewModel", "Starting correlation calculation...")
            // Fetch all available health data, as correlation might need a longer history
            val historicalData = healthDatabaseHelper.fetchAllData()

            if (historicalData.size < 15) { // Minimum data points for meaningful correlation (adjust as needed)
                Log.d("StatisticsViewModel", "Not enough data for correlation calculation (min 15 days needed). Found: ${historicalData.size}")
                _isCalculatingCorrelations.value = false
                _correlations.value = emptyList() // Clear correlations if not enough data
                _correlationOverviewSentence.value = "Not enough data (min 15 days) to calculate correlations. 🤷‍♀️"
                return@launch // Exit coroutine
            }

            try {
                // This call will now use CorrelationRepository to calculate and store correlations
                val newlyDetected = correlationRepository.calculateAndStoreAllCorrelations(historicalData)
                Log.d("StatisticsViewModel", "Calculated and stored ${newlyDetected.size} new/updated correlations.")
                loadCorrelations() // Reload all correlations after calculation to update UI
            } catch (e: Exception) {
                Log.e("StatisticsViewModel", "Error during correlation calculation: ${e.message}", e)
                _correlationOverviewSentence.value = "Oops! Something went wrong calculating correlations. 🐞"
            } finally {
                _isCalculatingCorrelations.value = false
            }
        }
    }

    // Function to update a correlation's preference score
    fun updateCorrelationPreference(correlationId: Long, delta: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            correlationRepository.updatePreference(correlationId, delta)
            loadCorrelations() // Refresh list after update to show new scores
        }
    }

    override fun onCleared() {
        super.onCleared()
        healthDatabaseHelper.close()
        correlationRepository.close()
    }
}

// Data class for MetricInfo
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
package com.example.mattshealthtracker

import android.content.Context
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
    ONE_MONTH("1 Month"),
    THREE_MONTHS("3 Months"),
    SIX_MONTHS("6 Months")
}

class StatisticsViewModel(applicationContext: Context, private val initialOpenedDay: String) : ViewModel() {
    private val dbHelper = HealthDatabaseHelper(applicationContext)

    private val _currentOpenedDay = MutableStateFlow(initialOpenedDay)
    val currentOpenedDay: StateFlow<String> = _currentOpenedDay.asStateFlow()

    // --- NEW ADDITION ---
    private val _currentStartDate = MutableStateFlow(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
    val currentStartDate: StateFlow<String> = _currentStartDate.asStateFlow()
    // --- END NEW ADDITION ---

    private val _healthData = mutableStateOf<List<HealthData>>(emptyList())
    val healthData: State<List<HealthData>> = _healthData

    private val _selectedTimeframe = mutableStateOf(Timeframe.ONE_MONTH)
    val selectedTimeframe: State<Timeframe> = _selectedTimeframe

    private var referenceDate: LocalDate = LocalDate.parse(initialOpenedDay)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _currentOpenedDay.collectLatest { dayString ->
                try {
                    referenceDate = LocalDate.parse(dayString)
                    fetchHealthDataForSelectedTimeframe()
                } catch (e: Exception) {
                    // Log.e("StatisticsViewModel", "Error parsing openedDay: $dayString", e)
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
                Timeframe.ONE_MONTH -> endDate.minusMonths(1).plusDays(1)
                Timeframe.THREE_MONTHS -> endDate.minusMonths(3).plusDays(1)
                Timeframe.SIX_MONTHS -> endDate.minusMonths(6).plusDays(1)
            }

            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            _currentStartDate.value = startDate.format(formatter) // <-- UPDATE THE NEW STATEFLOW HERE

            val data = dbHelper.fetchDataInDateRange(startDate.format(formatter), endDate.format(formatter))
            _healthData.value = data
        }
    }
}

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
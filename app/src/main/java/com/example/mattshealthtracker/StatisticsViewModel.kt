package com.example.mattshealthtracker

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Enum to define available timeframes
enum class Timeframe(val label: String) {
    ONE_MONTH("1 Month"),
    THREE_MONTHS("3 Months"),
    SIX_MONTHS("6 Months")
}

class StatisticsViewModel(applicationContext: Context) : ViewModel() {
    private val dbHelper = HealthDatabaseHelper(applicationContext)

    // State to hold the fetched health data
    private val _healthData = mutableStateOf<List<HealthData>>(emptyList())
    val healthData: State<List<HealthData>> = _healthData

    // State to hold the selected timeframe
    private val _selectedTimeframe = mutableStateOf(Timeframe.ONE_MONTH)
    val selectedTimeframe: State<Timeframe> = _selectedTimeframe

    init {
        // Fetch initial data when the ViewModel is created
        fetchHealthDataForSelectedTimeframe()
    }

    // Function to update the selected timeframe and trigger data fetch
    fun updateTimeframe(timeframe: Timeframe) {
        _selectedTimeframe.value = timeframe
        fetchHealthDataForSelectedTimeframe()
    }

    // Fetches health data based on the currently selected timeframe
    private fun fetchHealthDataForSelectedTimeframe() {
        viewModelScope.launch(Dispatchers.IO) { // Perform database operation on a background thread
            val endDate = LocalDate.now()
            val startDate = when (_selectedTimeframe.value) {
                Timeframe.ONE_MONTH -> endDate.minusMonths(1)
                Timeframe.THREE_MONTHS -> endDate.minusMonths(3)
                Timeframe.SIX_MONTHS -> endDate.minusMonths(6)
            }

            // Format dates to "YYYY-MM-DD" for database query
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val data = dbHelper.fetchDataInDateRange(startDate.format(formatter), endDate.format(formatter))
            _healthData.value = data
        }
    }
}

// Factory for ViewModel to pass context safely
class StatisticsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StatisticsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StatisticsViewModel(context.applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
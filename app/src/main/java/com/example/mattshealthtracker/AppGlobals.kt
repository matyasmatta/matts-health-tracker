// AppGlobals.kt
package com.example.mattshealthtracker

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.*
import java.time.LocalDate
import java.time.ZoneId

// Enum for energy units
enum class EnergyUnit { KCAL, KJ }

object AppGlobals {
    // Current day formatted as String (yyyy-MM-dd)
    val currentDay: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    // Initialized openedDay to currentDay
    var openedDay: String = currentDay

    // Add a mutable state for energy unit preference, default to KCAL
    var energyUnitPreference by mutableStateOf(EnergyUnit.KCAL)

    // Helper function to convert string date to LocalDate
    fun getCurrentDayAsLocalDate(): LocalDate {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.parse(currentDay)?.let {
            it.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        } ?: LocalDate.now() // Default to today's date if parsing fails
    }

    // Helper function to convert string date to LocalDate
    fun getOpenedDayAsLocalDate(): LocalDate {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.parse(openedDay)?.let {
            it.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        } ?: LocalDate.now() // Default to today's date if parsing fails
    }

    // Helper function to convert LocalDate to string
    fun setOpenedDayFromLocalDate(localDate: LocalDate) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        openedDay = sdf.format(Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant()))
    }

    // Function to get a UTC timestamp formatted as "yyyyMMddHHmmss"
    fun getUtcTimestamp(): String {
        val sdf = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC") // Set timezone to UTC
        return sdf.format(Date())
    }
}
// FoodHelper.kt
package com.example.mattshealthtracker

import java.time.Duration
import kotlin.math.roundToInt // Import for rounding

object FoodHelper {

    private const val KCAL_TO_KJ_FACTOR = 4.184

    /**
     * Calculates the total energy expenditure as the sum of BMR and active calories burned,
     * rounded to whole kcal.
     * @param bmr The Basal Metabolic Rate in kcal/day.
     * @param activeCaloriesBurned The active calories burned in kcal/day.
     * @return The total energy in whole kcal as a Long, or null if either input is null.
     */
    fun calculateTotalEnergy(bmr: Double?, activeCaloriesBurned: Double?): Long? {
        if (bmr == null || activeCaloriesBurned == null) {
            return null
        }
        // Sum and then round to the nearest whole number (in kcal)
        return (bmr + activeCaloriesBurned).roundToInt().toLong()
    }

    /**
     * Converts a kilocalorie value to kilojoules.
     * @param kcal The energy value in kilocalories.
     * @return The energy value in kilojoules, rounded to the nearest whole number.
     */
    private fun convertKcalToKj(kcal: Long): Long {
        return (kcal * KCAL_TO_KJ_FACTOR).roundToInt().toLong()
    }

    /**
     * Converts a kilocalorie double value to kilojoules.
     * @param kcal The energy value in kilocalories (Double).
     * @return The energy value in kilojoules (Double), rounded to the nearest whole number.
     */
    private fun convertKcalToKj(kcal: Double): Long {
        return (kcal * KCAL_TO_KJ_FACTOR).roundToInt().toLong()
    }


    /**
     * Formats an energy value based on the user's preferred unit (kcal or kJ).
     * @param kcalValue The energy value in kilocalories (Double).
     * @return Formatted string with appropriate unit, or "N/A" if null.
     */
    fun formatEnergyValue(kcalValue: Double?): String {
        return kcalValue?.let {
            when (AppGlobals.energyUnitPreference) {
                EnergyUnit.KCAL -> "${it.roundToInt()} kcal"
                EnergyUnit.KJ -> "${convertKcalToKj(it)} kJ"
            }
        } ?: "N/A"
    }

    /**
     * Formats a total energy value (which is already a Long in kcal) based on the user's preferred unit.
     * @param kcalTotalValue The total energy value in kilocalories (Long).
     * @return Formatted string with appropriate unit, or "N/A" if null.
     */
    fun formatTotalEnergy(kcalTotalValue: Long?): String {
        return kcalTotalValue?.let {
            when (AppGlobals.energyUnitPreference) {
                EnergyUnit.KCAL -> "${it} kcal"
                EnergyUnit.KJ -> "${convertKcalToKj(it)} kJ"
            }
        } ?: "N/A"
    }

    /**
     * Categorizes the total energy expenditure into descriptive labels based on the given ranges.
     * The ranges are assumed to be in KCAL, as per your request, so we convert `totalEnergy` if needed.
     * @param totalEnergy The calculated total energy in whole kcal.
     * @return A string representing the energy level (e.g., "Low", "Medium", "High").
     */
    fun getEnergyStatus(totalEnergy: Long?): String {
        return when {
            totalEnergy == null -> "Not available"
            totalEnergy < 1800 -> "Low"
            totalEnergy >= 1800 && totalEnergy < 2100 -> "Lower"
            totalEnergy >= 2100 && totalEnergy < 2400 -> "Medium"
            totalEnergy >= 2400 && totalEnergy < 2700 -> "Higher"
            totalEnergy >= 2700 -> "High"
            else -> "Unknown"
        }
    }

    /**
     * Provides a descriptive phrase for the energy status, suitable for a default content in a card.
     * @param totalEnergy The calculated total energy in whole kcal.
     * @return A short phrase describing the energy level.
     */
    fun getEnergyStatusPhrase(totalEnergy: Long?): String {
        return when {
            totalEnergy == null -> "Your energy use is not yet available."
            totalEnergy < 1800 -> "Your energy use is low today."
            totalEnergy >= 1800 && totalEnergy < 2100 -> "Your energy use is on the lower side today."
            totalEnergy >= 2100 && totalEnergy < 2400 -> "Your energy use is medium today."
            totalEnergy >= 2400 && totalEnergy < 2700 -> "Your energy use is high today."
            totalEnergy >= 2700 -> "Your energy use is very high today!"
            else -> "Unable to determine energy use."
        }
    }

    /**
     * Formats a duration into a human-readable string (e.g., "7h 30m").
     * @param duration The Duration object.
     * @return Formatted string, or "N/A" if null.
     */
    fun formatDuration(duration: Duration?): String {
        return duration?.let {
            val hours = it.toHours()
            val minutes = it.toMinutes() % 60
            "${hours}h ${minutes}m"
        } ?: "N/A"
    }

    /**
     * Formats a Long value. (Generic, not for energy specifically)
     * @param value The Long value.
     * @return Formatted string, or "N/A" if null.
     */
    fun formatLong(value: Long?): String {
        return value?.let { it.toString() } ?: "N/A"
    }
}
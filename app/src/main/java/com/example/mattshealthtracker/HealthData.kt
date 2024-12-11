package com.example.mattshealthtracker

data class HealthData(
    val id: Int = 0,
    val timestamp: String,
    val malaise: Float,
    val soreThroat: Float,
    val lymphadenopathy: Float,
    val exerciseLevel: Float,
    val stressLevel: Float,
    val illnessImpact: Float,
    val depression: Float,
    val hopelessness: Float,
    val notes: String
)

data class MedicationData(
    val id: Int = 0,
    val timestamp: String,
    val doxyLactose: Boolean,
    val doxyMeal: Boolean,
    val doxyDose: Boolean,
    val doxyWater: Boolean,
    val prednisoneDose: Boolean,
    val prednisoneMeal: Boolean,
    val vitamins: Boolean,
    val probioticsMorning: Boolean,
    val probioticsEvening: Boolean,
    val sideEffects: String
)
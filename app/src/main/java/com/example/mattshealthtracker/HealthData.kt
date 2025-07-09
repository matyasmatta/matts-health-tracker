package com.example.mattshealthtracker

data class HealthData(
    val currentDate: String,
    val malaise: Float,
    val soreThroat: Float,
    val lymphadenopathy: Float,
    val exerciseLevel: Float,
    val stressLevel: Float,
    val illnessImpact: Float,
    val depression: Float,
    val hopelessness: Float,
    val sleepQuality: Float,
    val sleepLength: Float,
    val sleepReadiness: Float,
    val notes: String
)

data class MedicationData(
    val currentDate: String,
    val doxyLactose: Boolean,
    val doxyMeal: Boolean,
    val doxyDose: Boolean,
    val doxyWater: Boolean,
    val prednisoneDose: Boolean,
    val prednisoneMeal: Boolean,
    val vitamins: Boolean,
    val probioticsMorning: Boolean,
    val probioticsEvening: Boolean,
    val sideEffects: String,
    val doxyDosage: Int
)

data class ExerciseData(
    val currentDate: String,
    val pushups: Int,
    val posture: Int,
    // Add new fields for breathing exercises
    val relaxMinutes: Int = 0,
    val sleepMinutes: Int = 0,
    val napMinutes: Int = 0,
    val focusMinutes: Int = 0
)
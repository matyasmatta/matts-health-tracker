// In a file named 'Correlation.kt'
package com.example.mattshealthtracker

// Model for a single detected correlation
data class Correlation(
    val id: Long = 0, // Database ID, 0 for new entries
    // Granular details for Symptom A
    val baseSymptomA: String, // e.g., "Malaise"
    val windowSizeA: Int,    // 1 for raw data, >1 for moving window
    val calcTypeA: String,   // "raw", "avg" (as per our last decision)

    // Granular details for Symptom B
    val baseSymptomB: String, // e.g., "Stress Level"
    val windowSizeB: Int,    // 1 for raw data, >1 for moving window
    val calcTypeB: String,   // "raw", "avg" (as per our last decision)

    val lag: Int, // 0 for contemporaneous, positive for lagged (A -> B after `lag` days)
    val isPositiveCorrelation: Boolean, // True if both increase/decrease together, False if one increases and other decreases
    val confidence: Float, // Pearson R value (-1.0 to 1.0), representing strength and direction.
    val insightfulnessScore: Double, // Your calculated score. If directly from Pearson R, will be -1.0 to 1.0.
    var preferenceScore: Int = 0, // User's preference, starts at 0. Can be negative.
    val lastCalculatedDate: Long // Unix timestamp (milliseconds) of when this correlation was last detected/updated
) {
    // Helper to get display names for UI/logging, now only returning the base name
    fun getDisplayNameA(): String = baseSymptomA
    fun getDisplayNameB(): String = baseSymptomB

    // New helper to provide a concise string about averaging, returns empty if none
    fun getAverageInfo(): String {
        val avgA = if (calcTypeA == "avg" && windowSizeA > 1) "${windowSizeA}-day" else ""
        val avgB = if (calcTypeB == "avg" && windowSizeB > 1) "${windowSizeB}-day" else ""

        return when {
            avgA.isNotEmpty() && avgB.isNotEmpty() && avgA == avgB -> "Avg: $avgA"
            avgA.isNotEmpty() && avgB.isNotEmpty() && avgA != avgB -> "Avg: ${avgA} (A) and ${avgB} (B)"
            avgA.isNotEmpty() -> "Avg: $avgA (A)"
            avgB.isNotEmpty() -> "Avg: $avgB (B)"
            else -> "" // Return empty if no averaging is applied to either symptom
        }
    }

    // Helper to create a consistent unique key for identifying correlations based on base metrics
    fun getBaseMetricUniqueKey(): String {
        val (s1, s2) = if (baseSymptomA <= baseSymptomB) {
            baseSymptomA to baseSymptomB
        } else {
            baseSymptomB to baseSymptomA
        }
        return "$s1-$s2-$lag"
    }

    // A more user-friendly representation for logging/debugging
    override fun toString(): String {
        val correlationType = if (isPositiveCorrelation) "positive" else "negative"
        return "Correlation(id=$id, ${getDisplayNameA()} vs ${getDisplayNameB()}, lag=$lag, type=$correlationType, conf=${"%.2f".format(confidence)}, insight=${"%.2f".format(insightfulnessScore)}, pref=$preferenceScore, avgInfo=${getAverageInfo()})"
    }
}

data class AnalysisMetric(
    val baseMetricName: String, // e.g., "Stress Level"
    val windowSize: Int = 1,    // 1 for raw data, >1 for moving window
    val calculationType: String = "raw" // "raw", "avg" (removed "sum" generation for new correlations)
) {
    fun getDisplayName(): String {
        return when (calculationType) {
            "avg" -> "$baseMetricName (${windowSize}-day Avg)"
            // Keep "sum" case here for display if old 'sum' correlations are ever loaded from DB
            "sum" -> "$baseMetricName (${windowSize}-day Sum)"
            else -> baseMetricName
        }
    }
}
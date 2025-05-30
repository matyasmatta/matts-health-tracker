// In a file named 'CorrelationAnalytics.kt'
package com.example.mattshealthtracker // Updated package name

// Model for a single detected correlation
data class Correlation(
    val id: Long = 0, // Database ID, 0 for new entries
    val symptomA: String,
    val symptomB: String,
    val lag: Int, // 0 for contemporaneous, positive for lagged (A -> B after `lag` days)
    val isPositiveCorrelation: Boolean, // True if both increase/decrease together, False if one increases and other decreases
    val confidence: Double, // e.g., R^2 value (0.0 to 1.0)
    val insightfulnessScore: Double, // Your calculated score (e.g., 0.0 to 5.0)
    var preferenceScore: Int, // User's preference, starts at 0. Can be negative.
    val lastCalculatedDate: Long // Unix timestamp (milliseconds) of when this correlation was last detected/updated
) {
    // Helper to create a consistent unique key for identifying correlations
    fun getUniqueKey(): String {
        val (s1, s2) = if (symptomA <= symptomB) {
            symptomA to symptomB
        } else {
            symptomB to symptomA
        }
        return "$s1-$s2-$lag-$isPositiveCorrelation"
    }

    // A more user-friendly representation for logging/debugging
    override fun toString(): String {
        val correlationType = if (isPositiveCorrelation) "positive" else "negative"
        return "Correlation(id=$id, $symptomA-$symptomB, lag=$lag, type=$correlationType, conf=${"%.2f".format(confidence)}, insight=${"%.2f".format(insightfulnessScore)}, pref=$preferenceScore)"
    }
}
package com.example.mattshealthtracker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation
import java.time.LocalDate
import kotlin.math.abs

class CorrelationDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "correlations.db"
        const val DATABASE_VERSION = 18 // Increment version for schema change and new logic
        const val TABLE_CORRELATIONS = "correlations"
        const val COLUMN_ID = "_id"

        // New columns for granular metric details (for Symptom A)
        const val COLUMN_BASE_SYMPTOM_A = "base_symptom_a"
        const val COLUMN_WINDOW_SIZE_A = "window_size_a"
        const val COLUMN_CALC_TYPE_A = "calc_type_a"
        // New columns for granular metric details (for Symptom B)
        const val COLUMN_BASE_SYMPTOM_B = "base_symptom_b"
        const val COLUMN_WINDOW_SIZE_B = "window_size_b"
        const val COLUMN_CALC_TYPE_B = "calc_type_b"

        const val COLUMN_LAG = "lag"
        const val COLUMN_IS_POSITIVE_CORRELATION = "is_positive_correlation"
        const val COLUMN_CONFIDENCE = "confidence"
        const val COLUMN_INSIGHTFULNESS_SCORE = "insightfulness_score"
        const val COLUMN_PREFERENCE_SCORE = "preference_score"
        const val COLUMN_LAST_CALCULATED_DATE = "last_calculated_date"

        private const val CORRELATION_THRESHOLD = 0.2
        private const val MIN_DATA_POINTS_FOR_CORRELATION = 15
        private const val MAX_LAG_DAYS = 7 // Max lag for correlations
        private const val TAG = "CorrelationDB"

        // Threshold for a new correlation to replace an existing one (e.10 means 10% more significant)
        private const val REPLACE_SIGNIFICANCE_DIFFERENCE = 0.10f
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Updated table creation with new columns.
        // The UNIQUE constraint is removed from here; uniqueness and replacement logic
        // are now handled explicitly by the application in `insertOrUpdateCorrelation`.
        val CREATE_CORRELATIONS_TABLE = """
            CREATE TABLE $TABLE_CORRELATIONS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_BASE_SYMPTOM_A TEXT NOT NULL,
                $COLUMN_WINDOW_SIZE_A INTEGER NOT NULL,
                $COLUMN_CALC_TYPE_A TEXT NOT NULL,
                $COLUMN_BASE_SYMPTOM_B TEXT NOT NULL,
                $COLUMN_WINDOW_SIZE_B INTEGER NOT NULL,
                $COLUMN_CALC_TYPE_B TEXT NOT NULL,
                $COLUMN_LAG INTEGER NOT NULL,
                $COLUMN_IS_POSITIVE_CORRELATION INTEGER NOT NULL,
                $COLUMN_CONFIDENCE REAL NOT NULL,
                $COLUMN_INSIGHTFULNESS_SCORE REAL NOT NULL,
                $COLUMN_PREFERENCE_SCORE INTEGER NOT NULL DEFAULT 0,
                $COLUMN_LAST_CALCULATED_DATE INTEGER NOT NULL
            )
        """.trimIndent()
        db.execSQL(CREATE_CORRELATIONS_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Critical: Drop and recreate table on schema changes. This will clear existing data.
        // Users will need to recalculate correlations after an upgrade.
        Log.w("CorrelationDB", "Upgrading database from version $oldVersion to $newVersion. This will clear ALL existing correlation data.")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CORRELATIONS")
        onCreate(db)
    }

    /**
     * Inserts a new correlation or updates an existing one if the new one is significantly better.
     * The decision is based on the confidence of the new correlation vs. any existing one for
     * the same base metric pair and lag.
     * Returns the ID of the affected row (new or existing), or -1 if no action was taken (existing kept).
     */
    fun insertOrUpdateCorrelation(newCorrelation: Correlation): Long {
        val db = writableDatabase
        var existingId: Long? = null
        var existingConfidence = 0.0f
        var existingPreferenceScore = 0

        // 1. Try to find an existing correlation for the SAME BASE METRIC PAIR AND LAG
        val cursor = db.query(
            TABLE_CORRELATIONS,
            arrayOf(COLUMN_ID, COLUMN_CONFIDENCE, COLUMN_PREFERENCE_SCORE),
            "$COLUMN_BASE_SYMPTOM_A = ? AND $COLUMN_BASE_SYMPTOM_B = ? AND $COLUMN_LAG = ?",
            arrayOf(newCorrelation.baseSymptomA, newCorrelation.baseSymptomB, newCorrelation.lag.toString()),
            null, null, null
        )

        cursor.use {
            if (it.moveToFirst()) {
                existingId = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID))
                existingConfidence = it.getDouble(it.getColumnIndexOrThrow(COLUMN_CONFIDENCE)).toFloat()
                existingPreferenceScore = it.getInt(it.getColumnIndexOrThrow(COLUMN_PREFERENCE_SCORE))
            }
        }

        val values = ContentValues().apply {
            // Store all granular details
            put(COLUMN_BASE_SYMPTOM_A, newCorrelation.baseSymptomA)
            put(COLUMN_WINDOW_SIZE_A, newCorrelation.windowSizeA)
            put(COLUMN_CALC_TYPE_A, newCorrelation.calcTypeA)
            put(COLUMN_BASE_SYMPTOM_B, newCorrelation.baseSymptomB)
            put(COLUMN_WINDOW_SIZE_B, newCorrelation.windowSizeB)
            put(COLUMN_CALC_TYPE_B, newCorrelation.calcTypeB)

            put(COLUMN_LAG, newCorrelation.lag)
            put(COLUMN_IS_POSITIVE_CORRELATION, if (newCorrelation.isPositiveCorrelation) 1 else 0)
            put(COLUMN_CONFIDENCE, newCorrelation.confidence)
            // Insightfulness score is already calculated correctly before calling this method
            put(COLUMN_INSIGHTFULNESS_SCORE, newCorrelation.insightfulnessScore)
            put(COLUMN_LAST_CALCULATED_DATE, newCorrelation.lastCalculatedDate)
            // Initial preference score will be 0 for new insertions or the existing one for updates
            put(COLUMN_PREFERENCE_SCORE, newCorrelation.preferenceScore)
        }

        return if (existingId != null) {
            // 2. An existing correlation for this base pair and lag was found.
            // Apply the cherry-picking logic based on significance.
            if (abs(newCorrelation.confidence) > abs(existingConfidence) + REPLACE_SIGNIFICANCE_DIFFERENCE) {
                // New correlation is significantly better, so update the existing record.
                // Crucially, we keep the existing preference score, as it's user-driven.
                values.put(COLUMN_PREFERENCE_SCORE, existingPreferenceScore)
                db.update(TABLE_CORRELATIONS, values, "$COLUMN_ID = ?", arrayOf(existingId.toString()))
                Log.d("CorrelationDB", "Updated existing correlation for (${newCorrelation.getDisplayNameA()} vs ${newCorrelation.getDisplayNameB()} L${newCorrelation.lag}) with higher confidence (Old: %.2f, New: %.2f). Kept old preference.".format(existingConfidence, newCorrelation.confidence))
                existingId!!
            } else {
                // New correlation is NOT significantly better, keep the old one.
                Log.d("CorrelationDB", "Keeping existing correlation for (${newCorrelation.getDisplayNameA()} vs ${newCorrelation.getDisplayNameB()} L${newCorrelation.lag}). New (%.2f) not significantly better than old (%.2f).".format(newCorrelation.confidence, existingConfidence))
                existingId!! // Return the existing ID, as no change was made
            }
        } else {
            // 3. No existing correlation for this base pair and lag, so insert it.
            db.insert(TABLE_CORRELATIONS, null, values)
        }
    }


    /**
     * Retrieves the top correlations from the database, ordered by confidence, insightfulness, and preference.
     */
    fun getTopCorrelations(limit: Int = -1): List<Correlation> {
        val correlations = mutableListOf<Correlation>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CORRELATIONS,
            null, // Select all columns
            null,
            null,
            null,
            null,
            "ABS($COLUMN_CONFIDENCE) DESC, $COLUMN_INSIGHTFULNESS_SCORE DESC, $COLUMN_PREFERENCE_SCORE DESC",
            limit.toString()
        )

        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID))
                // Retrieve all granular columns
                val baseSymptomA = it.getString(it.getColumnIndexOrThrow(COLUMN_BASE_SYMPTOM_A))
                val windowSizeA = it.getInt(it.getColumnIndexOrThrow(COLUMN_WINDOW_SIZE_A))
                val calcTypeA = it.getString(it.getColumnIndexOrThrow(COLUMN_CALC_TYPE_A))
                val baseSymptomB = it.getString(it.getColumnIndexOrThrow(COLUMN_BASE_SYMPTOM_B))
                val windowSizeB = it.getInt(it.getColumnIndexOrThrow(COLUMN_WINDOW_SIZE_B))
                val calcTypeB = it.getString(it.getColumnIndexOrThrow(COLUMN_CALC_TYPE_B))

                val lag = it.getInt(it.getColumnIndexOrThrow(COLUMN_LAG))
                val isPositiveCorrelation = it.getInt(it.getColumnIndexOrThrow(COLUMN_IS_POSITIVE_CORRELATION)) == 1
                val confidence = it.getDouble(it.getColumnIndexOrThrow(COLUMN_CONFIDENCE)).toFloat()
                val insightfulnessScore = it.getFloat(it.getColumnIndexOrThrow(COLUMN_INSIGHTFULNESS_SCORE))
                val preferenceScore = it.getInt(it.getColumnIndexOrThrow(COLUMN_PREFERENCE_SCORE))
                val lastCalculatedDate = it.getLong(it.getColumnIndexOrThrow(COLUMN_LAST_CALCULATED_DATE))

                correlations.add(
                    Correlation(
                        id,
                        baseSymptomA, windowSizeA, calcTypeA,
                        baseSymptomB, windowSizeB, calcTypeB,
                        lag, isPositiveCorrelation,
                        confidence, insightfulnessScore, preferenceScore, lastCalculatedDate
                    )
                )
            }
        }
        return correlations
    }

    // --- REMOVED DUPLICATE updatePreference FUNCTION HERE ---
    // The updatePreference in CorrelationRepository is the correct one.

    /**
     * Helper function to extract the value of a specific health metric for a given date
     * and apply windowing (raw or average).
     * Returns null if any data point within the window is missing.
     */
    private fun getAnalysisMetricValueForDate(
        healthDataMap: Map<LocalDate, HealthData>,
        analysisMetric: AnalysisMetric,
        targetDate: LocalDate
    ): Double? {
        fun extractRawValue(healthData: HealthData, metricName: String): Float? {
            return when (metricName) {
                "Malaise" -> healthData.malaise
                "Stress Level" -> healthData.stressLevel
                "Sleep Quality" -> healthData.sleepQuality
                "Illness Impact" -> healthData.illnessImpact
                "Depression" -> healthData.depression
                "Hopelessness" -> healthData.hopelessness
                "Sore Throat" -> healthData.soreThroat
                "Sleep Length" -> healthData.sleepLength
                "Lymphadenopathy" -> healthData.lymphadenopathy
                "Exercise Level" -> healthData.exerciseLevel
                "Sleep Readiness" -> healthData.sleepReadiness
                else -> null
            }
        }

        if (analysisMetric.windowSize == 1) {
            val healthData = healthDataMap[targetDate] ?: return null
            return extractRawValue(healthData, analysisMetric.baseMetricName)?.toDouble()
        } else {
            val valuesInWindow = mutableListOf<Double>()
            for (i in 0 until analysisMetric.windowSize) {
                val dateInWindow = targetDate.minusDays(i.toLong())
                val healthData = healthDataMap[dateInWindow]

                if (healthData != null) {
                    val value = extractRawValue(healthData, analysisMetric.baseMetricName)
                    if (value != null) {
                        valuesInWindow.add(value.toDouble())
                    } else {
                        // If any value in the window is null, the entire window is considered null
                        return null
                    }
                } else {
                    // If any date in the window is missing data, the entire window is considered null
                    return null
                }
            }

            // Ensure we actually collected 'windowSize' values
            if (valuesInWindow.size != analysisMetric.windowSize) {
                return null
            }

            return when (analysisMetric.calculationType) {
                "avg" -> valuesInWindow.average()
                "sum" -> valuesInWindow.sum() // 'sum' is still here for display of old data, but new correlations won't generate it.
                else -> null
            }
        }
    }
    // This function needs to be placed within your CorrelationDatabaseHelper class
    private fun calculateInsightfulnessScore(correlation: Correlation): Float {
        // Step 1: Calculate a raw score based on heuristic rules
        var rawScore = 0.0f

        val symptomPair = setOf(correlation.baseSymptomA, correlation.baseSymptomB)

        // --- Define symptom categories for systematic penalization/rewarding ---
        val actionableInputs = setOf("Exercise Level", "Sleep Length", "Sleep Readiness")
        val healthOutcomes = setOf("Malaise", "Stress Level", "Sleep Quality", "Illness Impact", "Depression", "Hopelessness", "Sore Throat", "Lymphadenopathy")


        // --- PENALTY RULES (Increased and More Specific) ---

        // HIGHEST PENALTY: True Tautologies (definitionally intertwined, nearly identical concepts)
        if (symptomPair == setOf("Depression", "Hopelessness")) {
            rawScore -= 50.0f // Max penalty for direct tautology
            Log.d(TAG, "Insight Calc: HIGHEST PENALTY for true tautology: ${correlation.getDisplayNameA()} vs ${correlation.getDisplayNameB()}. Raw score: $rawScore")
        }
        // HIGH PENALTY: Very Strongly Expected / Direct Symptom-Symptom Links
        // These are often manifestations of the same underlying condition or direct consequences.
        else if (
            symptomPair == setOf("Malaise", "Illness Impact") ||
            symptomPair == setOf("Illness Impact", "Sore Throat") ||
            symptomPair == setOf("Illness Impact", "Lymphadenopathy") ||
            symptomPair == setOf("Illness Impact", "Hopelessness") ||
            symptomPair == setOf("Illness Impact", "Depression") ||
            symptomPair == setOf("Malaise", "Sore Throat") ||
            symptomPair == setOf("Malaise", "Lymphadenopathy") ||
            symptomPair == setOf("Sore Throat", "Lymphadenopathy")  // Added common link
            //symptomPair == setOf("Depression", "Stress Level") // Highly expected psychological link
        ) {
            rawScore -= 30.0f // Increased penalty
            Log.d(TAG, "Insight Calc: HIGH PENALTY for highly expected symptom link: ${correlation.getDisplayNameA()} vs ${correlation.getDisplayNameB()}. Raw score: $rawScore")
        }


        // --- REWARD RULES ---

        // Rule 1: Delay Reward
        // A delay of one day gives +4, longer delays multiply by 4.0f
        if (correlation.lag > 0) {
            rawScore += (correlation.lag * 4.0f) // Adjusted to 4.0f per day
            Log.d(TAG, "Insight Calc: Rewarded ${correlation.getDisplayNameA()} vs ${correlation.getDisplayNameB()} for lag: ${correlation.lag}. Raw score: $rawScore")
        }

        // Rule 2: Averaging Reward
        val isSymptomAAveraged = correlation.calcTypeA == "avg" && correlation.windowSizeA > 1
        val isSymptomBAveraged = correlation.calcTypeB == "avg" && correlation.windowSizeB > 1

        if (isSymptomAAveraged && isSymptomBAveraged) {
            rawScore += 10.0f // Reward for BOTH symptoms being averaged (adjusted)
            Log.d(TAG, "Insight Calc: Rewarded ${correlation.getDisplayNameA()} vs ${correlation.getDisplayNameB()} for BOTH being averaged. Raw score: $rawScore")
        } else if (isSymptomAAveraged || isSymptomBAveraged) {
            rawScore += 5.0f // Reward for ONLY ONE symptom being averaged (adjusted)
            Log.d(TAG, "Insight Calc: Rewarded ${correlation.getDisplayNameA()} vs ${correlation.getDisplayNameB()} for ONE being averaged. Raw score: $rawScore")
        }

        // Rule 3: Actionable Insight Reward (NEW)
        // Reward correlations where one symptom is an 'actionable input' and the other is a 'health outcome'.
        val isSymptomAActionable = actionableInputs.contains(correlation.baseSymptomA)
        val isSymptomBActionable = actionableInputs.contains(correlation.baseSymptomB)
        val isSymptomAOutcome = healthOutcomes.contains(correlation.baseSymptomA)
        val isSymptomBOutcome = healthOutcomes.contains(correlation.baseSymptomB)

        // Check if it's a cross-category correlation (Actionable <-> Outcome)
        if ((isSymptomAActionable && isSymptomBOutcome) || (isSymptomBActionable && isSymptomAOutcome)) {
            // Ensure it's not both actionable OR both outcomes, but one of each type
            if (!((isSymptomAActionable && isSymptomBActionable) || (isSymptomAOutcome && isSymptomBOutcome))) {
                rawScore += 12.0f // Reward for actionable insight, e.g., Exercise Level -> Sleep Quality
                Log.d(TAG, "Insight Calc: Rewarded ${correlation.getDisplayNameA()} vs ${correlation.getDisplayNameB()} for actionable insight. Raw score: $rawScore")
            }
        }


        // --- NORMALIZATION ---

        // Step 2: Define the conceptual min/max range for the RAW insightfulness score.
        // These bounds should encompass the full range of possible raw scores after penalties and rewards.
        // Max possible raw score calculation:
        // Max Lag Reward (7 days * 4.0f) = 28.0f
        // Max Averaging Reward (both averaged) = 10.0f
        // Max Actionable Insight Reward = 12.0f
        // Total Max Raw Score = 28.0f + 10.0f + 12.0f = 50.0f
        // Min possible raw score: -50.0f (from highest penalty)
        val minRawInsight = -50.0f
        val maxRawInsight = 50.0f

        // Step 3: Coerce the raw score to ensure it's within the defined mapping range.
        val coercedRawScore = rawScore.coerceIn(minRawInsight, maxRawInsight)

        // Step 4: Normalize the coerced raw score to the 0.0f - 1.0f range.
        val normalizedScore = (coercedRawScore - minRawInsight) / (maxRawInsight - minRawInsight)

        Log.d(TAG, "Insight Calc: Final Normalized Insightfulness for ${correlation.getDisplayNameA()} vs ${correlation.getDisplayNameB()}: ${"%.2f".format(normalizedScore)}")
        return normalizedScore
    }

    /**
     * Calculates all relevant correlations between health metrics and stores the most significant ones.
     * This function now implements cherry-picking: for each base metric pair and lag,
     * only the correlation with the highest confidence (or significantly higher) is stored.
     */
    fun calculateAndStoreAllCorrelations(allHealthData: List<HealthData>): List<Correlation> {
        val calculatedCorrelations = mutableListOf<Correlation>()

        if (allHealthData.size < MIN_DATA_POINTS_FOR_CORRELATION) {
            Log.d("CorrelationCalc", "Not enough data points for correlation calculation (${allHealthData.size} found, $MIN_DATA_POINTS_FOR_CORRELATION needed). Returning empty list.")
            return emptyList()
        }

        val sortedData = allHealthData.sortedBy { it.currentDate }
        val dateMap = sortedData.associateBy { LocalDate.parse(it.currentDate) }

        val rawMetricNames = listOf(
            "Malaise", "Stress Level", "Sleep Quality", "Illness Impact", "Depression",
            "Hopelessness", "Sore Throat", "Sleep Length", "Lymphadenopathy", "Exercise Level", "Sleep Readiness"
        )

        val allAnalysisMetrics = mutableListOf<AnalysisMetric>()

        // Add raw metrics (windowSize = 1, calculationType = "raw")
        for (name in rawMetricNames) {
            allAnalysisMetrics.add(AnalysisMetric(name))
        }

        val windowSizesForDerivedMetrics = listOf(2, 3, 5, 7)

        // Add ONLY 'avg' derived metrics
        for (name in rawMetricNames) {
            for (windowSize in windowSizesForDerivedMetrics) {
                allAnalysisMetrics.add(AnalysisMetric(name, windowSize, "avg"))
            }
        }

        Log.d("CorrelationCalc", "Starting correlation calculation for ${allAnalysisMetrics.size} analysis metrics (raw + avg) and ${MAX_LAG_DAYS + 1} lags.")

        // Map to store the best correlation found *in memory* for each (baseMetricA, baseMetricB, lag) combination.
        // This is where the cherry-picking logic happens before database insertion.
        // Key: Triple<String, String, Int> (baseMetricA, baseMetricB, lag)
        // Value: Correlation object (the best one found so far for this base pair and lag)
        val bestCorrelationsPerBasePairAndLag = mutableMapOf<Triple<String, String, Int>, Correlation>()


        for (i in allAnalysisMetrics.indices) {
            for (j in i + 1 until allAnalysisMetrics.size) {
                val analysisMetricA = allAnalysisMetrics[i]
                val analysisMetricB = allAnalysisMetrics[j]

                // Skip correlations between different forms (e.g., raw vs. 3-day avg) of the same base metric.
                // We're looking for relationships BETWEEN DIFFERENT base metrics.
                if (analysisMetricA.baseMetricName == analysisMetricB.baseMetricName) {
                    Log.d("CorrelationCalc", "Skipping self-correlation for same base metric: ${analysisMetricA.getDisplayName()} vs ${analysisMetricB.getDisplayName()}")
                    continue
                }

                for (lag in 0..MAX_LAG_DAYS) {
                    val valuesA = mutableListOf<Double>()
                    val valuesB = mutableListOf<Double>()

                    // Collect data points for the current A and B metrics with the given lag
                    for (data in sortedData) {
                        val dateA = LocalDate.parse(data.currentDate)
                        val dateBForLag = dateA.plusDays(lag.toLong())

                        // Get the value for analysisMetricA at dateA
                        val valA = getAnalysisMetricValueForDate(dateMap, analysisMetricA, dateA)
                        // Get the value for analysisMetricB at dateBForLag (considering lag)
                        val valB = getAnalysisMetricValueForDate(dateMap, analysisMetricB, dateBForLag)

                        // Only add to lists if both values are valid (not null)
                        if (valA != null && valB != null) {
                            valuesA.add(valA)
                            valuesB.add(valB)
                        }
                    }

                    Log.d("CorrelationCalc", "Analyzing: ${analysisMetricA.getDisplayName()} vs ${analysisMetricB.getDisplayName()} (Lag: $lag days). Data points found: ${valuesA.size}")

                    // Proceed only if enough data points are available for a reliable correlation
                    if (valuesA.size >= MIN_DATA_POINTS_FOR_CORRELATION) {
                        try {
                            val pearsonCorrelation = PearsonsCorrelation().correlation(valuesA.toDoubleArray(), valuesB.toDoubleArray())
                            val absConfidence = abs(pearsonCorrelation).toFloat()

                            // Check if the correlation is significant (above CORRELATION_THRESHOLD)
                            if (!pearsonCorrelation.isNaN() && absConfidence >= CORRELATION_THRESHOLD) {

                                // --- FIX START ---
                                // 1. Create a temporary Correlation object with all fields EXCEPT insightfulnessScore
                                //    (or with a placeholder)
                                val tempCorrelationCandidate = Correlation(
                                    baseSymptomA = analysisMetricA.baseMetricName,
                                    windowSizeA = analysisMetricA.windowSize,
                                    calcTypeA = analysisMetricA.calculationType,
                                    baseSymptomB = analysisMetricB.baseMetricName,
                                    windowSizeB = analysisMetricB.windowSize,
                                    calcTypeB = analysisMetricB.calculationType,
                                    lag = lag,
                                    isPositiveCorrelation = pearsonCorrelation > 0,
                                    confidence = pearsonCorrelation.toFloat(),
                                    insightfulnessScore = 0.0f, // Placeholder, will be overwritten
                                    preferenceScore = 0, // Initial preference for new candidates
                                    lastCalculatedDate = System.currentTimeMillis()
                                )

                                // 2. Calculate the insightfulness score using the helper function
                                val calculatedInsightfulness = calculateInsightfulnessScore(tempCorrelationCandidate)

                                // 3. Create the final Correlation object with the correctly calculated insightfulnessScore
                                val currentCorrelation = tempCorrelationCandidate.copy(
                                    insightfulnessScore = calculatedInsightfulness
                                )
                                // --- FIX END ---


                                // Use the base metric names and lag as the key for cherry-picking
                                val baseKey = Triple(analysisMetricA.baseMetricName, analysisMetricB.baseMetricName, lag)
                                val existingBest = bestCorrelationsPerBasePairAndLag[baseKey]

                                // Cherry-picking logic:
                                // 1. If no best correlation exists for this base pair/lag yet, this one is the best.
                                // 2. If an existing best exists, replace it ONLY if the current one is significantly better
                                //    (absConfidence is greater than existingBest.confidence + REPLACE_SIGNIFICANCE_DIFFERENCE).
                                if (existingBest == null || absConfidence > abs(existingBest.confidence) + REPLACE_SIGNIFICANCE_DIFFERENCE) {
                                    bestCorrelationsPerBasePairAndLag[baseKey] = currentCorrelation
                                    Log.d("CorrelationCalc", "  -> Candidate: ${currentCorrelation.getDisplayNameA()} vs ${currentCorrelation.getDisplayNameB()} (Lag: $lag, Conf: %.2f, Insight: %.2f). Setting as new best for base pair.".format(absConfidence, currentCorrelation.insightfulnessScore))
                                } else {
                                    Log.d("CorrelationCalc", "  -> Candidate: ${currentCorrelation.getDisplayNameA()} vs ${currentCorrelation.getDisplayNameB()} (Lag: $lag, Conf: %.2f, Insight: %.2f). Not significantly better than existing best (Conf: %.2f). Keeping existing.".format(absConfidence, currentCorrelation.insightfulnessScore, abs(existingBest.confidence)))
                                }
                            } else {
                                Log.d("CorrelationCalc", "  Correlation for ${analysisMetricA.getDisplayName()} vs ${analysisMetricB.getDisplayName()} (Lag: $lag) is not significant (R=%.4f)".format(pearsonCorrelation))
                            }
                        } catch (e: Exception) {
                            Log.e("CorrelationCalc", "Error calculating correlation for ${analysisMetricA.getDisplayName()} - ${analysisMetricB.getDisplayName()} (lag $lag): ${e.message}")
                        }
                    } else {
                        Log.d("CorrelationCalc", "  Not enough data points for reliable calculation for ${analysisMetricA.getDisplayName()} - ${analysisMetricB.getDisplayName()} (Lag: $lag). Skipped.")
                    }
                }
            }
        }

        // After all possible correlations have been calculated and cherry-picked in memory,
        // iterate through the final selection and store/update them in the database.
        Log.d("CorrelationCalc", "Storing ${bestCorrelationsPerBasePairAndLag.size} cherry-picked correlations to database.")
        for (correlation in bestCorrelationsPerBasePairAndLag.values) {
            val affectedId = insertOrUpdateCorrelation(correlation)
            // If the correlation was either inserted or updated (not just kept the old one),
            // add it to the list returned to the caller.
            if (affectedId != -1L) {
                // Ensure the returned correlation has the correct DB ID if it was an update.
                calculatedCorrelations.add(correlation.copy(id = affectedId))
            }
        }

        Log.d("CorrelationCalc", "Finished correlation calculation. Total significant and cherry-picked correlations found: ${calculatedCorrelations.size}.")
        return calculatedCorrelations
    }
}
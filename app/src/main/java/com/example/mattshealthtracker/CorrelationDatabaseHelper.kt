package com.example.mattshealthtracker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation
import java.time.LocalDate
import kotlin.math.abs // Ensure this import is present if abs is used elsewhere, though it's now primarily Math.abs

class CorrelationDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        // Change 'private const val' to 'const val' for public access
        const val DATABASE_NAME = "correlations.db"
        const val DATABASE_VERSION = 3
        const val TABLE_CORRELATIONS = "correlations"
        const val COLUMN_ID = "_id"
        const val COLUMN_SYMPTOM_A = "symptom_a"
        const val COLUMN_SYMPTOM_B = "symptom_b"
        const val COLUMN_LAG = "lag"
        const val COLUMN_IS_POSITIVE_CORRELATION = "is_positive_correlation"
        const val COLUMN_CONFIDENCE = "confidence"
        const val COLUMN_INSIGHTFULNESS_SCORE = "insightfulness_score"
        const val COLUMN_PREFERENCE_SCORE = "preference_score"
        const val COLUMN_LAST_CALCULATED_DATE = "last_calculated_date"

        // These can remain private if they are only used internally within this file
        private const val CORRELATION_THRESHOLD = 0.2
        private const val MIN_DATA_POINTS_FOR_CORRELATION = 15
        private const val MAX_LAG_DAYS = 3
    }

    override fun onCreate(db: SQLiteDatabase) {
        val CREATE_CORRELATIONS_TABLE = """
            CREATE TABLE $TABLE_CORRELATIONS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_SYMPTOM_A TEXT NOT NULL,
                $COLUMN_SYMPTOM_B TEXT NOT NULL,
                $COLUMN_LAG INTEGER NOT NULL,
                $COLUMN_IS_POSITIVE_CORRELATION INTEGER NOT NULL,
                $COLUMN_CONFIDENCE REAL NOT NULL,
                $COLUMN_INSIGHTFULNESS_SCORE REAL NOT NULL,
                $COLUMN_PREFERENCE_SCORE INTEGER NOT NULL DEFAULT 0,
                $COLUMN_LAST_CALCULATED_DATE INTEGER NOT NULL,
                UNIQUE($COLUMN_SYMPTOM_A, $COLUMN_SYMPTOM_B, $COLUMN_LAG) ON CONFLICT REPLACE
            )
        """.trimIndent()
        db.execSQL(CREATE_CORRELATIONS_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CORRELATIONS")
        onCreate(db)
    }

    fun insertOrUpdateCorrelation(newCorrelation: Correlation): Long {
        val db = writableDatabase
        var existingCorrelationId: Long? = null
        var existingPreferenceScore = 0

        val cursor = db.query(
            TABLE_CORRELATIONS,
            arrayOf(COLUMN_ID, COLUMN_PREFERENCE_SCORE),
            "$COLUMN_SYMPTOM_A = ? AND $COLUMN_SYMPTOM_B = ? AND $COLUMN_LAG = ?",
            arrayOf(newCorrelation.symptomA, newCorrelation.symptomB, newCorrelation.lag.toString()),
            null, null, null
        )

        cursor.use {
            if (it.moveToFirst()) {
                existingCorrelationId = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID))
                existingPreferenceScore = it.getInt(it.getColumnIndexOrThrow(COLUMN_PREFERENCE_SCORE))
            }
        }

        val values = ContentValues().apply {
            put(COLUMN_SYMPTOM_A, newCorrelation.symptomA)
            put(COLUMN_SYMPTOM_B, newCorrelation.symptomB)
            put(COLUMN_LAG, newCorrelation.lag)
            put(COLUMN_IS_POSITIVE_CORRELATION, if (newCorrelation.isPositiveCorrelation) 1 else 0)
            put(COLUMN_CONFIDENCE, newCorrelation.confidence) // This will now correctly store the signed value
            put(COLUMN_INSIGHTFULNESS_SCORE, newCorrelation.insightfulnessScore)
            put(COLUMN_LAST_CALCULATED_DATE, newCorrelation.lastCalculatedDate)

            put(COLUMN_PREFERENCE_SCORE, existingPreferenceScore)
        }

        return if (existingCorrelationId != null) {
            db.update(TABLE_CORRELATIONS, values, "$COLUMN_ID = ?", arrayOf(existingCorrelationId.toString()))
            existingCorrelationId!!
        } else {
            db.insert(TABLE_CORRELATIONS, null, values)
        }
    }


    fun getTopCorrelations(limit: Int = -1): List<Correlation> {
        val correlations = mutableListOf<Correlation>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CORRELATIONS,
            null,
            null,
            null,
            null,
            null,
            // MODIFIED SORT ORDER STARTS HERE
            "ABS($COLUMN_CONFIDENCE) DESC, $COLUMN_INSIGHTFULNESS_SCORE DESC, $COLUMN_PREFERENCE_SCORE DESC",
            // MODIFIED SORT ORDER ENDS HERE
            limit.toString()
        )

        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID))
                val symptomA = it.getString(it.getColumnIndexOrThrow(COLUMN_SYMPTOM_A))
                val symptomB = it.getString(it.getColumnIndexOrThrow(COLUMN_SYMPTOM_B))
                val lag = it.getInt(it.getColumnIndexOrThrow(COLUMN_LAG))
                val isPositiveCorrelation = it.getInt(it.getColumnIndexOrThrow(COLUMN_IS_POSITIVE_CORRELATION)) == 1
                val confidence = it.getDouble(it.getColumnIndexOrThrow(COLUMN_CONFIDENCE)).toFloat()
                val insightfulnessScore = it.getDouble(it.getColumnIndexOrThrow(COLUMN_INSIGHTFULNESS_SCORE))
                val preferenceScore = it.getInt(it.getColumnIndexOrThrow(COLUMN_PREFERENCE_SCORE))
                val lastCalculatedDate = it.getLong(it.getColumnIndexOrThrow(COLUMN_LAST_CALCULATED_DATE))

                correlations.add(
                    Correlation(
                        id, symptomA, symptomB, lag, isPositiveCorrelation,
                        confidence, insightfulnessScore, preferenceScore, lastCalculatedDate
                    )
                )
            }
        }
        return correlations
    }

    fun updatePreference(correlationId: Long, delta: Int) {
        val db = writableDatabase
        db.execSQL("UPDATE $TABLE_CORRELATIONS SET $COLUMN_PREFERENCE_SCORE = $COLUMN_PREFERENCE_SCORE + ? WHERE $COLUMN_ID = ?", arrayOf(delta.toString(), correlationId.toString()))
        Log.d("CorrelationDB", "Updated preference for ID $correlationId by $delta")
    }


    private fun getMetricValues(
        healthDataList: List<HealthData>,
        metricName: String
    ): List<Float>? {
        return when (metricName) {
            "Malaise" -> healthDataList.map { it.malaise }
            "Stress Level" -> healthDataList.map { it.stressLevel }
            "Sleep Quality" -> healthDataList.map { it.sleepQuality }
            "Illness Impact" -> healthDataList.map { it.illnessImpact }
            "Depression" -> healthDataList.map { it.depression }
            "Hopelessness" -> healthDataList.map { it.hopelessness }
            "Sore Throat" -> healthDataList.map { it.soreThroat }
            "Sleep Length" -> healthDataList.map { it.sleepLength }
            "Lymphadenopathy" -> healthDataList.map { it.lymphadenopathy }
            "Exercise Level" -> healthDataList.map { it.exerciseLevel }
            "Sleep Readiness" -> healthDataList.map { it.sleepReadiness }
            else -> null
        }
    }


    fun calculateAndStoreAllCorrelations(allHealthData: List<HealthData>): List<Correlation> {
        val calculatedCorrelations = mutableListOf<Correlation>()

        if (allHealthData.size < MIN_DATA_POINTS_FOR_CORRELATION) {
            Log.d("CorrelationCalc", "Not enough data points for correlation calculation (${allHealthData.size} found, $MIN_DATA_POINTS_FOR_CORRELATION needed). Returning empty list.")
            return emptyList()
        }

        // Sort data by date for consistent time series
        val sortedData = allHealthData.sortedBy { it.currentDate }
        val dateMap = sortedData.associateBy { LocalDate.parse(it.currentDate) }

        val allMetricNames = listOf(
            "Malaise", "Stress Level", "Sleep Quality", "Illness Impact", "Depression",
            "Hopelessness", "Sore Throat", "Sleep Length", "Lymphadenopathy", "Exercise Level", "Sleep Readiness"
        )

        Log.d("CorrelationCalc", "Starting correlation calculation for ${allMetricNames.size} metrics and ${MAX_LAG_DAYS + 1} lags.")

        // Iterate through all unique pairs of metrics
        for (i in allMetricNames.indices) {
            for (j in i + 1 until allMetricNames.size) { // j starts from i+1 to avoid duplicates (A,B) and (B,A)
                val metricA = allMetricNames[i]
                val metricB = allMetricNames[j]

                for (lag in 0..MAX_LAG_DAYS) { // Loop through all specified lags
                    // Collect aligned data points for current metric pair and lag
                    val valuesA = mutableListOf<Double>()
                    val valuesB = mutableListOf<Double>()

                    // Iterate through each HealthData entry as the reference for metricA's date
                    for (data in sortedData) {
                        val dateA = LocalDate.parse(data.currentDate)
                        // Calculate the date for metricB based on the current lag
                        val dateBForLag = dateA.plusDays(lag.toLong())

                        // Get HealthData entries for both dates
                        val healthDataA = dateMap[dateA]
                        val healthDataB = dateMap[dateBForLag]

                        // If both data points exist, extract their values
                        if (healthDataA != null && healthDataB != null) {
                            val valA = getMetricValues(listOf(healthDataA), metricA)?.firstOrNull()
                            val valB = getMetricValues(listOf(healthDataB), metricB)?.firstOrNull()

                            if (valA != null && valB != null) {
                                valuesA.add(valA.toDouble())
                                valuesB.add(valB.toDouble())
                            }
                        }
                    }

                    Log.d("CorrelationCalc", "Analyzing: $metricA vs $metricB (Lag: $lag days). Data points found: ${valuesA.size}")

                    if (valuesA.size >= MIN_DATA_POINTS_FOR_CORRELATION) {
                        try {
                            // Calculate Pearson correlation
                            val pearsonCorrelation = PearsonsCorrelation().correlation(valuesA.toDoubleArray(), valuesB.toDoubleArray())

                            Log.d("CorrelationCalc", "  Calculated Pearson R: %.4f".format(pearsonCorrelation))

                            // Check if correlation is valid and meets the threshold
                            if (!pearsonCorrelation.isNaN() && abs(pearsonCorrelation) >= CORRELATION_THRESHOLD) {
                                val isPositive = pearsonCorrelation > 0
                                val confidenceValueForStorage = pearsonCorrelation.toFloat()
                                val insightfulness = pearsonCorrelation.toDouble() // Assuming insightfulness also uses the signed value directly

                                Log.d("CorrelationCalc", "  -> Storing significant correlation: $metricA ${if(isPositive) "INCREASES with" else "DECREASES with"} $metricB (Lag: $lag, Conf: %.2f)".format(Math.abs(pearsonCorrelation)))

                                val correlation = Correlation(
                                    symptomA = metricA,
                                    symptomB = metricB,
                                    lag = lag,
                                    isPositiveCorrelation = isPositive,
                                    confidence = confidenceValueForStorage,
                                    insightfulnessScore = insightfulness,
                                    preferenceScore = 0,
                                    lastCalculatedDate = System.currentTimeMillis()
                                )

                                insertOrUpdateCorrelation(correlation)
                                calculatedCorrelations.add(correlation)
                            } else {
                                Log.d("CorrelationCalc", "  Correlation for $metricA vs $metricB (Lag: $lag) is not significant (R=%.4f)".format(pearsonCorrelation))
                            }
                        } catch (e: Exception) {
                            Log.e("CorrelationCalc", "Error calculating correlation for $metricA - $metricB (lag $lag): ${e.message}")
                        }
                    } else {
                        Log.d("CorrelationCalc", "  Not enough data points for reliable calculation for $metricA - $metricB (Lag: $lag). Skipped.")
                    }
                }
            }
        }
        Log.d("CorrelationCalc", "Finished correlation calculation. Total significant correlations found: ${calculatedCorrelations.size}.")
        return calculatedCorrelations
    }
}
// In a file named 'CorrelationDatabaseHelper.kt'
package com.example.mattshealthtracker // Updated package name

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class CorrelationDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    // --- Database Constants and Schema ---
    companion object {
        private const val TAG = "CorrelationDBHelper"

        const val DATABASE_NAME = "correlations.db"
        const val DATABASE_VERSION = 1

        // Table and Column Names
        const val TABLE_CORRELATIONS = "correlations"
        const val COLUMN_ID = "id"
        const val COLUMN_SYMPTOM_A = "symptom_a"
        const val COLUMN_SYMPTOM_B = "symptom_b"
        const val COLUMN_LAG = "lag"
        const val COLUMN_IS_POSITIVE_CORRELATION = "is_positive_correlation"
        const val COLUMN_CONFIDENCE = "confidence"
        const val COLUMN_INSIGHTFULNESS_SCORE = "insightfulness_score"
        const val COLUMN_PREFERENCE_SCORE = "preference_score"
        const val COLUMN_LAST_CALCULATED_DATE = "last_calculated_date"

        // SQL statement to create the table
        private const val SQL_CREATE_ENTRIES =
            "CREATE TABLE $TABLE_CORRELATIONS (" +
                    "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "$COLUMN_SYMPTOM_A TEXT NOT NULL," +
                    "$COLUMN_SYMPTOM_B TEXT NOT NULL," +
                    "$COLUMN_LAG INTEGER NOT NULL," +
                    "$COLUMN_IS_POSITIVE_CORRELATION INTEGER NOT NULL," +
                    "$COLUMN_CONFIDENCE REAL NOT NULL," +
                    "$COLUMN_INSIGHTFULNESS_SCORE REAL NOT NULL," +
                    "$COLUMN_PREFERENCE_SCORE INTEGER NOT NULL," +
                    "$COLUMN_LAST_CALCULATED_DATE INTEGER NOT NULL," +
                    "UNIQUE($COLUMN_SYMPTOM_A, $COLUMN_SYMPTOM_B, $COLUMN_LAG, $COLUMN_IS_POSITIVE_CORRELATION) ON CONFLICT REPLACE" +
                    ");"

        private const val SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS $TABLE_CORRELATIONS"

        const val SUPPRESSION_THRESHOLD = -5
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_ENTRIES)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL(SQL_DELETE_ENTRIES)
        onCreate(db)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }

    // --- Database Operations ---

    fun insertOrUpdateCorrelation(correlation: Correlation) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_SYMPTOM_A, if (correlation.symptomA <= correlation.symptomB) correlation.symptomA else correlation.symptomB)
            put(COLUMN_SYMPTOM_B, if (correlation.symptomA <= correlation.symptomB) correlation.symptomB else correlation.symptomA)
            put(COLUMN_LAG, correlation.lag)
            put(COLUMN_IS_POSITIVE_CORRELATION, if (correlation.isPositiveCorrelation) 1 else 0)
            put(COLUMN_CONFIDENCE, correlation.confidence)
            put(COLUMN_INSIGHTFULNESS_SCORE, correlation.insightfulnessScore)
            val existingPreference = getPreferenceScoreForCorrelationInternal(db, correlation)
            put(COLUMN_PREFERENCE_SCORE, existingPreference ?: correlation.preferenceScore)
            put(COLUMN_LAST_CALCULATED_DATE, System.currentTimeMillis())
        }

        db.beginTransaction()
        try {
            val result = db.insertWithOnConflict(
                TABLE_CORRELATIONS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
            if (result == -1L) {
                Log.e(TAG, "Failed to insert or update correlation: ${correlation.getUniqueKey()}")
            } else {
                Log.d(TAG, "Successfully inserted or updated correlation: ${correlation.getUniqueKey()} (ID: $result)")
            }
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e(TAG, "Error during insertOrUpdateCorrelation: ${e.message}", e)
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    fun updatePreference(correlationId: Long, delta: Int) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_PREFERENCE_SCORE, "(${COLUMN_PREFERENCE_SCORE} + $delta)")
        }

        val rowsAffected = db.update(
            TABLE_CORRELATIONS,
            values,
            "$COLUMN_ID = ?",
            arrayOf(correlationId.toString())
        )
        if (rowsAffected > 0) {
            Log.d(TAG, "Updated preference for correlation ID $correlationId by $delta")
        } else {
            Log.w(TAG, "No correlation found with ID $correlationId to update preference.")
        }
        db.close()
    }

    fun getTopCorrelations(limit: Int = 10, minPreferenceThreshold: Int = SUPPRESSION_THRESHOLD): List<Correlation> {
        val db = readableDatabase
        val correlations = mutableListOf<Correlation>()
        val orderBy = "$COLUMN_INSIGHTFULNESS_SCORE DESC, " +
                "$COLUMN_CONFIDENCE DESC, " +
                "$COLUMN_PREFERENCE_SCORE DESC"
        val selection = "$COLUMN_PREFERENCE_SCORE >= ?"
        val selectionArgs = arrayOf(minPreferenceThreshold.toString())

        var cursor: Cursor? = null
        try {
            cursor = db.query(
                TABLE_CORRELATIONS, null, selection, selectionArgs, null, null, orderBy, limit.toString()
            )
            correlations.addAll(cursorToCorrelations(cursor))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting top correlations: ${e.message}", e)
        } finally {
            cursor?.close()
            db.close()
        }
        return correlations
    }

    fun getAllCorrelations(): List<Correlation> {
        val db = readableDatabase
        val correlations = mutableListOf<Correlation>()
        val orderBy = "$COLUMN_INSIGHTFULNESS_SCORE DESC, " +
                "$COLUMN_CONFIDENCE DESC, " +
                "$COLUMN_PREFERENCE_SCORE DESC"

        var cursor: Cursor? = null
        try {
            cursor = db.query(
                TABLE_CORRELATIONS, null, null, null, null, null, orderBy, null
            )
            correlations.addAll(cursorToCorrelations(cursor))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all correlations: ${e.message}", e)
        } finally {
            cursor?.close()
            db.close()
        }
        return correlations
    }

    private fun getPreferenceScoreForCorrelationInternal(db: SQLiteDatabase, correlation: Correlation): Int? {
        var preference: Int? = null
        val (s1, s2) = if (correlation.symptomA <= correlation.symptomB) {
            correlation.symptomA to correlation.symptomB
        } else {
            correlation.symptomB to correlation.symptomA
        }

        val selection = "$COLUMN_SYMPTOM_A = ? AND " +
                "$COLUMN_SYMPTOM_B = ? AND " +
                "$COLUMN_LAG = ? AND " +
                "$COLUMN_IS_POSITIVE_CORRELATION = ?"
        val selectionArgs = arrayOf(
            s1, s2, correlation.lag.toString(), if (correlation.isPositiveCorrelation) "1" else "0"
        )

        var cursor: Cursor? = null
        try {
            cursor = db.query(
                TABLE_CORRELATIONS, arrayOf(COLUMN_PREFERENCE_SCORE), selection, selectionArgs, null, null, null, null
            )
            if (cursor.moveToFirst()) {
                val preferenceIndex = cursor.getColumnIndexOrThrow(COLUMN_PREFERENCE_SCORE)
                preference = cursor.getInt(preferenceIndex)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting existing preference score: ${e.message}", e)
        } finally {
            cursor?.close()
        }
        return preference
    }

    private fun cursorToCorrelations(cursor: Cursor?): List<Correlation> {
        val correlations = mutableListOf<Correlation>()
        cursor?.use {
            if (it.moveToFirst()) {
                val idIndex = it.getColumnIndexOrThrow(COLUMN_ID)
                val symptomAIndex = it.getColumnIndexOrThrow(COLUMN_SYMPTOM_A)
                val symptomBIndex = it.getColumnIndexOrThrow(COLUMN_SYMPTOM_B)
                val lagIndex = it.getColumnIndexOrThrow(COLUMN_LAG)
                val isPositiveIndex = it.getColumnIndexOrThrow(COLUMN_IS_POSITIVE_CORRELATION)
                val confidenceIndex = it.getColumnIndexOrThrow(COLUMN_CONFIDENCE)
                val insightfulnessIndex = it.getColumnIndexOrThrow(COLUMN_INSIGHTFULNESS_SCORE)
                val preferenceIndex = it.getColumnIndexOrThrow(COLUMN_PREFERENCE_SCORE)
                val lastCalculatedIndex = it.getColumnIndexOrThrow(COLUMN_LAST_CALCULATED_DATE)

                do {
                    correlations.add(
                        Correlation(
                            id = it.getLong(idIndex),
                            symptomA = it.getString(symptomAIndex),
                            symptomB = it.getString(symptomBIndex),
                            lag = it.getInt(lagIndex),
                            isPositiveCorrelation = it.getInt(isPositiveIndex) == 1,
                            confidence = it.getDouble(confidenceIndex),
                            insightfulnessScore = it.getDouble(insightfulnessIndex),
                            preferenceScore = it.getInt(preferenceIndex),
                            lastCalculatedDate = it.getLong(lastCalculatedIndex)
                        )
                    )
                } while (it.moveToNext())
            }
        }
        return correlations
    }

    // --- Correlation Calculation Logic ---

    // Define your symptom groups for insightfulness calculation based on HealthData metrics
    // Using string keys that match the HealthData property names
    private val symptomGroups = mapOf(
        "malaise" to "Symptom",
        "soreThroat" to "Symptom",
        "lymphadenopathy" to "Symptom",
        "illnessImpact" to "Symptom",
        "depression" to "Mood",
        "hopelessness" to "Mood",
        "stressLevel" to "Mood",
        "exerciseLevel" to "Activity",
        "sleepQuality" to "Sleep",
        "sleepLength" to "Sleep",
        "sleepReadiness" to "Sleep"
    )

    // Define "self-evident" pairs that get an insightfulness penalty
    // Using string keys that match the HealthData property names
    private val selfEvidentPairs = setOf(
        "malaise-illnessImpact",
        "malaise-soreThroat",
        "sleepQuality-sleepLength",
        "depression-hopelessness"
    )

    /**
     * Main function to calculate all correlations from historical HealthData.
     * This method fetches raw data, performs calculations, and then saves them to the database.
     * @param historicalData A list of your `HealthData` objects for the desired look-back period.
     * @return A list of newly detected/updated `Correlation` objects.
     */
    fun calculateAndStoreAllCorrelations(historicalData: List<HealthData>): List<Correlation> {
        val newlyDetectedCorrelations = mutableListOf<Correlation>()

        if (historicalData.size < 15) {
            Log.d(TAG, "Not enough historical data (${historicalData.size} days) for correlation calculation. Need at least 15.")
            return newlyDetectedCorrelations
        }

        val metricsData = prepareMetricTimeSeries(historicalData)
        val metricNames = metricsData.keys.toList()

        val maxLag = 14

        for (i in metricNames.indices) {
            for (j in i + 1 until metricNames.size) {
                val metricA = metricNames[i]
                val metricB = metricNames[j]

                val seriesA = metricsData[metricA] ?: continue
                val seriesB = metricsData[metricB] ?: continue

                for (lag in 0..maxLag) {
                    val (laggedSeriesA, laggedSeriesB) = createLaggedSeries(seriesA, seriesB, lag)

                    if (laggedSeriesA.size < 10) {
                        Log.d(TAG, "Skipping $metricA vs $metricB (lag $lag): Not enough aligned data points (${laggedSeriesA.size})")
                        continue
                    }

                    val pearsonR = calculatePearsonCorrelation(laggedSeriesA, laggedSeriesB)

                    if (pearsonR.isNaN()) {
                        Log.d(TAG, "Skipping $metricA vs $metricB (lag $lag): Pearson correlation resulted in NaN.")
                        continue
                    }

                    val minSignificantR = 0.5
                    if (kotlin.math.abs(pearsonR) >= minSignificantR) {
                        val confidence = pearsonR * pearsonR
                        val isPositiveCorrelation = pearsonR > 0

                        val insightfulness = calculateInsightfulness(metricA, metricB, lag, isPositiveCorrelation)

                        val newCorrelation = Correlation(
                            symptomA = metricA,
                            symptomB = metricB,
                            lag = lag,
                            isPositiveCorrelation = isPositiveCorrelation,
                            confidence = confidence,
                            insightfulnessScore = insightfulness,
                            preferenceScore = 0,
                            lastCalculatedDate = System.currentTimeMillis()
                        )
                        newlyDetectedCorrelations.add(newCorrelation)
                        insertOrUpdateCorrelation(newCorrelation)
                    }
                }
            }
        }
        return newlyDetectedCorrelations
    }

    /**
     * Extracts and aligns metric values from `HealthData` list into time series.
     * Handles chronological ordering and basic placeholder for missing data.
     *
     * @param historicalData A list of `HealthData` objects, potentially from `HealthDatabaseHelper.fetchDataInDateRange()`.
     * @return A map where keys are metric names (e.g., "malaise") and values are lists of their daily values (Double).
     */
    private fun prepareMetricTimeSeries(historicalData: List<HealthData>): Map<String, List<Double>> {
        val metricTimeSeries = mutableMapOf<String, MutableList<Double>>()

        val allMetricNames = symptomGroups.keys
        allMetricNames.forEach { metricTimeSeries[it] = mutableListOf() }

        val sortedData = historicalData.sortedBy { LocalDate.parse(it.currentDate, DateTimeFormatter.ISO_LOCAL_DATE) }

        if (sortedData.isEmpty()) {
            return emptyMap()
        }

        val startDate = LocalDate.parse(sortedData.first().currentDate, DateTimeFormatter.ISO_LOCAL_DATE)
        val endDate = LocalDate.parse(sortedData.last().currentDate, DateTimeFormatter.ISO_LOCAL_DATE)
        val totalDays = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1

        val dataByDate = sortedData.associateBy { LocalDate.parse(it.currentDate, DateTimeFormatter.ISO_LOCAL_DATE) }

        var currentDay = startDate
        for (i in 0 until totalDays) {
            val dailyLog = dataByDate[currentDay]
            if (dailyLog != null) {
                // Data exists for this day, add actual values
                metricTimeSeries["malaise"]?.add(dailyLog.malaise.toDouble())
                metricTimeSeries["soreThroat"]?.add(dailyLog.soreThroat.toDouble())
                metricTimeSeries["lymphadenopathy"]?.add(dailyLog.lymphadenopathy.toDouble())
                metricTimeSeries["exerciseLevel"]?.add(dailyLog.exerciseLevel.toDouble())
                metricTimeSeries["stressLevel"]?.add(dailyLog.stressLevel.toDouble())
                metricTimeSeries["illnessImpact"]?.add(dailyLog.illnessImpact.toDouble())
                metricTimeSeries["depression"]?.add(dailyLog.depression.toDouble())
                metricTimeSeries["hopelessness"]?.add(dailyLog.hopelessness.toDouble())
                metricTimeSeries["sleepQuality"]?.add(dailyLog.sleepQuality.toDouble())
                metricTimeSeries["sleepLength"]?.add(dailyLog.sleepLength.toDouble())
                metricTimeSeries["sleepReadiness"]?.add(dailyLog.sleepReadiness.toDouble())
            } else {
                // Data missing for this day. Add NaN for all metrics.
                allMetricNames.forEach { metricTimeSeries[it]?.add(Double.NaN) }
            }
            currentDay = currentDay.plusDays(1)
        }

        return metricTimeSeries.filterValues { it.isNotEmpty() }
    }


    private fun createLaggedSeries(seriesA: List<Double>, seriesB: List<Double>, lag: Int): Pair<List<Double>, List<Double>> {
        if (lag >= seriesA.size || lag >= seriesB.size) return emptyList<Double>() to emptyList<Double>()

        val laggedA = seriesA.dropLast(lag)
        val laggedB = seriesB.drop(lag)

        val alignedA = mutableListOf<Double>()
        val alignedB = mutableListOf<Double>()

        for (i in 0 until kotlin.math.min(laggedA.size, laggedB.size)) {
            val valA = laggedA[i]
            val valB = laggedB[i]
            if (!valA.isNaN() && !valB.isNaN()) {
                alignedA.add(valA)
                alignedB.add(valB)
            }
        }
        return alignedA to alignedB
    }

    private fun calculatePearsonCorrelation(x: List<Double>, y: List<Double>): Double {
        if (x.size != y.size || x.size < 2) return Double.NaN

        val n = x.size.toDouble()

        val sumX = x.sum()
        val sumY = y.sum()
        val sumXY = x.zip(y) { xi, yi -> xi * yi }.sum()
        val sumX2 = x.sumOf { it * it }
        val sumY2 = y.sumOf { it * it }

        val numerator = n * sumXY - sumX * sumY
        val denominatorX = n * sumX2 - sumX * sumX
        val denominatorY = n * sumY2 - sumY * sumY

        if (denominatorX <= 0 || denominatorY <= 0) return Double.NaN

        return numerator / kotlin.math.sqrt(denominatorX * denominatorY)
    }

    private fun calculateInsightfulness(
        metricA: String,
        metricB: String,
        lag: Int,
        isPositiveCorrelation: Boolean
    ): Double {
        var score = 1.0

        val groupA = symptomGroups[metricA]
        val groupB = symptomGroups[metricB]

        if (groupA != null && groupB != null && groupA != groupB) {
            score += 2.0
        }

        if (lag > 0) {
            score += 1.0
        }

        val pairKey1 = "$metricA-$metricB"
        val pairKey2 = "$metricB-$metricA"
        if (selfEvidentPairs.contains(pairKey1) || selfEvidentPairs.contains(pairKey2)) {
            score -= 1.5
        }

        return kotlin.math.max(0.0, score)
    }
}
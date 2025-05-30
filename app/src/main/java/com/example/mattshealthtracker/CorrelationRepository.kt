package com.example.mattshealthtracker

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.util.Log
import kotlin.math.abs

class CorrelationRepository(context: Context) {

    private val dbHelper = CorrelationDatabaseHelper(context)

    companion object {
        private const val TAG = "CorrelationRepo"
        const val SUPPRESSION_THRESHOLD = -5
    }

    // --- Core Operations ---

    fun calculateAndStoreAllCorrelations(allHealthData: List<HealthData>): List<Correlation> {
        return dbHelper.calculateAndStoreAllCorrelations(allHealthData)
    }

    fun updatePreference(correlationId: Long, delta: Int) {
        val db = dbHelper.writableDatabase
        val sql = "UPDATE ${CorrelationDatabaseHelper.TABLE_CORRELATIONS} " +
                "SET ${CorrelationDatabaseHelper.COLUMN_PREFERENCE_SCORE} = ${CorrelationDatabaseHelper.COLUMN_PREFERENCE_SCORE} + ? " +
                "WHERE ${CorrelationDatabaseHelper.COLUMN_ID} = ?"

        try {
            db.execSQL(sql, arrayOf(delta.toString(), correlationId.toString()))
            Log.d(TAG, "Updated preference for correlation ID $correlationId by $delta")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating preference for ID $correlationId: ${e.message}", e)
        } finally {
            db.close()
        }
    }

    // --- Query Operations (No changes needed here, as the change is in the helper) ---

    fun getTopCorrelations(limit: Int = 10, minPreferenceThreshold: Int = SUPPRESSION_THRESHOLD): List<Correlation> {
        val db = dbHelper.readableDatabase
        val correlations = mutableListOf<Correlation>()

        val orderBy = "ABS(${CorrelationDatabaseHelper.COLUMN_CONFIDENCE}) DESC, " +
                "${CorrelationDatabaseHelper.COLUMN_INSIGHTFULNESS_SCORE} DESC, " +
                "${CorrelationDatabaseHelper.COLUMN_PREFERENCE_SCORE} DESC"

        val selection = "${CorrelationDatabaseHelper.COLUMN_PREFERENCE_SCORE} >= ?"
        val selectionArgs = arrayOf(minPreferenceThreshold.toString())

        var cursor: Cursor? = null
        try {
            cursor = db.query(
                CorrelationDatabaseHelper.TABLE_CORRELATIONS,
                null,
                selection,
                selectionArgs,
                null,
                null,
                orderBy,
                null
            )

            val allRetrievedCorrelations = cursorToCorrelations(cursor)
            // Apply the filter: keep only the highest strength correlation for each base pair
            val filteredCorrelations = filterForHighestStrengthPerBasePair(allRetrievedCorrelations)

            return filteredCorrelations
                .sortedWith(
                    compareByDescending<Correlation> { abs(it.confidence) }
                        .thenByDescending { it.insightfulnessScore }
                        .thenByDescending { it.preferenceScore }
                )
                .take(limit)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting top correlations: ${e.message}", e)
        } finally {
            cursor?.close()
            db.close()
        }
        return correlations
    }

    fun getAllCorrelations(): List<Correlation> {
        val db = dbHelper.readableDatabase
        val correlations = mutableListOf<Correlation>()

        val orderBy = "ABS(${CorrelationDatabaseHelper.COLUMN_CONFIDENCE}) DESC, " +
                "${CorrelationDatabaseHelper.COLUMN_INSIGHTFULNESS_SCORE} DESC, " +
                "${CorrelationDatabaseHelper.COLUMN_PREFERENCE_SCORE} DESC"

        var cursor: Cursor? = null
        try {
            cursor = db.query(
                CorrelationDatabaseHelper.TABLE_CORRELATIONS,
                null,
                null,
                null,
                null,
                null,
                orderBy,
                null
            )
            val allRetrievedCorrelations = cursorToCorrelations(cursor)
            // Apply the filter: keep only the highest strength correlation for each base pair
            val filteredCorrelations = filterForHighestStrengthPerBasePair(allRetrievedCorrelations)

            return filteredCorrelations
                .sortedWith(
                    compareByDescending<Correlation> { abs(it.confidence) }
                        .thenByDescending { it.insightfulnessScore }
                        .thenByDescending { it.preferenceScore }
                )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all correlations: ${e.message}", e)
        } finally {
            cursor?.close()
            db.close()
        }
        return correlations
    }

    // --- Helper Methods ---

    /**
     * Filters a list of correlations to ensure only the single highest strength correlation
     * is kept for each unique pair of base symptoms (e.g., "Malaise" and "Stress Level"),
     * regardless of their lag, window size, or calculation type.
     * Symptom names are normalized to lowercase and trimmed for robust comparison.
     */
    private fun filterForHighestStrengthPerBasePair(correlations: List<Correlation>): List<Correlation> {
        // The key is now solely based on the base symptom pair, excluding lag,
        // and using normalized (trimmed, lowercase) names.
        Log.d(TAG, "Filtering: Received ${correlations.size} correlations to filter")
        val bestCorrelationsPerPair = mutableMapOf<String, Correlation>()

        for (correlation in correlations) {
            // Normalize symptom names: trim whitespace and convert to lowercase
            val cleanedSymptomA = correlation.baseSymptomA.trim().lowercase()
            val cleanedSymptomB = correlation.baseSymptomB.trim().lowercase()

            // Create a consistent, normalized key for the base symptom pair
            // Always put the alphabetically smaller one first to ensure consistency
            val (s1, s2) = if (cleanedSymptomA <= cleanedSymptomB) {
                cleanedSymptomA to cleanedSymptomB
            } else {
                cleanedSymptomB to cleanedSymptomA
            }
            // KEY: Only includes normalized base symptom names
            val pairKey = "$s1-$s2"

            val existingBest = bestCorrelationsPerPair[pairKey]

            // If no correlation exists for this pair, or the current one is stronger, update it.
            if (existingBest == null || abs(correlation.confidence) >= abs(existingBest.confidence)) {
                bestCorrelationsPerPair[pairKey] = correlation
                Log.d(TAG, "Filtering: Selected '${correlation.getDisplayNameA()}' vs '${correlation.getDisplayNameB()}' (Conf: ${"%.2f".format(correlation.confidence)}) as best for pair '$pairKey'.")
            } else {
                Log.d(TAG, "Filtering: Keeping '${existingBest.getDisplayNameA()}' vs '${existingBest.getDisplayNameB()}' (Conf: ${"%.2f".format(existingBest.confidence)}) for pair '$pairKey' over '${correlation.getDisplayNameA()}' vs '${correlation.getDisplayNameB()}' (Conf: ${"%.2f".format(correlation.confidence)}).")
            }
        }
        return bestCorrelationsPerPair.values.toList()
    }

    /**
     * Helper function to convert a Cursor to a list of Correlation objects.
     */
    private fun cursorToCorrelations(cursor: Cursor?): List<Correlation> {
        val correlations = mutableListOf<Correlation>()
        cursor?.use {
            if (it.moveToFirst()) {
                val idIndex = it.getColumnIndexOrThrow(CorrelationDatabaseHelper.COLUMN_ID)
                val baseSymptomAIndex = it.getColumnIndexOrThrow(CorrelationDatabaseHelper.COLUMN_BASE_SYMPTOM_A)
                val windowSizeAIndex = it.getColumnIndexOrThrow(CorrelationDatabaseHelper.COLUMN_WINDOW_SIZE_A)
                val calcTypeAIndex = it.getColumnIndexOrThrow(CorrelationDatabaseHelper.COLUMN_CALC_TYPE_A)
                val baseSymptomBIndex = it.getColumnIndexOrThrow(CorrelationDatabaseHelper.COLUMN_BASE_SYMPTOM_B)
                val windowSizeBIndex = it.getColumnIndexOrThrow(CorrelationDatabaseHelper.COLUMN_WINDOW_SIZE_B)
                val calcTypeBIndex = it.getColumnIndexOrThrow(CorrelationDatabaseHelper.COLUMN_CALC_TYPE_B)
                val lagIndex = it.getColumnIndexOrThrow(CorrelationDatabaseHelper.COLUMN_LAG)
                val isPositiveIndex = it.getColumnIndexOrThrow(CorrelationDatabaseHelper.COLUMN_IS_POSITIVE_CORRELATION)
                val confidenceIndex = it.getColumnIndexOrThrow(CorrelationDatabaseHelper.COLUMN_CONFIDENCE)
                val insightfulnessIndex = it.getColumnIndexOrThrow(CorrelationDatabaseHelper.COLUMN_INSIGHTFULNESS_SCORE)
                val preferenceIndex = it.getColumnIndexOrThrow(CorrelationDatabaseHelper.COLUMN_PREFERENCE_SCORE)
                val lastCalculatedIndex = it.getColumnIndexOrThrow(CorrelationDatabaseHelper.COLUMN_LAST_CALCULATED_DATE)

                do {
                    correlations.add(
                        Correlation(
                            id = it.getLong(idIndex),
                            baseSymptomA = it.getString(baseSymptomAIndex),
                            windowSizeA = it.getInt(windowSizeAIndex),
                            calcTypeA = it.getString(calcTypeAIndex),
                            baseSymptomB = it.getString(baseSymptomBIndex),
                            windowSizeB = it.getInt(windowSizeBIndex),
                            calcTypeB = it.getString(calcTypeBIndex),
                            lag = it.getInt(lagIndex),
                            isPositiveCorrelation = it.getInt(isPositiveIndex) == 1,
                            confidence = it.getFloat(confidenceIndex),
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

    fun close() {
        dbHelper.close()
    }
}
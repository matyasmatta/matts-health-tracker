package com.example.mattshealthtracker

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.util.Log

class CorrelationRepository(context: Context) {

    private val dbHelper = CorrelationDatabaseHelper(context)

    companion object {
        private const val TAG = "CorrelationRepo"
        // Define a threshold for preference score to consider an item "suppressed"
        // Correlations with preferenceScore below this will be filtered out by default
        const val SUPPRESSION_THRESHOLD = -5 // Example: 5 thumbs down
    }

    // --- Core Operations ---

    /**
     * Triggers the comprehensive correlation calculation and storage process
     * managed by CorrelationDatabaseHelper.
     * This is the primary way to get correlations into the database now.
     * This method doesn't take a single Correlation object for insert/update,
     * as the helper handles the full calculation and cherry-picking internally.
     */
    fun calculateAndStoreAllCorrelations(allHealthData: List<HealthData>): List<Correlation> {
        return dbHelper.calculateAndStoreAllCorrelations(allHealthData)
    }

    /**
     * Updates the preference score for a specific correlation identified by its ID.
     */
    fun updatePreference(correlationId: Long, delta: Int) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            // Fix: Directly use SQL to increment/decrement the existing value
            // This is safer than reading it into Kotlin, incrementing, and writing back,
            // as it avoids race conditions if multiple updates happen concurrently.
            // However, ContentValue.put() does not directly accept SQL expressions.
            // We need to use execSQL for this specific kind of update.
        }

        // Using execSQL for direct SQL update for preference
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

    // --- Query Operations ---

    /**
     * Retrieves correlations for the "Top N" display, ordered by composite score.
     * Filters out correlations below the preference suppression threshold.
     */
    fun getTopCorrelations(limit: Int = 10, minPreferenceThreshold: Int = SUPPRESSION_THRESHOLD): List<Correlation> {
        val db = dbHelper.readableDatabase
        val correlations = mutableListOf<Correlation>()

        // Order by Insightfulness (desc), then Confidence (abs desc), then Preference (desc)
        // Use ABS(confidence) for sorting strength regardless of positive/negative direction
        val orderBy = "ABS(${CorrelationDatabaseHelper.COLUMN_CONFIDENCE}) DESC, " +
                "${CorrelationDatabaseHelper.COLUMN_INSIGHTFULNESS_SCORE} DESC, " +
                "${CorrelationDatabaseHelper.COLUMN_PREFERENCE_SCORE} DESC"

        val selection = "${CorrelationDatabaseHelper.COLUMN_PREFERENCE_SCORE} >= ?"
        val selectionArgs = arrayOf(minPreferenceThreshold.toString())

        var cursor: Cursor? = null
        try {
            cursor = db.query(
                CorrelationDatabaseHelper.TABLE_CORRELATIONS,
                null, // All columns
                selection,
                selectionArgs,
                null, // Group by
                null, // Having
                orderBy,
                limit.toString() // LIMIT clause
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

    /**
     * Retrieves all correlations, including those below the suppression threshold,
     * ordered by a composite score. This is for your "Show All Correlations" list.
     */
    fun getAllCorrelations(): List<Correlation> {
        val db = dbHelper.readableDatabase
        val correlations = mutableListOf<Correlation>()

        // Order by Insightfulness (desc), then Confidence (abs desc), then Preference (desc)
        val orderBy = "ABS(${CorrelationDatabaseHelper.COLUMN_CONFIDENCE}) DESC, " +
                "${CorrelationDatabaseHelper.COLUMN_INSIGHTFULNESS_SCORE} DESC, " +
                "${CorrelationDatabaseHelper.COLUMN_PREFERENCE_SCORE} DESC"

        var cursor: Cursor? = null
        try {
            cursor = db.query(
                CorrelationDatabaseHelper.TABLE_CORRELATIONS,
                null, // All columns
                null, // No selection (no WHERE clause)
                null, // No selection arguments
                null, // Group by
                null, // Having
                orderBy,
                null // No LIMIT
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

    // --- Helper Methods ---

    /**
     * Helper function to convert a Cursor to a list of Correlation objects.
     * This is updated to read the new granular columns.
     */
    private fun cursorToCorrelations(cursor: Cursor?): List<Correlation> {
        val correlations = mutableListOf<Correlation>()
        cursor?.use {
            if (it.moveToFirst()) {
                val idIndex = it.getColumnIndexOrThrow(CorrelationDatabaseHelper.COLUMN_ID)
                // Get indices for the new granular columns
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
                            // Map to new granular fields
                            baseSymptomA = it.getString(baseSymptomAIndex),
                            windowSizeA = it.getInt(windowSizeAIndex),
                            calcTypeA = it.getString(calcTypeAIndex),
                            baseSymptomB = it.getString(baseSymptomBIndex),
                            windowSizeB = it.getInt(windowSizeBIndex),
                            calcTypeB = it.getString(calcTypeBIndex),
                            lag = it.getInt(lagIndex),
                            isPositiveCorrelation = it.getInt(isPositiveIndex) == 1, // Store as Int, retrieve as Boolean
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

    /**
     * Closes the database helper. Call this when the app context is no longer needed (e.g., in onDestroy).
     */
    fun close() {
        dbHelper.close()
    }
}
package com.example.mattshealthtracker

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log

class CorrelationRepository(context: Context) {

    private val dbHelper = CorrelationDatabaseHelper(context) // Changed to CorrelationDatabaseHelper

    companion object {
        private const val TAG = "CorrelationRepo"
        // Define a threshold for preference score to consider an item "suppressed"
        // Correlations with preferenceScore below this will be filtered out by default
        const val SUPPRESSION_THRESHOLD = -5 // Example: 5 thumbs down
    }

    // --- Core Operations ---

    /**
     * Inserts a new correlation or updates an existing one based on its unique key.
     * This uses the `ON CONFLICT REPLACE` defined in the table creation SQL.
     */
    fun insertOrUpdateCorrelation(correlation: Correlation) {
        val db = dbHelper.writableDatabase // Get a writable database instance
        val values = ContentValues().apply {
            // Ensure symptomA and symptomB are stored in consistent alphabetical order
            // to correctly match the UNIQUE constraint.
            put(CorrelationDatabaseHelper.COLUMN_SYMPTOM_A, if (correlation.symptomA <= correlation.symptomB) correlation.symptomA else correlation.symptomB)
            put(CorrelationDatabaseHelper.COLUMN_SYMPTOM_B, if (correlation.symptomA <= correlation.symptomB) correlation.symptomB else correlation.symptomA)
            put(CorrelationDatabaseHelper.COLUMN_LAG, correlation.lag)
            put(CorrelationDatabaseHelper.COLUMN_IS_POSITIVE_CORRELATION, if (correlation.isPositiveCorrelation) 1 else 0)
            put(CorrelationDatabaseHelper.COLUMN_CONFIDENCE, correlation.confidence)
            put(CorrelationDatabaseHelper.COLUMN_INSIGHTFULNESS_SCORE, correlation.insightfulnessScore)
            // When updating an existing correlation, we want to retain its current preferenceScore.
            // When inserting a new one, it starts at 0 (from the Correlation data class default).
            // We fetch the existing preference score first to preserve it.
            val existingPreference = getPreferenceScoreForCorrelation(correlation)
            put(CorrelationDatabaseHelper.COLUMN_PREFERENCE_SCORE, existingPreference ?: correlation.preferenceScore)
            put(CorrelationDatabaseHelper.COLUMN_LAST_CALCULATED_DATE, System.currentTimeMillis())
        }

        db.beginTransaction()
        try {
            // Using insertWithOnConflict with CONFLICT_REPLACE is a straightforward way to upsert
            val result = db.insertWithOnConflict(
                CorrelationDatabaseHelper.TABLE_CORRELATIONS,
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
            db.close() // Close the database after each operation
        }
    }

    /**
     * Updates the preference score for a specific correlation identified by its ID.
     */
    fun updatePreference(correlationId: Long, delta: Int) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            // Directly use SQL to increment/decrement the existing value
            put(CorrelationDatabaseHelper.COLUMN_PREFERENCE_SCORE, "(${CorrelationDatabaseHelper.COLUMN_PREFERENCE_SCORE} + $delta)")
        }

        val rowsAffected = db.update(
            CorrelationDatabaseHelper.TABLE_CORRELATIONS,
            values,
            "${CorrelationDatabaseHelper.COLUMN_ID} = ?",
            arrayOf(correlationId.toString())
        )
        if (rowsAffected > 0) {
            Log.d(TAG, "Updated preference for correlation ID $correlationId by $delta")
        } else {
            Log.w(TAG, "No correlation found with ID $correlationId to update preference.")
        }
        db.close()
    }

    // --- Query Operations ---

    /**
     * Retrieves correlations for the "Top N" display, ordered by composite score.
     * Filters out correlations below the preference suppression threshold.
     */
    fun getTopCorrelations(limit: Int = 10, minPreferenceThreshold: Int = SUPPRESSION_THRESHOLD): List<Correlation> {
        val db = dbHelper.readableDatabase
        val correlations = mutableListOf<Correlation>()

        // Order by Insightfulness (desc), then Confidence (desc), then Preference (desc)
        val orderBy = "${CorrelationDatabaseHelper.COLUMN_INSIGHTFULNESS_SCORE} DESC, " +
                "${CorrelationDatabaseHelper.COLUMN_CONFIDENCE} DESC, " +
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

        // Order by Insightfulness (desc), then Confidence (desc), then Preference (desc)
        val orderBy = "${CorrelationDatabaseHelper.COLUMN_INSIGHTFULNESS_SCORE} DESC, " +
                "${CorrelationDatabaseHelper.COLUMN_CONFIDENCE} DESC, " +
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
     * Reads the preference score for an existing correlation to preserve it during UPSERT.
     * This query uses the unique fields to find the existing entry.
     */
    private fun getPreferenceScoreForCorrelation(correlation: Correlation): Int? {
        val db = dbHelper.readableDatabase
        var preference: Int? = null

        val (s1, s2) = if (correlation.symptomA <= correlation.symptomB) {
            correlation.symptomA to correlation.symptomB
        } else {
            correlation.symptomB to correlation.symptomA
        }

        val selection = "${CorrelationDatabaseHelper.COLUMN_SYMPTOM_A} = ? AND " +
                "${CorrelationDatabaseHelper.COLUMN_SYMPTOM_B} = ? AND " +
                "${CorrelationDatabaseHelper.COLUMN_LAG} = ? AND " +
                "${CorrelationDatabaseHelper.COLUMN_IS_POSITIVE_CORRELATION} = ?"
        val selectionArgs = arrayOf(
            s1,
            s2,
            correlation.lag.toString(),
            if (correlation.isPositiveCorrelation) "1" else "0"
        )

        var cursor: Cursor? = null
        try {
            cursor = db.query(
                CorrelationDatabaseHelper.TABLE_CORRELATIONS,
                arrayOf(CorrelationDatabaseHelper.COLUMN_PREFERENCE_SCORE), // Only get preference
                selection,
                selectionArgs,
                null, null, null, null
            )
            if (cursor.moveToFirst()) {
                val preferenceIndex = cursor.getColumnIndexOrThrow(CorrelationDatabaseHelper.COLUMN_PREFERENCE_SCORE)
                preference = cursor.getInt(preferenceIndex)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting existing preference score: ${e.message}", e)
        } finally {
            cursor?.close()
        }
        return preference
    }

    /**
     * Helper function to convert a Cursor to a list of Correlation objects.
     */
    private fun cursorToCorrelations(cursor: Cursor?): List<Correlation> {
        val correlations = mutableListOf<Correlation>()
        cursor?.use {
            if (it.moveToFirst()) {
                val idIndex = it.getColumnIndexOrThrow(CorrelationDatabaseHelper.COLUMN_ID)
                val symptomAIndex = it.getColumnIndexOrThrow(CorrelationDatabaseHelper.COLUMN_SYMPTOM_A)
                val symptomBIndex = it.getColumnIndexOrThrow(CorrelationDatabaseHelper.COLUMN_SYMPTOM_B)
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
                            symptomA = it.getString(symptomAIndex),
                            symptomB = it.getString(symptomBIndex),
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

    /**
     * Closes the database helper. Call this when the app context is no longer needed (e.g., in onDestroy).
     */
    fun close() {
        dbHelper.close()
    }
}
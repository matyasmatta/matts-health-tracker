package com.example.mattshealthtracker

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.os.Environment // Import Environment for external storage paths
import android.util.Log
import java.io.File // For file operations
import java.io.FileWriter // For writing to CSV
import kotlin.math.abs

class CorrelationRepository(private val context: Context) { // Make context a private val property

    private val dbHelper = CorrelationDatabaseHelper(context)

    companion object {
        private const val TAG = "CorrelationRepo"
        const val SUPPRESSION_THRESHOLD = -5
    }

    // --- Core Operations ---

    // This method will now trigger the CSV export internally after calculation
    fun calculateAndStoreAllCorrelations(allHealthData: List<HealthData>): List<Correlation> {
        val calculatedCorrelations = dbHelper.calculateAndStoreAllCorrelations(allHealthData)

        // After successful calculation and storage, export the full database
        // Note: dbHelper.calculateAndStoreAllCorrelations handles its own DB closing.
        // We'll export from a new readable instance to ensure we get the latest.
        val exportedFilePath = exportCorrelationsToCsv() // Call the new export function
        if (exportedFilePath != null) {
            Log.d(TAG, "Database export complete. File: $exportedFilePath")
        } else {
            Log.e(TAG, "Database export failed.")
        }

        return calculatedCorrelations // Return the list of correlations that were stored/updated
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

    // --- Query Operations ---

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
            Log.d(TAG, "Retrieved ${allRetrievedCorrelations.size} correlations from DB before filtering (getTop).")

            val filteredCorrelations = filterForHighestStrengthPerBasePair(allRetrievedCorrelations)
            Log.d(TAG, "Filtered down to ${filteredCorrelations.size} correlations after filtering (getTop).")

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
                null, // No selection for getAllCorrelations
                null, // No selection arguments
                null,
                null,
                orderBy,
                null
            )
            val allRetrievedCorrelations = cursorToCorrelations(cursor)
            Log.d(TAG, "Retrieved ${allRetrievedCorrelations.size} correlations from DB before filtering (getAll).")

            val filteredCorrelations = filterForHighestStrengthPerBasePair(allRetrievedCorrelations)
            Log.d(TAG, "Filtered down to ${filteredCorrelations.size} correlations after filtering (getAll).")

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
        val bestCorrelationsPerPair = mutableMapOf<String, Correlation>()

        Log.d(TAG, "Starting filterForHighestStrengthPerBasePair with ${correlations.size} input correlations.")

        for (correlation in correlations) {
            // Normalize symptom names: trim whitespace and convert to lowercase
            val cleanedSymptomA = correlation.baseSymptomA.trim().toLowerCase()
            val cleanedSymptomB = correlation.baseSymptomB.trim().toLowerCase()

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
            if (existingBest == null || abs(correlation.confidence) > abs(existingBest.confidence)) {
                bestCorrelationsPerPair[pairKey] = correlation
                Log.d(TAG, "Filtering: Selected '${correlation.getDisplayNameA()}' vs '${correlation.getDisplayNameB()}' (Conf: ${"%.2f".format(correlation.confidence)}) as best for pair '$pairKey'.")
            } else {
                Log.d(TAG, "Filtering: Keeping '${existingBest.getDisplayNameA()}' vs '${existingBest.getDisplayNameB()}' (Conf: ${"%.2f".format(existingBest.confidence)}) for pair '$pairKey' over '${correlation.getDisplayNameA()}' vs '${correlation.getDisplayNameB()}' (Conf: ${"%.2f".format(correlation.confidence)}).")
            }
        }
        Log.d(TAG, "Finished filterForHighestStrengthPerBasePair. Resulting size: ${bestCorrelationsPerPair.size}")
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
                            insightfulnessScore = it.getFloat(insightfulnessIndex),
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
     * Exports the entire correlations table to a CSV file.
     * The file is saved in the app's external files directory (Downloads).
     * @return The absolute path to the created CSV file, or null if an error occurred.
     */
    fun exportCorrelationsToCsv(): String? {
        val db = dbHelper.readableDatabase
        var cursor: Cursor? = null
        var filePath: String? = null
        try {
            // Query all data from the correlations table
            cursor = db.rawQuery("SELECT * FROM ${CorrelationDatabaseHelper.TABLE_CORRELATIONS}", null)

            if (cursor == null || cursor.count == 0) {
                Log.d(TAG, "No data to export for correlations.")
                return null
            }

            // Get the app-specific external downloads directory
            // This directory is located at Android/data/com.your.package.name/files/Downloads
            // No runtime permissions are needed for this location.
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            downloadsDir?.mkdirs() // Ensure the directory exists

            val fileName = "correlations_export_${System.currentTimeMillis()}.csv"
            val file = File(downloadsDir, fileName)
            filePath = file.absolutePath

            FileWriter(file).use { writer -> // Use 'use' to ensure writer is closed
                // Write header row (column names)
                val columnNames = cursor.columnNames
                writer.append(columnNames.joinToString(",") { it.replace(",", "") }) // Sanitize commas in column names
                writer.append("\n")

                // Write data rows
                while (cursor.moveToNext()) {
                    val rowData = mutableListOf<String>()
                    for (i in columnNames.indices) {
                        val value = cursor.getString(i) ?: "" // Get string value, default to empty string if null
                        rowData.add(escapeCsv(value)) // Escape values for CSV (handle commas, quotes, newlines)
                    }
                    writer.append(rowData.joinToString(","))
                    writer.append("\n")
                }
            } // writer.close() is called automatically by .use{}

            Log.d(TAG, "Correlations exported successfully to: $filePath")
            return filePath

        } catch (e: Exception) {
            Log.e(TAG, "Error exporting correlations to CSV: ${e.message}", e)
            return null
        } finally {
            cursor?.close()
            // db.close() is managed by CorrelationDatabaseHelper's own methods
            // or by the caller if this db instance was passed in.
            // In this specific helper, we opened it, so we close it here.
            db.close()
        }
    }

    // Helper function to escape values for CSV (handle commas, double quotes, and newlines)
    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"${value.replace("\"", "\"\"")}\"" // Enclose in quotes and escape internal quotes
        }
        return value
    }

    fun close() {
        dbHelper.close()
    }
}
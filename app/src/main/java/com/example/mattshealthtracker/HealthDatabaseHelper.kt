package com.example.mattshealthtracker

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.io.File
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.IOException
import com.example.mattshealthtracker.AppGlobals
import kotlin.compareTo

class HealthDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "health_tracker.db"
        const val DATABASE_VERSION = 1
        const val TABLE_NAME = "health_data"
        const val COLUMN_DATE = "date"
        const val COLUMN_MALAIS = "malaise"
        const val COLUMN_SORE_THROAT = "sore_throat"
        const val COLUMN_LYMPHADENOPATHY = "lymphadenopathy"
        const val COLUMN_EXERCISE_LEVEL = "exercise_level"
        const val COLUMN_STRESS_LEVEL = "stress_level"
        const val COLUMN_ILLNESS_IMPACT = "illness_impact"
        const val COLUMN_DEPRESSION = "depression"
        const val COLUMN_HOPELESSNESS = "hopelessness"
        const val COLUMN_NOTES = "notes"
    }

    // Called when the database is created for the first time
    override fun onCreate(db: SQLiteDatabase?) {
        val CREATE_TABLE = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_DATE STRING PRIMARY KEY,
                $COLUMN_MALAIS REAL,
                $COLUMN_SORE_THROAT REAL,
                $COLUMN_LYMPHADENOPATHY REAL,
                $COLUMN_EXERCISE_LEVEL REAL,
                $COLUMN_STRESS_LEVEL REAL,
                $COLUMN_ILLNESS_IMPACT REAL,
                $COLUMN_DEPRESSION REAL,
                $COLUMN_HOPELESSNESS REAL,
                $COLUMN_NOTES TEXT
            )
        """
        db?.execSQL(CREATE_TABLE)
    }

    // Called when the database needs to be upgraded
    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    // Insert data into the database
    fun insertData(data: HealthData) {
        val db = writableDatabase

        val values = ContentValues().apply {
            put(COLUMN_MALAIS, data.malaise)
            put(COLUMN_SORE_THROAT, data.soreThroat)
            put(COLUMN_LYMPHADENOPATHY, data.lymphadenopathy)
            put(COLUMN_EXERCISE_LEVEL, data.exerciseLevel)
            put(COLUMN_STRESS_LEVEL, data.stressLevel)
            put(COLUMN_ILLNESS_IMPACT, data.illnessImpact)
            put(COLUMN_DEPRESSION, data.depression)
            put(COLUMN_HOPELESSNESS, data.hopelessness)
            put(COLUMN_NOTES, data.notes)
        }

        // Insert data into the table
        val result = db.insert(TABLE_NAME, null, values)
        if (result == -1L) {
            Log.e("DatabaseError", "Error inserting data")
        } else {
            Log.d("Database", "Data inserted successfully")
        }
        db.close()
    }

    fun fetchHealthDataForDate(date: String): HealthData? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_NAME,
            null,
            "$COLUMN_DATE = ?",
            arrayOf(date),
            null,
            null,
            null
        )

        var healthData: HealthData? = null
        if (cursor.moveToFirst()) {
            healthData = HealthData(
                currentDate = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DATE)),
                malaise = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_MALAIS)),
                soreThroat = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_SORE_THROAT)),
                lymphadenopathy = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_LYMPHADENOPATHY)),
                exerciseLevel = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_EXERCISE_LEVEL)),
                stressLevel = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_STRESS_LEVEL)),
                illnessImpact = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_ILLNESS_IMPACT)),
                depression = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_DEPRESSION)),
                hopelessness = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_HOPELESSNESS)),
                notes = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NOTES))
            )
        }

        cursor.close()
        db.close()
        return healthData
    }

    fun insertOrUpdateHealthData(data: HealthData) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_DATE, data.currentDate)
            put(COLUMN_MALAIS, data.malaise)
            put(COLUMN_SORE_THROAT, data.soreThroat)
            put(COLUMN_LYMPHADENOPATHY, data.lymphadenopathy)
            put(COLUMN_EXERCISE_LEVEL, data.exerciseLevel)
            put(COLUMN_STRESS_LEVEL, data.stressLevel)
            put(COLUMN_ILLNESS_IMPACT, data.illnessImpact)
            put(COLUMN_DEPRESSION, data.depression)
            put(COLUMN_HOPELESSNESS, data.hopelessness)
            put(COLUMN_NOTES, data.notes)
        }

        val result = db.insertWithOnConflict(
            TABLE_NAME,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )

        if (result == -1L) {
            Log.e("DatabaseError", "Error inserting or updating data")
        } else {
            Log.d("Database", "Data inserted or updated successfully")
        }
        db.close()
    }

    // LEAVE THESE ALONE
    // Fetch all data from the database
    fun fetchAllData(): List<HealthData> {
        val db = readableDatabase
        val cursor: Cursor = db.query(
            TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_DATE ASC"  // Sort in ascending order
        )

        val data = mutableListOf<HealthData>()

        if (cursor.moveToFirst()) {
            do {
                // Validate column indices
                val dateIndex = cursor.getColumnIndexOrThrow(COLUMN_DATE)
                val malaiseIndex = cursor.getColumnIndexOrThrow(COLUMN_MALAIS)
                val soreThroatIndex = cursor.getColumnIndexOrThrow(COLUMN_SORE_THROAT)
                val lymphadenopathyIndex = cursor.getColumnIndexOrThrow(COLUMN_LYMPHADENOPATHY)
                val exerciseLevelIndex = cursor.getColumnIndexOrThrow(COLUMN_EXERCISE_LEVEL)
                val stressLevelIndex = cursor.getColumnIndexOrThrow(COLUMN_STRESS_LEVEL)
                val illnessImpactIndex = cursor.getColumnIndexOrThrow(COLUMN_ILLNESS_IMPACT)
                val depressionIndex = cursor.getColumnIndexOrThrow(COLUMN_DEPRESSION)
                val hopelessnessIndex = cursor.getColumnIndexOrThrow(COLUMN_HOPELESSNESS)
                val notesIndex = cursor.getColumnIndexOrThrow(COLUMN_NOTES)

                // If any of the column indices are -1, something went wrong
                if (dateIndex < 0 || malaiseIndex < 0 || soreThroatIndex < 0 || lymphadenopathyIndex < 0) {
                    Log.e("DatabaseError", "Invalid column indices for fetching data.")
                    return emptyList() // Or handle as needed
                }

                val dataItem = HealthData(
                    currentDate = cursor.getString(dateIndex),
                    malaise = cursor.getFloat(malaiseIndex),
                    soreThroat = cursor.getFloat(soreThroatIndex),
                    lymphadenopathy = cursor.getFloat(lymphadenopathyIndex),
                    exerciseLevel = cursor.getFloat(exerciseLevelIndex),
                    stressLevel = cursor.getFloat(stressLevelIndex),
                    illnessImpact = cursor.getFloat(illnessImpactIndex),
                    depression = cursor.getFloat(depressionIndex),
                    hopelessness = cursor.getFloat(hopelessnessIndex),
                    notes = cursor.getString(notesIndex)
                )
                data.add(dataItem)

            } while (cursor.moveToNext())
        }

        cursor.close()
        db.close()

        return data
    }

    // Export data to a CSV file
    fun exportToCSV(context: Context) {
        val data = fetchAllData()
        val csvFile = File(context.getExternalFilesDir(null), "health_data.csv")

        try {
            val writer = BufferedWriter(FileWriter(csvFile))

            // Write header
            writer.write("Date,Malaise,Sore Throat,Lymphadenopathy,Exercise Level,Stress Level,Illness Impact,Depression,Hopelessness,Notes\n")

            // Write data
            for (entry in data) {
                // Wrap the notes column in quotes to handle commas or special characters
                val sanitizedNotes = "\"${entry.notes.replace("\"", "\"\"")}\""
                writer.write("${entry.currentDate},${entry.malaise},${entry.soreThroat},${entry.lymphadenopathy},${entry.exerciseLevel},${entry.stressLevel},${entry.illnessImpact},${entry.depression},${entry.hopelessness},$sanitizedNotes\n")
            }

            writer.close()
            Log.d("CSVExport", "Exported data to CSV file.")
        } catch (e: IOException) {
            Log.e("CSVExportError", "Error exporting data to CSV: ${e.message}")
        }
    }
}

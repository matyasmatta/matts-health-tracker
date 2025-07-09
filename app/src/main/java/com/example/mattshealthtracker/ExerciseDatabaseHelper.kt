package com.example.mattshealthtracker

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import com.example.mattshealthtracker.AppGlobals
import com.example.mattshealthtracker.MedicationDatabaseHelper.Companion.COLUMN_DATE

class ExerciseDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "exercise_tracker.db"
        // IMPORTANT: Increment DATABASE_VERSION when you change the schema
        const val DATABASE_VERSION = 2 // <--- Increment this!
        const val TABLE_NAME = "exercise"
        const val COLUMN_ID = "id"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_PUSHUP = "pushups"
        const val COLUMN_POSTURE = "posture"
        // New columns for breathing exercises
        const val COLUMN_RELAX_MINUTES = "relax_minutes"
        const val COLUMN_SLEEP_MINUTES = "sleep_minutes"
        const val COLUMN_NAP_MINUTES = "nap_minutes"
        const val COLUMN_FOCUS_MINUTES = "focus_minutes"
    }

    // Called when the database is created for the first time
    override fun onCreate(db: SQLiteDatabase?) {
        val CREATE_TABLE = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TIMESTAMP DATETIME DEFAULT CURRENT_TIMESTAMP,
                $COLUMN_PUSHUP INTEGER,
                $COLUMN_POSTURE INTEGER,
                $COLUMN_RELAX_MINUTES INTEGER DEFAULT 0,
                $COLUMN_SLEEP_MINUTES INTEGER DEFAULT 0,
                $COLUMN_NAP_MINUTES INTEGER DEFAULT 0,
                $COLUMN_FOCUS_MINUTES INTEGER DEFAULT 0
            )
        """
        db?.execSQL(CREATE_TABLE)
    }

    // Called when the database needs to be upgraded
    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        // Handle schema changes
        if (oldVersion < 2) {
            // Add new columns for breathing exercises
            db?.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_RELAX_MINUTES INTEGER DEFAULT 0")
            db?.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_SLEEP_MINUTES INTEGER DEFAULT 0")
            db?.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_NAP_MINUTES INTEGER DEFAULT 0")
            db?.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_FOCUS_MINUTES INTEGER DEFAULT 0")
        }
        // If you have future versions, add more if (oldVersion < X) blocks
    }


    // Insert data into the database
    fun insertOrUpdateData(data: ExerciseData) {
        val db = writableDatabase
        // AppGlobals.openedDay is already formatted as "YYYY-MM-DD" which is good for DATE comparison
        val date = AppGlobals.openedDay

        // Check if an entry for the current day exists
        val cursor = db.query(
            TABLE_NAME,
            null,
            "DATE($COLUMN_TIMESTAMP) = ?", // Use DATE() function to match YYYY-MM-DD
            arrayOf(date),
            null,
            null,
            null
        )

        val values = ContentValues().apply {
            put(COLUMN_TIMESTAMP, date)
            put(COLUMN_PUSHUP, data.pushups)
            put(COLUMN_POSTURE, data.posture)
            // Add new breathing exercise data
            put(COLUMN_RELAX_MINUTES, data.relaxMinutes)
            put(COLUMN_SLEEP_MINUTES, data.sleepMinutes)
            put(COLUMN_NAP_MINUTES, data.napMinutes)
            put(COLUMN_FOCUS_MINUTES, data.focusMinutes)
        }

        if (cursor.moveToFirst()) {
            // Update the existing record
            val result = db.update(
                TABLE_NAME,
                values,
                "DATE($COLUMN_TIMESTAMP) = ?",
                arrayOf(date)
            )
            if (result == -1) {
                Log.e("DatabaseError", "Error updating data for date: $date")
            } else {
                Log.d("Database", "Data updated successfully for date: $date")
            }
        } else {
            // Insert a new record
            val result = db.insert(TABLE_NAME, null, values)
            if (result == -1L) {
                Log.e("DatabaseError", "Error inserting data for date: $date")
            } else {
                Log.d("Database", "Data inserted successfully for date: $date")
            }
        }

        cursor.close()
        db.close()
    }


    // Fetch data for today's date
    fun fetchExerciseDataForToday(): ExerciseData? {
        val db = readableDatabase
        val todayDate = AppGlobals.currentDay // Assuming AppGlobals.currentDay is "YYYY-MM-DD"
        val query = "SELECT * FROM $TABLE_NAME WHERE DATE($COLUMN_TIMESTAMP) = ?"
        val cursor = db.rawQuery(query, arrayOf(todayDate))

        var data: ExerciseData? = null
        if (cursor.moveToFirst()) {
            data = cursorToExerciseData(cursor)
        }

        cursor.close()
        db.close()
        return data
    }

    // Fetch data for any other specific date
    fun fetchExerciseDataForDate(date: String): ExerciseData? {
        val db = readableDatabase
        val query = "SELECT * FROM $TABLE_NAME WHERE DATE($COLUMN_TIMESTAMP) = ?"
        val cursor = db.rawQuery(query, arrayOf(date))

        var data: ExerciseData? = null
        if (cursor.moveToFirst()) {
            data = cursorToExerciseData(cursor)
        }

        cursor.close()
        db.close()
        return data
    }

    // Helper function to convert Cursor to ExerciseData
    private fun cursorToExerciseData(cursor: Cursor): ExerciseData {
        val timestamp = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
        val pushups = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PUSHUP))
        val posture = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_POSTURE))
        // Retrieve new breathing exercise data
        val relaxMinutes = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_RELAX_MINUTES))
        val sleepMinutes = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SLEEP_MINUTES))
        val napMinutes = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_NAP_MINUTES))
        val focusMinutes = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_FOCUS_MINUTES))

        return ExerciseData(
            currentDate = timestamp,
            pushups = pushups,
            posture = posture,
            relaxMinutes = relaxMinutes,
            sleepMinutes = sleepMinutes,
            napMinutes = napMinutes,
            focusMinutes = focusMinutes
        )
    }


    // Fetch all data from the database
    fun fetchAllData(): List<ExerciseData> {
        val db = readableDatabase
        val cursor: Cursor = db.query(TABLE_NAME, null, null, null, null, null, "$COLUMN_TIMESTAMP ASC")

        val data = mutableListOf<ExerciseData>()

        if (cursor.moveToFirst()) {
            do {
                data.add(cursorToExerciseData(cursor))
            } while (cursor.moveToNext())
        }

        cursor.close()
        db.close()

        return data
    }

    // Export data to a CSV file
    fun exportToCSV(context: Context) {
        val data = fetchAllData()
        val csvFile = File(context.getExternalFilesDir(null), "exercise_data.csv")

        try {
            val writer = BufferedWriter(FileWriter(csvFile))

            // Write header - updated with new columns
            writer.write("Date,Pushups,Posture,RelaxMinutes,SleepMinutes,NapMinutes,FocusMinutes\n")

            // Write each row of data - updated to include new columns
            for (item in data) {
                writer.write("${item.currentDate},${item.pushups},${item.posture}," +
                        "${item.relaxMinutes},${item.sleepMinutes},${item.napMinutes},${item.focusMinutes}\n")
            }

            writer.close()
            Log.d("CSVExport", "Exported data to CSV file at ${csvFile.absolutePath}")
        } catch (e: IOException) {
            Log.e("CSVExportError", "Error exporting data to CSV: ${e.message}")
        }
    }
}
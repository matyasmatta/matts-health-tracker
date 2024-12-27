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

class ExerciseDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "exercise_tracker.db"
        const val DATABASE_VERSION = 1
        const val TABLE_NAME = "exercise"
        const val COLUMN_ID = "id"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_PUSHUP = "pushups"
        const val COLUMN_POSTURE = "posture"
    }

    // Called when the database is created for the first time
    override fun onCreate(db: SQLiteDatabase?) {
        val CREATE_TABLE = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TIMESTAMP DATETIME DEFAULT CURRENT_TIMESTAMP,
                $COLUMN_PUSHUP INTEGER,
                $COLUMN_POSTURE INTEGER
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
    fun insertOrUpdateData(data: ExerciseData) {
        val db = writableDatabase
        val date = AppGlobals.openedDay

        // Check if an entry for the current day exists
        val cursor = db.query(
            TABLE_NAME,
            null,
            "$COLUMN_TIMESTAMP = ?",
            arrayOf(date),
            null,
            null,
            null
        )

        val values = ContentValues().apply {
            put(COLUMN_TIMESTAMP, date)
            put(COLUMN_PUSHUP, data.pushups)
            put(COLUMN_POSTURE, data.posture)
        }

        if (cursor.moveToFirst()) {
            // Update the existing record
            val result = db.update(
                TABLE_NAME,
                values,
                "$COLUMN_TIMESTAMP = ?",
                arrayOf(date)
            )
            if (result == -1) {
                Log.e("DatabaseError", "Error updating data")
            } else {
                Log.d("Database", "Data updated successfully")
            }
        } else {
            // Insert a new record
            val result = db.insert(TABLE_NAME, null, values)
            if (result == -1L) {
                Log.e("DatabaseError", "Error inserting data")
            } else {
                Log.d("Database", "Data inserted successfully")
            }
        }

        cursor.close()
        db.close()
    }


    // Fetch data for today's date
    fun fetchExerciseDataForToday(): ExerciseData? {
        val db = readableDatabase
        val todayDate = AppGlobals.currentDay
        val query = "SELECT * FROM $TABLE_NAME WHERE DATE($COLUMN_TIMESTAMP) = ?"
        val cursor = db.rawQuery(query, arrayOf(todayDate))

        var data: ExerciseData? = null
        if (cursor.moveToFirst()) {
            val pushups = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PUSHUP))
            val posture = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_POSTURE))
            data = ExerciseData(currentDate = todayDate, pushups = pushups, posture = posture)
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
            val pushups = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PUSHUP))
            val posture = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_POSTURE))
            data = ExerciseData(currentDate = date, pushups = pushups, posture = posture)
        }

        cursor.close()
        db.close()
        return data
    }

    // Fetch all data from the database
    fun fetchAllData(): List<ExerciseData> {
        val db = readableDatabase
        val cursor: Cursor = db.query(TABLE_NAME, null, null, null, null, null, null)

        val data = mutableListOf<ExerciseData>()

        if (cursor.moveToFirst()) {
            do {
                val timestamp = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                val pushups = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PUSHUP))
                val posture = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_POSTURE))

                val dataItem = ExerciseData(
                    currentDate = timestamp,
                    pushups = pushups,
                    posture = posture
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
        val csvFile = File(context.getExternalFilesDir(null), "exercise_data.csv")

        try {
            val writer = BufferedWriter(FileWriter(csvFile))

            // Write header
            writer.write("Date,Pushups,Posture\n")

            // Write each row of data
            for (item in data) {
                writer.write("${item.currentDate},${item.pushups},${item.posture}\n")
            }

            writer.close()
            Log.d("CSVExport", "Exported data to CSV file.")
        } catch (e: IOException) {
            Log.e("CSVExportError", "Error exporting data to CSV: ${e.message}")
        }
    }
}

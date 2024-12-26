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

class ExerciseDatabaseHelper (context: Context) :
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
                $COLUMN_PUSHUP REAL,
                $COLUMN_POSTURE REAL
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
        fun insertData(data: ExerciseData) {
            val db = writableDatabase

            val values = ContentValues().apply {
                put(COLUMN_PUSHUP, data.pushups)
                put(COLUMN_POSTURE, data.posture)
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

        // Fetch all data from the database
        fun fetchAllData(): List<ExerciseData> {
            val db = readableDatabase
            val cursor: Cursor = db.query(TABLE_NAME, null, null, null, null, null, null)

            val data = mutableListOf<ExerciseData>()

            if (cursor.moveToFirst()) {
                do {
                    // Validate column indices
                    val idIndex = cursor.getColumnIndexOrThrow(COLUMN_ID)
                    val timestampIndex = cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)
                    val pushupIndex = cursor.getColumnIndexOrThrow(COLUMN_PUSHUP)
                    val postureIndex = cursor.getColumnIndexOrThrow(COLUMN_POSTURE)

                    // If any of the column indices are -1, something went wrong
                    if (timestampIndex < 0 || pushupIndex < 0 || postureIndex < 0) {
                        Log.e("DatabaseError", "Invalid column indices for fetching data.")
                        return emptyList() // Or handle as needed
                    }

                    val dataItem = ExerciseData(
                        id = cursor.getInt(idIndex),
                        timestamp = cursor.getString(timestampIndex),
                        pushups = cursor.getInt(pushupIndex),
                        posture = cursor.getInt(postureIndex),
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
                writer.write("ID,Timestamp,Pushups,Posture\n")

                // Write each row of data
                for (item in data) {
                    writer.write("${item.id},${item.timestamp},${item.pushups},${item.posture}\n")
                }

                writer.close()
                Log.d("CSVExport", "Exported data to CSV file.")
            } catch (e: IOException) {
                Log.e("CSVExportError", "Error exporting data to CSV: ${e.message}")
            }
        }
    }
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

class RoutineDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val context: Context // Hold the context for file operations

    init {
        this.context = context
    }

    companion object {
        const val DATABASE_NAME = "routine_tracker.db"
        const val DATABASE_VERSION = 1
        const val TABLE_NAME = "routine_checks"
        const val COLUMN_ID = "id"
        const val COLUMN_DATE = "date"
        const val COLUMN_EXERCISE = "exercise_name"
        const val COLUMN_IS_CHECKED = "is_checked"
        const val COLUMN_AM_PM = "am_pm"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val CREATE_TABLE = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_DATE TEXT NOT NULL,
                $COLUMN_EXERCISE TEXT NOT NULL,
                $COLUMN_IS_CHECKED INTEGER NOT NULL,
                $COLUMN_AM_PM TEXT NOT NULL,
                UNIQUE ($COLUMN_DATE, $COLUMN_EXERCISE, $COLUMN_AM_PM)
            )
        """
        db?.execSQL(CREATE_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun updateCheckState(date: String, exerciseName: String, isChecked: Boolean, amPm: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_IS_CHECKED, if (isChecked) 1 else 0)
        }

        val rowsAffected = db.update(
            TABLE_NAME,
            values,
            "$COLUMN_DATE = ? AND $COLUMN_EXERCISE = ? AND $COLUMN_AM_PM = ?",
            arrayOf(date, exerciseName, amPm)
        )

        if (rowsAffected == 0) {
            // Insert a new record if it doesn't exist
            val newValues = ContentValues().apply {
                put(COLUMN_DATE, date)
                put(COLUMN_EXERCISE, exerciseName)
                put(COLUMN_IS_CHECKED, if (isChecked) 1 else 0)
                put(COLUMN_AM_PM, amPm)
            }
            val newRowId = db.insert(TABLE_NAME, null, newValues)
            if (newRowId == -1L) {
                Log.e("RoutineDB", "Error inserting new check state for $exerciseName on $date $amPm")
            } else {
                Log.d("RoutineDB", "Inserted new check state for $exerciseName on $date $amPm: $isChecked")
            }
        } else {
            Log.d("RoutineDB", "Updated check state for $exerciseName on $date $amPm to: $isChecked")
        }
        db.close()
    }

    fun getCheckState(date: String, exerciseName: String, amPm: String): Boolean {
        val db = readableDatabase
        var isChecked = false
        val cursor = db.query(
            TABLE_NAME,
            arrayOf(COLUMN_IS_CHECKED),
            "$COLUMN_DATE = ? AND $COLUMN_EXERCISE = ? AND $COLUMN_AM_PM = ?",
            arrayOf(date, exerciseName, amPm),
            null,
            null,
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                isChecked = it.getInt(it.getColumnIndexOrThrow(COLUMN_IS_CHECKED)) == 1
            }
        }
        db.close()
        return isChecked
    }

    fun getRoutineDataForDate(date: String): Map<String, Map<String, Boolean>> {
        val db = readableDatabase
        val result = mutableMapOf<String, MutableMap<String, Boolean>>()
        val cursor = db.query(
            TABLE_NAME,
            arrayOf(COLUMN_EXERCISE, COLUMN_IS_CHECKED, COLUMN_AM_PM),
            "$COLUMN_DATE = ?",
            arrayOf(date),
            null,
            null,
            null
        )

        cursor?.use {
            while (it.moveToNext()) {
                val exerciseName = it.getString(it.getColumnIndexOrThrow(COLUMN_EXERCISE))
                val isCheckedInt = it.getInt(it.getColumnIndexOrThrow(COLUMN_IS_CHECKED))
                val amPm = it.getString(it.getColumnIndexOrThrow(COLUMN_AM_PM))
                val isChecked = isCheckedInt == 1

                if (!result.containsKey(amPm)) {
                    result[amPm] = mutableMapOf()
                }
                result[amPm]?.set(exerciseName, isChecked) // changed back to set
            }
        }
        db.close()
        return result
    }

    fun insertOrUpdateRoutineData(date: String, routineData: Map<String, Map<String, Boolean>>) {
        val db = writableDatabase
        db.beginTransaction() // Use a transaction for efficiency

        try {
            // Delete existing data for the date
            db.delete(TABLE_NAME, "$COLUMN_DATE = ?", arrayOf(date))

            // Insert the new data
            for ((amPm, exerciseChecks) in routineData) {
                for ((exerciseName, isChecked) in exerciseChecks) {
                    val values = ContentValues().apply {
                        put(COLUMN_DATE, date)
                        put(COLUMN_EXERCISE, exerciseName)
                        put(COLUMN_IS_CHECKED, if (isChecked) 1 else 0)
                        put(COLUMN_AM_PM, amPm)
                    }
                    db.insert(TABLE_NAME, null, values)
                }
            }
            db.setTransactionSuccessful()
            Log.d("RoutineDB", "Successfully updated routine data for $date")

        } catch (e: Exception) {
            Log.e("RoutineDB", "Error updating routine data for $date: ${e.message}")
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    fun exportToCSV(context: Context) {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_NAME,
            arrayOf(COLUMN_DATE, COLUMN_EXERCISE, COLUMN_IS_CHECKED, COLUMN_AM_PM),
            null, null, null, null, null
        )
        val fileName = "routine_data.csv"
        try {
            // Use getExternalFilesDir instead of filesDir
            val file = File(context.getExternalFilesDir(null), fileName)
            val writer = BufferedWriter(FileWriter(file))

            // Write header
            writer.write("Date,Exercise,Is Checked,AM/PM\n")

            cursor?.use {
                while (it.moveToNext()) {
                    val date = it.getString(it.getColumnIndexOrThrow(COLUMN_DATE))
                    val exercise = it.getString(it.getColumnIndexOrThrow(COLUMN_EXERCISE))
                    val isChecked = it.getInt(it.getColumnIndexOrThrow(COLUMN_IS_CHECKED))
                    val amPm = it.getString(it.getColumnIndexOrThrow(COLUMN_AM_PM))
                    writer.write("$date,$exercise,$isChecked,$amPm\n")
                }
            }
            writer.close()
            Log.d("CSVExport", "Exported data to ${file.absolutePath}")
        } catch (e: IOException) {
            Log.e("CSVExportError", "Error exporting data to CSV: ${e.message}")
        } finally {
            db.close()
        }
    }
}

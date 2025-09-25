package com.example.mattshealthtracker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/**
 * Data class to hold the notes for a specific day.
 */
data class HealthNotes(
    val date: String, // "YYYY-MM-DD"
    val stressNote: String?,
    val depressionNote: String?,
    val impactNote: String?
)

/**
 * A dedicated database helper to manage storing and retrieving daily notes
 * for stress, depression, and health impact.
 */
class HealthNotesDbHelper private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "HealthNotes.db"
        private const val TABLE_NAME = "daily_notes"

        // Columns
        private const val COLUMN_DATE = "date" // Primary Key
        private const val COLUMN_STRESS_NOTE = "stress_note"
        private const val COLUMN_DEPRESSION_NOTE = "depression_note"
        private const val COLUMN_IMPACT_NOTE = "impact_note"

        @Volatile
        private var INSTANCE: HealthNotesDbHelper? = null

        fun getInstance(context: Context): HealthNotesDbHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HealthNotesDbHelper(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableSQL = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_DATE TEXT PRIMARY KEY,
                $COLUMN_STRESS_NOTE TEXT,
                $COLUMN_DEPRESSION_NOTE TEXT,
                $COLUMN_IMPACT_NOTE TEXT
            )
        """.trimIndent()
        db.execSQL(createTableSQL)
        Log.d("HealthNotesDbHelper", "Database and table '$TABLE_NAME' created.")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // For simple caches, dropping and recreating is a valid strategy.
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
        Log.d("HealthNotesDbHelper", "Database upgraded from v$oldVersion to v$newVersion.")
    }

    /**
     * Saves or updates the notes for a specific day.
     * Uses REPLACE conflict strategy to simplify insert/update logic.
     *
     * @param notes The HealthNotes object to save.
     */
    fun saveNotes(notes: HealthNotes) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_DATE, notes.date)
            put(COLUMN_STRESS_NOTE, notes.stressNote)
            put(COLUMN_DEPRESSION_NOTE, notes.depressionNote)
            put(COLUMN_IMPACT_NOTE, notes.impactNote)
        }

        try {
            // Use insertWithOnConflict to either insert a new row or replace an existing one
            // based on the PRIMARY KEY (date).
            val rowId =
                db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            if (rowId != -1L) {
                Log.d("HealthNotesDbHelper", "Successfully saved notes for date: ${notes.date}")
            } else {
                Log.e("HealthNotesDbHelper", "Error saving notes for date: ${notes.date}")
            }
        } catch (e: Exception) {
            Log.e("HealthNotesDbHelper", "Exception while saving notes: ${e.message}", e)
        }
    }

    /**
     * Retrieves the notes for a specific day.
     *
     * @param date The date string in "YYYY-MM-DD" format.
     * @return A HealthNotes object if found, otherwise null.
     */
    fun getNotesForDate(date: String): HealthNotes? {
        val db = this.readableDatabase
        var healthNotes: HealthNotes? = null
        val columns =
            arrayOf(COLUMN_DATE, COLUMN_STRESS_NOTE, COLUMN_DEPRESSION_NOTE, COLUMN_IMPACT_NOTE)
        val selection = "$COLUMN_DATE = ?"
        val selectionArgs = arrayOf(date)

        try {
            val cursor = db.query(TABLE_NAME, columns, selection, selectionArgs, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    healthNotes = HealthNotes(
                        date = it.getString(it.getColumnIndexOrThrow(COLUMN_DATE)),
                        stressNote = it.getString(it.getColumnIndexOrThrow(COLUMN_STRESS_NOTE)),
                        depressionNote = it.getString(
                            it.getColumnIndexOrThrow(
                                COLUMN_DEPRESSION_NOTE
                            )
                        ),
                        impactNote = it.getString(it.getColumnIndexOrThrow(COLUMN_IMPACT_NOTE))
                    )
                    Log.d("HealthNotesDbHelper", "Successfully fetched notes for date: $date")
                } else {
                    Log.d("HealthNotesDbHelper", "No notes found for date: $date")
                }
            }
        } catch (e: Exception) {
            Log.e("HealthNotesDbHelper", "Exception while fetching notes: ${e.message}", e)
        }
        return healthNotes
    }
}

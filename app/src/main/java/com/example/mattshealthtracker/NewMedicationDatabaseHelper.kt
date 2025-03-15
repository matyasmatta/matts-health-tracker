package com.example.mattshealthtracker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class NewMedicationDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "new_medication_tracker.db"
        const val DATABASE_VERSION = 1

        // Table for medication items
        const val TABLE_MEDICATIONS = "medications"
        const val COLUMN_DATE = "date"
        const val COLUMN_MEDICATION_NAME = "medication_name"
        const val COLUMN_DOSAGE = "dosage"
        const val COLUMN_IS_STARRED = "is_starred"

        // Table for side effects (one row per day)
        const val TABLE_SIDE_EFFECTS = "side_effects"
        const val COLUMN_SIDE_EFFECTS = "side_effects"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        // Create table for medication items.
        val createMedicationsTable = """
            CREATE TABLE $TABLE_MEDICATIONS (
                $COLUMN_DATE TEXT,
                $COLUMN_MEDICATION_NAME TEXT,
                $COLUMN_DOSAGE REAL,
                $COLUMN_IS_STARRED INTEGER,
                PRIMARY KEY ($COLUMN_DATE, $COLUMN_MEDICATION_NAME)
            );
        """.trimIndent()

        // Create table for side effects.
        val createSideEffectsTable = """
            CREATE TABLE $TABLE_SIDE_EFFECTS (
                $COLUMN_DATE TEXT PRIMARY KEY,
                $COLUMN_SIDE_EFFECTS TEXT
            );
        """.trimIndent()

        db?.execSQL(createMedicationsTable)
        db?.execSQL(createSideEffectsTable)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_MEDICATIONS")
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_SIDE_EFFECTS")
        onCreate(db)
    }

    // Inserts or updates a single medication item for a given date.
    fun insertOrUpdateMedicationItem(date: String, item: MedicationItem) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_DATE, date)
            put(COLUMN_MEDICATION_NAME, item.name)
            put(COLUMN_DOSAGE, item.dosage)
            put(COLUMN_IS_STARRED, if (item.isStarred) 1 else 0)
        }
        val rowsUpdated = db.update(
            TABLE_MEDICATIONS,
            values,
            "$COLUMN_DATE = ? AND $COLUMN_MEDICATION_NAME = ?",
            arrayOf(date, item.name)
        )
        if (rowsUpdated == 0) {
            db.insert(TABLE_MEDICATIONS, null, values)
        }
        db.close()
    }

    // Bulk update: delete existing medication items for the date and insert the new list.
    fun insertOrUpdateMedicationList(date: String, items: List<MedicationItem>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_MEDICATIONS, "$COLUMN_DATE = ?", arrayOf(date))
            items.forEach { item ->
                val values = ContentValues().apply {
                    put(COLUMN_DATE, date)
                    put(COLUMN_MEDICATION_NAME, item.name)
                    put(COLUMN_DOSAGE, item.dosage)
                    put(COLUMN_IS_STARRED, if (item.isStarred) 1 else 0)
                }
                db.insert(TABLE_MEDICATIONS, null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        db.close()
    }

    // Fetches the medication items for a given date.
    fun fetchMedicationItemsForDate(date: String): List<MedicationItem> {
        val items = mutableListOf<MedicationItem>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_MEDICATIONS,
            arrayOf(COLUMN_MEDICATION_NAME, COLUMN_DOSAGE, COLUMN_IS_STARRED),
            "$COLUMN_DATE = ?",
            arrayOf(date),
            null, null, null
        )
        if (cursor.moveToFirst()) {
            do {
                val name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MEDICATION_NAME))
                val dosage = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_DOSAGE))
                val isStarred = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_STARRED)) == 1
                // Look up the default step and unit values from a default mapping.
                val defaultMapping = mapOf(
                    "amitriptyline" to Pair(6.25f, "mg"),
                    "inosine pranobex" to Pair(500f, "mg"),
                    "paracetamol" to Pair(250f, "mg"),
                    "ibuprofen" to Pair(200f, "mg"),
                    "vitamin D" to Pair(500f, "IU"),
                    "bisulepine" to Pair(1f, "mg"),
                    "cetirizine" to Pair(5f, "mg"),
                    "doxycycline" to Pair(100f, "mg"),
                    "corticosteroids" to Pair(5f, "mg-eq"),
                    "magnesium glycinate" to Pair(500f, "mg")
                )
                val (step, unit) = defaultMapping[name] ?: Pair(0f, "")
                items.add(MedicationItem(name, dosage, step, unit, isStarred))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return items
    }

    fun fetchMedicationItemsForDateWithDefaults(date: String): List<MedicationItem> {
        val fetchedMedications = fetchMedicationItemsForDate(date)

        if (fetchedMedications.isNotEmpty()) {
            return mergeWithDefaults(fetchedMedications)
        }

        // If no medications found for the date, check previous day
        val previousDate = AppGlobals.getOpenedDayAsLocalDate().minusDays(1).toString()
        val previousDayMedications = fetchMedicationItemsForDate(previousDate)

        return if (previousDayMedications.isNotEmpty()) {
            // Carry over starred items and merge with defaults
            mergeWithDefaults(previousDayMedications.map { it.copy(isStarred = true) })
        } else {
            // No previous data, just return defaults
            defaultMedications
        }
    }

    // Helper function to merge fetched meds with default meds
    private fun mergeWithDefaults(fetched: List<MedicationItem>): List<MedicationItem> {
        val fetchedNames = fetched.map { it.name }.toSet()
        val missingDefaults = defaultMedications.filter { it.name !in fetchedNames }
        return fetched + missingDefaults
    }

    // Default medication list (moved inside the helper)
    private val defaultMedications = listOf(
        MedicationItem("amitriptyline", 0f, 6.25f, "mg"),
        MedicationItem("inosine pranobex", 0f, 500f, "mg"),
        MedicationItem("paracetamol", 0f, 250f, "mg"),
        MedicationItem("ibuprofen", 0f, 200f, "mg"),
        MedicationItem("vitamin D", 0f, 500f, "IU"),
        MedicationItem("bisulepine", 0f, 1f, "mg"),
        MedicationItem("cetirizine", 0f, 5f, "mg"),
        MedicationItem("doxycycline", 0f, 100f, "mg"),
        MedicationItem("corticosteroids", 0f, 5f, "mg-eq"),
        MedicationItem("magnesium glycinate", 0f, 500f, "mg")
    )

    // Inserts or updates side effects for a given date.
    fun insertOrUpdateSideEffects(date: String, sideEffects: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_DATE, date)
            put(COLUMN_SIDE_EFFECTS, sideEffects)
        }
        val rowsUpdated = db.update(
            TABLE_SIDE_EFFECTS,
            values,
            "$COLUMN_DATE = ?",
            arrayOf(date)
        )
        if (rowsUpdated == 0) {
            db.insert(TABLE_SIDE_EFFECTS, null, values)
        }
        db.close()
    }

    // Fetches side effects for a given date.
    fun fetchSideEffectsForDate(date: String): String? {
        var sideEffects: String? = null
        val db = readableDatabase
        val cursor = db.query(
            TABLE_SIDE_EFFECTS,
            arrayOf(COLUMN_SIDE_EFFECTS),
            "$COLUMN_DATE = ?",
            arrayOf(date),
            null, null, null
        )
        if (cursor.moveToFirst()) {
            sideEffects = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SIDE_EFFECTS))
        }
        cursor.close()
        db.close()
        return sideEffects
    }

    // Exports the entire database (all medication items and side effects) to a CSV file.
    fun exportToCSV(context: Context) {
        val csvFile = File(context.getExternalFilesDir(null), "new_medication_data_full.csv")
        try {
            val writer = BufferedWriter(FileWriter(csvFile))
            val db = readableDatabase

            // Write header for medication items.
            writer.write("Date,Medication Name,Dosage,Is Starred\n")
            val medicationCursor = db.query(
                TABLE_MEDICATIONS,
                null,
                null,
                null,
                null,
                null,
                "$COLUMN_DATE ASC, $COLUMN_MEDICATION_NAME ASC"
            )
            if (medicationCursor.moveToFirst()) {
                do {
                    val date = medicationCursor.getString(medicationCursor.getColumnIndexOrThrow(COLUMN_DATE))
                    val name = medicationCursor.getString(medicationCursor.getColumnIndexOrThrow(COLUMN_MEDICATION_NAME))
                    val dosage = medicationCursor.getFloat(medicationCursor.getColumnIndexOrThrow(COLUMN_DOSAGE))
                    val isStarred = medicationCursor.getInt(medicationCursor.getColumnIndexOrThrow(COLUMN_IS_STARRED))
                    writer.write("$date,$name,$dosage,$isStarred\n")
                } while (medicationCursor.moveToNext())
            }
            medicationCursor.close()

            writer.write("\n")
            // Write header for side effects.
            writer.write("Date,Side Effects\n")
            val sideEffectsCursor = db.query(
                TABLE_SIDE_EFFECTS,
                null,
                null,
                null,
                null,
                null,
                "$COLUMN_DATE ASC"
            )
            if (sideEffectsCursor.moveToFirst()) {
                do {
                    val date = sideEffectsCursor.getString(sideEffectsCursor.getColumnIndexOrThrow(COLUMN_DATE))
                    val sideEffects = sideEffectsCursor.getString(sideEffectsCursor.getColumnIndexOrThrow(COLUMN_SIDE_EFFECTS))
                    writer.write("$date,\"${sideEffects.replace("\"", "\"\"")}\"\n")
                } while (sideEffectsCursor.moveToNext())
            }
            sideEffectsCursor.close()
            writer.close()
            db.close()
            Log.d("CSVExport", "Exported full medication data to CSV file.")
        } catch (e: IOException) {
            Log.e("CSVExportError", "Error exporting data to CSV: ${e.message}")
        }
    }
}

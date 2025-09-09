package com.example.mattshealthtracker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import androidx.compose.ui.platform.LocalContext
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException

data class DefaultMedicationTemplate(
    val name: String,
    val step: Float,
    val unit: String
)

class NewMedicationDatabaseHelper(private val context: Context) :
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

        // SharedPreferences keys
        const val PREF_MEDICATION_TEMPLATES = "medication_templates"
        private const val PREF_KEY_MEDICATION_TEMPLATES_IN_HELPER = "medication_templates_local"
    }

    private val gson = Gson()

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

    // Save medication templates to SharedPreferences
    // Save medication templates to SharedPreferences
    fun saveMedicationTemplates(templates: List<DefaultMedicationTemplate>) {
        val json = gson.toJson(templates)
        // Use the context passed to the helper
        AppGlobals.putString(context, PREF_KEY_MEDICATION_TEMPLATES_IN_HELPER, json)
        Log.d("MedicationDBHelper", "Saved templates: $json")

    }

    // Load medication templates from SharedPreferences
    fun loadMedicationTemplates(): List<DefaultMedicationTemplate> {
        // Use the context passed to the helper
        val json = AppGlobals.getString(context, PREF_KEY_MEDICATION_TEMPLATES_IN_HELPER, null)
        Log.d("MedicationDBHelper", "Loaded templates JSON: $json")
        return if (json != null) {
            try {
                val type = object : TypeToken<List<DefaultMedicationTemplate>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                Log.e(
                    "MedicationDBHelper",
                    "Error parsing templates from JSON, returning defaults.",
                    e
                )
                getDefaultMedicationTemplates().also { saveMedicationTemplates(it) }
            }
        } else {
            getDefaultMedicationTemplates().also {
                saveMedicationTemplates(it)
            }
        }
    }

    // Add a new medication template
    fun addMedicationTemplate(name: String, step: Float, unit: String) {
        val templates = loadMedicationTemplates().toMutableList()
        // Check if medication already exists
        if (templates.none { it.name.equals(name, ignoreCase = true) }) {
            templates.add(DefaultMedicationTemplate(name, step, unit))
            saveMedicationTemplates(templates)
        }
    }

    // Remove a medication template
    fun removeMedicationTemplate(name: String) {
        val templates = loadMedicationTemplates().toMutableList()
        templates.removeAll { it.name.equals(name, ignoreCase = true) }
        saveMedicationTemplates(templates)
    }

    // Update a medication template
    fun updateMedicationTemplate(oldName: String, newName: String, step: Float, unit: String) {
        val templates = loadMedicationTemplates().toMutableList()
        val index = templates.indexOfFirst { it.name.equals(oldName, ignoreCase = true) }
        if (index != -1) {
            templates[index] = DefaultMedicationTemplate(newName, step, unit)
            saveMedicationTemplates(templates)
        }
    }

    // Get default medication templates (initial set)
    private fun getDefaultMedicationTemplates(): List<DefaultMedicationTemplate> {
        return listOf(
            DefaultMedicationTemplate("amitriptyline", 6.25f, "mg"),
            DefaultMedicationTemplate("inosine pranobex", 500f, "mg"),
            DefaultMedicationTemplate("paracetamol", 250f, "mg"),
            DefaultMedicationTemplate("ibuprofen", 200f, "mg"),
            DefaultMedicationTemplate("vitamin D", 500f, "IU"),
            DefaultMedicationTemplate("bisulepine", 1f, "mg"),
            DefaultMedicationTemplate("cetirizine", 5f, "mg"),
            DefaultMedicationTemplate("doxycycline", 100f, "mg"),
            DefaultMedicationTemplate("corticosteroids", 5f, "mg-eq"),
            DefaultMedicationTemplate("magnesium glycinate", 250f, "mg"),
            DefaultMedicationTemplate("ketotifen", 0.5f, "mg"),
            DefaultMedicationTemplate("desloratidine", 2.5f, "mg"),
            DefaultMedicationTemplate("omeprazol", 10f, "mg"),
            DefaultMedicationTemplate("pantoprazol", 40f, "mg"),
            DefaultMedicationTemplate("itopride", 50f, "mg"),
            DefaultMedicationTemplate("alginate", 1f, "pack"),
            DefaultMedicationTemplate("mirtazapine", 7.5f, "mg"),
            DefaultMedicationTemplate("moxastine teoclate", 12.5f, "mg")
        )
    }

    // Create default medications from templates
    private fun getDefaultMedications(): List<MedicationItem> {
        return loadMedicationTemplates().map { template ->
            MedicationItem(template.name, 0f, template.step, template.unit)
        }
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

                // Look up the step and unit values from saved templates
                val templates = loadMedicationTemplates()
                val template = templates.find { it.name.equals(name, ignoreCase = true) }
                val step = template?.step ?: 1f
                val unit = template?.unit ?: "mg"

                items.add(MedicationItem(name, dosage, step, unit, isStarred))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return items
    }

    fun fetchMedicationItemsForDateWithDefaults(date: String): List<MedicationItem> {
        val fetchedMedications = fetchMedicationItemsForDate(date)
        Log.d("NewMedicationDatabaseHelper", "Fetching items for data with defaults for ${date}.")

        if (fetchedMedications.isNotEmpty()) {
            Log.d("NewMedicationDatabaseHelper", "Found medication data for the day ${date}.")
            return mergeWithDefaults(fetchedMedications)
        }

        // If no medications found for the date, check previous day
        val previousDate = AppGlobals.getOpenedDayAsLocalDate().minusDays(1).toString()
        val previousDayMedications = fetchMedicationItemsForDate(previousDate)

        return if (previousDayMedications.isNotEmpty()) {
            Log.d("NewMedicationDatabaseHelper", "Found medication data for the day $date from previous day $previousDate.")
            // Carry over starred items and merge with defaults, but set dosage to 0
            val resetMedications = previousDayMedications.map {
                it.copy(dosage = 0f) // Reset dosage to 0
            }
            mergeWithDefaults(resetMedications)
        } else {
            // No previous data, just return defaults
            Log.d("NewMedicationDatabaseHelper", "Found no medication data even for previous day ${previousDate}.")
            getDefaultMedications()
        }
    }

    // Helper function to merge fetched meds with default meds
    private fun mergeWithDefaults(fetched: List<MedicationItem>): List<MedicationItem> {
        val fetchedNames = fetched.map { it.name }.toSet()
        val missingDefaults = getDefaultMedications().filter { it.name !in fetchedNames }
        return fetched + missingDefaults
    }

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
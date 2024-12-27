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

class MedicationDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "medication_tracker.db"
        const val DATABASE_VERSION = 1
        const val TABLE_NAME = "medication_data"
        const val COLUMN_DATE = "date"
        const val COLUMN_DOXY_LACTOSE = "doxy_lactose"
        const val COLUMN_DOXY_MEAL = "doxy_meal"
        const val COLUMN_DOXY_DOSE = "doxy_dose"
        const val COLUMN_DOXY_WATER = "doxy_water"
        const val COLUMN_PREDNISONE_DOSE = "prednisone_dose"
        const val COLUMN_PREDNISONE_MEAL = "prednisone_meal"
        const val COLUMN_VITAMINS = "vitamins"
        const val COLUMN_PROBIOTICS_MORNING = "probiotics_morning"
        const val COLUMN_PROBIOTICS_EVENING = "probiotics_evening"
        const val COLUMN_SIDE_EFFECTS = "side_effects"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val CREATE_TABLE = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_DATE TEXT PRIMARY KEY,
                $COLUMN_DOXY_LACTOSE INTEGER,
                $COLUMN_DOXY_MEAL INTEGER,
                $COLUMN_DOXY_DOSE INTEGER,
                $COLUMN_DOXY_WATER INTEGER,
                $COLUMN_PREDNISONE_DOSE INTEGER,
                $COLUMN_PREDNISONE_MEAL INTEGER,
                $COLUMN_VITAMINS INTEGER,
                $COLUMN_PROBIOTICS_MORNING INTEGER,
                $COLUMN_PROBIOTICS_EVENING INTEGER,
                $COLUMN_SIDE_EFFECTS TEXT
            )
        """
        db?.execSQL(CREATE_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    // Insert or update medication data for the current day
    fun insertOrUpdateMedicationData(data: MedicationData) {
        Log.d("Insert Data", "Updating data for today.")
        val db = writableDatabase
        val values = ContentValues()

        // Use the currentDate as the unique key
        values.put(COLUMN_DATE, data.currentDate)
        values.put(COLUMN_DOXY_LACTOSE, if (data.doxyLactose) 1 else 0)
        values.put(COLUMN_DOXY_MEAL, if (data.doxyMeal) 1 else 0)
        values.put(COLUMN_DOXY_DOSE, if (data.doxyDose) 1 else 0)
        values.put(COLUMN_DOXY_WATER, if (data.doxyWater) 1 else 0)
        values.put(COLUMN_PREDNISONE_DOSE, if (data.prednisoneDose) 1 else 0)
        values.put(COLUMN_PREDNISONE_MEAL, if (data.prednisoneMeal) 1 else 0)
        values.put(COLUMN_VITAMINS, if (data.vitamins) 1 else 0)
        values.put(COLUMN_PROBIOTICS_MORNING, if (data.probioticsMorning) 1 else 0)
        values.put(COLUMN_PROBIOTICS_EVENING, if (data.probioticsEvening) 1 else 0)
        values.put(COLUMN_SIDE_EFFECTS, data.sideEffects)

        // Try to insert a new row or update the existing one for today
        val rowsUpdated = db.update(TABLE_NAME, values, "$COLUMN_DATE = ?", arrayOf(data.currentDate))
        if (rowsUpdated == 0) {
            db.insert(TABLE_NAME, null, values)
        }

        db.close()
    }

    // Fetch medication data for the current day
    fun fetchMedicationDataForToday(): MedicationData? {
        val db = readableDatabase
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        Log.d("MedicationDatabaseHelper", "Fetching today's $currentDate data on launch.")


        val cursor: Cursor = db.query(
            TABLE_NAME,
            null,
            "$COLUMN_DATE = ?",
            arrayOf(currentDate),
            null,
            null,
            null
        )

        var data: MedicationData? = null
        if (cursor.moveToFirst()) {
            val dateIndex = cursor.getColumnIndexOrThrow(COLUMN_DATE)
            val doxyLactoseIndex = cursor.getColumnIndexOrThrow(COLUMN_DOXY_LACTOSE)
            val doxyMealIndex = cursor.getColumnIndexOrThrow(COLUMN_DOXY_MEAL)
            val doxyDoseIndex = cursor.getColumnIndexOrThrow(COLUMN_DOXY_DOSE)
            val doxyWaterIndex = cursor.getColumnIndexOrThrow(COLUMN_DOXY_WATER)
            val prednisoneDoseIndex = cursor.getColumnIndexOrThrow(COLUMN_PREDNISONE_DOSE)
            val prednisoneMealIndex = cursor.getColumnIndexOrThrow(COLUMN_PREDNISONE_MEAL)
            val vitaminsIndex = cursor.getColumnIndexOrThrow(COLUMN_VITAMINS)
            val probioticsMorningIndex = cursor.getColumnIndexOrThrow(COLUMN_PROBIOTICS_MORNING)
            val probioticsEveningIndex = cursor.getColumnIndexOrThrow(COLUMN_PROBIOTICS_EVENING)
            val sideEffectsIndex = cursor.getColumnIndexOrThrow(COLUMN_SIDE_EFFECTS)

            data = MedicationData(
                currentDate = cursor.getString(dateIndex),
                doxyLactose = cursor.getInt(doxyLactoseIndex) == 1,
                doxyMeal = cursor.getInt(doxyMealIndex) == 1,
                doxyDose = cursor.getInt(doxyDoseIndex) == 1,
                doxyWater = cursor.getInt(doxyWaterIndex) == 1,
                prednisoneDose = cursor.getInt(prednisoneDoseIndex) == 1,
                prednisoneMeal = cursor.getInt(prednisoneMealIndex) == 1,
                vitamins = cursor.getInt(vitaminsIndex) == 1,
                probioticsMorning = cursor.getInt(probioticsMorningIndex) == 1,
                probioticsEvening = cursor.getInt(probioticsEveningIndex) == 1,
                sideEffects = cursor.getString(sideEffectsIndex)
            )
        }

        cursor.close()
        db.close()
        Log.d("MedicationDatabaseHelper", "Fetched today's data: $data")
        return data
    }

    // Export data to a CSV file
    fun exportToCSV(context: Context) {
        val data = fetchAllMedicationData()
        val csvFile = File(context.getExternalFilesDir(null), "medication_data.csv")

        try {
            val writer = BufferedWriter(FileWriter(csvFile))

            // Write header
            writer.write("Date,Doxy Lactose,Doxy Meal,Doxy Dose,Doxy Water,Prednisone Dose,Prednisone Meal,Vitamins,Probiotics Morning,Probiotics Evening,Side Effects\n")

            // Write data
            for (entry in data) {
                // Wrap the side effects column in quotes to handle commas or special characters
                val sanitizedSideEffects = "\"${entry.sideEffects.replace("\"", "\"\"")}\""
                writer.write("${entry.currentDate},${entry.doxyLactose},${entry.doxyMeal},${entry.doxyDose},${entry.doxyWater},${entry.prednisoneDose},${entry.prednisoneMeal},${entry.vitamins},${entry.probioticsMorning},${entry.probioticsEvening},$sanitizedSideEffects\n")
            }

            writer.close()
            Log.d("CSVExport", "Exported medication data to CSV file.")
        } catch (e: IOException) {
            Log.e("CSVExportError", "Error exporting data to CSV: ${e.message}")
        }
    }

    // Fetch all medication data from the database
    private fun fetchAllMedicationData(): List<MedicationData> {
        val db = readableDatabase
        val cursor: Cursor = db.query(TABLE_NAME, null, null, null, null, null, null)

        val data = mutableListOf<MedicationData>()

        if (cursor.moveToFirst()) {
            do {
                val dateIndex = cursor.getColumnIndexOrThrow(COLUMN_DATE)
                val doxyLactoseIndex = cursor.getColumnIndexOrThrow(COLUMN_DOXY_LACTOSE)
                val doxyMealIndex = cursor.getColumnIndexOrThrow(COLUMN_DOXY_MEAL)
                val doxyDoseIndex = cursor.getColumnIndexOrThrow(COLUMN_DOXY_DOSE)
                val doxyWaterIndex = cursor.getColumnIndexOrThrow(COLUMN_DOXY_WATER)
                val prednisoneDoseIndex = cursor.getColumnIndexOrThrow(COLUMN_PREDNISONE_DOSE)
                val prednisoneMealIndex = cursor.getColumnIndexOrThrow(COLUMN_PREDNISONE_MEAL)
                val vitaminsIndex = cursor.getColumnIndexOrThrow(COLUMN_VITAMINS)
                val probioticsMorningIndex = cursor.getColumnIndexOrThrow(COLUMN_PROBIOTICS_MORNING)
                val probioticsEveningIndex = cursor.getColumnIndexOrThrow(COLUMN_PROBIOTICS_EVENING)
                val sideEffectsIndex = cursor.getColumnIndexOrThrow(COLUMN_SIDE_EFFECTS)

                val medicationData = MedicationData(
                    currentDate = cursor.getString(dateIndex),
                    doxyLactose = cursor.getInt(doxyLactoseIndex) == 1,
                    doxyMeal = cursor.getInt(doxyMealIndex) == 1,
                    doxyDose = cursor.getInt(doxyDoseIndex) == 1,
                    doxyWater = cursor.getInt(doxyWaterIndex) == 1,
                    prednisoneDose = cursor.getInt(prednisoneDoseIndex) == 1,
                    prednisoneMeal = cursor.getInt(prednisoneMealIndex) == 1,
                    vitamins = cursor.getInt(vitaminsIndex) == 1,
                    probioticsMorning = cursor.getInt(probioticsMorningIndex) == 1,
                    probioticsEvening = cursor.getInt(probioticsEveningIndex) == 1,
                    sideEffects = cursor.getString(sideEffectsIndex)
                )

                data.add(medicationData)
            } while (cursor.moveToNext())
        }

        cursor.close()
        db.close()

        return data
    }
}

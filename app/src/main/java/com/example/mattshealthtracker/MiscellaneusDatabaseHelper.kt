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

class MiscellaneousDatabaseHelper(
    private val context: Context,
    private val appGlobals: AppGlobals // Pass AppGlobals instance
) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "miscellaneous_tracker.db"
        const val DATABASE_VERSION =
            2 // User's specified version (or increment if schema changes significantly)
        const val TABLE_NAME = "miscellaneous_items"
        const val COLUMN_DATE = "date"
        const val COLUMN_ITEM_NAME = "item_name"
        const val COLUMN_VALUE = "value"
        const val COLUMN_IS_CHECKED = "is_checked"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        Log.d("MiscDB", "onCreate called")
        val CREATE_TABLE = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_DATE TEXT NOT NULL,
                $COLUMN_ITEM_NAME TEXT NOT NULL,
                $COLUMN_VALUE REAL NOT NULL,
                $COLUMN_IS_CHECKED INTEGER NOT NULL,
                PRIMARY KEY ($COLUMN_DATE, $COLUMN_ITEM_NAME)
            )
        """
        db?.execSQL(CREATE_TABLE)
        Log.d("MiscDB", "Table $TABLE_NAME created")
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        Log.d("MiscDB", "onUpgrade called: oldVersion=$oldVersion, newVersion=$newVersion")
        // For this change (user-defined symptoms), the table schema itself doesn't necessarily need to change,
        // unless you decide to store metadata about symptoms here too (which is better done in AppGlobals/Prefs).
        // If you were changing columns, you'd handle migration.
        // For simplicity, if schema HAD changed:
        // db?.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        // onCreate(db)
        // Log.d("MiscDB", "Table $TABLE_NAME dropped and recreated in onUpgrade")

        // If no schema change, you might not need to do anything here, or handle specific version upgrades.
        // For now, let's assume no schema change is required by this logical shift.
        if (oldVersion < 2) { // Example: if version 1 didn't have robust logging or a minor tweak
            // Perform any specific migration steps from v1 to v2 if needed.
            // If just dropping and recreating is acceptable for schema changes:
            // db?.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
            // onCreate(db)
            // Log.d("MiscDB", "Table $TABLE_NAME dropped and recreated for schema change from v$oldVersion to v$newVersion")
        }
    }

    /**
     * Inserts or updates a list of miscellaneous data items for a given date.
     * This implementation deletes all existing items for the date and re-inserts the current list.
     */
    fun insertOrUpdateMiscellaneousItems(date: String, itemsToSave: List<TrackerItem>) {
        Log.d(
            "MiscDB",
            "insertOrUpdateMiscellaneousItems called for date: $date with ${itemsToSave.size} items"
        )
        val db = writableDatabase
        db.beginTransaction()
        try {
            Log.d("MiscDB", "Deleting existing items for date: $date")
            db.delete(TABLE_NAME, "$COLUMN_DATE = ?", arrayOf(date))
            Log.d("MiscDB", "Existing items deleted for date: $date")

            for (item in itemsToSave) {
                // Only save items that are either checked or have a non-zero value,
                // or save all if that's the desired behavior.
                // This can prevent cluttering the DB with many zero/false entries if user has many symptoms defined.
                // For now, let's save all items passed to this function.
                val values = ContentValues().apply {
                    put(COLUMN_DATE, date)
                    put(COLUMN_ITEM_NAME, item.name)
                    put(COLUMN_VALUE, item.value)
                    put(COLUMN_IS_CHECKED, if (item.isChecked) 1 else 0)
                }
                val rowId = db.insert(TABLE_NAME, null, values)
                if (rowId == -1L) {
                    Log.e("MiscDB", "Error inserting item ${item.name} for date $date")
                } else {
                    Log.d("MiscDB", "Inserted item ${item.name} for date $date (rowId: $rowId)")
                }
            }
            db.setTransactionSuccessful()
            Log.d("MiscDB", "Transaction successful for date: $date")
        } catch (e: Exception) {
            Log.e("MiscDB", "Error updating miscellaneous items for $date: ${e.message}", e)
        } finally {
            db.endTransaction()
            Log.d("MiscDB", "Transaction ended for date: $date")
            // Consider keeping the DB open if performing many operations, but for single ops, closing is fine.
            // db.close() // Re-evaluate: Often better to close only when the helper instance is no longer needed.
            // Or, if methods are infrequent, open/close per method is okay but less performant.
            // For now, let's assume the caller manages the overall DB lifecycle if many ops are batched.
            // If each call is standalone, then closing here is fine.
            // Let's assume the helper is instantiated per screen/use-case and can be closed then.
        }
    }


    /**
     * Fetches miscellaneous data items for a specific date, merging with user-defined symptoms.
     * Returns a list of TrackerItem. Items not found in the database for the given date
     * but present in AppGlobals.userDefinedSymptomNames will be included with default values (0f, false).
     */
    fun fetchMiscellaneousItemsForDate(date: String): List<TrackerItem> {
        Log.d("MiscDB", "fetchMiscellaneousItemsForDate called for date: $date")
        val db = readableDatabase
        val savedItemsMap = mutableMapOf<String, TrackerItem>()

        try {
            Log.d("MiscDB", "Querying DB for saved items for date: $date")
            val cursor = db.query(
                TABLE_NAME,
                arrayOf(COLUMN_ITEM_NAME, COLUMN_VALUE, COLUMN_IS_CHECKED),
                "$COLUMN_DATE = ?",
                arrayOf(date),
                null, null, null
            )

            cursor?.use {
                Log.d("MiscDB", "Cursor obtained for date: $date, count: ${it.count}")
                while (it.moveToNext()) {
                    val name = it.getString(it.getColumnIndexOrThrow(COLUMN_ITEM_NAME))
                    val value = it.getFloat(it.getColumnIndexOrThrow(COLUMN_VALUE))
                    val isChecked = it.getInt(it.getColumnIndexOrThrow(COLUMN_IS_CHECKED)) == 1
                    savedItemsMap[name.lowercase()] = TrackerItem(
                        name,
                        value,
                        isChecked
                    ) // Store with original name, but use lowercase for lookup
                    Log.d("MiscDB", "Fetched saved item: $name, $value, $isChecked")
                }
            }
            Log.d(
                "MiscDB",
                "Finished querying DB for date: $date. Found ${savedItemsMap.size} saved items."
            )
        } catch (e: Exception) {
            Log.e("MiscDB", "Exception during DB fetch for date: $date: ${e.message}", e)
            // Depending on desired behavior, could return emptyList() or rethrow
        } finally {
            // db.close() // Same consideration as above for when to close.
            // Typically, the db instance passed to onCreate/onUpgrade is managed by SQLiteOpenHelper.
            // Instances obtained from readableDatabase/writableDatabase should be closed.
            // However, if the helper is short-lived, it's okay.
            // Let's ensure it's closed if opened here.
            if (db.isOpen) {
                // db.close() // Let's not close here to allow the helper to be used for multiple ops if needed.
                // The screen/ViewModel using this helper should call a .close() method on the helper when done.
            }
        }

        // Get the current list of user-defined symptom names from AppGlobals
        val userDefinedNames = appGlobals.userDefinedSymptomNames
        Log.d("MiscDB", "User-defined symptom names from AppGlobals: $userDefinedNames")

        val finalTrackerItems = userDefinedNames.map { symptomName ->
            val savedItem = savedItemsMap[symptomName.lowercase()] // Case-insensitive lookup
            TrackerItem(
                name = symptomName, // Use the canonical name from AppGlobals
                value = savedItem?.value ?: 0f,
                isChecked = savedItem?.isChecked ?: false
            )
        }
        Log.d(
            "MiscDB",
            "Constructed final list of ${finalTrackerItems.size} TrackerItems for date $date."
        )
        finalTrackerItems.forEach {
            Log.v(
                "MiscDB",
                "Final item: ${it.name}, ${it.value}, ${it.isChecked}"
            )
        }

        return finalTrackerItems
    }


    /**
     * Exports all miscellaneous data to a CSV file.
     * This function remains largely the same, as it exports what's in the DB.
     */
    fun exportToCSV() {
        Log.d("CSVExportMisc", "Exporting miscellaneous data to CSV started.")
        val db = readableDatabase // Ensure we get a readable instance
        var cursor: Cursor? = null
        val fileName = "miscellaneous_data.csv"
        var writer: BufferedWriter? = null

        try {
            Log.d("CSVExportMisc", "Querying all data from table: $TABLE_NAME")
            cursor = db.query(
                TABLE_NAME,
                arrayOf(COLUMN_DATE, COLUMN_ITEM_NAME, COLUMN_VALUE, COLUMN_IS_CHECKED),
                null, null, null, null, "$COLUMN_DATE ASC, $COLUMN_ITEM_NAME ASC"
            )
            Log.d("CSVExportMisc", "Query completed. Cursor count: ${cursor?.count}")

            val file = File(context.getExternalFilesDir(null), fileName)
            Log.d("CSVExportMisc", "Writing to file: ${file.absolutePath}")
            writer = BufferedWriter(FileWriter(file))

            val header = "$COLUMN_DATE,$COLUMN_ITEM_NAME,$COLUMN_VALUE,$COLUMN_IS_CHECKED\n"
            writer.write(header)
            Log.d("CSVExportMisc", "Wrote header: $header")

            cursor?.use {
                Log.d("CSVExportMisc", "Writing data rows...")
                var rowCount = 0
                while (it.moveToNext()) {
                    val date = it.getString(it.getColumnIndexOrThrow(COLUMN_DATE))
                    val itemName = it.getString(it.getColumnIndexOrThrow(COLUMN_ITEM_NAME))
                    val value = it.getFloat(it.getColumnIndexOrThrow(COLUMN_VALUE))
                    val isCheckedInt = it.getInt(it.getColumnIndexOrThrow(COLUMN_IS_CHECKED))
                    val isCheckedBoolean = if (isCheckedInt == 1) "true" else "false"
                    val escapedItemName = "\"${itemName.replace("\"", "\"\"")}\""
                    val rowData = "$date,$escapedItemName,$value,$isCheckedBoolean\n"
                    writer.write(rowData)
                    rowCount++
                }
                Log.d("CSVExportMisc", "Finished writing $rowCount data rows.")
            }
            Log.d(
                "CSVExportMisc",
                "Exported Miscellaneous data to ${file.absolutePath} successfully."
            )
        } catch (e: IOException) {
            Log.e("CSVExportMisc", "IOException during CSV export: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(
                "CSVExportMisc",
                "An unexpected error occurred during Miscellaneous data export: ${e.message}",
                e
            )
        } finally {
            try {
                writer?.close()
                Log.d("CSVExportMisc", "Writer closed.")
            } catch (e: IOException) {
                Log.e("CSVExportMisc", "Error closing writer: ${e.message}")
            }
            // Cursor is closed by the .use{} block
            // Database closing is handled by the overall lifecycle of the helper in this approach
        }
    }

    // It's good practice for an SQLiteOpenHelper to have a close method if it's long-lived
    // However, SQLiteOpenHelper manages its own database instance internally.
    // Calling getWritableDatabase() or getReadableDatabase() returns the same DB instance.
    // The system closes the database when the helper object is garbage collected or when the app closes.
    // If you explicitly want to close it, you can add:
    // fun closeDatabase() {
    //     super.close() // Calls close on the underlying SQLiteDatabase
    //     Log.d("MiscDB", "Database explicitly closed via helper method.")
    // }
    // But typically, you don't need to call this manually very often if using the helper per screen.
}


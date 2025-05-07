package com.example.mattshealthtracker // Make sure this matches your package name

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

class MiscellaneousDatabaseHelper(private val context: Context) : // Added 'private val context: Context' here
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "miscellaneous_tracker.db"
        const val DATABASE_VERSION = 2 // User's specified version
        const val TABLE_NAME = "miscellaneous_items" // Table for individual items per date
        const val COLUMN_DATE = "date"
        const val COLUMN_ITEM_NAME = "item_name"
        const val COLUMN_VALUE = "value"
        const val COLUMN_IS_CHECKED = "is_checked"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        Log.d("MiscDB", "onCreate called") // Add log
        val CREATE_TABLE = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_DATE TEXT NOT NULL,
                $COLUMN_ITEM_NAME TEXT NOT NULL,
                $COLUMN_VALUE REAL NOT NULL,
                $COLUMN_IS_CHECKED INTEGER NOT NULL,
                PRIMARY KEY ($COLUMN_DATE, $COLUMN_ITEM_NAME) -- Unique combination of date and item name
            )
        """
        db?.execSQL(CREATE_TABLE)
        Log.d("MiscDB", "Table $TABLE_NAME created") // Add log
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        Log.d("MiscDB", "onUpgrade called: oldVersion=$oldVersion, newVersion=$newVersion") // Add log
        // Simple upgrade by dropping and recreating the table.
        // In a real app, you'd handle schema migration carefully if you need to preserve data.
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
        Log.d("MiscDB", "Table $TABLE_NAME dropped and recreated") // Add log
    }

    /**
     * Inserts or updates a list of miscellaneous data items for a given date.
     * This implementation deletes all existing items for the date and re-inserts the current list.
     * This is simpler but might be less efficient for large numbers of items.
     */
    fun insertOrUpdateMiscellaneousItems(date: String, items: List<TrackerItem>) {
        Log.d("MiscDB", "insertOrUpdateMiscellaneousItems called for date: $date with ${items.size} items") // Add log
        val db = writableDatabase
        db.beginTransaction() // Use a transaction for efficiency
        try {
            Log.d("MiscDB", "Deleting existing items for date: $date") // Add log
            // Delete all existing items for this date
            db.delete(TABLE_NAME, "$COLUMN_DATE = ?", arrayOf(date))
            Log.d("MiscDB", "Existing items deleted for date: $date") // Add log


            // Insert the current list of items
            for (item in items) {
                val values = ContentValues().apply {
                    put(COLUMN_DATE, date)
                    put(COLUMN_ITEM_NAME, item.name)
                    put(COLUMN_VALUE, item.value)
                    put(COLUMN_IS_CHECKED, if (item.isChecked) 1 else 0)
                }
                val rowId = db.insert(TABLE_NAME, null, values) // Insert the item
                if (rowId == -1L) {
                    Log.e("MiscDB", "Error inserting item ${item.name} for date $date") // Log insertion error
                } else {
                    Log.d("MiscDB", "Inserted item ${item.name} for date $date (rowId: $rowId)") // Log successful insertion
                }
            }
            db.setTransactionSuccessful()
            Log.d("MiscDB", "Transaction successful for date: $date") // Add log
        } catch (e: Exception) {
            Log.e("MiscDB", "Error updating miscellaneous items for $date: ${e.message}", e) // Log exception with stack trace
        } finally {
            db.endTransaction()
            Log.d("MiscDB", "Transaction ended for date: $date") // Add log
            db.close()
            Log.d("MiscDB", "Database closed after insert/update for date: $date") // Add log
        }
    }

    /**
     * Fetches miscellaneous data items for a specific date.
     * Returns a list of TrackerItem loaded from the database.
     * If no items are found for the date, it returns the default list of items.
     */
    fun fetchMiscellaneousItems(date: String): List<TrackerItem> {
        Log.d("MiscDB", "fetchMiscellaneousItems called for date: $date") // Add log
        val db = readableDatabase
        val items = mutableListOf<TrackerItem>()

        try { // Add try-catch block
            Log.d("MiscDB", "Querying DB for date: $date") // Add log
            val cursor = db.query(
                TABLE_NAME,
                arrayOf(COLUMN_ITEM_NAME, COLUMN_VALUE, COLUMN_IS_CHECKED),
                "$COLUMN_DATE = ?",
                arrayOf(date),
                null, // groupBy
                null, // having
                null  // orderBy
            )

            cursor?.use {
                Log.d("MiscDB", "Cursor obtained for date: $date, count: ${it.count}") // Add log
                while (it.moveToNext()) {
                    val name = it.getString(it.getColumnIndexOrThrow(COLUMN_ITEM_NAME))
                    val value = it.getFloat(it.getColumnIndexOrThrow(COLUMN_VALUE))
                    val isChecked = it.getInt(it.getColumnIndexOrThrow(COLUMN_IS_CHECKED)) == 1
                    items.add(TrackerItem(name, value, isChecked))
                    Log.d("MiscDB", "Fetched item: $name, $value, $isChecked") // Add log
                }
            }
            // Cursor is closed by cursor.use {}
            Log.d("MiscDB", "Finished querying DB for date: $date") // Add log
        } catch (e: Exception) {
            Log.e("MiscDB", "Exception during DB fetch for date: $date: ${e.message}", e) // Log exception
            // Re-throw the exception to help debug
            throw e
        } finally {
            // db.close() is called after this block
        }


        // Close the database connection
        db.close()
        Log.d("MiscDB", "Database closed after fetch for date: $date") // Add log


        return if (items.isEmpty()) {
            Log.d("MiscDB", "No items found for $date, returning default list") // Add log
            defaultMiscellaneousItems()
        } else {
            Log.d("MiscDB", "Returning ${items.size} items loaded from DB for $date") // Add log
            items
        }
    }

    /**
     * Exports all miscellaneous data to a CSV file.
     */
    fun exportToCSV() { // Removed context param, using the one passed to the helper constructor
        Log.d("CSVExportMisc", "Exporting miscellaneous data to CSV started.") // Add log
        val db = readableDatabase
        var cursor: Cursor? = null
        val fileName = "miscellaneous_data.csv"
        var writer: BufferedWriter? = null

        try {
            Log.d("CSVExportMisc", "Querying all data from table: $TABLE_NAME") // Add log
            // Query all data from the table, ordered by date and item name
            cursor = db.query(
                TABLE_NAME,
                arrayOf(COLUMN_DATE, COLUMN_ITEM_NAME, COLUMN_VALUE, COLUMN_IS_CHECKED),
                null, null, null, null, "$COLUMN_DATE ASC, $COLUMN_ITEM_NAME ASC"
            )
            Log.d("CSVExportMisc", "Query completed. Cursor count: ${cursor?.count}") // Add log


            // Get the external files directory
            val file = File(context.getExternalFilesDir(null), fileName)
            Log.d("CSVExportMisc", "Writing to file: ${file.absolutePath}") // Add log
            writer = BufferedWriter(FileWriter(file))

            // Write header row
            val header = "$COLUMN_DATE,$COLUMN_ITEM_NAME,$COLUMN_VALUE,$COLUMN_IS_CHECKED\n"
            writer.write(header)
            Log.d("CSVExportMisc", "Wrote header: $header") // Add log

            // Write data rows
            cursor?.use { // Use use block for automatic closing of cursor
                Log.d("CSVExportMisc", "Writing data rows...") // Add log
                var rowCount = 0
                while (it.moveToNext()) {
                    val date = it.getString(it.getColumnIndexOrThrow(COLUMN_DATE))
                    val itemName = it.getString(it.getColumnIndexOrThrow(COLUMN_ITEM_NAME))
                    val value = it.getFloat(it.getColumnIndexOrThrow(COLUMN_VALUE))
                    val isCheckedInt = it.getInt(it.getColumnIndexOrThrow(COLUMN_IS_CHECKED))
                    val isCheckedBoolean = if (isCheckedInt == 1) "true" else "false" // Convert int to boolean string

                    // Escape commas or double quotes in item_name by wrapping in double quotes and escaping existing double quotes
                    val escapedItemName = "\"${itemName.replace("\"", "\"\"")}\""

                    val rowData = "$date,$escapedItemName,$value,$isCheckedBoolean\n"
                    writer.write(rowData)
                    // Log.v("CSVExportMisc", "Wrote row: $rowData") // Use verbose log if needed for every row
                    rowCount++
                }
                Log.d("CSVExportMisc", "Finished writing $rowCount data rows.") // Add log
            }

            Log.d("CSVExportMisc", "Exported Miscellaneous data to ${file.absolutePath} successfully.") // Final success log

        } catch (e: IOException) {
            Log.e("CSVExportMisc", "IOException during CSV export: ${e.message}", e) // Log IO exception with stack trace
        } catch (e: Exception) {
            // Catch other potential exceptions during database query or data processing
            Log.e("CSVExportMisc", "An unexpected error occurred during Miscellaneous data export: ${e.message}", e) // Log unexpected exceptions
        } finally {
            // Ensure writer and database are closed
            try {
                writer?.close()
                Log.d("CSVExportMisc", "Writer closed.") // Add log
            } catch (e: IOException) {
                Log.e("CSVExportMisc", "Error closing writer: ${e.message}")
            }
            // Cursor is closed by the .use{} block
            if (db.isOpen) {
                db.close()
                Log.d("CSVExportMisc", "Database closed after export.") // Add log
            }
        }
    }
}
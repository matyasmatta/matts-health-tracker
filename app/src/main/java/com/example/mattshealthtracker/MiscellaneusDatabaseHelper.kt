package com.example.mattshealthtracker // Make sure this matches your package name

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException

class MiscellaneousDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "miscellaneous_tracker.db"
        const val DATABASE_VERSION = 2
        const val TABLE_NAME = "miscellaneous_items" // Table for individual items per date
        const val COLUMN_DATE = "date"
        const val COLUMN_ITEM_NAME = "item_name"
        const val COLUMN_VALUE = "value"
        const val COLUMN_IS_CHECKED = "is_checked"
    }

    override fun onCreate(db: SQLiteDatabase?) {
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
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        // Simple upgrade by dropping and recreating the table.
        // In a real app, you'd handle schema migration carefully if you need to preserve data.
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    /**
     * Inserts or updates a list of miscellaneous data items for a given date.
     * This implementation deletes all existing items for the date and re-inserts the current list.
     * This is simpler but might be less efficient for large numbers of items.
     */
    fun insertOrUpdateMiscellaneousItems(date: String, items: List<TrackerItem>) {
        val db = writableDatabase
        db.beginTransaction() // Use a transaction for efficiency
        try {
            // Delete all existing items for this date
            db.delete(TABLE_NAME, "$COLUMN_DATE = ?", arrayOf(date))

            // Insert the current list of items
            for (item in items) {
                val values = ContentValues().apply {
                    put(COLUMN_DATE, date)
                    put(COLUMN_ITEM_NAME, item.name)
                    put(COLUMN_VALUE, item.value)
                    put(COLUMN_IS_CHECKED, if (item.isChecked) 1 else 0)
                }
                db.insert(TABLE_NAME, null, values) // Insert the item
            }
            db.setTransactionSuccessful()
            Log.d("MiscDB", "Successfully updated miscellaneous items for $date")
        } catch (e: Exception) {
            Log.e("MiscDB", "Error updating miscellaneous items for $date: ${e.message}")
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    /**
     * Fetches miscellaneous data items for a specific date.
     * Returns a list of TrackerItem loaded from the database.
     * If no items are found for the date, it returns the default list of items.
     */
    fun fetchMiscellaneousItems(date: String): List<TrackerItem> {
        val db = readableDatabase
        val items = mutableListOf<TrackerItem>()

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
            while (it.moveToNext()) {
                val name = it.getString(it.getColumnIndexOrThrow(COLUMN_ITEM_NAME))
                val value = it.getFloat(it.getColumnIndexOrThrow(COLUMN_VALUE))
                val isChecked = it.getInt(it.getColumnIndexOrThrow(COLUMN_IS_CHECKED)) == 1
                items.add(TrackerItem(name, value, isChecked))
            }
        }
        // Close the cursor and database connection
        cursor?.close()
        db.close()

        // If no items were found for this date in the database, return the default list.
        // Otherwise, return the list loaded from the database.
        return if (items.isEmpty()) defaultMiscellaneousItems() else items
    }

    // You could add methods here for exporting this data to CSV as well if needed later
}
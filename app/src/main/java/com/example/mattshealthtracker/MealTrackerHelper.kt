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
import java.util.UUID // Make sure UUID is imported for FoodItem IDs

// Assuming these data classes and enums are defined elsewhere,
// for example, in FoodScreen.kt or a separate models file.
// If they are not in the same package, you might need an import statement here.
// For demonstration, I'm including them as comments.
/*
enum class MealType {
    BREAKFAST, SNACK1, LUNCH, SNACK2, DINNER, SNACK3
}

data class FoodItem(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "New Food Item",
    var calories: Int = 0,
    var healthyRating: Float = 0.5f,
    var lprFriendlyRating: Float = 0.5f,
    var ingredients: String = ""
)
*/

class MealTrackerHelper(private val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "meal_tracker.db"
        const val DATABASE_VERSION = 1
        const val TABLE_NAME = "food_items" // Table for individual food items
        const val COLUMN_ID = "id" // UUID for each food item
        const val COLUMN_DATE = "date"
        const val COLUMN_MEAL_TYPE = "meal_type"
        const val COLUMN_TITLE = "title"
        const val COLUMN_CALORIES = "calories"
        const val COLUMN_HEALTHY_RATING = "healthy_rating"
        const val COLUMN_LPR_FRIENDLY_RATING = "lpr_friendly_rating"
        const val COLUMN_INGREDIENTS = "ingredients"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        Log.d("MealDB", "onCreate called")
        val CREATE_TABLE = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID TEXT PRIMARY KEY NOT NULL,
                $COLUMN_DATE TEXT NOT NULL,
                $COLUMN_MEAL_TYPE TEXT NOT NULL,
                $COLUMN_TITLE TEXT NOT NULL,
                $COLUMN_CALORIES INTEGER NOT NULL,
                $COLUMN_HEALTHY_RATING REAL NOT NULL,
                $COLUMN_LPR_FRIENDLY_RATING REAL NOT NULL,
                $COLUMN_INGREDIENTS TEXT NOT NULL
            )
        """
        db?.execSQL(CREATE_TABLE)
        Log.d("MealDB", "Table $TABLE_NAME created")
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        Log.d("MealDB", "onUpgrade called: oldVersion=$oldVersion, newVersion=$newVersion")
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
        Log.d("MealDB", "Table $TABLE_NAME dropped and recreated")
    }

    /**
     * Saves all food items for a specific date and meal type.
     * This method deletes all existing food items for the given date and meal type,
     * then inserts the provided list of food items.
     */
    fun saveFoodItemsForMeal(date: String, mealType: MealType, foodItems: List<FoodItem>) {
        Log.d("MealDB", "saveFoodItemsForMeal called for date: $date, mealType: $mealType with ${foodItems.size} items")
        val db = writableDatabase
        db.beginTransaction()
        try {
            // Delete existing food items for this date and meal type
            db.delete(
                TABLE_NAME,
                "$COLUMN_DATE = ? AND $COLUMN_MEAL_TYPE = ?",
                arrayOf(date, mealType.name)
            )
            Log.d("MealDB", "Deleted existing items for $mealType on $date")

            // Insert new food items
            for (item in foodItems) {
                val values = ContentValues().apply {
                    put(COLUMN_ID, item.id)
                    put(COLUMN_DATE, date)
                    put(COLUMN_MEAL_TYPE, mealType.name) // Save enum name as string
                    put(COLUMN_TITLE, item.title)
                    put(COLUMN_CALORIES, item.calories)
                    put(COLUMN_HEALTHY_RATING, item.healthyRating)
                    put(COLUMN_LPR_FRIENDLY_RATING, item.lprFriendlyRating)
                    put(COLUMN_INGREDIENTS, item.ingredients)
                }
                val rowId = db.insert(TABLE_NAME, null, values)
                if (rowId == -1L) {
                    Log.e("MealDB", "Error inserting food item ${item.title} for $mealType on $date")
                } else {
                    Log.d("MealDB", "Inserted food item ${item.title} (ID: ${item.id}) for $mealType on $date (rowId: $rowId)")
                }
            }
            db.setTransactionSuccessful()
            Log.d("MealDB", "Transaction successful for $mealType on $date")
        } catch (e: Exception) {
            Log.e("MealDB", "Error saving food items for $mealType on $date: ${e.message}", e)
        } finally {
            db.endTransaction()
            db.close()
            Log.d("MealDB", "Database closed after save for $mealType on $date")
        }
    }

    /**
     * Fetches all food items for a specific date and meal type.
     * Returns a list of FoodItem loaded from the database.
     */
    fun fetchFoodItemsForMeal(date: String, mealType: MealType): List<FoodItem> {
        Log.d("MealDB", "fetchFoodItemsForMeal called for date: $date, mealType: $mealType")
        val db = readableDatabase
        val items = mutableListOf<FoodItem>()
        var cursor: Cursor? = null

        try {
            cursor = db.query(
                TABLE_NAME,
                arrayOf(
                    COLUMN_ID,
                    COLUMN_TITLE,
                    COLUMN_CALORIES,
                    COLUMN_HEALTHY_RATING,
                    COLUMN_LPR_FRIENDLY_RATING,
                    COLUMN_INGREDIENTS
                ),
                "$COLUMN_DATE = ? AND $COLUMN_MEAL_TYPE = ?",
                arrayOf(date, mealType.name),
                null, null, null
            )

            cursor?.use {
                Log.d("MealDB", "Cursor obtained for $mealType on $date, count: ${it.count}")
                while (it.moveToNext()) {
                    val id = it.getString(it.getColumnIndexOrThrow(COLUMN_ID))
                    val title = it.getString(it.getColumnIndexOrThrow(COLUMN_TITLE))
                    val calories = it.getInt(it.getColumnIndexOrThrow(COLUMN_CALORIES))
                    val healthyRating = it.getFloat(it.getColumnIndexOrThrow(COLUMN_HEALTHY_RATING))
                    val lprFriendlyRating = it.getFloat(it.getColumnIndexOrThrow(COLUMN_LPR_FRIENDLY_RATING))
                    val ingredients = it.getString(it.getColumnIndexOrThrow(COLUMN_INGREDIENTS))

                    items.add(
                        FoodItem(
                            id = id,
                            title = title,
                            calories = calories,
                            healthyRating = healthyRating,
                            lprFriendlyRating = lprFriendlyRating,
                            ingredients = ingredients
                        )
                    )
                    Log.d("MealDB", "Fetched food item: $title (ID: $id)")
                }
            }
        } catch (e: Exception) {
            Log.e("MealDB", "Exception during DB fetch for $mealType on $date: ${e.message}", e)
            throw e
        } finally {
            cursor?.close()
            db.close()
            Log.d("MealDB", "Database closed after fetch for $mealType on $date")
        }
        Log.d("MealDB", "Returning ${items.size} food items loaded from DB for $mealType on $date")
        return items
    }

    /**
     * Exports all meal data to a CSV file.
     */
    fun exportToCSV() {
        Log.d("CSVExportMeal", "Exporting meal data to CSV started.")
        val db = readableDatabase
        var cursor: Cursor? = null
        val fileName = "meal_data.csv"
        var writer: BufferedWriter? = null

        try {
            Log.d("CSVExportMeal", "Querying all data from table: $TABLE_NAME")
            cursor = db.query(
                TABLE_NAME,
                arrayOf(
                    COLUMN_DATE,
                    COLUMN_MEAL_TYPE,
                    COLUMN_TITLE,
                    COLUMN_CALORIES,
                    COLUMN_HEALTHY_RATING,
                    COLUMN_LPR_FRIENDLY_RATING,
                    COLUMN_INGREDIENTS
                ),
                null, null, null, null, "$COLUMN_DATE ASC, $COLUMN_MEAL_TYPE ASC, $COLUMN_TITLE ASC"
            )
            Log.d("CSVExportMeal", "Query completed. Cursor count: ${cursor?.count}")

            val file = File(context.getExternalFilesDir(null), fileName)
            Log.d("CSVExportMeal", "Writing to file: ${file.absolutePath}")
            writer = BufferedWriter(FileWriter(file))

            // Write header row
            val header = "$COLUMN_DATE,$COLUMN_MEAL_TYPE,$COLUMN_TITLE,$COLUMN_CALORIES,$COLUMN_HEALTHY_RATING,$COLUMN_LPR_FRIENDLY_RATING,$COLUMN_INGREDIENTS\n"
            writer.write(header)
            Log.d("CSVExportMeal", "Wrote header: $header")

            // Write data rows
            cursor?.use {
                Log.d("CSVExportMeal", "Writing data rows...")
                var rowCount = 0
                while (it.moveToNext()) {
                    val date = it.getString(it.getColumnIndexOrThrow(COLUMN_DATE))
                    val mealType = it.getString(it.getColumnIndexOrThrow(COLUMN_MEAL_TYPE))
                    val title = it.getString(it.getColumnIndexOrThrow(COLUMN_TITLE))
                    val calories = it.getInt(it.getColumnIndexOrThrow(COLUMN_CALORIES))
                    val healthyRating = it.getFloat(it.getColumnIndexOrThrow(COLUMN_HEALTHY_RATING))
                    val lprFriendlyRating = it.getFloat(it.getColumnIndexOrThrow(COLUMN_LPR_FRIENDLY_RATING))
                    val ingredients = it.getString(it.getColumnIndexOrThrow(COLUMN_INGREDIENTS))

                    // Escape commas or double quotes in text fields
                    val escapedTitle = "\"${title.replace("\"", "\"\"")}\""
                    val escapedIngredients = "\"${ingredients.replace("\"", "\"\"")}\""

                    val rowData = "$date,$mealType,$escapedTitle,$calories,$healthyRating,$lprFriendlyRating,$escapedIngredients\n"
                    writer.write(rowData)
                    rowCount++
                }
                Log.d("CSVExportMeal", "Finished writing $rowCount data rows.")
            }

            Log.d("CSVExportMeal", "Exported Meal data to ${file.absolutePath} successfully.")

        } catch (e: IOException) {
            Log.e("CSVExportMeal", "IOException during CSV export: ${e.message}", e)
        } catch (e: Exception) {
            Log.e("CSVExportMeal", "An unexpected error occurred during Meal data export: ${e.message}", e)
        } finally {
            try {
                writer?.close()
                Log.d("CSVExportMeal", "Writer closed.")
            } catch (e: IOException) {
                Log.e("CSVExportMeal", "Error closing writer: ${e.message}")
            }
            if (db.isOpen) {
                db.close()
                Log.d("CSVExportMeal", "Database closed after export.")
            }
        }
    }
}
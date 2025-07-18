package com.example.mattshealthtracker

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class GroceryDatabaseHelper(private val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "grocery_db.db"
        const val DATABASE_VERSION = 2
        const val TABLE_NAME = "grocery_items"
        const val COLUMN_ID = "id"
        const val COLUMN_NAME = "name"
        const val COLUMN_IS_HEALTHY = "is_healthy"
        const val COLUMN_IS_LPR_FRIENDLY = "is_lpr_friendly"
        const val COLUMN_AVG_CALORIES_PER_100G = "avg_calories_per_100g"
        const val COLUMN_COMMON_UNITS = "common_units"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        Log.d("GroceryDB", "onCreate called")
        val CREATE_TABLE = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID TEXT PRIMARY KEY NOT NULL,
                $COLUMN_NAME TEXT NOT NULL UNIQUE,
                $COLUMN_IS_HEALTHY INTEGER NOT NULL DEFAULT 0,
                $COLUMN_IS_LPR_FRIENDLY INTEGER NOT NULL DEFAULT 0,
                $COLUMN_AVG_CALORIES_PER_100G INTEGER,
                $COLUMN_COMMON_UNITS TEXT
            )
        """
        db?.execSQL(CREATE_TABLE)
        Log.d("GroceryDB", "Table $TABLE_NAME created")

        // Pre-populate with some initial data
        insertInitialGroceries(db)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        Log.d("GroceryDB", "onUpgrade called: oldVersion=$oldVersion, newVersion=$newVersion")
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
        Log.d("GroceryDB", "Table $TABLE_NAME dropped and recreated")
    }

    private fun insertInitialGroceries(db: SQLiteDatabase?) {
        val initialItems = listOf(
            // --- Fruits 🍎 ---
            GroceryItem("f1", "Apple", true, true),
            GroceryItem("f2", "Banana", true, true),
            GroceryItem("f3", "Orange", true, true),
            GroceryItem("f4", "Grapes", true, true),
            GroceryItem("f5", "Strawberry", true, true),
            GroceryItem("f6", "Blueberry", true, true),
            GroceryItem("f7", "Raspberry", true, true),
            GroceryItem("f8", "Avocado", true, true),
            GroceryItem("f9", "Mango", true, false),
            GroceryItem("f10", "Pineapple", true, false),
            GroceryItem("f11", "Watermelon", true, true),
            GroceryItem("f12", "Kiwi", true, true),
            GroceryItem("f13", "Lemon", true, true),
            GroceryItem("f14", "Lime", true, true),
            GroceryItem("f15", "Pear", true, true),
            GroceryItem("f16", "Peach", true, true),
            GroceryItem("f17", "Plum", true, true),
            GroceryItem("f18", "Cherry", true, true),
            GroceryItem("f19", "Cantaloupe", true, true),
            GroceryItem("f20", "Honeydew Melon", true, true),

            // --- Vegetables 🥦 ---
            GroceryItem("v1", "Broccoli", true, true),
            GroceryItem("v2", "Spinach", true, true),
            GroceryItem("v3", "Carrot", true, true),
            GroceryItem("v4", "Cucumber", true, true),
            GroceryItem("v5", "Lettuce", true, true),
            GroceryItem("v6", "Tomato", true, true),
            GroceryItem("v7", "Bell Pepper", true, true),
            GroceryItem("v8", "Onion", true, true),
            GroceryItem("v9", "Garlic", true, true),
            GroceryItem("v10", "Potato", true, false),
            GroceryItem("v11", "Sweet Potato", true, true),
            GroceryItem("v12", "Zucchini", true, true),
            GroceryItem("v13", "Eggplant", true, true),
            GroceryItem("v14", "Mushrooms", true, true),
            GroceryItem("v15", "Green Beans", true, true),
            GroceryItem("v16", "Asparagus", true, true),
            GroceryItem("v17", "Cauliflower", true, true),
            GroceryItem("v18", "Cabbage", true, true),
            GroceryItem("v19", "Celery", true, true),
            GroceryItem("v20", "Kale", true, true),
            GroceryItem("v21", "Brussels Sprouts", true, true),
            GroceryItem("v22", "Artichoke", true, true),
            GroceryItem("v23", "Corn", true, false),
            GroceryItem("v24", "Peas", true, true),
            GroceryItem("v25", "Pumpkin", true, true),

            // --- Proteins (Meat, Poultry, Fish, Legumes, Dairy) 🍗 ---
            GroceryItem("p1", "Chicken Breast", true, true),
            GroceryItem("p2", "Salmon", true, true),
            GroceryItem("p3", "Cod", true, true),
            GroceryItem("p4", "Tuna", true, true),
            GroceryItem("p5", "Beef Steak", true, false),
            GroceryItem("p6", "Pork Loin", true, false),
            GroceryItem("p7", "Ground Turkey", true, true),
            GroceryItem("p8", "Egg", true, true),
            GroceryItem("p9", "Yogurt (Plain)", true, true),
            GroceryItem("p10", "Cottage Cheese", true, true),
            GroceryItem("p11", "Tofu", true, true),
            GroceryItem("p12", "Lentils", true, true),
            GroceryItem("p13", "Chickpeas", true, true),
            GroceryItem("p14", "Black Beans", true, true),
            GroceryItem("p15", "Kidney Beans", true, true),
            GroceryItem("p16", "Almonds", true, true),
            GroceryItem("p17", "Walnuts", true, true),
            GroceryItem("p18", "Peanuts", true, false),
            GroceryItem("p19", "Whey Protein Powder", true, false),
            GroceryItem("p20", "Casein Protein Powder", true, false),
            GroceryItem("p21", "Tempeh", true, true),
            GroceryItem("p22", "Edamame", true, true),
            GroceryItem("p23", "Shrimp", true, true),
            GroceryItem("p24", "Scallops", true, true),
            GroceryItem("p25", "Halibut", true, true),

            // --- Grains & Starches 🍚 ---
            GroceryItem("g1", "Rice (Brown)", true, true),
            GroceryItem("g2", "Oatmeal", true, true),
            GroceryItem("g3", "Quinoa", true, true),
            GroceryItem("g4", "Whole Wheat Bread", true, false),
            GroceryItem("g5", "Whole Wheat Pasta", true, false),
            GroceryItem("g6", "Corn Tortillas", true, false),
            GroceryItem("g7", "Couscous", true, false),
            GroceryItem("g8", "Barley", true, false),
            GroceryItem("g9", "Rye Bread", true, false),
            GroceryItem("g10", "Granola", false, false),
            GroceryItem("g11", "White Bread", false, false),
            GroceryItem("g12", "White Rice", false, false),
            GroceryItem("g13", "White Pasta", false, false),
            GroceryItem("g14", "Breakfast Cereal (Sugary)", false, false),
            GroceryItem("g15", "Bagel", false, false),

            // --- Dairy & Alternatives 🥛 ---
            GroceryItem("d1", "Milk (Cow's)", false, false),
            GroceryItem("d2", "Almond Milk", true, true),
            GroceryItem("d3", "Soy Milk", true, true),
            GroceryItem("d4", "Oat Milk", true, false),
            GroceryItem("d5", "Greek Yogurt", true, true),
            GroceryItem("d6", "Kefir", true, false),
            GroceryItem("d7", "Cheddar Cheese", false, false),
            GroceryItem("d8", "Mozzarella Cheese", false, false),
            GroceryItem("d9", "Parmesan Cheese", false, false),
            GroceryItem("d10", "Butter", false, false),
            GroceryItem("d11", "Cream Cheese", false, false),
            GroceryItem("d12", "Sour Cream", false, false),
            GroceryItem("d13", "Lactose-Free Milk", false, true),
            GroceryItem("d14", "Goat Cheese", false, false),

            // --- Fats & Oils 🫒 ---
            GroceryItem("o1", "Olive Oil", true, true),
            GroceryItem("o2", "Coconut Oil", true, false),
            GroceryItem("o3", "Avocado Oil", true, true),
            GroceryItem("o4", "Flaxseed Oil", true, true),
            GroceryItem("o5", "Peanut Butter", true, false),
            GroceryItem("o6", "Almond Butter", true, true),
            GroceryItem("o7", "Tahini", true, true),
            GroceryItem("o8", "Mayonnaise", false, false),
            GroceryItem("o9", "Margarine", false, false),

            // --- Beverages 🥤 ---
            GroceryItem("b1", "Water", true, true),
            GroceryItem("b2", "Coffee (Black)", true, true),
            GroceryItem("b3", "Green Tea", true, true),
            GroceryItem("b4", "Black Tea", true, true),
            GroceryItem("b5", "Herbal Tea", true, true),
            GroceryItem("b6", "Sparkling Water", true, true),
            GroceryItem("b7", "Orange Juice (100%)", false, false),
            GroceryItem("b8", "Apple Juice (100%)", false, false),
            GroceryItem("b9", "Soda (Cola)", false, false),
            GroceryItem("b10", "Diet Soda", false, false),
            GroceryItem("b11", "Energy Drink", false, false),
            GroceryItem("b12", "Wine", false, false),
            GroceryItem("b13", "Beer", false, false),
            GroceryItem("b14", "Sports Drink", false, false),

            // --- Spices & Condiments 🧂 ---
            GroceryItem("s1", "Salt", true, true),
            GroceryItem("s2", "Black Pepper", true, true),
            GroceryItem("s3", "Turmeric", true, true),
            GroceryItem("s4", "Cumin", true, true),
            GroceryItem("s5", "Paprika", true, true),
            GroceryItem("s6", "Oregano", true, true),
            GroceryItem("s7", "Basil", true, true),
            GroceryItem("s8", "Chili Powder", true, false),
            GroceryItem("s9", "Soy Sauce", false, false),
            GroceryItem("s10", "Mustard", false, true),
            GroceryItem("s11", "Ketchup", false, false),
            GroceryItem("s12", "Vinegar (Apple Cider)", true, true),
            GroceryItem("s13", "Honey", true, false),
            GroceryItem("s14", "Maple Syrup", true, false),
            GroceryItem("s15", "Sugar", false, false),
            GroceryItem("s16", "Artificial Sweetener", false, true),
            GroceryItem("s17", "Sriracha", false, false),
            GroceryItem("s18", "Mayonnaise", false, false),
            GroceryItem("s19", "Hot Sauce", true, true),
            GroceryItem("s20", "Nutritional Yeast", true, true),

            // --- Snacks & Desserts 🍪 ---
            GroceryItem("sn1", "Chips (Potato)", false, false),
            GroceryItem("sn2", "Cookies", false, false),
            GroceryItem("sn3", "Chocolate Bar", false, false),
            GroceryItem("sn4", "Ice Cream", false, false),
            GroceryItem("sn5", "Popcorn (Plain)", true, false),
            GroceryItem("sn6", "Rice Cakes", true, true),
            GroceryItem("sn7", "Energy Bar", false, false),
            GroceryItem("sn8", "Pretzels", false, false),
            GroceryItem("sn9", "Fruit Leather", false, false),
            GroceryItem("sn10", "Smoothie", true, true),

            // --- Prepared & Miscellaneous 🥫 ---
            GroceryItem("m1", "Canned Tomatoes", true, true),
            GroceryItem("m2", "Tomato Paste", true, true),
            GroceryItem("m3", "Broth (Chicken/Veg)", true, true),
            GroceryItem("m4", "Canned Tuna", true, true),
            GroceryItem("m5", "Frozen Vegetables (Mixed)", true, true),
            GroceryItem("m6", "Frozen Berries", true, true),
            GroceryItem("m7", "Pizza (Frozen)", false, false),
            GroceryItem("m8", "Chicken Noodle Soup (Canned)", false, false),
            GroceryItem("m9", "Peanut Butter (Regular)", false, false),
            GroceryItem("m10", "Hummus", true, true),
            GroceryItem("m11", "Salsa", true, true),
            GroceryItem("m12", "Guacamole", true, true),
            GroceryItem("m13", "Salad Dressing (Creamy)", false, false),
            GroceryItem("m14", "Vinegar (Balsamic)", true, false),
            GroceryItem("m15", "Soy Sauce (Low Sodium)", true, false),
            GroceryItem("m16", "Kombucha", true, false),
            GroceryItem("m17", "Pickles", true, true),
            GroceryItem("m18", "Olives", true, true),
            GroceryItem("m19", "Canned Sardines", true, true),
            GroceryItem("m20", "Kimchi", true, true)
        )

        initialItems.forEach { item ->
            val values = ContentValues().apply {
                put(COLUMN_ID, item.id)
                put(COLUMN_NAME, item.name)
                put(COLUMN_IS_HEALTHY, if (item.isHealthy) 1 else 0)
                put(COLUMN_IS_LPR_FRIENDLY, if (item.isLPRFriendly) 1 else 0)
                put(COLUMN_AVG_CALORIES_PER_100G, item.averageCaloriesPer100g)
                put(COLUMN_COMMON_UNITS, item.commonUnits)
            }
            try {
                db?.insert(TABLE_NAME, null, values)
                Log.d("GroceryDB", "Inserted initial grocery: ${item.name}")
            } catch (e: Exception) {
                Log.e("GroceryDB", "Error inserting initial grocery ${item.name}: ${e.message}")
            }
        }
    }

    /**
     * Inserts a single grocery item into the database.
     * Returns true if successful, false otherwise.
     */
    fun insertGrocery(item: GroceryItem): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_ID, item.id)
            put(COLUMN_NAME, item.name)
            put(COLUMN_IS_HEALTHY, if (item.isHealthy) 1 else 0)
            put(COLUMN_IS_LPR_FRIENDLY, if (item.isLPRFriendly) 1 else 0)
            put(COLUMN_AVG_CALORIES_PER_100G, item.averageCaloriesPer100g)
            put(COLUMN_COMMON_UNITS, item.commonUnits)
        }
        val rowId = db.insert(TABLE_NAME, null, values)
        db.close()
        return rowId != -1L
    }

    /**
     * Searches for grocery items whose names contain the given query string (case-insensitive).
     */
    fun searchGroceries(query: String): List<GroceryItem> {
        val db = readableDatabase
        val items = mutableListOf<GroceryItem>()
        var cursor: Cursor? = null
        val selection = "$COLUMN_NAME LIKE ?"
        val selectionArgs = arrayOf("%$query%") // Wildcard for contains search
        val orderBy = "$COLUMN_NAME ASC" // Order results alphabetically

        try {
            cursor = db.query(
                TABLE_NAME,
                null, // All columns
                selection,
                selectionArgs,
                null, null,
                orderBy
            )

            cursor?.use {
                val idColIndex = it.getColumnIndexOrThrow(COLUMN_ID)
                val nameColIndex = it.getColumnIndexOrThrow(COLUMN_NAME)
                val isHealthyColIndex = it.getColumnIndexOrThrow(COLUMN_IS_HEALTHY)
                val isLPRFriendlyColIndex = it.getColumnIndexOrThrow(COLUMN_IS_LPR_FRIENDLY)
                val avgCaloriesColIndex = it.getColumnIndexOrThrow(COLUMN_AVG_CALORIES_PER_100G)
                val commonUnitsColIndex = it.getColumnIndexOrThrow(COLUMN_COMMON_UNITS)

                while (it.moveToNext()) {
                    val id = it.getString(idColIndex)
                    val name = it.getString(nameColIndex)
                    val isHealthy = it.getInt(isHealthyColIndex) == 1
                    val isLPRFriendly = it.getInt(isLPRFriendlyColIndex) == 1
                    val avgCalories = if (it.isNull(avgCaloriesColIndex)) null else it.getInt(avgCaloriesColIndex)
                    val commonUnits = it.getString(commonUnitsColIndex)

                    items.add(
                        GroceryItem(id, name, isHealthy, isLPRFriendly, avgCalories, commonUnits)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("GroceryDB", "Error searching groceries: ${e.message}", e)
        } finally {
            cursor?.close()
            db.close()
        }
        return items
    }

    // You can add more methods here like updateGrocery, deleteGrocery, getAllGroceries if needed
}
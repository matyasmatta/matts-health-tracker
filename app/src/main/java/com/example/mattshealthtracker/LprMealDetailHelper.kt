// LprMealDetailHelper.kt
package com.example.mattshealthtracker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class LprMealDetailHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "LprMealDetails.db"
        private const val TABLE_LPR_MEAL_DETAILS = "lpr_meal_details"

        private const val KEY_DATE = "date" // TEXT, "yyyy-MM-dd"
        private const val KEY_MEAL_TYPE = "meal_type" // TEXT, store MealType.name
        private const val KEY_TOOK_ITOPRIDE = "took_itopride" // INTEGER (0 or 1)
        private const val KEY_ITOPRIDE_TIMESTAMP = "itopride_timestamp" // INTEGER (Long millis)
        private const val KEY_STARTED_UPRIGHT_TIMER = "started_upright_timer" // INTEGER
        private const val KEY_COMPLETED_UPRIGHT = "completed_upright" // INTEGER
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = ("CREATE TABLE $TABLE_LPR_MEAL_DETAILS ("
                + "$KEY_DATE TEXT,"
                + "$KEY_MEAL_TYPE TEXT,"
                + "$KEY_TOOK_ITOPRIDE INTEGER DEFAULT 0,"
                + "$KEY_ITOPRIDE_TIMESTAMP INTEGER,"
                + "$KEY_STARTED_UPRIGHT_TIMER INTEGER DEFAULT 0,"
                + "$KEY_COMPLETED_UPRIGHT INTEGER DEFAULT 0,"
                + "PRIMARY KEY ($KEY_DATE, $KEY_MEAL_TYPE))") // Composite primary key
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_LPR_MEAL_DETAILS")
        onCreate(db)
    }

    fun saveOrUpdateLprMealDetail(detail: LprMealDetail) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(KEY_DATE, detail.date)
            put(KEY_MEAL_TYPE, detail.mealType.name)
            put(KEY_TOOK_ITOPRIDE, if (detail.tookItopride) 1 else 0)
            put(KEY_ITOPRIDE_TIMESTAMP, detail.itoprideTimestamp)
            put(KEY_STARTED_UPRIGHT_TIMER, if (detail.startedUprightTimer) 1 else 0)
            put(KEY_COMPLETED_UPRIGHT, if (detail.completedUprightRequirement) 1 else 0)
        }

        // Try to update, if it fails (returns 0 rows affected), then insert.
        val rowsAffected = db.update(
            TABLE_LPR_MEAL_DETAILS,
            values,
            "$KEY_DATE = ? AND $KEY_MEAL_TYPE = ?",
            arrayOf(detail.date, detail.mealType.name)
        )

        if (rowsAffected == 0) {
            db.insert(TABLE_LPR_MEAL_DETAILS, null, values)
        }
        Log.d(
            "LprMealDetailHelper",
            "Saved/Updated LPR detail for ${detail.date} - ${detail.mealType.name}"
        )
        db.close()
    }

    fun getLprMealDetail(date: String, mealType: MealType): LprMealDetail? {
        val db = this.readableDatabase
        var detail: LprMealDetail? = null
        val cursor = db.query(
            TABLE_LPR_MEAL_DETAILS,
            null, // All columns
            "$KEY_DATE = ? AND $KEY_MEAL_TYPE = ?",
            arrayOf(date, mealType.name),
            null, null, null
        )

        if (cursor.moveToFirst()) {
            detail = LprMealDetail(
                date = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DATE)),
                mealType = MealType.valueOf(
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            KEY_MEAL_TYPE
                        )
                    )
                ),
                tookItopride = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_TOOK_ITOPRIDE)) == 1,
                itoprideTimestamp = cursor.getLong(
                    cursor.getColumnIndexOrThrow(
                        KEY_ITOPRIDE_TIMESTAMP
                    )
                ).let {
                    if (it == 0L && !(cursor.getInt(
                            cursor.getColumnIndexOrThrow(KEY_TOOK_ITOPRIDE)
                        ) == 1)
                    ) null else it
                }, // Handle null if not taken
                startedUprightTimer = cursor.getInt(
                    cursor.getColumnIndexOrThrow(
                        KEY_STARTED_UPRIGHT_TIMER
                    )
                ) == 1,
                completedUprightRequirement = cursor.getInt(
                    cursor.getColumnIndexOrThrow(
                        KEY_COMPLETED_UPRIGHT
                    )
                ) == 1
            )
        }
        cursor.close()
        db.close()
        return detail
    }

    fun getAllLprMealDetailsForDate(date: String): Map<MealType, LprMealDetail> {
        val db = this.readableDatabase
        val detailsMap = mutableMapOf<MealType, LprMealDetail>()
        val cursor = db.query(
            TABLE_LPR_MEAL_DETAILS,
            null, // All columns
            "$KEY_DATE = ?",
            arrayOf(date),
            null, null, null
        )

        while (cursor.moveToNext()) {
            val mealType =
                MealType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(KEY_MEAL_TYPE)))
            val detail = LprMealDetail(
                date = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DATE)),
                mealType = mealType,
                tookItopride = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_TOOK_ITOPRIDE)) == 1,
                itoprideTimestamp = cursor.getLong(
                    cursor.getColumnIndexOrThrow(
                        KEY_ITOPRIDE_TIMESTAMP
                    )
                ).let {
                    if (it == 0L && !(cursor.getInt(
                            cursor.getColumnIndexOrThrow(KEY_TOOK_ITOPRIDE)
                        ) == 1)
                    ) null else it
                },
                startedUprightTimer = cursor.getInt(
                    cursor.getColumnIndexOrThrow(
                        KEY_STARTED_UPRIGHT_TIMER
                    )
                ) == 1,
                completedUprightRequirement = cursor.getInt(
                    cursor.getColumnIndexOrThrow(
                        KEY_COMPLETED_UPRIGHT
                    )
                ) == 1
            )
            detailsMap[mealType] = detail
        }
        cursor.close()
        db.close()
        return detailsMap
    }
}
   
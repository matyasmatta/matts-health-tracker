package com.example.mattshealthtracker

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.provider.BaseColumns
import android.provider.Settings // For ANDROID_ID
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord // Added import
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.request.AggregateRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mattshealthtracker.AppGlobals.openedDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

// --- Database Contract and Helper (No Changes) ---

object HealthMetricsContract {
    object DailyMetricEntry : BaseColumns {
        const val TABLE_NAME = "daily_health_metrics"
        const val COLUMN_NAME_DATE = "date_iso" // Storing date as "YYYY-MM-DD"
        const val COLUMN_NAME_STEPS = "steps"
        const val COLUMN_NAME_SLEEP_DURATION_MILLIS = "sleep_duration_millis"
        const val COLUMN_NAME_ACTIVE_CALORIES = "active_calories"
        const val COLUMN_NAME_WEIGHT_KG = "weight_kg"
        const val COLUMN_NAME_EXERCISE_MINUTES = "exercise_minutes" // <<< NEW COLUMN
        const val COLUMN_NAME_LAST_UPDATED_TIMESTAMP = "last_updated_timestamp"
        const val COLUMN_NAME_SOURCE_DEVICE_ID = "source_device_id"
    }
}

data class DailyHealthMetric(
    val date: String, // "YYYY-MM-DD"
    val steps: Long?,
    val sleepDurationMillis: Long?,
    val activeCaloriesBurned: Double?,
    val weightKg: Double?,
    val exerciseMinutes: Long?, // <<< NEW FIELD
    val lastUpdatedTimestamp: Long,
    val sourceDeviceId: String
)

class HealthMetricsDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_ENTRIES)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Simple upgrade path for adding one column without dropping the table
            try {
                db.execSQL("ALTER TABLE ${HealthMetricsContract.DailyMetricEntry.TABLE_NAME} ADD COLUMN ${HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_EXERCISE_MINUTES} INTEGER")
                Log.i(
                    "HealthMetricsDbHelper",
                    "Upgraded database from $oldVersion to $newVersion, added EXERCISE_MINUTES column."
                )
            } catch (e: Exception) {
                Log.e("HealthMetricsDbHelper", "Error upgrading DB. Dropping and recreating.", e)
                // Fallback to dropping if alter fails
                db.execSQL(SQL_DELETE_ENTRIES)
                onCreate(db)
            }
        } else {
            // For future upgrades, or if something went wrong
            db.execSQL(SQL_DELETE_ENTRIES)
            onCreate(db)
        }
    }

    suspend fun insertOrUpdateDailyMetric(metric: DailyHealthMetric) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_DATE, metric.date)
            metric.steps?.let { put(HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_STEPS, it) }
                ?: putNull(HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_STEPS)
            metric.sleepDurationMillis?.let {
                put(
                    HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_SLEEP_DURATION_MILLIS,
                    it
                )
            }
                ?: putNull(HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_SLEEP_DURATION_MILLIS)
            metric.activeCaloriesBurned?.let {
                put(
                    HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_ACTIVE_CALORIES,
                    it
                )
            }
                ?: putNull(HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_ACTIVE_CALORIES)
            metric.weightKg?.let {
                put(
                    HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_WEIGHT_KG,
                    it
                )
            }
                ?: putNull(HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_WEIGHT_KG)
            metric.exerciseMinutes?.let {
                put(
                    HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_EXERCISE_MINUTES,
                    it
                )
            }
                ?: putNull(HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_EXERCISE_MINUTES)
            put(
                HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_LAST_UPDATED_TIMESTAMP,
                metric.lastUpdatedTimestamp
            )
            put(
                HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_SOURCE_DEVICE_ID,
                metric.sourceDeviceId
            )
        }

        val updatedRows = db.update(
            HealthMetricsContract.DailyMetricEntry.TABLE_NAME,
            values,
            "${HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_DATE} = ?",
            arrayOf(metric.date)
        )

        if (updatedRows == 0) {
            db.insert(HealthMetricsContract.DailyMetricEntry.TABLE_NAME, null, values)
        }
        Log.d("HealthMetricsDbHelper", "Inserted/Updated metric for date: ${metric.date}")
    }


    suspend fun getDailyMetric(dateString: String): DailyHealthMetric? =
        withContext(Dispatchers.IO) {
            val db = readableDatabase
            val projection = arrayOf(
                BaseColumns._ID,
                HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_DATE,
                HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_STEPS,
                HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_SLEEP_DURATION_MILLIS,
                HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_ACTIVE_CALORIES,
                HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_WEIGHT_KG,
                HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_EXERCISE_MINUTES,
                HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_LAST_UPDATED_TIMESTAMP,
                HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_SOURCE_DEVICE_ID
            )
            val selection = "${HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_DATE} = ?"
            val selectionArgs = arrayOf(dateString)
            val cursor = db.query(
                HealthMetricsContract.DailyMetricEntry.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                null
            )
            var metric: DailyHealthMetric? = null
            with(cursor) {
                if (moveToFirst()) {
                    metric = DailyHealthMetric(
                        date = getString(getColumnIndexOrThrow(HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_DATE)),
                        steps = if (isNull(getColumnIndexOrThrow(HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_STEPS))) null else getLong(
                            getColumnIndexOrThrow(HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_STEPS)
                        ),
                        sleepDurationMillis = if (isNull(getColumnIndexOrThrow(HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_SLEEP_DURATION_MILLIS))) null else getLong(
                            getColumnIndexOrThrow(HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_SLEEP_DURATION_MILLIS)
                        ),
                        activeCaloriesBurned = if (isNull(
                                getColumnIndexOrThrow(
                                    HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_ACTIVE_CALORIES
                                )
                            )
                        ) null else getDouble(getColumnIndexOrThrow(HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_ACTIVE_CALORIES)),
                        weightKg = if (isNull(getColumnIndexOrThrow(HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_WEIGHT_KG))) null else getDouble(
                            getColumnIndexOrThrow(HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_WEIGHT_KG)
                        ),
                        exerciseMinutes = if (isNull(getColumnIndexOrThrow(HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_EXERCISE_MINUTES))) null else getLong(
                            getColumnIndexOrThrow(HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_EXERCISE_MINUTES)
                        ),
                        lastUpdatedTimestamp = getLong(getColumnIndexOrThrow(HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_LAST_UPDATED_TIMESTAMP)),
                        sourceDeviceId = getString(getColumnIndexOrThrow(HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_SOURCE_DEVICE_ID))
                    )
                }
            }
            cursor.close()
            Log.d("HealthMetricsDbHelper", "Fetched metric for $dateString: ${metric != null}")
            metric
        }

    companion object {
        const val DATABASE_VERSION = 2
        const val DATABASE_NAME = "HealthMetrics.db"

        private const val SQL_CREATE_ENTRIES =
            "CREATE TABLE ${HealthMetricsContract.DailyMetricEntry.TABLE_NAME} (" +
                    "${BaseColumns._ID} INTEGER PRIMARY KEY," +
                    "${HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_DATE} TEXT UNIQUE," +
                    "${HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_STEPS} INTEGER," +
                    "${HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_SLEEP_DURATION_MILLIS} INTEGER," +
                    "${HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_ACTIVE_CALORIES} REAL," +
                    "${HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_WEIGHT_KG} REAL," +
                    "${HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_EXERCISE_MINUTES} INTEGER," +
                    "${HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_LAST_UPDATED_TIMESTAMP} INTEGER," +
                    "${HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_SOURCE_DEVICE_ID} TEXT)"

        private const val SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS ${HealthMetricsContract.DailyMetricEntry.TABLE_NAME}"
    }
}


class HealthConnectIntegrator(private val context: Context) {

    private val dbHelper by lazy { HealthMetricsDbHelper(context) }
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val healthConnectClient: HealthConnectClient? by lazy {
        val status = HealthConnectClient.getSdkStatus(context)
        if (status == HealthConnectClient.SDK_AVAILABLE) {
            Log.d("HealthConnectIntegrator", "Health Connect SDK is available.")
            HealthConnectClient.getOrCreate(context)
        } else {
            Log.w("HealthConnectIntegrator", "Health Connect SDK not available. Status: $status")
            null
        }
    }

    val permissions = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    )

    private fun getMyDeviceId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown_device_${UUID.randomUUID()}"
    }

    fun isHealthConnectAvailable(): Boolean {
        return healthConnectClient != null
    }

    suspend fun hasPermissions(): Boolean {
        if (!isHealthConnectAvailable()) return false
        return try {
            healthConnectClient?.permissionController?.getGrantedPermissions()?.containsAll(permissions) == true
        } catch (e: Exception) {
            Log.e("HealthConnectIntegrator", "Error checking permissions: ${e.message}", e)
            false
        }
    }

    fun requestPermissions(requestPermissions: ActivityResultLauncher<Array<String>>) {
        requestPermissions.launch(permissions.toTypedArray())
    }

    fun openHealthConnectSettings() {
        val providerPackageName = "com.google.android.apps.healthdata"
        val settingsIntent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
        settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (settingsIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(settingsIntent)
        } else {
            Log.w("HealthConnectIntegrator", "Health Connect settings intent not resolvable. Attempting Play Store.")
            try {
                val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$providerPackageName"))
                playStoreIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(playStoreIntent)
            } catch (e: Exception) {
                Log.e("HealthConnectIntegrator", "Could not resolve Play Store intent.", e)
            }
        }
    }

    private fun createTimeRangeFilter(date: LocalDate, offsetHours: Long = 0L): TimeRangeFilter {
        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).minusHours(offsetHours)
        val endOfDay = startOfDay.plusDays(1).minusNanos(1).minusHours(offsetHours)
        return TimeRangeFilter.between(startOfDay.toInstant(), endOfDay.toInstant())
    }

    // --- User-Facing Data Fetching Functions ---

    suspend fun getSleepDurationForDay(openedDayString: String): Duration? {
        val date = runCatching { LocalDate.parse(openedDayString) }.getOrNull()
        if (date == null) {
            Log.e(
                "HealthConnectIntegrator",
                "[SLEEP DEBUG] Invalid date format for sleep: $openedDayString"
            )
            return null
        }
        val dateIso = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        Log.d(
            "HealthConnectIntegrator",
            "[SLEEP DEBUG] Attempting to get sleep for $dateIso, Role: ${AppGlobals.deviceRole}"
        )

        if (AppGlobals.deviceRole == DeviceRole.SECONDARY) {
            Log.d(
                "HealthConnectIntegrator",
                "[SLEEP DEBUG] SECONDARY: Reading sleep for $dateIso from DB."
            )
            return dbHelper.getDailyMetric(dateIso)?.sleepDurationMillis?.let { Duration.ofMillis(it) }
        }

        // PRIMARY Device Logic: Try HC, fallback to DB
        Log.d(
            "HealthConnectIntegrator",
            "[SLEEP DEBUG] PRIMARY: Attempting to read from Health Connect for $dateIso."
        )
        return try {
            val timeRangeFilter = createTimeRangeFilter(date, offsetHours = 6)
            val request = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = timeRangeFilter
            )
            val sleepSessionsResponse = healthConnectClient?.readRecords(request)

            if (sleepSessionsResponse == null || sleepSessionsResponse.records.isEmpty()) {
                Log.w(
                    "HealthConnectIntegrator",
                    "[SLEEP DEBUG] PRIMARY: No sleep records found in HC for $dateIso. Trying DB."
                )
                return dbHelper.getDailyMetric(dateIso)?.sleepDurationMillis?.let {
                    Duration.ofMillis(
                        it
                    )
                }
            }

            val totalSleepDuration =
                calculateNonOverlappingSleepDuration(sleepSessionsResponse.records)

            if (totalSleepDuration == null || totalSleepDuration.isZero || totalSleepDuration.isNegative) {
                Log.w(
                    "HealthConnectIntegrator",
                    "[SLEEP DEBUG] PRIMARY: No valid sleep duration calculated for $dateIso. Trying DB."
                )
                return dbHelper.getDailyMetric(dateIso)?.sleepDurationMillis?.let {
                    Duration.ofMillis(
                        it
                    )
                }
            }

            Log.d(
                "HealthConnectIntegrator",
                "[SLEEP DEBUG] PRIMARY: HC calculated TOTAL sleep for $dateIso: ${totalSleepDuration.toMinutes()} minutes."
            )

            // Save valid HC data to DB
            val existingMetric = dbHelper.getDailyMetric(dateIso)
            val metricToSave = existingMetric?.copy(
                sleepDurationMillis = totalSleepDuration.toMillis(),
                lastUpdatedTimestamp = System.currentTimeMillis(),
                sourceDeviceId = getMyDeviceId()
            ) ?: DailyHealthMetric(
                date = dateIso,
                steps = existingMetric?.steps,
                sleepDurationMillis = totalSleepDuration.toMillis(),
                activeCaloriesBurned = existingMetric?.activeCaloriesBurned,
                weightKg = existingMetric?.weightKg,
                exerciseMinutes = existingMetric?.exerciseMinutes,
                lastUpdatedTimestamp = System.currentTimeMillis(),
                sourceDeviceId = getMyDeviceId()
            )
            dbHelper.insertOrUpdateDailyMetric(metricToSave)
            totalSleepDuration
        } catch (e: Exception) {
            Log.e(
                "HealthConnectIntegrator",
                "[SLEEP DEBUG] PRIMARY: Error reading sleep data from HC for $dateIso: ${e.message}. Trying DB.",
                e
            )
            // Fallback to DB if HC read fails (e.g., permissions error)
            dbHelper.getDailyMetric(dateIso)?.sleepDurationMillis?.let { Duration.ofMillis(it) }
        }
    }

    // ... (SleepPeriod data class and helper functions remain the same) ...
    private data class SleepPeriod(
        val startTime: Instant,
        val endTime: Instant,
        val duration: Duration,
        val originalRecord: SleepSessionRecord
    )

    private fun calculateNonOverlappingSleepDuration(sleepRecords: List<SleepSessionRecord>): Duration? {
        if (sleepRecords.isEmpty()) return null
        val sleepPeriods = sleepRecords
            .map { record ->
                SleepPeriod(
                    startTime = record.startTime,
                    endTime = record.endTime,
                    duration = Duration.between(record.startTime, record.endTime),
                    originalRecord = record
                )
            }
            .filter { it.duration.toMillis() > 0 }
            .sortedBy { it.startTime }

        if (sleepPeriods.isEmpty()) return null
        val longestSleep = sleepPeriods.maxByOrNull { it.duration } ?: return null
        var totalSleepDuration = longestSleep.duration
        val usedPeriods = mutableSetOf(longestSleep)

        for (candidate in sleepPeriods) {
            if (candidate in usedPeriods) continue
            val hasOverlap = usedPeriods.any { used ->
                doPeriodsOverlap(candidate, used)
            }
            if (!hasOverlap) {
                totalSleepDuration = totalSleepDuration.plus(candidate.duration)
                usedPeriods.add(candidate)
            }
        }
        return totalSleepDuration
    }

    private fun doPeriodsOverlap(period1: SleepPeriod, period2: SleepPeriod): Boolean {
        return period1.startTime.isBefore(period2.endTime) && period2.startTime.isBefore(period1.endTime)
    }


    suspend fun getStepsForDay(openedDayString: String): Long? {
        val date = runCatching { LocalDate.parse(openedDayString) }.getOrNull()
        if (date == null) {
            Log.e("HealthConnectIntegrator", "Invalid date format for steps: $openedDayString")
            return null
        }
        val dateIso = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

        if (AppGlobals.deviceRole == DeviceRole.SECONDARY) {
            Log.d("HealthConnectIntegrator", "SECONDARY: Reading steps for $dateIso from DB.")
            return dbHelper.getDailyMetric(dateIso)?.steps
        }

        // PRIMARY Device Logic: Try HC, fallback to DB
        Log.d("HealthConnectIntegrator", "PRIMARY: Attempting to read steps from HC for $dateIso.")
        return try {
            val timeRangeFilter = createTimeRangeFilter(date)
            val aggregateRequest = AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = timeRangeFilter
            )
            val response = healthConnectClient?.aggregate(aggregateRequest)
            val totalSteps = response?.get(StepsRecord.COUNT_TOTAL)

            Log.d("HealthConnectIntegrator", "PRIMARY: HC steps for $date: $totalSteps")

            // Save to DB (even if null, to record the "checked" state)
            val existingMetric = dbHelper.getDailyMetric(dateIso)
            val metricToSave = existingMetric?.copy(
                steps = totalSteps,
                lastUpdatedTimestamp = System.currentTimeMillis(),
                sourceDeviceId = getMyDeviceId()
            ) ?: DailyHealthMetric(
                date = dateIso,
                steps = totalSteps,
                sleepDurationMillis = null,
                activeCaloriesBurned = null,
                weightKg = null,
                exerciseMinutes = null,
                lastUpdatedTimestamp = System.currentTimeMillis(),
                sourceDeviceId = getMyDeviceId()
            )
            dbHelper.insertOrUpdateDailyMetric(metricToSave)

            totalSteps
        } catch (e: Exception) {
            Log.e(
                "HealthConnectIntegrator",
                "Error reading steps data from HC: ${e.message}. Trying DB.",
                e
            )
            dbHelper.getDailyMetric(dateIso)?.steps
        }
    }


    suspend fun getActiveCaloriesBurnedForDay(openedDayString: String): Double? {
        val date = runCatching { LocalDate.parse(openedDayString) }.getOrNull()
        if (date == null) {
            Log.e("HealthConnectIntegrator", "Invalid date format for calories: $openedDayString")
            return null
        }
        val dateIso = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

        if (AppGlobals.deviceRole == DeviceRole.SECONDARY) {
            Log.d("HealthConnectIntegrator", "SECONDARY: Reading calories for $dateIso from DB.")
            return dbHelper.getDailyMetric(dateIso)?.activeCaloriesBurned
        }

        // PRIMARY Device Logic: Try HC, fallback to DB
        Log.d(
            "HealthConnectIntegrator",
            "PRIMARY: Attempting to read calories from HC for $dateIso."
        )
        return try {
            val timeRangeFilter = createTimeRangeFilter(date)
            val request = ReadRecordsRequest(
                ActiveCaloriesBurnedRecord::class,
                timeRangeFilter = timeRangeFilter
            )
            val response = healthConnectClient?.readRecords(request)
            // Use sumOf over non-empty list, or default to 0.0, then check if null
            val totalCalories = response?.records?.sumOf { it.energy.inKilocalories }

            Log.d(
                "HealthConnectIntegrator",
                "PRIMARY: HC active calories for $date: $totalCalories kcal"
            )

            val existingMetric = dbHelper.getDailyMetric(dateIso)
            val metricToSave = existingMetric?.copy(
                activeCaloriesBurned = totalCalories,
                lastUpdatedTimestamp = System.currentTimeMillis(),
                sourceDeviceId = getMyDeviceId()
            ) ?: DailyHealthMetric(
                date = dateIso,
                steps = null,
                sleepDurationMillis = null,
                activeCaloriesBurned = totalCalories,
                weightKg = null,
                exerciseMinutes = null,
                lastUpdatedTimestamp = System.currentTimeMillis(),
                sourceDeviceId = getMyDeviceId()
            )
            dbHelper.insertOrUpdateDailyMetric(metricToSave)

            totalCalories
        } catch (e: Exception) {
            Log.e(
                "HealthConnectIntegrator",
                "Error reading active calories from HC: ${e.message}. Trying DB.",
                e
            )
            dbHelper.getDailyMetric(dateIso)?.activeCaloriesBurned
        }
    }

    suspend fun getWeightForDay(openedDayString: String): Double? {
        val date = runCatching { LocalDate.parse(openedDayString) }.getOrNull()
        if (date == null) {
            Log.e("HealthConnectIntegrator", "Invalid date format for weight: $openedDayString")
            return null
        }
        val dateIso = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

        if (AppGlobals.deviceRole == DeviceRole.SECONDARY) {
            Log.d("HealthConnectIntegrator", "SECONDARY: Reading weight for $dateIso from DB.")
            return dbHelper.getDailyMetric(dateIso)?.weightKg
        }

        // PRIMARY Device Logic: Try HC, fallback to DB
        Log.d("HealthConnectIntegrator", "PRIMARY: Attempting to read weight from HC for $dateIso.")
        return try {
            val timeRangeFilter = createTimeRangeFilter(date)
            val request = ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = timeRangeFilter,
                ascendingOrder = false
            )
            val response = healthConnectClient?.readRecords(request)
            val latestWeight = response?.records?.firstOrNull()?.weight?.inKilograms
            Log.d("HealthConnectIntegrator", "PRIMARY: HC Weight for $date: $latestWeight kg")

            if (latestWeight != null) {
                // Only save to DB if we found a new value from HC
                val existingMetric = dbHelper.getDailyMetric(dateIso)
                val metricToSave = existingMetric?.copy(
                    weightKg = latestWeight,
                    lastUpdatedTimestamp = System.currentTimeMillis(),
                    sourceDeviceId = getMyDeviceId()
                ) ?: DailyHealthMetric(
                    date = dateIso,
                    steps = null,
                    sleepDurationMillis = null,
                    activeCaloriesBurned = null,
                    weightKg = latestWeight,
                    exerciseMinutes = null,
                    lastUpdatedTimestamp = System.currentTimeMillis(),
                    sourceDeviceId = getMyDeviceId()
                )
                dbHelper.insertOrUpdateDailyMetric(metricToSave)
                latestWeight
            } else {
                // If no weight from HC, just return whatever is in the DB (which might be null)
                Log.d(
                    "HealthConnectIntegrator",
                    "PRIMARY: No weight found in HC for $date. Returning DB value."
                )
                dbHelper.getDailyMetric(dateIso)?.weightKg
            }
        } catch (e: Exception) {
            Log.e(
                "HealthConnectIntegrator",
                "Error reading weight from HC for $date: ${e.message}. Trying DB.",
                e
            )
            dbHelper.getDailyMetric(dateIso)?.weightKg
        }
    }


    suspend fun getTotalExerciseMinutesForDay(openedDayString: String): Long? {
        val date = runCatching { LocalDate.parse(openedDayString) }.getOrNull()
        if (date == null) {
            Log.e("HealthConnectIntegrator", "Invalid date format for exercise: $openedDayString")
            return null
        }
        val dateIso = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        Log.d(
            "HealthConnectIntegrator",
            "[EXERCISE DEBUG] Attempting to get exercise for $dateIso, Role: ${AppGlobals.deviceRole}"
        )

        if (AppGlobals.deviceRole == DeviceRole.SECONDARY) {
            Log.d(
                "HealthConnectIntegrator",
                "SECONDARY: Reading exercise mins for $dateIso from DB."
            )
            return dbHelper.getDailyMetric(dateIso)?.exerciseMinutes
        }

        // PRIMARY Device Logic: Try HC, fallback to DB
        Log.d(
            "HealthConnectIntegrator",
            "[EXERCISE DEBUG] PRIMARY: Attempting to read from Health Connect for $dateIso."
        )
        return try {
            val timeRangeFilter = createTimeRangeFilter(date)
            val request = ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = timeRangeFilter
            )
            val response = healthConnectClient?.readRecords(request)

            if (response == null || response.records.isEmpty()) {
                Log.w(
                    "HealthConnectIntegrator",
                    "[EXERCISE DEBUG] PRIMARY: No exercise records found in HC for $dateIso. Trying DB."
                )
                return dbHelper.getDailyMetric(dateIso)?.exerciseMinutes
            }

            val totalDuration = response.records.fold(Duration.ZERO) { acc, record ->
                acc.plus(Duration.between(record.startTime, record.endTime))
            }
            val totalMinutes = totalDuration.toMinutes()

            Log.d(
                "HealthConnectIntegrator",
                "PRIMARY: HC total exercise for $date: $totalMinutes minutes from ${response.records.size} sessions"
            )

            val existingMetric = dbHelper.getDailyMetric(dateIso)
            val metricToSave = existingMetric?.copy(
                exerciseMinutes = totalMinutes,
                lastUpdatedTimestamp = System.currentTimeMillis(),
                sourceDeviceId = getMyDeviceId()
            ) ?: DailyHealthMetric(
                date = dateIso,
                steps = existingMetric?.steps,
                sleepDurationMillis = existingMetric?.sleepDurationMillis,
                activeCaloriesBurned = existingMetric?.activeCaloriesBurned,
                weightKg = existingMetric?.weightKg,
                exerciseMinutes = totalMinutes,
                lastUpdatedTimestamp = System.currentTimeMillis(),
                sourceDeviceId = getMyDeviceId()
            )
            dbHelper.insertOrUpdateDailyMetric(metricToSave)

            totalMinutes
        } catch (e: Exception) {
            Log.e(
                "HealthConnectIntegrator",
                "Error reading exercise data from HC: ${e.message}. Trying DB.",
                e
            )
            dbHelper.getDailyMetric(dateIso)?.exerciseMinutes
        }
    }


    suspend fun getLatestOverallWeight(): Double? {
        if (AppGlobals.deviceRole == DeviceRole.SECONDARY) {
            val todayIso = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val weightFromDb = dbHelper.getDailyMetric(todayIso)?.weightKg
            if (weightFromDb != null) return weightFromDb
        }

        // PRIMARY Device Logic: Try HC, fallback to DB
        Log.d(
            "HealthConnectIntegrator",
            "PRIMARY: Attempting to read latest *overall* weight from HC."
        )
        return try {
            val request = ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.between(Instant.EPOCH, Instant.now()),
                ascendingOrder = false,
                pageSize = 1
            )
            val response = healthConnectClient?.readRecords(request)
            val hcWeight = response?.records?.firstOrNull()?.weight?.inKilograms

            if (AppGlobals.deviceRole == DeviceRole.PRIMARY && hcWeight != null) {
                val todayIso = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val existingMetric = dbHelper.getDailyMetric(todayIso)
                val metricToSave = existingMetric?.copy(
                    weightKg = hcWeight,
                    lastUpdatedTimestamp = System.currentTimeMillis(),
                    sourceDeviceId = getMyDeviceId()
                ) ?: DailyHealthMetric(
                    date = todayIso,
                    steps = existingMetric?.steps,
                    sleepDurationMillis = existingMetric?.sleepDurationMillis,
                    activeCaloriesBurned = existingMetric?.activeCaloriesBurned,
                    weightKg = hcWeight,
                    exerciseMinutes = existingMetric?.exerciseMinutes,
                    lastUpdatedTimestamp = System.currentTimeMillis(),
                    sourceDeviceId = getMyDeviceId()
                )
                dbHelper.insertOrUpdateDailyMetric(metricToSave)
            }
            hcWeight
        } catch (e: Exception) {
            Log.e(
                "HealthConnectIntegrator",
                "Error reading latest overall weight from HC: ${e.message}. Trying DB.",
                e
            )
            val todayIso = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            dbHelper.getDailyMetric(todayIso)?.weightKg // Fallback to today's DB record
        }
    }

    // --- Helper Functions (BMR, Age) - These remain the same ---
    fun calculateBMR(weightKg: Double, heightCm: Double, ageYears: Int, gender: Gender): Double {
        return when (gender) {
            Gender.MALE -> (10 * weightKg) + (6.25 * heightCm) - (5 * ageYears) + 5
            Gender.FEMALE -> (10 * weightKg) + (6.25 * heightCm) - (5 * ageYears) - 161
            Gender.PREFER_NOT_TO_SAY -> {
                val maleBMR = (10 * weightKg) + (6.25 * heightCm) - (5 * ageYears) + 5
                val femaleBMR = (10 * weightKg) + (6.25 * heightCm) - (5 * ageYears) - 161
                (maleBMR + femaleBMR) / 2
            }
            Gender.OTHER -> (10 * weightKg) + (6.25 * heightCm) - (5 * ageYears) + 5 // Defaulting to MALE BMR as a placeholder
        }
    }

    fun calculateAge(birthDate: ZonedDateTime): Int {
        val today = ZonedDateTime.now(birthDate.zone)
        return ChronoUnit.YEARS.between(birthDate.toLocalDate(), today.toLocalDate()).toInt()
    }
}


// --- HealthConnectViewModel ---
class HealthConnectViewModel(private val applicationContext: Context) :
    ViewModel() {
    val healthConnectIntegrator = HealthConnectIntegrator(applicationContext)

    // Data states for the UI
    var latestWeight by mutableStateOf<Double?>(null)
    var bmr by mutableStateOf<Double?>(null)
    var totalSteps by mutableStateOf<Long?>(null)
    var totalSleepDuration by mutableStateOf<Duration?>(null)
    var activeCaloriesBurned by mutableStateOf<Double?>(null)

    // Status states for the UI
    var healthConnectAvailable by mutableStateOf(false)
    var permissionsGranted by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    val permissions = healthConnectIntegrator.permissions.toTypedArray()

    init {
        checkHealthConnectStatus()
    }

    private fun checkHealthConnectStatus() {
        healthConnectAvailable = healthConnectIntegrator.isHealthConnectAvailable()
    }

    suspend fun fetchDataForDay(dayString: String) {
        isLoading = true
        errorMessage = null
        Log.d(
            "HealthConnectViewModel",
            "Fetching data for day: $dayString, Role: ${AppGlobals.deviceRole}"
        )

        try {
            // Check permissions *once* for the UI
            permissionsGranted = healthConnectIntegrator.hasPermissions()
            Log.d("HealthConnectViewModel", "Permissions granted: $permissionsGranted")

            // Fetch data using the integrator
            // The integrator functions will now handle their own HC/DB fallback logic
            totalSteps = healthConnectIntegrator.getStepsForDay(dayString)
            totalSleepDuration = healthConnectIntegrator.getSleepDurationForDay(dayString)
            activeCaloriesBurned = healthConnectIntegrator.getActiveCaloriesBurnedForDay(dayString)

            latestWeight = healthConnectIntegrator.getWeightForDay(dayString)
                ?: healthConnectIntegrator.getLatestOverallWeight() // Fallback to overall latest

            Log.d(
                "HealthConnectViewModel",
                "Fetched Steps: $totalSteps, Sleep: ${totalSleepDuration?.toMinutes()}, Cals: $activeCaloriesBurned, Weight: $latestWeight"
            )

            // BMR Calculation
            val weight = latestWeight
            val currentUserProfile = AppGlobals.userProfile

            if (weight != null && currentUserProfile.dateOfBirth != null && currentUserProfile.heightCm != null) {
                val ageYears = healthConnectIntegrator.calculateAge(currentUserProfile.dateOfBirth)
                bmr = healthConnectIntegrator.calculateBMR(
                    weight,
                    currentUserProfile.heightCm,
                    ageYears,
                    currentUserProfile.gender
                )
            } else {
                bmr = null
                Log.d(
                    "HealthConnectViewModel",
                    "BMR calculation skipped: Missing weight, DOB, or height."
                )
            }

            if (!permissionsGranted && AppGlobals.deviceRole == DeviceRole.PRIMARY) {
                if (totalSteps == null && totalSleepDuration == null && activeCaloriesBurned == null && latestWeight == null) {
                    errorMessage =
                        "Health Connect permissions are needed for the primary device to fetch and sync data."
                }
            }
        } catch (e: Exception) {
            Log.e("HealthConnectViewModel", "Error in fetchDataForDay($dayString): ${e.message}", e)
            errorMessage = "Error fetching data: ${e.localizedMessage ?: "Unknown error"}"
            latestWeight = null; bmr = null; totalSteps = null; totalSleepDuration =
                null; activeCaloriesBurned = null
        } finally {
            isLoading = false
        }
    }

    fun requestPermissions(launcher: ActivityResultLauncher<Array<String>>) {
        healthConnectIntegrator.requestPermissions(launcher)
    }

    fun openHealthConnectSettings() {
        healthConnectIntegrator.openHealthConnectSettings()
    }
}
// HealthConnectIntegrator.kt
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
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
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

// --- Database Contract and Helper ---

object HealthMetricsContract {
    object DailyMetricEntry : BaseColumns {
        const val TABLE_NAME = "daily_health_metrics"
        const val COLUMN_NAME_DATE = "date_iso" // Storing date as "YYYY-MM-DD"
        const val COLUMN_NAME_STEPS = "steps"
        const val COLUMN_NAME_SLEEP_DURATION_MILLIS = "sleep_duration_millis"
        const val COLUMN_NAME_ACTIVE_CALORIES = "active_calories"
        const val COLUMN_NAME_WEIGHT_KG = "weight_kg"
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
    val lastUpdatedTimestamp: Long,
    val sourceDeviceId: String
)

class HealthMetricsDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_ENTRIES)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // This database is only a cache, so its upgrade policy is
        // to simply to discard the data and start over
        db.execSQL(SQL_DELETE_ENTRIES)
        onCreate(db)
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
            put(
                HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_LAST_UPDATED_TIMESTAMP,
                metric.lastUpdatedTimestamp
            )
            put(
                HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_SOURCE_DEVICE_ID,
                metric.sourceDeviceId
            )
        }

        // Try to update first, if not found, then insert
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
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "HealthMetrics.db"

        private const val SQL_CREATE_ENTRIES =
            "CREATE TABLE ${HealthMetricsContract.DailyMetricEntry.TABLE_NAME} (" +
                    "${BaseColumns._ID} INTEGER PRIMARY KEY," +
                    "${HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_DATE} TEXT UNIQUE," +
                    "${HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_STEPS} INTEGER," +
                    "${HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_SLEEP_DURATION_MILLIS} INTEGER," +
                    "${HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_ACTIVE_CALORIES} REAL," +
                    "${HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_WEIGHT_KG} REAL," +
                    "${HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_LAST_UPDATED_TIMESTAMP} INTEGER," +
                    "${HealthMetricsContract.DailyMetricEntry.COLUMN_NAME_SOURCE_DEVICE_ID} TEXT)"

        private const val SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS ${HealthMetricsContract.DailyMetricEntry.TABLE_NAME}"
    }
}


class HealthConnectIntegrator(private val context: Context) {

    private val dbHelper by lazy { HealthMetricsDbHelper(context) }
    private val coroutineScope = CoroutineScope(Dispatchers.IO) // For DB operations off main thread

    private val healthConnectClient: HealthConnectClient? by lazy {
        // Ensure AppGlobals is initialized before accessing deviceRole
        // This should happen in Application.onCreate or MainActivity.onCreate
        // AppGlobals.initialize(context) // Redundant if already done elsewhere

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
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
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
        // If device is secondary and we don't have permissions, it's fine, we'll read from DB.
        // However, the primary device NEEDS permissions to write to the DB.
        // For simplicity here, we check permissions always. UI can decide how to react.
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
            val metricFromDb = dbHelper.getDailyMetric(dateIso)
            val sleepMillis = metricFromDb?.sleepDurationMillis
            Log.d(
                "HealthConnectIntegrator",
                "[SLEEP DEBUG] SECONDARY: DB sleepMillis: $sleepMillis for $dateIso"
            )
            return sleepMillis?.let { Duration.ofMillis(it) }
        }

        // PRIMARY Device Logic
        if (!hasPermissions()) { // This hasPermissions() checks ALL permissions in your set
            Log.w(
                "HealthConnectIntegrator",
                "[SLEEP DEBUG] PRIMARY: Attempted to read sleep without HC permissions. Trying DB for $dateIso."
            )
            val metricFromDb = dbHelper.getDailyMetric(dateIso)
            val sleepMillis = metricFromDb?.sleepDurationMillis
            Log.d(
                "HealthConnectIntegrator",
                "[SLEEP DEBUG] PRIMARY (no HC perm): DB sleepMillis: $sleepMillis for $dateIso"
            )
            return sleepMillis?.let { Duration.ofMillis(it) }
        }
        Log.d(
            "HealthConnectIntegrator",
            "[SLEEP DEBUG] PRIMARY: Has permissions. Attempting to read from Health Connect for $dateIso."
        )

        return try {
            val timeRangeFilter = createTimeRangeFilter(date, offsetHours = 6)
            Log.d(
                "HealthConnectIntegrator",
                "[SLEEP DEBUG] PRIMARY: TimeRangeFilter for $dateIso: ${timeRangeFilter.startTime} to ${timeRangeFilter.endTime}"
            )
            val request =
                ReadRecordsRequest(SleepSessionRecord::class, timeRangeFilter = timeRangeFilter)
            val sleepSessionsResponse = healthConnectClient?.readRecords(request)

            if (sleepSessionsResponse == null) {
                Log.w(
                    "HealthConnectIntegrator",
                    "[SLEEP DEBUG] PRIMARY: healthConnectClient.readRecords(request) returned null for $dateIso."
                )
                // Fallback to DB
                return dbHelper.getDailyMetric(dateIso)?.sleepDurationMillis?.let {
                    Duration.ofMillis(
                        it
                    )
                }
            }

            Log.d(
                "HealthConnectIntegrator",
                "[SLEEP DEBUG] PRIMARY: HC response for $dateIso - Record count: ${sleepSessionsResponse.records.size}"
            )
            sleepSessionsResponse.records.forEachIndexed { index, record ->
                Log.d(
                    "HealthConnectIntegrator",
                    "[SLEEP DEBUG] PRIMARY: Record $index for $dateIso - Start: ${record.startTime}, End: ${record.endTime}, Title: ${record.title}, Notes: ${record.notes}"
                )
                record.stages.forEach { stage ->
                    Log.d(
                        "HealthConnectIntegrator",
                        "[SLEEP DEBUG] PRIMARY: Record $index Stage: ${stage.stage} (${stage.startTime} - ${stage.endTime})"
                    )
                }
            }

            val totalDuration = sleepSessionsResponse.records
                .map { session -> Duration.between(session.startTime, session.endTime) }
                .fold(Duration.ZERO, Duration::plus)

            Log.d(
                "HealthConnectIntegrator",
                "[SLEEP DEBUG] PRIMARY: HC calculated total sleep for $dateIso: ${totalDuration?.toMinutes()} minutes. Record count: ${sleepSessionsResponse.records.size}"
            )

            if (AppGlobals.deviceRole == DeviceRole.PRIMARY) {
                val existingMetric = dbHelper.getDailyMetric(dateIso)
                Log.d(
                    "HealthConnectIntegrator",
                    "[SLEEP DEBUG] PRIMARY: Existing DB metric for $dateIso before update: $existingMetric"
                )
                val metricToSave = existingMetric?.copy(
                    sleepDurationMillis = totalDuration?.toMillis(),
                    lastUpdatedTimestamp = System.currentTimeMillis(),
                    sourceDeviceId = getMyDeviceId()
                ) ?: DailyHealthMetric(
                    date = dateIso,
                    steps = null, // Or existingMetric?.steps
                    sleepDurationMillis = totalDuration?.toMillis(),
                    activeCaloriesBurned = null, // Or existingMetric?.activeCaloriesBurned
                    weightKg = null, // Or existingMetric?.weightKg
                    lastUpdatedTimestamp = System.currentTimeMillis(),
                    sourceDeviceId = getMyDeviceId()
                )
                Log.d(
                    "HealthConnectIntegrator",
                    "[SLEEP DEBUG] PRIMARY: Metric to save to DB for $dateIso: $metricToSave"
                )
                dbHelper.insertOrUpdateDailyMetric(metricToSave)
            }
            totalDuration
        } catch (e: Exception) {
            Log.e(
                "HealthConnectIntegrator",
                "[SLEEP DEBUG] PRIMARY: Error reading sleep data from HC for $dateIso: ${e.message}",
                e
            )
            // Fallback to DB if HC read fails for primary
            dbHelper.getDailyMetric(dateIso)?.sleepDurationMillis?.let { Duration.ofMillis(it) }
        }
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

        if (!hasPermissions()) {
            Log.w(
                "HealthConnectIntegrator",
                "Attempted to read steps without HC permissions (PRIMARY)."
            )
            return dbHelper.getDailyMetric(dateIso)?.steps
        }

        return try {
            val timeRangeFilter = createTimeRangeFilter(date)
            val request = ReadRecordsRequest(StepsRecord::class, timeRangeFilter = timeRangeFilter)
            val response = healthConnectClient?.readRecords(request)
            val totalSteps = response?.records?.sumOf { it.count }
            Log.d("HealthConnectIntegrator", "PRIMARY: HC steps for $date: $totalSteps")

            if (AppGlobals.deviceRole == DeviceRole.PRIMARY) {
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
                    lastUpdatedTimestamp = System.currentTimeMillis(),
                    sourceDeviceId = getMyDeviceId()
                )
                dbHelper.insertOrUpdateDailyMetric(metricToSave)
            }
            totalSteps
        } catch (e: Exception) {
            Log.e("HealthConnectIntegrator", "Error reading steps data from HC: ${e.message}", e)
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

        if (!hasPermissions()) {
            Log.w(
                "HealthConnectIntegrator",
                "Attempted to read calories without HC permissions (PRIMARY)."
            )
            return dbHelper.getDailyMetric(dateIso)?.activeCaloriesBurned
        }

        return try {
            val timeRangeFilter = createTimeRangeFilter(date)
            val request = ReadRecordsRequest(
                ActiveCaloriesBurnedRecord::class,
                timeRangeFilter = timeRangeFilter
            )
            val response = healthConnectClient?.readRecords(request)
            val totalCalories = response?.records?.sumOf { it.energy.inKilocalories }
            Log.d(
                "HealthConnectIntegrator",
                "PRIMARY: HC active calories for $date: $totalCalories kcal"
            )

            if (AppGlobals.deviceRole == DeviceRole.PRIMARY) {
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
                    lastUpdatedTimestamp = System.currentTimeMillis(),
                    sourceDeviceId = getMyDeviceId()
                )
                dbHelper.insertOrUpdateDailyMetric(metricToSave)
            }
            totalCalories
        } catch (e: Exception) {
            Log.e(
                "HealthConnectIntegrator",
                "Error reading active calories from HC: ${e.message}",
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

        if (!hasPermissions()) {
            Log.w(
                "HealthConnectIntegrator",
                "Attempted to read weight without HC permissions (PRIMARY)."
            )
            return dbHelper.getDailyMetric(dateIso)?.weightKg
        }

        return try {
            val timeRangeFilter = createTimeRangeFilter(date) // For a specific day's latest
            val request = ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = timeRangeFilter,
                ascendingOrder = false
            )
            val response = healthConnectClient?.readRecords(request)
            val latestWeight = response?.records?.firstOrNull()?.weight?.inKilograms
            Log.d("HealthConnectIntegrator", "PRIMARY: HC Weight for $date: $latestWeight kg")

            if (AppGlobals.deviceRole == DeviceRole.PRIMARY) {
                // Only update if a weight was actually found for that day from HC
                if (latestWeight != null) {
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
                        lastUpdatedTimestamp = System.currentTimeMillis(),
                        sourceDeviceId = getMyDeviceId()
                    )
                    dbHelper.insertOrUpdateDailyMetric(metricToSave)
                } else if (dbHelper.getDailyMetric(dateIso)?.weightKg == null) {
                    // If HC has no weight for the day, and DB also has no weight,
                    // we might want to fetch the overall latest weight from HC
                    // and store it in the DB for *today* if openedDayString is today.
                    // This part is a bit more complex for "latest weight" vs "weight on a specific day".
                    // For now, if HC has no weight for the day, we don't update the DB's weight field
                    // unless we explicitly want to clear it.
                    // Let's stick to updating DB only if HC provides a value for the specific day.
                }
            }
            latestWeight
        } catch (e: Exception) {
            Log.e(
                "HealthConnectIntegrator",
                "Error reading weight from HC for $date: ${e.message}",
                e
            )
            dbHelper.getDailyMetric(dateIso)?.weightKg
        }
    }


    /**
     * Reads the overall latest weight record from Health Connect.
     * This function might be less used if getWeightForDay becomes the primary way,
     * but can be a fallback.
     */
    suspend fun getLatestOverallWeight(): Double? {
        // This function doesn't directly align with the daily metric storage well
        // unless we decide to store "latest known weight" in a separate preference or
        // always associate it with today's DailyHealthMetric if fetched.
        // For now, let's assume it primarily consults HC, and primary might update today's DB record.

        if (AppGlobals.deviceRole == DeviceRole.SECONDARY) {
            // Secondary devices could get the latest weight from today's DB record,
            // or we could query the DB for the most recent non-null weight entry.
            // For simplicity, let's use today's DB entry if available.
            val todayIso = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val weightFromDb = dbHelper.getDailyMetric(todayIso)?.weightKg
            if (weightFromDb != null) return weightFromDb
            // Could add more complex logic to find most recent DB weight entry here.
        }

        if (!hasPermissions()) {
            Log.w(
                "HealthConnectIntegrator",
                "Attempted to read latest overall weight without HC permissions."
            )
            return null // Or try DB again for primary
        }
        return try {
            val request = ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.between(Instant.EPOCH, Instant.now()),
                ascendingOrder = false,
                pageSize = 1
            )
            val response = healthConnectClient?.readRecords(request)
            val hcWeight = response?.records?.firstOrNull()?.weight?.inKilograms

            // If primary, update today's DB record with this latest weight
            if (AppGlobals.deviceRole == DeviceRole.PRIMARY && hcWeight != null) {
                val todayIso = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val existingMetric = dbHelper.getDailyMetric(todayIso)
                val metricToSave = existingMetric?.copy(
                    weightKg = hcWeight, // Update weight
                    lastUpdatedTimestamp = System.currentTimeMillis(),
                    sourceDeviceId = getMyDeviceId()
                ) ?: DailyHealthMetric(
                    date = todayIso,
                    steps = existingMetric?.steps, // Preserve other data if any
                    sleepDurationMillis = existingMetric?.sleepDurationMillis,
                    activeCaloriesBurned = existingMetric?.activeCaloriesBurned,
                    weightKg = hcWeight,
                    lastUpdatedTimestamp = System.currentTimeMillis(),
                    sourceDeviceId = getMyDeviceId()
                )
                dbHelper.insertOrUpdateDailyMetric(metricToSave)
            }
            hcWeight
        } catch (e: Exception) {
            Log.e(
                "HealthConnectIntegrator",
                "Error reading latest overall weight from HC: ${e.message}",
                e
            )
            null
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

            Gender.OTHER -> TODO()
        }
    }

    fun calculateAge(birthDate: ZonedDateTime): Int {
        val today = ZonedDateTime.now(birthDate.zone)
        return ChronoUnit.YEARS.between(birthDate.toLocalDate(), today.toLocalDate()).toInt()
    }
}


// --- HealthConnectViewModel ---
// ViewModel should now use the new HealthConnectIntegrator methods.
// The core logic of deciding where to get data (HC or DB) is now within HealthConnectIntegrator.
class HealthConnectViewModel(private val applicationContext: Context) :
    ViewModel() { // Pass application context
    // Ensure AppGlobals is initialized
    // This typically happens in Application.onCreate() or MainActivity.onCreate()
    // For safety, you could add AppGlobals.initialize(applicationContext) here if you suspect it might not be.
    // However, it's better practice to do it once at app startup.

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
        // ViewModel is a good place to initialize AppGlobals if not done earlier,
        // but Application class is generally preferred for globals.
        // AppGlobals.initialize(applicationContext)

        checkHealthConnectStatus()
        // Optionally, load data for the current 'openedDay' on init
        // viewModelScope.launch { fetchDataForDay(AppGlobals.openedDay) }
    }

    private fun checkHealthConnectStatus() {
        healthConnectAvailable = healthConnectIntegrator.isHealthConnectAvailable()
    }

    // Renamed from checkPermissionsAndFetchData for clarity, as it fetches for a specific day
    suspend fun fetchDataForDay(dayString: String) {
        isLoading = true
        errorMessage = null
        Log.d(
            "HealthConnectViewModel",
            "Fetching data for day: $dayString, Role: ${AppGlobals.deviceRole}"
        )
        try {
            // For secondary devices, hasPermissions might be false, but data can still come from DB.
            // The integrator handles this. The UI might want to reflect if HC permissions are missing
            // even if data is shown from DB.
            permissionsGranted = healthConnectIntegrator.hasPermissions()

            // Fetch data using the integrator, which now handles DB/HC logic
            totalSteps = healthConnectIntegrator.getStepsForDay(dayString)
            totalSleepDuration = healthConnectIntegrator.getSleepDurationForDay(dayString)
            activeCaloriesBurned = healthConnectIntegrator.getActiveCaloriesBurnedForDay(dayString)

            // For weight, decide if you want 'weight on specific day' or 'latest overall weight'
            // Using getWeightForDay aligns with other daily metrics.
            latestWeight = healthConnectIntegrator.getWeightForDay(dayString)
                ?: healthConnectIntegrator.getLatestOverallWeight() // Fallback to overall latest if no weight for specific day

            Log.d(
                "HealthConnectViewModel",
                "Fetched Steps: $totalSteps, Sleep: ${totalSleepDuration?.toMinutes()}, Cals: $activeCaloriesBurned, Weight: $latestWeight"
            )


            // BMR Calculation (remains similar)
            val weight = latestWeight
            if (weight != null) {
                // TODO: Replace dummy data with actual user profile data
                val userProfile =
                    UserProfile(Gender.MALE, ZonedDateTime.now().minusYears(30)) // Example
                val heightCm = 175.0 // Example

                userProfile.dateOfBirth?.let { dob ->
                    val ageYears = healthConnectIntegrator.calculateAge(dob)
                    bmr = healthConnectIntegrator.calculateBMR(
                        weight,
                        heightCm,
                        ageYears,
                        userProfile.gender
                    )
                } ?: run { bmr = null }
            } else {
                bmr = null
            }

            if (!permissionsGranted && AppGlobals.deviceRole == DeviceRole.PRIMARY) {
                if (totalSteps == null && totalSleepDuration == null && activeCaloriesBurned == null && latestWeight == null) {
                    // Only show permission error if primary and no data could be fetched (neither HC nor DB)
                    // and permissions are actually missing.
                    errorMessage =
                        "Health Connect permissions are needed for the primary device to fetch and sync data."
                }
            }


        } catch (e: Exception) {
            Log.e("HealthConnectViewModel", "Error in fetchDataForDay($dayString): ${e.message}", e)
            errorMessage = "Error fetching data: ${e.localizedMessage ?: "Unknown error"}"
            // Clear data on error
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

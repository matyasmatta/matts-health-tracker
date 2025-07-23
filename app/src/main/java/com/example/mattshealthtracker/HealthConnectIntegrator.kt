// HealthConnectIntegrator.kt
package com.example.mattshealthtracker

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.example.mattshealthtracker.AppGlobals.openedDay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

// Consider creating a data class for user profile if you don't have one
data class UserProfile(
    val gender: Gender,
    val dateOfBirth: ZonedDateTime? // For age calculation
)

enum class Gender { MALE, FEMALE, OTHER } // Define Gender enum

class HealthConnectIntegrator(private val context: Context) {

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

    // Permissions you need from Health Connect
    // Added permissions for Sleep, Steps, and Active Calories.
    val permissions = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
    )

    /**
     * Checks if the Health Connect provider is installed and available.
     * @return true if the provider is available, false otherwise.
     */
    fun isHealthConnectAvailable(): Boolean {
        return healthConnectClient != null
    }

    /**
     * Checks if all necessary Health Connect permissions have been granted.
     * @return true if all permissions are granted, false otherwise.
     */
    suspend fun hasPermissions(): Boolean {
        if (!isHealthConnectAvailable()) return false
        return try {
            healthConnectClient?.permissionController?.getGrantedPermissions()?.containsAll(permissions) == true
        } catch (e: Exception) {
            Log.e("HealthConnectIntegrator", "Error checking permissions: ${e.message}", e)
            false
        }
    }

    /**
     * Launches the Health Connect permission request flow.
     * This should be called from your Activity or Fragment.
     *
     * @param requestPermissions The ActivityResultLauncher from your UI component.
     */
    fun requestPermissions(requestPermissions: ActivityResultLauncher<Array<String>>) {
        requestPermissions.launch(permissions.toTypedArray())
    }

    /**
     * Opens the Health Connect app settings or directs the user to the Play Store to install it.
     */
    fun openHealthConnectSettings() {
        val providerPackageName = "com.google.android.apps.healthdata"
        val settingsIntent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)

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

    /**
     * Helper function to create a TimeRangeFilter for a specific day.
     */
    private fun createTimeRangeFilter(date: LocalDate): TimeRangeFilter {
        val startOfDay = date.atStartOfDay(ZoneId.systemDefault())
        val endOfDay = startOfDay.plusDays(1).minusNanos(1)
        return TimeRangeFilter.between(startOfDay.toInstant(), endOfDay.toInstant())
    }
    suspend fun getSleepDurationForDay(openedDay: String): Duration? {
        val date = runCatching { LocalDate.parse(openedDay) }.getOrNull()
        if (date == null) {
            Log.e("HealthConnectIntegrator", "Invalid date format: $openedDay")
            return null
        }
        return getSleepDuration(date)
    }


    suspend fun getStepsForDay(openedDay: String): Long? {
        val date = runCatching { LocalDate.parse(openedDay) }.getOrNull()
        if (date == null) {
            Log.e("HealthConnectIntegrator", "Invalid date format: $openedDay")
            return null
        }
        return getSteps(date)
    }


    suspend fun getActiveCaloriesBurnedForDay(openedDay: String): Double? {
        val date = runCatching { LocalDate.parse(openedDay) }.getOrNull()
        if (date == null) {
            Log.e("HealthConnectIntegrator", "Invalid date format: $openedDay")
            return null
        }
        return getActiveCaloriesBurned(date)
    }

    suspend fun getWeightForDay(openedDay: String): Double? {
        val date = runCatching { LocalDate.parse(openedDay) }.getOrNull()
        if (date == null) {
            Log.e("HealthConnectIntegrator", "Invalid date format: $openedDay")
            return null
        }

        if (!hasPermissions()) {
            Log.w("HealthConnectIntegrator", "Attempted to read weight without necessary permissions.")
            return null
        }

        return try {
            val timeRangeFilter = createTimeRangeFilter(date)
            val request = ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = timeRangeFilter,
                ascendingOrder = false // most recent first
            )
            val response = healthConnectClient?.readRecords(request)
            val latestWeight = response?.records?.firstOrNull()?.weight?.inKilograms

            Log.d("HealthConnectIntegrator", "Weight for $openedDay: $latestWeight kg")
            latestWeight
        } catch (e: Exception) {
            Log.e("HealthConnectIntegrator", "Error reading weight for $openedDay: ${e.message}", e)
            null
        }
    }




    /*OLD FUNCTIONS*/
    suspend fun getSleepDuration(date: LocalDate): Duration? {
        if (!hasPermissions()) {
            Log.w("HealthConnectIntegrator", "Attempted to read sleep without necessary permissions.")
            return null
        }
        return try {
            val timeRangeFilter = createTimeRangeFilter(date)
            val request = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = timeRangeFilter
            )
            val sleepSessions = healthConnectClient?.readRecords(request)
            // Sum the duration of all sleep sessions for the given day
            val totalDuration = sleepSessions?.records?.map { record ->
                Duration.between(record.startTime, record.endTime)
            }?.fold(Duration.ZERO, Duration::plus)

            Log.d("HealthConnectIntegrator", "Total sleep for $date: ${totalDuration?.toMinutes()} minutes")
            totalDuration
        } catch (e: Exception) {
            Log.e("HealthConnectIntegrator", "Error reading sleep data: ${e.message}", e)
            null
        }
    }

    /**
     * Reads the total step count for a specific day from Health Connect.
     * @param date The date for which to retrieve step data.
     * @return Total steps as a [Long], or null if unavailable or permissions are denied.
     */
    suspend fun getSteps(date: LocalDate): Long? {
        if (!hasPermissions()) {
            Log.w("HealthConnectIntegrator", "Attempted to read steps without necessary permissions.")
            return null
        }
        return try {
            val timeRangeFilter = createTimeRangeFilter(date)
            val request = ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = timeRangeFilter
            )
            val response = healthConnectClient?.readRecords(request)
            // Sum up the steps from all records for the day
            val totalSteps = response?.records?.sumOf { it.count }
            Log.d("HealthConnectIntegrator", "Total steps for $date: $totalSteps")
            totalSteps
        } catch (e: Exception) {
            Log.e("HealthConnectIntegrator", "Error reading steps data: ${e.message}", e)
            null
        }
    }

    /**
     * Reads the total active calories burned for a specific day from Health Connect.
     * @param date The date for which to retrieve calorie data.
     * @return Total calories in kilocalories as a [Double], or null if unavailable or permissions are denied.
     */
    suspend fun getActiveCaloriesBurned(date: LocalDate): Double? {
        if (!hasPermissions()) {
            Log.w("HealthConnectIntegrator", "Attempted to read calories without necessary permissions.")
            return null
        }
        return try {
            val timeRangeFilter = createTimeRangeFilter(date)
            val request = ReadRecordsRequest(
                recordType = ActiveCaloriesBurnedRecord::class,
                timeRangeFilter = timeRangeFilter
            )
            val response = healthConnectClient?.readRecords(request)
            // Sum up the calories from all records for the day
            val totalCalories = response?.records?.sumOf { it.energy.inKilocalories }
            Log.d("HealthConnectIntegrator", "Total active calories for $date: $totalCalories kcal")
            totalCalories
        } catch (e: Exception) {
            Log.e("HealthConnectIntegrator", "Error reading active calories data: ${e.message}", e)
            null
        }
    }

    /**
     * Reads the latest weight record from Health Connect.
     * @return The latest weight in kilograms (Double), or null if unavailable or permissions are denied.
     */
    suspend fun getLatestWeight(): Double? {
        if (!hasPermissions()) {
            Log.w("HealthConnectIntegrator", "Attempted to read weight without necessary permissions.")
            return null
        }
        return try {
            val request = ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.between(Instant.EPOCH, Instant.now()),
                ascendingOrder = false,
                pageSize = 1
            )
            val response = healthConnectClient?.readRecords(request)
            response?.records?.firstOrNull()?.weight?.inKilograms
        } catch (e: Exception) {
            Log.e("HealthConnectIntegrator", "Error reading weight from Health Connect: ${e.message}", e)
            null
        }
    }

    /**
     * Calculates Basal Metabolic Rate (BMR) using the Mifflin-St Jeor Equation.
     */
    fun calculateBMR(weightKg: Double, heightCm: Double, ageYears: Int, gender: Gender): Double {
        return when (gender) {
            Gender.MALE -> (10 * weightKg) + (6.25 * heightCm) - (5 * ageYears) + 5
            Gender.FEMALE -> (10 * weightKg) + (6.25 * heightCm) - (5 * ageYears) - 161
            Gender.OTHER -> {
                val maleBMR = (10 * weightKg) + (6.25 * heightCm) - (5 * ageYears) + 5
                val femaleBMR = (10 * weightKg) + (6.25 * heightCm) - (5 * ageYears) - 161
                (maleBMR + femaleBMR) / 2
            }
        }
    }

    /**
     * Helper function to calculate age from birth date.
     */
    fun calculateAge(birthDate: ZonedDateTime): Int {
        val today = ZonedDateTime.now(birthDate.zone)
        return ChronoUnit.YEARS.between(birthDate.toLocalDate(), today.toLocalDate()).toInt()
    }
}

class HealthConnectViewModel(context: Context) : ViewModel() {
    private val healthConnectIntegrator = HealthConnectIntegrator(context)

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

    // Expose permissions from integrator for the UI to use
    val permissions = healthConnectIntegrator.permissions.toTypedArray()

    init {
        checkHealthConnectStatus()
    }

    private fun checkHealthConnectStatus() {
        healthConnectAvailable = healthConnectIntegrator.isHealthConnectAvailable()
    }

    /**
     * Checks for permissions and fetches all relevant health data for today.
     */
    suspend fun checkPermissionsAndFetchData() {
        isLoading = true
        errorMessage = null
        try {
            permissionsGranted = healthConnectIntegrator.hasPermissions()
            if (permissionsGranted) {
                // Fetch data for today
                totalSteps = healthConnectIntegrator.getStepsForDay(openedDay)
                totalSleepDuration = healthConnectIntegrator.getSleepDurationForDay(openedDay)
                activeCaloriesBurned = healthConnectIntegrator.getActiveCaloriesBurnedForDay(openedDay)
                latestWeight = healthConnectIntegrator.getWeightForDay(openedDay)

                // Example BMR calculation
                val weight = latestWeight
                if (weight != null) {
                    val dummyHeightCm = 183.7 // Replace with actual user data
                    val dummyGender = Gender.MALE // Replace with actual user data
                    val dummyDateOfBirth = ZonedDateTime.now().minusYears(19) // Replace with actual user data
                    val ageYears = healthConnectIntegrator.calculateAge(dummyDateOfBirth)
                    bmr = healthConnectIntegrator.calculateBMR(weight, dummyHeightCm, ageYears, dummyGender)
                } else {
                    bmr = null
                }
            } else {
                // Clear data if permissions are not granted
                latestWeight = null
                bmr = null
                totalSteps = null
                totalSleepDuration = null
                activeCaloriesBurned = null
            }
        } catch (e: Exception) {
            Log.e("HealthConnectViewModel", "Error fetching Health Connect data: ${e.message}", e)
            errorMessage = "Error fetching data: ${e.localizedMessage ?: "Unknown error"}"
        } finally {
            isLoading = false
        }
    }

    suspend fun fetchDataForDay(openedDay: String) {
        isLoading = true
        errorMessage = null
        try {
            permissionsGranted = healthConnectIntegrator.hasPermissions()
            if (permissionsGranted) {
                totalSteps = healthConnectIntegrator.getStepsForDay(openedDay)
                totalSleepDuration = healthConnectIntegrator.getSleepDurationForDay(openedDay)
                activeCaloriesBurned = healthConnectIntegrator.getActiveCaloriesBurnedForDay(openedDay)
                latestWeight = healthConnectIntegrator.getWeightForDay(openedDay)
                    ?: healthConnectIntegrator.getLatestWeight() // fallback


                val weight = latestWeight
                if (weight != null) {
                    val dummyHeightCm = 183.7 // Replace with real data
                    val dummyGender = Gender.MALE
                    val dummyDob = ZonedDateTime.now().minusYears(19)
                    val age = healthConnectIntegrator.calculateAge(dummyDob)
                    bmr = healthConnectIntegrator.calculateBMR(weight, dummyHeightCm, age, dummyGender)
                } else {
                    bmr = null
                }
            } else {
                latestWeight = null
                bmr = null
                totalSteps = null
                totalSleepDuration = null
                activeCaloriesBurned = null
            }
        } catch (e: Exception) {
            Log.e("HealthConnectViewModel", "Error fetching data for $openedDay", e)
            errorMessage = "Error fetching data: ${e.message ?: "Unknown"}"
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
// AppGlobals.kt
package com.example.mattshealthtracker

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Date
import java.util.Locale
import java.util.TimeZone // For UTC timestamp
import java.util.UUID // Import UUID

// Enums (keep them here or move to separate files if they grow)
enum class EnergyUnit { KCAL, KJ }
enum class DeviceRole { PRIMARY, SECONDARY }
enum class Gender { MALE, FEMALE, OTHER, PREFER_NOT_TO_SAY } // Added PREFER_NOT_TO_SAY

// UserProfile Data Class
data class UserProfile(
    val gender: Gender = Gender.PREFER_NOT_TO_SAY, // Default value
    val dateOfBirth: ZonedDateTime? = null, // Default value
    val heightCm: Double? = null // Optional: Add height if needed for BMR globally
)

object AppGlobals {
    // Current day formatted as String (yyyy-MM-dd)
    val currentDay: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    var openedDay: String = currentDay
    var energyUnitPreference by mutableStateOf(EnergyUnit.KCAL)
    var deviceRole by mutableStateOf(DeviceRole.PRIMARY)
        private set // Setter is private, use updateDeviceRole

    // --- User Profile State ---
    var userProfile by mutableStateOf(UserProfile()) // Initialize with default UserProfile
        private set // Setter is private, use updateUserProfile

    // --- Device Identifier ---
    var appDeviceID: String? = null
        private set // Loaded during initialization

    // --- SharedPreferences related ---
    // Keep PREFS_NAME consistent if AppIdentifiers was merged here
    const val PREFS_NAME = "AppPrefs" // Make it accessible if needed by GoogleDriveUtils directly
    private const val KEY_DEVICE_ROLE = "device_role"
    private const val KEY_ENERGY_UNIT = "energy_unit" // For persisting energy unit
    private const val KEY_USER_GENDER = "user_gender"
    private const val KEY_USER_DOB = "user_dob_iso_zoned" // Store as ISO ZonedDateTime string
    private const val KEY_USER_HEIGHT_CM = "user_height_cm"
    private const val KEY_APP_DEVICE_ID = "app_device_id_hex8" // New key for our 8-char hex ID

    // Make sharedPreferences accessible within AppGlobals or pass context to methods needing it
    private var sharedPreferencesInstance: SharedPreferences? = null

    private fun getPrefs(context: Context): SharedPreferences {
        if (sharedPreferencesInstance == null) {
            sharedPreferencesInstance =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        return sharedPreferencesInstance!!
    }


    fun initialize(context: Context) {
        // No need to check sharedPreferencesInstance == null here, getPrefs handles it
        loadDeviceRole(context)
        loadEnergyUnit(context)
        loadUserProfile(context)
        loadOrGenerateAppDeviceID(context) // Load or generate the device ID
    }

    private fun loadDeviceRole(context: Context) {
        val prefs = getPrefs(context)
        val roleString = prefs.getString(KEY_DEVICE_ROLE, DeviceRole.PRIMARY.name)
        deviceRole = try {
            DeviceRole.valueOf(roleString ?: DeviceRole.PRIMARY.name)
        } catch (e: IllegalArgumentException) {
            DeviceRole.PRIMARY
        }
    }

    fun updateDeviceRole(context: Context, newRole: DeviceRole) {
        deviceRole = newRole
        getPrefs(context).edit().putString(KEY_DEVICE_ROLE, newRole.name).apply()
    }

    private fun loadEnergyUnit(context: Context) {
        val prefs = getPrefs(context)
        val unitString = prefs.getString(KEY_ENERGY_UNIT, EnergyUnit.KCAL.name)
        energyUnitPreference = try {
            EnergyUnit.valueOf(unitString ?: EnergyUnit.KCAL.name)
        } catch (e: IllegalArgumentException) {
            EnergyUnit.KCAL
        }
    }

    fun updateEnergyUnit(context: Context, newUnit: EnergyUnit) {
        energyUnitPreference = newUnit
        getPrefs(context).edit().putString(KEY_ENERGY_UNIT, newUnit.name).apply()
    }

    private fun loadUserProfile(context: Context) {
        val prefs = getPrefs(context)
        val genderString =
            prefs.getString(KEY_USER_GENDER, Gender.PREFER_NOT_TO_SAY.name)
        val dobString = prefs.getString(KEY_USER_DOB, null)
        val heightCm =
            prefs.getFloat(KEY_USER_HEIGHT_CM, 0f).toDouble().takeIf { it > 0 }

        val gender = try {
            Gender.valueOf(genderString ?: Gender.PREFER_NOT_TO_SAY.name)
        } catch (e: IllegalArgumentException) {
            Gender.PREFER_NOT_TO_SAY
        }

        val dateOfBirth = dobString?.let {
            try {
                ZonedDateTime.parse(it, DateTimeFormatter.ISO_ZONED_DATE_TIME)
            } catch (e: DateTimeParseException) {
                null // Could not parse
            }
        }
        userProfile = UserProfile(gender, dateOfBirth, heightCm)
    }

    fun updateUserProfile(context: Context, updatedProfile: UserProfile) {
        userProfile = updatedProfile
        getPrefs(context).edit().apply {
            putString(KEY_USER_GENDER, updatedProfile.gender.name)
            updatedProfile.dateOfBirth?.let {
                putString(
                    KEY_USER_DOB,
                    it.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)
                )
            } ?: remove(KEY_USER_DOB)
            updatedProfile.heightCm?.let { putFloat(KEY_USER_HEIGHT_CM, it.toFloat()) }
                ?: remove(KEY_USER_HEIGHT_CM)
            apply()
        }
    }

    // --- Device ID Logic ---
    private fun loadOrGenerateAppDeviceID(context: Context) {
        val prefs = getPrefs(context)
        var deviceId = prefs.getString(KEY_APP_DEVICE_ID, null)
        if (deviceId == null) {
            // Generate a new UUID, take the first 8 characters of its hex string
            // UUID.randomUUID().toString() gives "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
            // We'll take the first part which is 8 hex chars.
            deviceId = UUID.randomUUID().toString().substring(0, 8)
            prefs.edit().putString(KEY_APP_DEVICE_ID, deviceId).apply()
            Log.d("AppGlobals", "Generated new AppDeviceID: $deviceId")
        } else {
            Log.d("AppGlobals", "Loaded existing AppDeviceID: $deviceId")
        }
        appDeviceID = deviceId
    }

    /**
     * Retrieves the 8-character hexadecimal App Device ID.
     * Ensures AppGlobals.initialize() has been called first.
     * @return The App Device ID string.
     * @throws IllegalStateException if initialize() hasn't been called or ID is somehow still null.
     */

    // --- End SharedPreferences related ---


    // Helper function to convert string date to LocalDate
    fun getCurrentDayAsLocalDate(): LocalDate {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.parse(currentDay)?.let {
            it.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        } ?: LocalDate.now() // Default to today's date if parsing fails
    }

    // Helper function to convert string date to LocalDate
    fun getOpenedDayAsLocalDate(): LocalDate {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.parse(openedDay)?.let {
            it.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        } ?: LocalDate.now() // Default to today's date if parsing fails
    }

    // Helper function to convert LocalDate to string
    fun setOpenedDayFromLocalDate(localDate: LocalDate) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        openedDay = sdf.format(Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant()))
    }

    // Function to get a UTC timestamp formatted as "yyyyMMddHHmmss"
    // For Drive filename, "yyyyMMdd'T'HHmmss'Z'" is better for ISO 8601 compliance
    fun getUtcTimestampForFileName(): String {
        val sdf = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US) // US locale for consistency
        sdf.timeZone = TimeZone.getTimeZone("UTC") // Set timezone to UTC
        return sdf.format(Date())
    }

    // Kept your original getUtcTimestamp if used elsewhere, but for filenames, the above is preferred.
    fun getUtcTimestamp(): String {
        val sdf = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC") // Set timezone to UTC
        return sdf.format(Date())
    }

    fun regenerateAppDeviceID(context: Context) {
        val prefs = getPrefs(context)
        val newId = generateNewAppDeviceID(prefs) // Generate and save
        appDeviceID = newId // Update the mutableState variable to trigger recomposition
        Log.d("AppGlobals", "Regenerated AppDeviceID: $newId")
        // Optionally: you might want to inform the user that this action could affect sync
        // if old files on Drive used the previous ID and are no longer recognized as "from this device".
    }

    private fun generateNewAppDeviceID(prefs: SharedPreferences): String {
        val newId = UUID.randomUUID().toString().substring(0, 8)
        prefs.edit().putString(KEY_APP_DEVICE_ID, newId).apply()
        return newId
    }
}

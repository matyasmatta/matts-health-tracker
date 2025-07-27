// AppGlobals.kt
package com.example.mattshealthtracker

import android.content.Context
import android.content.SharedPreferences
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

    // --- SharedPreferences related ---
    private const val PREFS_NAME = "AppPrefs"
    private const val KEY_DEVICE_ROLE = "device_role"
    private const val KEY_ENERGY_UNIT = "energy_unit" // For persisting energy unit
    private const val KEY_USER_GENDER = "user_gender"
    private const val KEY_USER_DOB = "user_dob_iso_zoned" // Store as ISO ZonedDateTime string
    private const val KEY_USER_HEIGHT_CM = "user_height_cm"

    private var sharedPreferences: SharedPreferences? = null

    fun initialize(context: Context) {
        if (sharedPreferences == null) {
            sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadDeviceRole()
            loadEnergyUnit()
            loadUserProfile() // Load user profile on initialization
        }
    }

    private fun loadDeviceRole() {
        val roleString = sharedPreferences?.getString(KEY_DEVICE_ROLE, DeviceRole.PRIMARY.name)
        deviceRole = try {
            DeviceRole.valueOf(roleString ?: DeviceRole.PRIMARY.name)
        } catch (e: IllegalArgumentException) {
            DeviceRole.PRIMARY
        }
    }

    fun updateDeviceRole(newRole: DeviceRole) {
        deviceRole = newRole
        sharedPreferences?.edit()?.putString(KEY_DEVICE_ROLE, newRole.name)?.apply()
    }

    private fun loadEnergyUnit() {
        val unitString = sharedPreferences?.getString(KEY_ENERGY_UNIT, EnergyUnit.KCAL.name)
        energyUnitPreference = try {
            EnergyUnit.valueOf(unitString ?: EnergyUnit.KCAL.name)
        } catch (e: IllegalArgumentException) {
            EnergyUnit.KCAL
        }
    }

    fun updateEnergyUnit(newUnit: EnergyUnit) {
        energyUnitPreference = newUnit
        sharedPreferences?.edit()?.putString(KEY_ENERGY_UNIT, newUnit.name)?.apply()
    }

    private fun loadUserProfile() {
        val genderString =
            sharedPreferences?.getString(KEY_USER_GENDER, Gender.PREFER_NOT_TO_SAY.name)
        val dobString = sharedPreferences?.getString(KEY_USER_DOB, null)
        val heightCm =
            sharedPreferences?.getFloat(KEY_USER_HEIGHT_CM, 0f)?.toDouble()?.takeIf { it > 0 }


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

    fun updateUserProfile(updatedProfile: UserProfile) {
        userProfile = updatedProfile
        sharedPreferences?.edit()?.apply {
            putString(KEY_USER_GENDER, updatedProfile.gender.name)
            // Store ZonedDateTime as ISO string to preserve timezone
            updatedProfile.dateOfBirth?.let {
                putString(
                    KEY_USER_DOB,
                    it.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)
                )
            }
                ?: remove(KEY_USER_DOB)
            updatedProfile.heightCm?.let { putFloat(KEY_USER_HEIGHT_CM, it.toFloat()) }
                ?: remove(KEY_USER_HEIGHT_CM)
            apply()
        }
    }
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
    fun getUtcTimestamp(): String {
        val sdf = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC") // Set timezone to UTC
        return sdf.format(Date())
    }
}


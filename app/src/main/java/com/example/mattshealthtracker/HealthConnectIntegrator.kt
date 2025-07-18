// HealthConnectIntegrator.kt
package com.example.mattshealthtracker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

// Consider creating a data class for user profile if you don't have one
data class UserProfile(
    val gender: Gender,
    val dateOfBirth: ZonedDateTime? // For age calculation
)

enum class Gender { MALE, FEMALE, OTHER } // Define Gender enum

class HealthConnectIntegrator(private val context: Context) {

    private val healthConnectClient: HealthConnectClient? by lazy {
        val availabilityStatus = HealthConnectClient.is : (context) // Corrected syntax
        if (availabilityStatus == HealthConnectClient.SDK_AVAILABLE) {
            Log.d("HealthConnectIntegrator", "Health Connect SDK is available.")
            HealthConnectClient.getOrCreate(context)
        } else {
            Log.w("HealthConnectIntegrator", "Health Connect SDK not available. Status: $availabilityStatus")
            null
        }
    }

    // Permissions you need from Health Connect
    // We request READ_WEIGHT to get the latest weight.
    val permissions = setOf(
        HealthPermission.createReadPermission(WeightRecord::class)
        // Add other permissions if you need to read other data types (e.g., HeightRecord, ExerciseSessionRecord)
        // HealthPermission.createReadPermission(HeightRecord::class),
        // HealthPermission.createWritePermission(WeightRecord::class) // If you ever write weight
    )

    // Check if Health Connect is installed/available
    fun isHealthConnectAvailable(): Boolean {
        return healthConnectClient != null
    }

    // Function to check if permissions are already granted
    suspend fun hasPermissions(): Boolean {
        if (!isHealthConnectAvailable()) {
            return false
        }
        return try {
            healthConnectClient?.permissionController?.getGrantedPermissions()?.containsAll(permissions) == true
        } catch (e: Exception) {
            Log.e("HealthConnectIntegrator", "Error checking permissions: ${e.message}", e)
            false
        }
    }

    /**
     * Launches the Health Connect permission request flow.
     * It's crucial to call this from an Activity or Fragment, typically in response to a user action.
     *
     * @param requestPermissions The ActivityResultLauncher created in your Activity/Fragment.
     */
    fun requestPermissions(requestPermissions: ActivityResultLauncher<Set<String>>) {
        // You generally don't check hasPermissions() here directly, as the launcher
        // is designed to handle the result. The user might have revoked permissions
        // from HC settings, so re-requesting is often appropriate.
        requestPermissions.launch(permissions)
    }

    /**
     * Opens the Health Connect app's settings or prompts to install it.
     * Useful if Health Connect is not available or permissions need to be managed manually.
     */
    fun openHealthConnectSettings() {
        // This intent directly opens the Health Connect app (if installed) or its Play Store page.
        // It's robust as it uses the official Health Connect intent action.
        val intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            Log.w("HealthConnectIntegrator", "Health Connect settings intent not resolvable. Attempting Play Store.")
            val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))
            if (playStoreIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(playStoreIntent)
            } else {
                Log.e("HealthConnectIntegrator", "Neither Health Connect settings nor Play Store intent resolvable.")
            }
        }
    }

    /**
     * Reads the latest weight record from Health Connect.
     * @return The latest weight in kilograms (Double), or null if not found or permissions are missing.
     */
    suspend fun getLatestWeight(): Double? {
        if (!isHealthConnectAvailable()) {
            Log.w("HealthConnectIntegrator", "Health Connect client is not available.")
            return null
        }
        if (!hasPermissions()) { // Ensure permissions are still granted before attempting read
            Log.w("HealthConnectIntegrator", "Permissions not granted for reading weight.")
            return null
        }

        return try {
            val response = healthConnectClient?.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.period(Instant.EPOCH, Instant.now()), // Read all history up to now
                    ascendingOrder = false, // Get latest first
                    pageSize = 1 // Only need the latest
                )
            )
            val latestWeight = response?.records?.firstOrNull()?.weight?.inKilograms
            if (latestWeight != null) {
                Log.d("HealthConnectIntegrator", "Latest weight read: $latestWeight kg")
            } else {
                Log.d("HealthConnectIntegrator", "No weight records found in Health Connect.")
            }
            latestWeight
        } catch (e: Exception) {
            Log.e("HealthConnectIntegrator", "Error reading weight from Health Connect: ${e.message}", e)
            null
        }
    }

    /**
     * Calculates Basal Metabolic Rate (BMR) using the Mifflin-St Jeor Equation.
     * This is a common and widely accepted formula.
     *
     * Males: BMR = (10 × weight in kg) + (6.25 × height in cm) - (5 × age in years) + 5
     * Females: BMR = (10 × weight in kg) + (6.25 × height in cm) - (5 × age in years) - 161
     *
     * @param weightKg User's weight in kilograms.
     * @param heightCm User's height in centimeters.
     * @param ageYears User's age in years.
     * @param gender User's gender (MALE, FEMALE).
     * @return BMR in calories per day.
     */
    fun calculateBMR(weightKg: Double, heightCm: Double, ageYears: Int, gender: Gender): Double {
        return when (gender) {
            Gender.MALE -> (10 * weightKg) + (6.25 * heightCm) - (5 * ageYears) + 5
            Gender.FEMALE -> (10 * weightKg) + (6.25 * heightCm) - (5 * ageYears) - 161
            Gender.OTHER -> {
                // For 'OTHER', you might choose to:
                // 1. Use the average of MALE/FEMALE (as done here).
                // 2. Default to MALE or FEMALE.
                // 3. Ask for a more specific input or use an alternative formula if available.
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
        val today = ZonedDateTime.now(birthDate.zone) // Use the same timezone as birthDate
        return ChronoUnit.YEARS.between(birthDate.toLocalDate(), today.toLocalDate()).toInt()
    }
}
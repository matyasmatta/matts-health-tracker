// AppGlobals.kt
package com.example.mattshealthtracker

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.gson.Gson // For saving Set<String> easily
import com.google.gson.reflect.TypeToken // For loading Set<String>
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
enum class Gender { MALE, FEMALE, OTHER, PREFER_NOT_TO_SAY }

// UserProfile Data Class
data class UserProfile(
    val gender: Gender = Gender.PREFER_NOT_TO_SAY,
    val dateOfBirth: ZonedDateTime? = null,
    val heightCm: Double? = null
)

// Sealed class for Bottom Navigation Item information
// This defines all possible items and their properties.
sealed class BottomNavItemInfo(
    val route: String,
    val defaultLabel: String, // Default label, can be overridden by user preferences later if needed
    val defaultIcon: ImageVector, // Default icon
    val isCoreFeature: Boolean = false // Core features might not be hideable or are shown by default
) {
    object AddData : BottomNavItemInfo("add_data", "Tracking", Icons.Default.MonitorHeart, true)
    object Statistics :
        BottomNavItemInfo("statistics", "Statistics", Icons.Default.QueryStats, false)

    object Food : BottomNavItemInfo("food", "Food", Icons.Default.Restaurant)
    object Exercises : BottomNavItemInfo("exercises", "Routines", Icons.Default.FitnessCenter)
    object MedicationTracking :
        BottomNavItemInfo("medication_tracking", "Meds", Icons.Default.Medication)
    // Add other potential items here in the future

    companion object {
        fun getAllItems(): List<BottomNavItemInfo> {
            // Defines the order in which items will appear if all are visible
            return listOf(AddData, Statistics, Food, Exercises, MedicationTracking)
        }

        fun getItemByRoute(route: String): BottomNavItemInfo? {
            return getAllItems().find { it.route == route }
        }
    }
}

// --- NEW: Sealed class for Exercise Screen Section Information ---
sealed class ExerciseScreenSectionInfo(
    val id: String, // Unique identifier for SharedPreferences & logic
    val defaultLabel: String,
    val defaultIcon: ImageVector, // For use in the customization dialog
    val isCoreSection: Boolean = false // If any section is considered essential and cannot be hidden
) {
    object Basics : ExerciseScreenSectionInfo(
        "basics",
        "Basic Exercises",
        Icons.Default.AccessibilityNew,
        false
    ) // Example: Basics is core

    object Breathing :
        ExerciseScreenSectionInfo("breathing", "Breathing Exercises", Icons.Default.SelfImprovement)

    object Routines :
        ExerciseScreenSectionInfo("routines", "Routine Checklist", Icons.Default.Checklist)
    // Add more sections here if they become available in ExercisesScreen

    companion object {
        fun getAllSections(): List<ExerciseScreenSectionInfo> {
            return listOf(Basics, Breathing, Routines)
        }

        fun getSectionById(id: String): ExerciseScreenSectionInfo? {
            return getAllSections().find { it.id == id }
        }
    }
}

object AppGlobals {
    // Current day formatted as String (yyyy-MM-dd)
    val currentDay: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    var openedDay: String = currentDay
    var energyUnitPreference by mutableStateOf(EnergyUnit.KCAL)
    var deviceRole by mutableStateOf(DeviceRole.PRIMARY)
        private set // Setter is private, use updateDeviceRole

    var userProfile by mutableStateOf(UserProfile())
        private set // Setter is private, use updateUserProfile

    var appDeviceID: String? = null
        private set // Loaded during initialization

    var performAutoSync by mutableStateOf(true)
        private set // Setter is private, use updatePerformAutoSync

    // --- Bottom Navigation Customization ---
    // Stores the routes of items the user wants to see.
    var visibleBottomNavRoutes by mutableStateOf<Set<String>>(emptySet())
        private set // Setter is private, use updateVisibleBottomNavRoutes

    // --- NEW: Exercise Screen Section Customization ---
    var visibleExerciseSectionIds by mutableStateOf<Set<String>>(emptySet())
        private set // Setter is private, use updateVisibleExerciseSectionIds

    // --- User-Defined Symptom (TrackerItem Names) Customization ---
    var userDefinedSymptomNames by mutableStateOf<List<String>>(emptyList())
        private set

    // --- SharedPreferences related ---
    const val PREFS_NAME = "AppPrefs"
    private const val KEY_DEVICE_ROLE = "device_role"
    private const val KEY_ENERGY_UNIT = "energy_unit"
    private const val KEY_USER_GENDER = "user_gender"
    private const val KEY_USER_DOB = "user_dob_iso_zoned"
    private const val KEY_USER_HEIGHT_CM = "user_height_cm"
    private const val KEY_APP_DEVICE_ID = "app_device_id_hex8"
    private const val KEY_PERFORM_AUTO_SYNC = "perform_auto_sync"
    private const val KEY_VISIBLE_BOTTOM_NAV_ROUTES =
        "visible_bottom_nav_routes" // Key for storing visible routes
    private const val KEY_VISIBLE_EXERCISE_SECTIONS = "visible_exercise_sections" // New key

    private const val KEY_USER_DEFINED_SYMPTOM_NAMES = "user_defined_symptom_names" // New Key

    private var sharedPreferencesInstance: SharedPreferences? = null
    private val gson = Gson() // For serializing/deserializing Set<String>

    private fun getPrefs(context: Context): SharedPreferences {
        if (sharedPreferencesInstance == null) {
            sharedPreferencesInstance =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        return sharedPreferencesInstance!!
    }

    fun initialize(context: Context) {
        Log.d("AppGlobals", "Initialization started.")
        loadDeviceRole(context)
        loadEnergyUnit(context)
        loadUserProfile(context)
        loadOrGenerateAppDeviceID(context)
        loadPerformAutoSync(context)
        loadVisibleBottomNavRoutes(context) // Load the new preference for bottom nav items
        loadUserDefinedSymptomNames(context)
        loadVisibleExerciseSectionIds(context)
        Log.d("AppGlobals", "Initialization complete.")
    }

    private fun loadDeviceRole(context: Context) {
        val prefs = getPrefs(context)
        val roleString = prefs.getString(KEY_DEVICE_ROLE, DeviceRole.PRIMARY.name)
        deviceRole = try {
            DeviceRole.valueOf(roleString ?: DeviceRole.PRIMARY.name)
        } catch (e: IllegalArgumentException) {
            DeviceRole.PRIMARY
        }
        Log.d("AppGlobals", "Loaded DeviceRole: $deviceRole")
    }

    fun updateDeviceRole(context: Context, newRole: DeviceRole) {
        deviceRole = newRole
        getPrefs(context).edit().putString(KEY_DEVICE_ROLE, newRole.name).apply()
        Log.d("AppGlobals", "Updated DeviceRole to: $newRole")
    }

    private fun loadEnergyUnit(context: Context) {
        val prefs = getPrefs(context)
        val unitString = prefs.getString(KEY_ENERGY_UNIT, EnergyUnit.KCAL.name)
        energyUnitPreference = try {
            EnergyUnit.valueOf(unitString ?: EnergyUnit.KCAL.name)
        } catch (e: IllegalArgumentException) {
            EnergyUnit.KCAL
        }
        Log.d("AppGlobals", "Loaded EnergyUnitPreference: $energyUnitPreference")
    }

    fun updateEnergyUnit(context: Context, newUnit: EnergyUnit) {
        energyUnitPreference = newUnit
        getPrefs(context).edit().putString(KEY_ENERGY_UNIT, newUnit.name).apply()
        Log.d("AppGlobals", "Updated EnergyUnitPreference to: $newUnit")
    }

    private fun loadUserProfile(context: Context) {
        val prefs = getPrefs(context)
        val genderString = prefs.getString(KEY_USER_GENDER, Gender.PREFER_NOT_TO_SAY.name)
        val dobString = prefs.getString(KEY_USER_DOB, null)
        val heightCm = prefs.getFloat(KEY_USER_HEIGHT_CM, 0f).toDouble().takeIf { it > 0 }

        val gender = try {
            Gender.valueOf(genderString ?: Gender.PREFER_NOT_TO_SAY.name)
        } catch (e: IllegalArgumentException) {
            Gender.PREFER_NOT_TO_SAY
        }
        val dateOfBirth = dobString?.let {
            try {
                ZonedDateTime.parse(it, DateTimeFormatter.ISO_ZONED_DATE_TIME)
            } catch (e: DateTimeParseException) {
                Log.w("AppGlobals", "Could not parse stored DoB: $it", e); null
            }
        }
        userProfile = UserProfile(gender, dateOfBirth, heightCm)
        Log.d("AppGlobals", "Loaded UserProfile: $userProfile")
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
            updatedProfile.heightCm?.let { putFloat(KEY_USER_HEIGHT_CM, it.toFloat()) } ?: remove(
                KEY_USER_HEIGHT_CM
            )
            apply()
        }
        Log.d("AppGlobals", "Updated UserProfile to: $updatedProfile")
    }

    private fun loadOrGenerateAppDeviceID(context: Context) {
        val prefs = getPrefs(context)
        var deviceId = prefs.getString(KEY_APP_DEVICE_ID, null)
        if (deviceId == null) {
            deviceId = generateNewAppDeviceID(prefs) // Call private fun to generate and save
            Log.d("AppGlobals", "Generated new AppDeviceID: $deviceId")
        } else {
            Log.d("AppGlobals", "Loaded existing AppDeviceID: $deviceId")
        }
        appDeviceID = deviceId
    }

    // Made public for potential use if user wants to explicitly regenerate ID (e.g., for privacy reset)
    fun regenerateAppDeviceID(context: Context) {
        val newId = generateNewAppDeviceID(getPrefs(context))
        appDeviceID = newId
        Log.d("AppGlobals", "Regenerated AppDeviceID: $newId")
    }

    private fun generateNewAppDeviceID(prefs: SharedPreferences): String {
        val newId = UUID.randomUUID().toString().substring(0, 8).uppercase(Locale.ROOT)
        prefs.edit().putString(KEY_APP_DEVICE_ID, newId).apply()
        return newId
    }

    private fun loadPerformAutoSync(context: Context) {
        val prefs = getPrefs(context)
        performAutoSync = prefs.getBoolean(KEY_PERFORM_AUTO_SYNC, true)
        Log.d("AppGlobals", "Loaded PerformAutoSync: $performAutoSync")
    }

    fun updatePerformAutoSync(context: Context, newValue: Boolean) {
        performAutoSync = newValue
        getPrefs(context).edit().putBoolean(KEY_PERFORM_AUTO_SYNC, newValue).apply()
        Log.d("AppGlobals", "Updated PerformAutoSync to: $newValue")
    }

    // --- Bottom Navigation Preferences ---
    private fun loadVisibleBottomNavRoutes(context: Context) {
        val prefs = getPrefs(context)
        val jsonString = prefs.getString(KEY_VISIBLE_BOTTOM_NAV_ROUTES, null)
        val defaultCoreRoutes = BottomNavItemInfo.getAllItems()
            .filter { it.isCoreFeature }
            .map { it.route }
            .toSet()

        if (jsonString != null) {
            val type = object : TypeToken<Set<String>>() {}.type
            try {
                val loadedRoutes: Set<String> = gson.fromJson(jsonString, type)
                // Ensure core features are always present. If loaded set is empty or missing core, start with core.
                if (loadedRoutes.isEmpty() || !loadedRoutes.containsAll(defaultCoreRoutes)) {
                    visibleBottomNavRoutes = BottomNavItemInfo.getAllItems().map { it.route }
                        .toSet() // Default to all if corrupted
                    Log.w(
                        "AppGlobals",
                        "Loaded visible routes were problematic, defaulting to all items."
                    )
                    // Persist this corrected default
                    updateVisibleBottomNavRoutes(context, visibleBottomNavRoutes)
                } else {
                    visibleBottomNavRoutes = loadedRoutes
                    Log.d("AppGlobals", "Loaded visible bottom nav routes: $visibleBottomNavRoutes")
                }
            } catch (e: Exception) { // Catches JsonSyntaxException and others
                Log.e(
                    "AppGlobals",
                    "Error loading visible bottom nav routes from JSON, defaulting to all items.",
                    e
                )
                visibleBottomNavRoutes = BottomNavItemInfo.getAllItems().map { it.route }.toSet()
                updateVisibleBottomNavRoutes(context, visibleBottomNavRoutes) // Persist default
            }
        } else {
            // No preference saved (e.g., first launch). Default to showing all items.
            visibleBottomNavRoutes = BottomNavItemInfo.getAllItems().map { it.route }.toSet()
            Log.d(
                "AppGlobals",
                "No saved bottom nav routes, defaulting to all items: $visibleBottomNavRoutes"
            )
            updateVisibleBottomNavRoutes(context, visibleBottomNavRoutes) // Save this default
        }
    }

    fun updateVisibleBottomNavRoutes(context: Context, newVisibleRoutesUserChoice: Set<String>) {
        val coreRoutes = BottomNavItemInfo.getAllItems()
            .filter { it.isCoreFeature }
            .map { it.route }
            .toSet()
        // Ensure core features are always included, regardless of user choice for them.
        val finalRoutesToShow = newVisibleRoutesUserChoice.union(coreRoutes)

        if (visibleBottomNavRoutes != finalRoutesToShow) { // Only update if there's an actual change
            visibleBottomNavRoutes = finalRoutesToShow
            val jsonString = gson.toJson(finalRoutesToShow)
            getPrefs(context).edit().putString(KEY_VISIBLE_BOTTOM_NAV_ROUTES, jsonString).apply()
            Log.d(
                "AppGlobals",
                "Updated and saved visible bottom nav routes to: $finalRoutesToShow"
            )
        } else {
            Log.d(
                "AppGlobals",
                "No change in visible bottom nav routes, not saving. Current: $visibleBottomNavRoutes"
            )
        }
    }

    // Helper to get the actual BottomNavItemInfo objects to display based on visible routes
    fun getCurrentlyVisibleBottomNavItems(): List<BottomNavItemInfo> {
        val allItemsInOrder = BottomNavItemInfo.getAllItems()
        // Filter based on the routes stored in visibleBottomNavRoutes, maintaining original order
        return allItemsInOrder.filter { it.route in visibleBottomNavRoutes }
    }

    // --- NEW: Load and Update methods for Exercise Screen Sections ---
    private fun loadVisibleExerciseSectionIds(context: Context) {
        val prefs = getPrefs(context)
        val jsonString = prefs.getString(KEY_VISIBLE_EXERCISE_SECTIONS, null)
        val allSectionIds = ExerciseScreenSectionInfo.getAllSections().map { it.id }.toSet()
        val defaultCoreSectionIds = ExerciseScreenSectionInfo.getAllSections()
            .filter { it.isCoreSection }
            .map { it.id }
            .toSet()

        if (jsonString != null) {
            val type = object : TypeToken<Set<String>>() {}.type
            try {
                val loadedIds: Set<String> = gson.fromJson(jsonString, type)
                // Filter loaded IDs to ensure they are still valid sections
                val validLoadedIds = loadedIds.filter { it in allSectionIds }.toSet()

                // Ensure core sections are present. If not, or if list is empty, default to all.
                if (validLoadedIds.isEmpty() || !validLoadedIds.containsAll(defaultCoreSectionIds)) {
                    visibleExerciseSectionIds = allSectionIds // Default to all if problematic
                    Log.w(
                        "AppGlobals",
                        "Loaded exercise sections were problematic ($validLoadedIds), defaulting to all sections."
                    )
                    updateVisibleExerciseSectionIds(
                        context,
                        visibleExerciseSectionIds
                    ) // Persist corrected default
                } else {
                    visibleExerciseSectionIds = validLoadedIds
                    Log.d(
                        "AppGlobals",
                        "Loaded visible exercise section IDs: $visibleExerciseSectionIds"
                    )
                }
            } catch (e: Exception) { // Catches JsonSyntaxException and others
                Log.e(
                    "AppGlobals",
                    "Error loading visible exercise sections from JSON, defaulting to all sections.",
                    e
                )
                visibleExerciseSectionIds = allSectionIds
                updateVisibleExerciseSectionIds(
                    context,
                    visibleExerciseSectionIds
                ) // Persist default
            }
        } else {
            // No preference saved (e.g., first launch). Default to showing all sections.
            visibleExerciseSectionIds = allSectionIds
            Log.d(
                "AppGlobals",
                "No saved exercise sections, defaulting to all: $visibleExerciseSectionIds"
            )
            updateVisibleExerciseSectionIds(context, visibleExerciseSectionIds) // Save this default
        }
    }

    fun updateVisibleExerciseSectionIds(context: Context, newVisibleIdsUserChoice: Set<String>) {
        val coreSectionIds = ExerciseScreenSectionInfo.getAllSections()
            .filter { it.isCoreSection }
            .map { it.id }
            .toSet()
        // Ensure core sections are always included, regardless of user choice for them.
        val finalIdsToShow = newVisibleIdsUserChoice.union(coreSectionIds)
        val allSectionIds = ExerciseScreenSectionInfo.getAllSections().map { it.id }.toSet()
        // Ensure we only save valid section IDs
        val validFinalIds = finalIdsToShow.filter { it in allSectionIds }.toSet()


        if (visibleExerciseSectionIds != validFinalIds) { // Only update if there's an actual change
            visibleExerciseSectionIds = validFinalIds
            val jsonString = gson.toJson(validFinalIds)
            getPrefs(context).edit().putString(KEY_VISIBLE_EXERCISE_SECTIONS, jsonString).apply()
            Log.d("AppGlobals", "Updated and saved visible exercise section IDs to: $validFinalIds")
        } else {
            Log.d(
                "AppGlobals",
                "No change in visible exercise section IDs, not saving. Current: $visibleExerciseSectionIds"
            )
        }
    }

    // Helper to check if a specific section should be visible
    fun isExerciseSectionVisible(sectionId: String): Boolean {
        // If visibleExerciseSectionIds is empty (e.g., during initial load before defaults are set),
        // it might appear that nothing is visible. The loading logic tries to prevent this.
        // However, this check is straightforward.
        return sectionId in visibleExerciseSectionIds
    }

    // Symptoms helpers

    // --- Load and Update for User-Defined Symptom Names ---
    private fun loadUserDefinedSymptomNames(context: Context) {
        val prefs = getPrefs(context)
        val jsonString = prefs.getString(KEY_USER_DEFINED_SYMPTOM_NAMES, null)
        val defaultSymptoms = listOf( // Your original defaults, users can modify/delete these
            "TMJ pain", "Neck clenching", "Ear discomfort", "Testicle pain", "Teeth pain",
            "Aura migraines", "Nausea", "Dizziness", "Acne", "Back pain",
            "Tendon pain", "Carpal tunnel", "Limb weakness", "Fatigue"
        )

        if (jsonString != null) {
            val type = object : TypeToken<List<String>>() {}.type
            try {
                val loadedNames: List<String> = gson.fromJson(jsonString, type)
                userDefinedSymptomNames =
                    loadedNames.distinct() // Ensure no duplicates from bad save
                Log.d("AppGlobals", "Loaded user-defined symptom names: $userDefinedSymptomNames")
                if (userDefinedSymptomNames.isEmpty() && defaultSymptoms.isNotEmpty()) {
                    // If for some reason the loaded list is empty but we used to have defaults, re-seed.
                    // This could happen if a user deletes all, or an error.
                    // Consider if user explicitly wanting an empty list is valid.
                    // For now, if empty AND defaults exist, re-seed (user can delete again).
                    Log.w("AppGlobals", "Loaded empty symptom list, reseeding with defaults.")
                    userDefinedSymptomNames = defaultSymptoms
                    _updateUserDefinedSymptomNamesInternal(
                        context,
                        userDefinedSymptomNames
                    ) // Persist defaults
                }
            } catch (e: Exception) {
                Log.e("AppGlobals", "Error loading user-defined symptom names, using defaults.", e)
                userDefinedSymptomNames = defaultSymptoms
                _updateUserDefinedSymptomNamesInternal(
                    context,
                    userDefinedSymptomNames
                ) // Persist defaults
            }
        } else {
            // No preference saved (e.g., first launch or after clearing data).
            userDefinedSymptomNames = defaultSymptoms
            Log.d(
                "AppGlobals",
                "No saved user-defined symptom names, using defaults: $userDefinedSymptomNames"
            )
            _updateUserDefinedSymptomNamesInternal(
                context,
                userDefinedSymptomNames
            ) // Save defaults
        }
    }

    // Internal update function to avoid logging loop if called from load
    private fun _updateUserDefinedSymptomNamesInternal(context: Context, newNames: List<String>) {
        val distinctNames = newNames.distinct() // Ensure no duplicates
        userDefinedSymptomNames = distinctNames
        val jsonString = gson.toJson(distinctNames)
        getPrefs(context).edit().putString(KEY_USER_DEFINED_SYMPTOM_NAMES, jsonString).apply()
    }


    // Public functions to modify the list from the UI
    fun addUserDefinedSymptom(context: Context, newSymptomName: String) {
        if (newSymptomName.isNotBlank() && !userDefinedSymptomNames.any {
                it.equals(
                    newSymptomName,
                    ignoreCase = true
                )
            }) {
            val updatedList = userDefinedSymptomNames + newSymptomName
            _updateUserDefinedSymptomNamesInternal(context, updatedList)
            Log.d(
                "AppGlobals",
                "Added symptom: $newSymptomName. New list: $userDefinedSymptomNames"
            )
        } else {
            Log.w("AppGlobals", "Symptom '$newSymptomName' is blank or already exists.")
        }
    }

    fun deleteUserDefinedSymptom(context: Context, symptomNameToDelete: String) {
        if (userDefinedSymptomNames.any { it.equals(symptomNameToDelete, ignoreCase = true) }) {
            val updatedList = userDefinedSymptomNames.filterNot {
                it.equals(
                    symptomNameToDelete,
                    ignoreCase = true
                )
            }
            _updateUserDefinedSymptomNamesInternal(context, updatedList)
            Log.d(
                "AppGlobals",
                "Deleted symptom: $symptomNameToDelete. New list: $userDefinedSymptomNames"
            )
        } else {
            Log.w("AppGlobals", "Symptom '$symptomNameToDelete' not found for deletion.")
        }
    }

    fun updateUserDefinedSymptomOrder(context: Context, newOrderedNames: List<String>) {
        // Ensure all items in newOrderedNames are actually known, and all known items are present
        val currentSet = userDefinedSymptomNames.toSet()
        val newSet = newOrderedNames.toSet()
        if (currentSet == newSet && newOrderedNames.size == currentSet.size) { // Ensure it's just a reorder
            _updateUserDefinedSymptomNamesInternal(context, newOrderedNames.distinct())
            Log.d("AppGlobals", "Updated symptom order. New list: $userDefinedSymptomNames")
        } else {
            Log.e(
                "AppGlobals",
                "Attempted to update order with a mismatched set of symptoms. Current: $currentSet, New: $newSet"
            )
            // Optionally, try to reconcile or just log the error. For now, just log.
        }
    }

    // Helper to get the TrackerItems for a given day based on user-defined symptoms
    // This would replace your current `defaultMiscellaneousItems()` in usage.
    // The actual daily data (value, isChecked) would still need to be loaded from your daily storage (e.g., a database for that day).
    fun getTrackerItemsForDay(dailySavedData: MiscellaneousData?): List<TrackerItem> {
        return userDefinedSymptomNames.map { name ->
            val savedItem = dailySavedData?.items?.find { it.name.equals(name, ignoreCase = true) }
            TrackerItem(
                name = name,
                value = savedItem?.value ?: 0f,
                isChecked = savedItem?.isChecked ?: false
            )
        }
    }

    // --- Date Helpers ---
    fun getCurrentDayAsLocalDate(): LocalDate {
        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(currentDay)?.toInstant()
                ?.atZone(ZoneId.systemDefault())?.toLocalDate() ?: LocalDate.now()
        } catch (e: Exception) {
            Log.e("AppGlobals", "Error parsing currentDay: $currentDay", e)
            LocalDate.now()
        }
    }

    fun getOpenedDayAsLocalDate(): LocalDate {
        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(openedDay)?.toInstant()
                ?.atZone(ZoneId.systemDefault())?.toLocalDate() ?: LocalDate.now()
        } catch (e: Exception) {
            Log.e("AppGlobals", "Error parsing openedDay: $openedDay", e)
            LocalDate.now()
        }
    }

    fun setOpenedDayFromLocalDate(localDate: LocalDate) {
        openedDay = SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.getDefault()
        ).format(Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant()))
    }

    fun getUtcTimestampForFileName(): String {
        val sdf = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    fun getUtcTimestamp(): String {
        // Kept the old name, but it's not really a timestamp in the common sense (seconds from epoch)
        // It's a formatted string. Consider renaming if it causes confusion.
        val sdf = SimpleDateFormat(
            "yyyyMMddHHmmss",
            Locale.US
        ) // Using US Locale for consistency if this is for filenames/internal IDs
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}

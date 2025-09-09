package com.example.mattshealthtracker

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import android.content.Context
import android.net.Uri
import com.example.mattshealthtracker.ui.theme.MattsHealthTrackerTheme
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import java.time.LocalDate
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import kotlin.math.roundToInt
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import kotlin.math.abs
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Addchart
import androidx.compose.runtime.saveable.rememberSaveable
import com.google.android.gms.auth.api.signin.GoogleSignInAccount // Import GoogleSignInAccount
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row // if you're scrolling a Row
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import java.time.DayOfWeek
import java.util.Locale
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.DinnerDining // Import the new icon
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Today
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.disabled
import androidx.lifecycle.ViewModelProvider
import java.text.NumberFormat
import java.time.Duration



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // It's best practice to call AppGlobals.initialize() in your Application class's onCreate.
        // If you haven't done that, you could do it here, but Application class is preferred
        // for objects that need context early and throughout the app lifecycle.
        // AppGlobals.initialize(applicationContext) // UNCOMMENT IF NOT DONE IN APPLICATION CLASS

        setContent {
            MattsHealthTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var signedInAccount by rememberSaveable { mutableStateOf<GoogleSignInAccount?>(null) }
                    val context = LocalContext.current

                    val onSignedInAccountChange: (GoogleSignInAccount?) -> Unit = { account ->
                        signedInAccount = account
                        if (account == null) {
                            Log.d("MainActivity", "User signed out or account became null.")
                        }
                    }

                    LaunchedEffect(Unit) {
                        Log.d(
                            "MainActivity",
                            "LaunchedEffect started. Checking for existing Google Sign-in."
                        )
                        val existingAccount = GoogleDriveUtils.getExistingAccount(context)
                        signedInAccount = existingAccount

                        if (existingAccount != null) {
                            Log.i(
                                "MainActivity",
                                "Existing Google Sign-in found: ${existingAccount.email}"
                            )
                            if (AppGlobals.performAutoSync) {
                                Log.i("MainActivity", "Attempting sync on app launch...")
                                val syncManager = Sync(context)
                                try {
                                    syncManager.syncOnAppLaunch()
                                    Log.i("MainActivity", "Sync on app launch process completed.")
                                } catch (e: Exception) {
                                    Log.e("MainActivity", "Error during syncOnAppLaunch", e)
                                }

                                Log.i(
                                    "MainActivity",
                                    "Proceeding with automatic data export/backup to Drive."
                                )
                                try {
                                    GoogleDriveUtils.exportDataToCSVZip(
                                        context,
                                        Uri.EMPTY,
                                        existingAccount
                                    )
                                    Log.i(
                                        "MainActivity",
                                        "Automatic data export/backup to Drive initiated."
                                    )
                                } catch (e: Exception) {
                                    Log.e(
                                        "MainActivity",
                                        "Error during automatic data export/backup",
                                        e
                                    )
                                }
                            }
                        } else {
                            Log.i("MainActivity", "No existing Google Sign-in found on startup.")
                        }
                    }

                    HealthTrackerApp(
                        currentSignedInAccount = signedInAccount,
                        onSignedInAccountChange = onSignedInAccountChange
                    )
                }
            }
        }
    }
}

@Composable
fun HealthTrackerApp(
    currentSignedInAccount: GoogleSignInAccount?,
    onSignedInAccountChange: (GoogleSignInAccount?) -> Unit
) {
    // Get the list of items to display from AppGlobals
    val visibleNavItems = remember(AppGlobals.visibleBottomNavRoutes) {
        AppGlobals.getCurrentlyVisibleBottomNavItems()
    }

    // Default to the first visible item, or a specific core item if the list could be empty initially
    // (though AppGlobals defaults to all, so visibleNavItems should not be empty)
    val initialScreen = visibleNavItems.firstOrNull()
        ?: BottomNavItemInfo.AddData // Fallback to AddData if list is somehow empty

    var currentScreen by remember { mutableStateOf<BottomNavItemInfo>(initialScreen) }
    var openedDay by remember { mutableStateOf(AppGlobals.openedDay) }

    // This effect ensures that if the visibleNavItems change (e.g., user changes settings
    // and comes back), and the currentScreen is no longer in the visible list,
    // we reset currentScreen to a valid visible item.
    LaunchedEffect(visibleNavItems) {
        if (currentScreen !in visibleNavItems && visibleNavItems.isNotEmpty()) {
            currentScreen = visibleNavItems.first()
            Log.d(
                "HealthTrackerApp",
                "Current screen was hidden, defaulted to ${currentScreen.route}"
            )
        } else if (visibleNavItems.isEmpty()) {
            // Handle the edge case where no items are visible (should be prevented by AppGlobals logic)
            Log.e("HealthTrackerApp", "No visible bottom navigation items configured!")
            // Optionally, navigate to a default/error screen or show a message
        }
    }


    Log.d(
        "HealthTrackerApp",
        "Recomposing HealthTrackerApp: openedDay=$openedDay, currentScreen=${currentScreen.route}, visibleItems=${visibleNavItems.map { it.route }}"
    )

    Scaffold(
        bottomBar = {
            if (visibleNavItems.isNotEmpty()) { // Only show bottom bar if there are items to display
                NavigationBar {
                    visibleNavItems.forEach { itemInfo -> // Use itemInfo from AppGlobals
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = itemInfo.defaultIcon,
                                    contentDescription = itemInfo.defaultLabel
                                )
                            },
                            label = { Text(itemInfo.defaultLabel) },
                            selected = currentScreen.route == itemInfo.route, // Compare routes for selection
                            onClick = {
                                Log.d(
                                    "HealthTrackerApp",
                                    "Switching to screen: ${itemInfo.defaultLabel}"
                                )
                                currentScreen = itemInfo
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            DateNavigationBar(
                openedDay = openedDay,
                onDateChange = { newDate ->
                    openedDay = newDate
                    AppGlobals.openedDay = newDate
                },
                currentSignedInAccount = currentSignedInAccount,
                onSignedInAccountChange = onSignedInAccountChange
            )

            Box(modifier = Modifier.fillMaxSize()) {
                // Use currentScreen.route for when conditions
                when (currentScreen.route) {
                    BottomNavItemInfo.AddData.route -> HealthTrackerScreen(openedDay)
                    BottomNavItemInfo.Statistics.route -> StatisticsScreen(openedDay)
                    BottomNavItemInfo.Food.route -> FoodScreen(openedDay)
                    BottomNavItemInfo.Diet.route -> DietScreen(openedDay)
                    BottomNavItemInfo.Exercises.route -> ExercisesScreen(openedDay)
                    BottomNavItemInfo.MedicationTracking.route -> MedicationScreen(openedDay)
                    // Add cases for any other items you might have
                    else -> {
                        // Fallback screen if the route is somehow unknown
                        // This shouldn't happen if currentScreen is always set from visibleNavItems
                        Text("Screen not found for route: ${currentScreen.route}")
                        Log.e(
                            "HealthTrackerApp",
                            "Unknown route for currentScreen: ${currentScreen.route}"
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDatePickerDialog(
    onDateSelected: (LocalDate) -> Unit,
    onDismissRequest: () -> Unit
) {
    val datePickerState = rememberDatePickerState()
    var selectedDateMillis by remember { mutableLongStateOf(AppGlobals.getOpenedDayAsLocalDate().toEpochDay() * 86400000) }

    // Update selectedDateMillis when datePickerState.selectedDateMillis changes
    LaunchedEffect(datePickerState.selectedDateMillis) {
        if (datePickerState.selectedDateMillis != null) {
            selectedDateMillis = datePickerState.selectedDateMillis!!
        }
    }

    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            Button(onClick = {
                onDateSelected(LocalDate.ofEpochDay(selectedDateMillis / 86400000))
                onDismissRequest()
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            Button(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(
            state = datePickerState, // Use datePickerState directly
            title = { Text("Select Date") },
            modifier = Modifier.padding(16.dp),
            colors = DatePickerDefaults.colors()
        )
        // Trailing lambda removed
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateNavigationBar(
    openedDay: String,
    onDateChange: (String) -> Unit,
    currentSignedInAccount: GoogleSignInAccount?,
    onSignedInAccountChange: (GoogleSignInAccount?) -> Unit
) {
    var isDatePickerVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // val currentDay = AppGlobals.currentDay // Not strictly needed if using 'today' directly
    val today = AppGlobals.getCurrentDayAsLocalDate().toString()
    val yesterday = AppGlobals.getCurrentDayAsLocalDate().minusDays(1).toString()
    var showToast by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Check if the currently openedDay is already today
    val isAlreadyToday by remember(openedDay, today) {
        mutableStateOf(openedDay == today)
    }

    Log.d(
        "DateNavigation",
        "Recomposing DateNavigationBar: openedDay=$openedDay, isAlreadyToday=$isAlreadyToday"
    )

    if (isDatePickerVisible) {
        CustomDatePickerDialog(
            onDateSelected = { selectedDate ->
                if (selectedDate <= AppGlobals.getCurrentDayAsLocalDate()) {
                    onDateChange(selectedDate.toString())
                } else {
                    showToast = true
                }
            },
            onDismissRequest = { isDatePickerVisible = false }
        )
    }

    if (showToast) {
        Toast.makeText(context, "Cannot select a future date!", Toast.LENGTH_SHORT).show()
        showToast = false
    }

    if (showSettingsDialog) {
        SettingsDialog(
            onDismissRequest = { showSettingsDialog = false },
            currentSignedInAccount = currentSignedInAccount,
            onSignedInAccountChange = onSignedInAccountChange
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp), // Main row padding
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left Group: Settings and Previous Day
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // horizontalArrangement = Arrangement.Start // Default for Row
        ) {
            IconButton(
                onClick = { showSettingsDialog = true },
                // modifier = Modifier.padding(end = 8.dp) // Keep padding if desired
            ) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
            }
            Spacer(Modifier.width(4.dp)) // Reduced space
            IconButton(
                onClick = {
                    val currentDate =
                        AppGlobals.getOpenedDayAsLocalDate() // Use this consistent way
                    val previousDayDate = currentDate.minusDays(1)
                    onDateChange(previousDayDate.toString())
                    Log.d(
                        "DateNavigation",
                        "Changed to previous day: ${previousDayDate.toString()}"
                    )
                }
            ) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Previous Day")
            }
        }

        // Center: Clickable Date Text
        Text(
            text = when (openedDay) {
                yesterday -> "Yesterday"
                today -> "Today"
                else -> openedDay // Consider formatting this for better display if it's an arbitrary date
            },
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier
                .clickable { isDatePickerVisible = true }
                .padding(horizontal = 8.dp) // Padding for the text itself
        )

        // Right Group: Next Day and Jump to Today
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // horizontalArrangement = Arrangement.End // Default behavior if Row is at the end of SpaceBetween
        ) {
            IconButton(
                onClick = {
                    val currentDate = AppGlobals.getOpenedDayAsLocalDate()
                    val nextDayDate = currentDate.plusDays(1)
                    if (nextDayDate <= AppGlobals.getCurrentDayAsLocalDate()) {
                        onDateChange(nextDayDate.toString())
                        Log.d("DateNavigation", "Changed to next day: ${nextDayDate.toString()}")
                    } else {
                        Log.d("DateNavigation", "Cannot go beyond today.")
                        Toast.makeText(context, "Cannot go beyond today!", Toast.LENGTH_SHORT)
                            .show()
                    }
                },
                // Disable if openedDay is already today or the day before today (as next day would be today)
                // More accurately, disable if next day would be after today
                enabled = AppGlobals.getOpenedDayAsLocalDate()
                    .plusDays(1) <= AppGlobals.getCurrentDayAsLocalDate()

            ) {
                Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Next Day")
            }
            Spacer(Modifier.width(4.dp)) // Reduced space

            // ***** NEW "JUMP TO TODAY" BUTTON *****
            IconButton(
                onClick = {
                    if (!isAlreadyToday) { // Only call if not already on today
                        onDateChange(today) // 'today' is already defined as AppGlobals.getCurrentDayAsLocalDate().toString()
                        Log.d("DateNavigation", "Jumped to today: $today")
                    }
                },
                enabled = !isAlreadyToday // Disable the button if already on today's date
            ) {
                Icon(imageVector = Icons.Filled.Today, contentDescription = "Jump to Today")
            }
        }
    }
}

@Composable
fun HealthConnectDialog(
    onDismissRequest: () -> Unit
    // Consider adding: openedDayString: String = AppGlobals.openedDay
    // if this dialog might need to show data for a day other than the global default
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Obtain the ViewModel, ensuring applicationContext is passed
    val healthConnectViewModel: HealthConnectViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(HealthConnectViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    // Use applicationContext
                    return HealthConnectViewModel(context.applicationContext) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    )

    // Determine which day's data to fetch. For this dialog, using AppGlobals.openedDay seems appropriate.
    val dayToFetch = AppGlobals.openedDay

    // Activity Result Launcher for Health Connect permissions
    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissionsResultMap ->
            val allRequiredPermissionsGranted =
                healthConnectViewModel.permissions.all { permissionString ->
                    permissionsResultMap[permissionString] == true
                }
            if (allRequiredPermissionsGranted) {
                Log.d("HealthConnectDialog", "All Health Connect permissions granted via launcher.")
                healthConnectViewModel.permissionsGranted = true // Update ViewModel state
                coroutineScope.launch {
                    Log.d(
                        "HealthConnectDialog",
                        "Permissions granted, fetching data for day: $dayToFetch"
                    )
                    healthConnectViewModel.fetchDataForDay(dayToFetch)
                }
            } else {
                Log.w(
                    "HealthConnectDialog",
                    "Not all Health Connect permissions granted via launcher."
                )
                healthConnectViewModel.permissionsGranted = false // Update ViewModel state
                healthConnectViewModel.errorMessage = "Not all necessary Health Connect permissions were granted."
            }
        }
    )

    // LaunchedEffect to check HC availability, then permissions, and then fetch data on initial composition
    // or if healthConnectAvailable status changes.
    LaunchedEffect(key1 = healthConnectViewModel.healthConnectAvailable, key2 = dayToFetch) {
        if (!healthConnectViewModel.healthConnectAvailable) {
            // This is handled by the check in ViewModel init, but double-checking here is fine.
            // UI will update based on healthConnectViewModel.errorMessage if already set.
            Log.w(
                "HealthConnectDialog",
                "Health Connect not available (checked in LaunchedEffect)."
            )
            // No need to set errorMessage here again if ViewModel already does it.
            return@LaunchedEffect
        }

        Log.d(
            "HealthConnectDialog",
            "Health Connect is available. Checking permissions status for day: $dayToFetch."
        )

        if (healthConnectViewModel.permissionsGranted) {
            Log.d(
                "HealthConnectDialog",
                "Permissions already marked as granted in ViewModel. Fetching data for $dayToFetch."
            )
            healthConnectViewModel.fetchDataForDay(dayToFetch)
        } else {
            val previouslyGranted = healthConnectViewModel.healthConnectIntegrator.hasPermissions()
            if (previouslyGranted) {
                Log.d(
                    "HealthConnectDialog",
                    "Permissions previously granted (checked with integrator). Fetching data for $dayToFetch."
                )
                healthConnectViewModel.permissionsGranted = true
                healthConnectViewModel.fetchDataForDay(dayToFetch)
            } else {
                Log.d("HealthConnectDialog", "Permissions not granted. Requesting permissions.")
                if (healthConnectViewModel.permissions.isNotEmpty()) {
                    requestPermissionsLauncher.launch(healthConnectViewModel.permissions)
                } else {
                    Log.e(
                        "HealthConnectDialog",
                        "Permissions array in ViewModel is empty. Cannot request."
                    )
                    healthConnectViewModel.errorMessage =
                        "Could not request Health Connect permissions: configuration error."
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🔗 Health Connect Demo",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (healthConnectViewModel.isLoading) {
                        CircularProgressIndicator()
                        Text("Loading health data...", style = MaterialTheme.typography.bodyMedium)
                    } else if (healthConnectViewModel.errorMessage != null) {
                        Text(
                            text = "Error: ${healthConnectViewModel.errorMessage}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        if (!healthConnectViewModel.healthConnectAvailable) {
                            Button(onClick = { healthConnectViewModel.openHealthConnectSettings() }) {
                                Text("Install Health Connect App")
                            }
                        } else if (!healthConnectViewModel.permissionsGranted) {
                            Button(onClick = {
                                if (healthConnectViewModel.permissions.isNotEmpty()) {
                                    healthConnectViewModel.requestPermissions(
                                        requestPermissionsLauncher
                                    )
                                } else {
                                    Log.e(
                                        "HealthConnectDialog",
                                        "Permissions array in ViewModel is empty for Grant button."
                                    )
                                    // Optionally show a toast or update error message
                                }
                            }) {
                                Text("Grant Health Connect Permissions")
                            }
                        } else {
                            Button(onClick = {
                                coroutineScope.launch {
                                    healthConnectViewModel.fetchDataForDay(dayToFetch)
                                }
                            }) {
                                Text("Retry Data Fetch")
                            }
                        }
                    } else if (!healthConnectViewModel.healthConnectAvailable) {
                        Text(
                            text = "Health Connect is not available on this device.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { healthConnectViewModel.openHealthConnectSettings() }) {
                            Text("Install Health Connect App")
                        }
                    } else if (!healthConnectViewModel.permissionsGranted) {
                        Text(
                            text = "Permissions are required to read health data.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            if (healthConnectViewModel.permissions.isNotEmpty()) {
                                healthConnectViewModel.requestPermissions(requestPermissionsLauncher)
                            } else {
                                Log.e(
                                    "HealthConnectDialog",
                                    "Permissions array in ViewModel is empty for Grant button (main flow)."
                                )
                            }
                        }) {
                            Text("Grant Health Connect Permissions")
                        }
                    } else {
                        // Display fetched data
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            healthConnectViewModel.latestWeight?.let { weight ->
                                Text(
                                    text = "Latest Weight: ${String.format("%.2f", weight)} kg",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    textAlign = TextAlign.Center
                                )
                            } ?: Text(
                                text = "No recent weight data found.",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )

                            healthConnectViewModel.bmr?.let { bmr ->
                                Text(
                                    text = "Est. BMR: ${String.format("%.0f", bmr)} kcal/day",
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center
                                )
                            } ?: run {
                                if (healthConnectViewModel.latestWeight != null) {
                                    // Check if user profile data is available in AppGlobals for a more accurate message
                                    val profile = AppGlobals.userProfile
                                    val message =
                                        if (profile.dateOfBirth == null || profile.heightCm == null || profile.gender == Gender.OTHER) {
                                            "Cannot calculate BMR (missing profile data: age, height, or gender)."
                                        } else {
                                            "BMR data not available." // Generic if profile seems complete but BMR is null
                                        }
                                    Text(
                                        text = message,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            healthConnectViewModel.totalSteps?.let { steps ->
                                Text(
                                    text = "Steps Today: ${String.format("% d", steps)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center
                                )
                            }

                            healthConnectViewModel.activeCaloriesBurned?.let { calories ->
                                Text(
                                    text = "Active Calories: ${String.format("%.0f", calories)} kcal",
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center
                                )
                            }

                            healthConnectViewModel.totalSleepDuration?.let { sleep ->
                                Text(
                                    text = "Sleep Duration: ${sleep.toHours()}h ${sleep.toMinutes() % 60}m",
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center
                                )
                            }

                            if (healthConnectViewModel.latestWeight == null && healthConnectViewModel.totalSteps == null &&
                                healthConnectViewModel.activeCaloriesBurned == null && healthConnectViewModel.totalSleepDuration == null &&
                                healthConnectViewModel.errorMessage == null && healthConnectViewModel.healthConnectAvailable && healthConnectViewModel.permissionsGranted
                            ) {
                                Text(
                                    "No health data found for $dayToFetch in Health Connect.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }


                            Spacer(Modifier.height(8.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(onClick = {
                                    coroutineScope.launch {
                                        healthConnectViewModel.fetchDataForDay(dayToFetch)
                                    }
                                }) {
                                    Text("Refresh Data")
                                }

                                Button(onClick = { healthConnectViewModel.openHealthConnectSettings() }) {
                                    Text("Settings")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "This demonstrates integration with Health Connect to retrieve health metrics. Ensure the Health Connect app is installed and permissions are granted for accurate data.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text("Close")
                    }
                }
            }
        }
    }
}


private fun exportDataToCSVZip(context: Context, destinationUri: Uri) {
    Log.d("Export CSV", "exportDataToCSVZip function CALLED. Destination URI: $destinationUri")

    val filesDir = context.getExternalFilesDir(null) // Directory for CSV files
    val filesToZip = filesDir?.listFiles()

    if (filesToZip == null || filesToZip.isEmpty()) {
        Log.d("Export CSV", "No CSV files to export found in directory: ${filesDir?.absolutePath}")
        Toast.makeText(context, "No data to export.", Toast.LENGTH_SHORT).show()
        return
    }

    Log.d("Export CSV", "Found ${filesToZip.size} items in directory: ${filesDir.absolutePath}")

    // Use the utility function to get the UTC timestamp
    val timestamp = AppGlobals.getUtcTimestamp()
    val zipFileName = "mht-backup-$timestamp.zip"

    try {
        context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
            ZipOutputStream(outputStream).use { zipOutputStream ->
                filesToZip.forEach { file ->
                    Log.d("Export CSV", "Processing item: ${file.name}, isFile: ${file.isFile}")
                    if (file.isFile && file.name.endsWith(".csv")) {
                        Log.d("Export CSV", "Zipping CSV file: ${file.name}")
                        val entry = ZipEntry(file.name)
                        zipOutputStream.putNextEntry(entry)
                        try {
                            file.inputStream().use { inputStream ->
                                inputStream.copyTo(zipOutputStream)
                            }
                            zipOutputStream.closeEntry()
                            Log.d("Export CSV", "File zipped successfully: ${file.name}")
                        } catch (e: Exception) {
                            Log.e("Export CSV", "Error zipping file ${file.name}", e)
                        }
                    } else {
                        Log.d("Export CSV", "Skipping non-CSV file: ${file.name}")
                    }
                }
            }
        }
        Toast.makeText(context, "Data exported to CSV zip successfully! File: $zipFileName", Toast.LENGTH_SHORT).show()
        Log.d("Export CSV", "Data exported to zip: $destinationUri")

    } catch (e: Exception) {
        Log.e("Export CSV", "Error exporting data to CSV zip", e)
        Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}

// Data class for an individual medication item.
data class MedicationItem(
    val name: String,
    val dosage: Float,  // current dosage value
    val step: Float,    // dosage increment/decrement step
    val unit: String,   // e.g., "mg", "IU", "mg-eq"
    val isStarred: Boolean = false
)

@Composable
fun MedicationTrackingScreen() {
    // Define state for medication tracking
    var medicationName by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("") }
    var timeToTake by remember { mutableStateOf("") }

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Medication Tracking", style = MaterialTheme.typography.headlineMedium)

        // Input field for medication name
        TextInputField(
            text = medicationName,
            onTextChange = { medicationName = it },
            label = "Medication Name"
        )

        // Input field for dosage
        TextInputField(
            text = dosage,
            onTextChange = { dosage = it },
            label = "Dosage (e.g., 500 mg)"
        )

        // Input field for time to take medication
        TextInputField(
            text = timeToTake,
            onTextChange = { timeToTake = it },
            label = "Time to Take (e.g., 08:00 AM)"
        )

        Button(
            onClick = {
                Toast.makeText(
                    context,
                    "Medication added: $medicationName, $dosage at $timeToTake",
                    Toast.LENGTH_SHORT
                ).show()
                // TODO: Save medication data to the database
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Add Medication")
        }
    }
}

@Composable
fun TextInputField(
    text: String,
    onTextChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun BrowseDataScreen() {
    // TODO: Implement data browsing UI
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Browse Data Screen", style = MaterialTheme.typography.headlineMedium)
    }
}

enum class SymptomSource { ORIGINAL_HEALTH_DATA, MISCELLANEOUS_DB }

data class UnifiedSymptomUIItem(
    val id: String, // e.g., "malaise_original", "headache_misc"
    val name: String,
    var currentValue: Float,
    var isActive: Boolean, // Combined active state for UI purposes
    val yesterdayValue: Float?,
    var wasActiveYesterday: Boolean, // If it was active the previous day (for pinning)
    val source: SymptomSource,
    // Specific to ORIGINAL_HEALTH_DATA source for easy mapping back
    val originalSymptomKey: String? = null, // e.g., "malaise", "soreThroat", "lymphadenopathy"
    // Optional: if different symptoms have different slider characteristics
    val valueRange: ClosedFloatingPointRange<Float> = 0f..4f,
    val stepLabels: List<String> = listOf("None", "Very Mild", "Mild", "Moderate", "Severe")
)
@Composable
fun UnifiedSymptomRow(
    item: UnifiedSymptomUIItem,
    onValueChange: (newValue: Float) -> Unit,
    onActiveChange: (isActive: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    // Optional: if you want to show a visual cue for items that were active yesterday
    // showWasActiveYesterdayMarker: Boolean = false
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp) // Spacing between rows
    ) {
        // Row for Symptom Name and optional marker
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium, // Or your preferred style
                // Optionally change color if not active, though checkbox and disabled slider convey this
                // color = if (item.isActive) MaterialTheme.colorScheme.onSurface
                // else MaterialTheme.colorScheme.onSurface.copy(alpha = ContentAlpha.disabled),
                modifier = Modifier.weight(1f) // Allow name to take available space
            )
            // if (showWasActiveYesterdayMarker && item.wasActiveYesterday) {
            //     Icon(
            //         imageVector = Icons.Filled.PushPin, // Example Icon
            //         contentDescription = "Active yesterday",
            //         tint = MaterialTheme.colorScheme.secondary,
            //         modifier = Modifier.size(16.dp)
            //     )
            // }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Row for Checkbox and Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isActive,
                onCheckedChange = { newActiveState ->
                    onActiveChange(newActiveState)
                }
            )
            Spacer(modifier = Modifier.width(8.dp)) // Space between checkbox and slider

            Box(modifier = Modifier.weight(1f)) { // Slider takes remaining space
                NewSliderInput(
                    // The 'label' for NewSliderInput is for the value text (e.g., "Mild")
                    // The main symptom name is handled by the Text composable above.
                    label = item.name, // Or pass "" if NewSliderInput doesn't need its own title
                    value = item.currentValue,
                    valueRange = item.valueRange, // Use from UnifiedSymptomUIItem
                    steps = item.stepLabels.size - 2, // If steps is 0-based index for N labels
                    labels = item.stepLabels,     // Use from UnifiedSymptomUIItem
                    yesterdayValue = item.yesterdayValue,
                    enabled = item.isActive, // Slider is enabled based on the item's active state
                    onValueChange = { newValue ->
                        onValueChange(newValue)
                    }
                )
            }
        }
    }
}

@Composable
fun HealthTrackerScreen(openedDay: String) {
    val context = LocalContext.current
    // Assuming your DB helpers are initialized here
    val dbHelper = remember { HealthDatabaseHelper(context) }
    val appGlobals = AppGlobals
    val miscellaneousDbHelper = remember { MiscellaneousDatabaseHelper(context, appGlobals) }
    val coroutineScope = rememberCoroutineScope() // For launching save operations

    // --- Unified Symptom List for UI ---
    var unifiedSymptomsUIList by remember(openedDay) { mutableStateOf<List<UnifiedSymptomUIItem>>(emptyList()) }
    var isLoading by remember(openedDay) { mutableStateOf(true) }

    // --- Data Loaded Directly From DB ---
    // For the "original" database (HealthData)
    var healthDataFromDb by remember(openedDay) {
        mutableStateOf(
            HealthData(openedDay, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, "")
        )
    }
    var yesterdayHealthDataFromDb by remember(openedDay) { mutableStateOf<HealthData?>(null) }

    // For the "miscellaneous" database (list of TrackerItem)
    // miscellaneousItemsFromDb will be populated by your MiscellaneousDatabaseHelper.fetchMiscellaneousItems(date)
    // which you confirmed returns List<TrackerItem>
    var miscellaneousItemsFromDb by remember(openedDay) { mutableStateOf<List<TrackerItem>>(emptyList()) }
    var yesterdayMiscellaneousItemsFromDb by remember(openedDay) { mutableStateOf<List<TrackerItem>>(emptyList()) }
    var showManageSymptomsDialog by remember { mutableStateOf(false) } // <<< ADD THIS STATE


    // --- UI States for Non-Symptom Fields (managed separately as before) ---
    // These will be initialized from healthDataFromDb in the new loading LaunchedEffect
    var externalUISliderValues by remember(openedDay) { mutableStateOf(listOf(0f, 0f, 0f)) }
    var mentalUISliderValues by remember(openedDay) { mutableStateOf(listOf(0f, 0f)) }
    var sleepUISliderValues by remember(openedDay) { mutableStateOf(listOf(0f, 0f, 0f)) }
    var notesUI by remember(openedDay) { mutableStateOf("") }

    val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }

    // --- Labels for non-symptom sections (kept as they don't change) ---
    val externalLabels = listOf("Exercise Level", "Stress Level", "Illness Impact")
    val mentalLabels = listOf("Depression", "Hopelessness")
    val sleepLabels = listOf("Sleep quality", "Sleep readiness", "Sleep length")

    val scrollState = rememberScrollState() // Keep your scroll state

    // --- Definitions for Original Symptoms (used in LaunchedEffect and potentially save logic) ---
    val originalSymptomKeys = listOf("Malaise", "Sore Throat", "Lymphadenopathy")
    var authoritativeSleepDurationHours by remember { mutableStateOf<Float?>(null) }
    var isLoadingSleepData by remember { mutableStateOf(false) } // To show loading state
    val healthConnectIntegrator = remember { HealthConnectIntegrator(context.applicationContext) }
    var authoritativeStepCount by remember { mutableStateOf<Long?>(null) }
    var isLoadingStepsData by remember { mutableStateOf(false) }

    LaunchedEffect(
        key1 = openedDay,
        appGlobals.userDefinedSymptomNames
    ) { // Re-fetch if date changes
        isLoadingSleepData = true
        Log.d("SleepSectionUI", "Fetching authoritative sleep data via integrator for $openedDay")

        val durationFromResult = healthConnectIntegrator.getSleepDurationForDay(openedDay)
        val hoursFromResult = durationFromResult?.toMinutes()?.toFloat()?.div(60f)
        authoritativeSleepDurationHours = hoursFromResult
        isLoadingSleepData = false
        Log.d("SleepSectionUI", "Integrator result for $openedDay: $hoursFromResult hours")

        // Update the specific sleepUISliderValues for "Sleep length"
        // This ensures that when data is saved, it reflects what the integrator provided.
        val sleepLengthIndex = sleepLabels.indexOf("Sleep length")
        if (sleepLengthIndex != -1) {
            val newList = sleepUISliderValues.toMutableList()
            // Default to 0f if null, and coerce to the slider's valid range (0-12 hours)
            newList[sleepLengthIndex] = (hoursFromResult ?: 0f).coerceIn(0f, 12f)
            sleepUISliderValues = newList
            Log.d(
                "SleepSectionUI",
                "Updated sleepUISliderValues[$sleepLengthIndex] to ${newList[sleepLengthIndex]} based on integrator result."
            )
        }
        isLoadingStepsData = true
        Log.d("StepsSectionUI", "Fetching authoritative steps data via integrator for $openedDay")

        // Assuming getStepsForDay returns Long? (number of steps)
        val stepsFromResult: Long? = healthConnectIntegrator.getStepsForDay(openedDay)
        authoritativeStepCount = stepsFromResult
        isLoadingStepsData = false
        Log.d("StepsSectionUI", "Integrator result for steps on $openedDay: $stepsFromResult steps")
    }

    // --- Data Loading and Merging ---
    LaunchedEffect(openedDay) {
        isLoading = true
        val today = openedDay
        val yesterdayDateString: String? = try {
            LocalDate.parse(today, formatter).minusDays(1).format(formatter)
        } catch (e: Exception) {
            null
        }

        // 1. Fetch Today's Data
        val fetchedHealthData = dbHelper.fetchHealthDataForDate(today)
            ?: HealthData(today, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, "")
        healthDataFromDb = fetchedHealthData // Update state

        val fetchedMiscellaneousItems = miscellaneousDbHelper.fetchMiscellaneousItemsForDate(today)
        miscellaneousItemsFromDb = fetchedMiscellaneousItems

        // 2. Fetch Yesterday's Data
        yesterdayHealthDataFromDb = yesterdayDateString?.let { dbHelper.fetchHealthDataForDate(it) }
        yesterdayMiscellaneousItemsFromDb =
            yesterdayDateString?.let { miscellaneousDbHelper.fetchMiscellaneousItemsForDate(it) }
                ?: emptyList()

        // 3. Initialize Non-Symptom UI States from Today's Fetched HealthData
        externalUISliderValues = listOf(fetchedHealthData.exerciseLevel, fetchedHealthData.stressLevel, fetchedHealthData.illnessImpact)
        mentalUISliderValues = listOf(fetchedHealthData.depression, fetchedHealthData.hopelessness)
        sleepUISliderValues = listOf(fetchedHealthData.sleepQuality, fetchedHealthData.sleepLength, fetchedHealthData.sleepReadiness)
        notesUI = fetchedHealthData.notes

        // 4. Build Unified Symptoms List
        val tempUnifiedList = mutableListOf<UnifiedSymptomUIItem>()

        // 4a. Process Original Symptoms (Malaise, Sore Throat, Lymphadenopathy)
        originalSymptomKeys.forEach { key ->
            val currentValue = when (key) {
                "Malaise" -> fetchedHealthData.malaise
                "Sore Throat" -> fetchedHealthData.soreThroat
                "Lymphadenopathy" -> fetchedHealthData.lymphadenopathy
                else -> 0f // Should not happen
            }
            val yesterdayValue = when (key) {
                "Malaise" -> yesterdayHealthDataFromDb?.malaise
                "Sore Throat" -> yesterdayHealthDataFromDb?.soreThroat
                "Lymphadenopathy" -> yesterdayHealthDataFromDb?.lymphadenopathy
                else -> null
            }

            tempUnifiedList.add(
                UnifiedSymptomUIItem(
                    id = "${key.replace(" ", "_").toLowerCase()}_original", // e.g., "malaise_original"
                    name = key, // "Malaise", "Sore Throat", etc.
                    currentValue = currentValue,
                    isActive = currentValue > 0f,
                    yesterdayValue = yesterdayValue,
                    wasActiveYesterday = yesterdayValue != null && yesterdayValue > 0f,
                    source = SymptomSource.ORIGINAL_HEALTH_DATA,
                    originalSymptomKey = key // Store the original key for saving
                )
            )
        }

        // 4b. Process Miscellaneous Symptoms
        miscellaneousItemsFromDb.forEach { miscItem ->
            val yesterdayMiscItem = yesterdayMiscellaneousItemsFromDb.find { it.name == miscItem.name } // Match by name

            tempUnifiedList.add(
                UnifiedSymptomUIItem(
                    // Create a somewhat safe ID from the name for misc items
                    id = "${miscItem.name.replace(" ", "_").toLowerCase()}_misc",
                    name = miscItem.name,
                    currentValue = miscItem.value,
                    isActive = miscItem.isChecked, // Directly from misc item's state
                    yesterdayValue = yesterdayMiscItem?.value,
                    wasActiveYesterday = yesterdayMiscItem?.isChecked ?: false, // Directly from yesterday's misc item's state
                    source = SymptomSource.MISCELLANEOUS_DB,
                    originalSymptomKey = null // Not applicable
                    // Potentially add custom valueRange and stepLabels here if misc items can vary
                )
            )
        }

        // 5. Update the main UI list and loading state
        unifiedSymptomsUIList = tempUnifiedList.sortedWith(
            compareByDescending<UnifiedSymptomUIItem> { it.wasActiveYesterday }
                .thenBy { it.name }
        ) // Pinned items first, then alphabetical
        isLoading = false
    }

    LaunchedEffect(healthDataFromDb, externalUISliderValues, mentalUISliderValues, sleepUISliderValues, notesUI, openedDay) {
        // Avoid saving during initial load or if openedDay is not yet reflected in healthDataFromDb
        if (!isLoading && healthDataFromDb.currentDate == openedDay) {
            // Construct the final HealthData object to save
            // It uses healthDataFromDb for malaise, soreThroat, lymphadenopathy
            // and the specific UI states for other fields.
            val dataToSave = healthDataFromDb.copy(
                exerciseLevel = externalUISliderValues[0],
                stressLevel = externalUISliderValues[1],
                illnessImpact = externalUISliderValues[2],
                depression = mentalUISliderValues[0],
                hopelessness = mentalUISliderValues[1],
                sleepQuality = sleepUISliderValues[0],
                sleepLength = sleepUISliderValues[1],
                sleepReadiness = sleepUISliderValues[2],
                notes = notesUI
            )
            Log.d("MainSaveEffect", "Saving HealthData for $openedDay: $dataToSave")
            dbHelper.insertOrUpdateHealthData(dataToSave)
            dbHelper.exportToCSV(context) // Optional: Export after any change
        }
    }

    var miscellaneousExpanded by rememberSaveable(openedDay) { mutableStateOf(false) }
    fun saveUpdatedSymptom(updatedItem: UnifiedSymptomUIItem) {
        coroutineScope.launch { // Use the coroutineScope defined at the top
            when (updatedItem.source) {
                SymptomSource.ORIGINAL_HEALTH_DATA -> {
                    // Update the specific field in healthDataFromDb
                    // This will trigger the LaunchedEffect below to save the whole HealthData object
                    val currentHealthData = healthDataFromDb
                    val newHealthData = currentHealthData.copy(
                        malaise = if (updatedItem.originalSymptomKey == "Malaise") updatedItem.currentValue else currentHealthData.malaise,
                        soreThroat = if (updatedItem.originalSymptomKey == "Sore Throat") updatedItem.currentValue else currentHealthData.soreThroat,
                        lymphadenopathy = if (updatedItem.originalSymptomKey == "Lymphadenopathy") updatedItem.currentValue else currentHealthData.lymphadenopathy
                        // Other fields (externals, mental, etc.) are taken from currentHealthData
                        // and will be updated by their own UI state changes triggering the main save effect.
                    )
                    healthDataFromDb = newHealthData // Update the state
                    // The main LaunchedEffect for HealthData will handle the actual DB write
                }
                SymptomSource.MISCELLANEOUS_DB -> {
                    // Convert UnifiedSymptomUIItem back to TrackerItem
                    val trackerItemToSave = TrackerItem(
                        name = updatedItem.name, // Assuming name is the key for misc items
                        value = updatedItem.currentValue,
                        isChecked = updatedItem.isActive
                    )
                    // Update the miscellaneousItemsFromDb list state
                    val updatedMiscList = miscellaneousItemsFromDb.map {
                        if (it.name == trackerItemToSave.name) trackerItemToSave else it
                    }.toMutableList()

                    // If the item wasn't in the list (e.g., a default item that was just activated)
                    if (updatedMiscList.none { it.name == trackerItemToSave.name}) {
                        // This case should ideally not happen if defaultMiscellaneousItems includes all potential items
                        // or if items are only ever modified, not added dynamically via this screen.
                        // For safety, you could add it:
                        // updatedMiscList.add(trackerItemToSave)
                        Log.w("SaveMisc", "Attempted to save a new misc item not in original list: ${trackerItemToSave.name}")
                    }

                    miscellaneousItemsFromDb = updatedMiscList
                    miscellaneousDbHelper.insertOrUpdateMiscellaneousItems(openedDay, miscellaneousItemsFromDb) // Save the whole list
                    miscellaneousDbHelper.exportToCSV() // Optional: Export after change
                }
            }
        }
    }

    fun handleSymptomUpdate(itemId: String, newValue: Float?, newActiveState: Boolean?) {
        val updatedList = unifiedSymptomsUIList.map { item ->
            if (item.id == itemId) {
                var updatedItem = item.copy() // Start with a copy of the current item

                // Apply changes based on what was passed (newValue or newActiveState)
                if (newActiveState != null) {
                    updatedItem = updatedItem.copy(isActive = newActiveState)
                    if (!newActiveState) {
                        // If deactivated, always set value to 0
                        updatedItem = updatedItem.copy(currentValue = 0f)
                    } else {
                        // If activated AND current value is 0, set to a default (e.g., 1f)
                        // This makes the slider visibly active.
                        if (updatedItem.currentValue == 0f) {
                            updatedItem = updatedItem.copy(currentValue = 1f)
                        }
                    }
                }

                if (newValue != null) {
                    updatedItem = updatedItem.copy(currentValue = newValue)
                    if (newValue == 0f) {
                        // If slider dragged to 0, deactivate
                        updatedItem = updatedItem.copy(isActive = false)
                    } else if (newValue > 0f && !updatedItem.isActive) {
                        // If slider dragged > 0 and was not active, activate
                        updatedItem = updatedItem.copy(isActive = true)
                    }
                }
                // TODO 10: Call save function here with updatedItem
                saveUpdatedSymptom(updatedItem)
                Log.d("SymptomUpdate", "Updated: ${updatedItem.name}, Active: ${updatedItem.isActive}, Value: ${updatedItem.currentValue}") // For testing
                updatedItem // Return the modified item
            } else {
                item // Return unmodified item
            }
        }
        unifiedSymptomsUIList = updatedList // Update the state list
    }

    // --- Main Layout ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(scrollState), // Ensure scrollState is defined
        verticalArrangement = Arrangement.spacedBy(5.dp) // Overall spacing for sections
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            // --- TODO 7: Display Pinned Symptoms ---
            val pinnedSymptoms = remember(unifiedSymptomsUIList) {
                unifiedSymptomsUIList.filter { it.wasActiveYesterday }
            }

            Row(
                modifier = Modifier.fillMaxWidth(), // Allow Row to span width for arrangement
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween // Option 1: Pushes icon to far right
            ) {
                Text("Symptoms", style = MaterialTheme.typography.titleLarge)
                // Option 2: Fixed space if not using SpaceBetween
                Spacer(Modifier.width(8.dp)) // This spacer now works correctly inside the Row
                IconButton(
                    onClick = {
                        showManageSymptomsDialog = true
                        Log.d("SymptomsEdit", "Edit symptoms icon clicked - dialog should open")
                        // TODO: Set state for dialog
                    },
                    modifier = Modifier.size(48.dp) // IconButton itself
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit Symptoms List",
                        // modifier = Modifier.size(24.dp) // You can also size the Icon within the IconButton
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp)) // Space between title and first item


            if (pinnedSymptoms.isNotEmpty()) {
                pinnedSymptoms.forEach { item ->
                    UnifiedSymptomRow(
                        item = item,
                        onValueChange = { newValue ->
                            handleSymptomUpdate(item.id, newValue = newValue, newActiveState = null)
                        },
                        onActiveChange = { newActiveState ->
                            handleSymptomUpdate(item.id, newValue = null, newActiveState = newActiveState)
                        }
                    )
                    // No need for an explicit Divider here if UnifiedSymptomRow has padding
                }
                Spacer(modifier = Modifier.height(8.dp)) // Space after the pinned symptoms section
            }
            // --- End of TODO 7 ---

            // TODO 8: Create and Display "Track Other Symptoms" Expandable Section will go here

            AppUiElements.CollapsibleCard(
                titleContent = {
                    Text("📊  Browse All Symptoms", style = MaterialTheme.typography.titleMedium) // Match style if needed
                },
                expanded = miscellaneousExpanded,
                onExpandedChange = { isNowExpanded -> miscellaneousExpanded = isNowExpanded },
                isExpandable = true, // Explicitly true

                // Option 1: No default content shown when expanded (simplest for this case)
                defaultContent = { /* Can be an empty composable if nothing to show by default */ },
                hideDefaultWhenExpanded = true, // Hide the (empty) default content when expanded

                // Option 2: If you wanted defaultContent to show something even when collapsed,
                // and then be replaced by expandableContent when expanded:
                // defaultContent = {
                //    Text("Click to browse all available symptom trackers.") // Example
                // },
                // hideDefaultWhenExpanded = true, // Or false if you want default to persist above expandable

                expandableContent = {
                    Column { // This is the direct content for the expanded state
                        if (unifiedSymptomsUIList.isEmpty()) {
                            Text(
                                "No symptoms configured or loaded.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            unifiedSymptomsUIList.sortedBy { it.name }.forEach { item ->
                                UnifiedSymptomRow(
                                    item = item,
                                    onValueChange = { newValue ->
                                        handleSymptomUpdate(item.id, newValue = newValue, newActiveState = null)
                                    },
                                    onActiveChange = { newActiveState ->
                                        handleSymptomUpdate(item.id, newValue = null, newActiveState = newActiveState)
                                    }
                                )
                                // If you need dividers BETWEEN items in this list, add them here:
                                /*if (item != unifiedSymptomsUIList.last()) { // Avoid divider after the last item
                                    Divider(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) // Example padding
                                }*/
                            }
                        }
                    }
                },
                // Optional: Adjust cardPadding if the default doesn't match your old ExpandableSection's look
                // cardPadding = PaddingValues(all = 12.dp)
            )
            Spacer(modifier = Modifier.height(16.dp)) // Space after the expandable section

            // --- Externals Section ---
            Text("Externals", style = MaterialTheme.typography.titleLarge)
            // Then, in your UI where you want to display it:
            ValueTile(
                title = "Steps Today",
                icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                iconContentDescription = "Steps icon",
                isLoading = isLoadingStepsData, // Use specific loading flag
                valueString = authoritativeStepCount?.let { NumberFormat.getInstance().format(it) }
            )
            Spacer(modifier = Modifier.height(8.dp))

            externalLabels.forEachIndexed { index, labelString ->
                Text(
                    text = labelString,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                val standardValueRange = 0f..4f
                val standardStepLabels = listOf("None", "Very Low", "Low", "Medium", "High")

                // *** ADDED: Get yesterday's value for this external item ***
                val yesterdayExtValue = when (index) {
                    0 -> yesterdayHealthDataFromDb?.exerciseLevel
                    1 -> yesterdayHealthDataFromDb?.stressLevel
                    2 -> yesterdayHealthDataFromDb?.illnessImpact
                    else -> null
                }

                NewSliderInput(
                    label = "",
                    value = externalUISliderValues[index],
                    valueRange = standardValueRange,
                    steps = standardStepLabels.size - 2,
                    labels = standardStepLabels,
                    // *** ADDED: Pass yesterday's value ***
                    yesterdayValue = yesterdayExtValue,
                    enabled = true,
                    onValueChange = { newValue ->
                        val newList = externalUISliderValues.toMutableList()
                        newList[index] = newValue
                        externalUISliderValues = newList
                    }
                )
                if (index < externalLabels.size - 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // --- Mental Health Section ---
            Text("Mental Health", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            mentalLabels.forEachIndexed { index, labelString ->
                Text(
                    text = labelString,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                val standardValueRange = 0f..4f
                val standardStepLabels = listOf("None", "Very Low", "Low", "Moderate", "Severe")

                // *** ADDED: Get yesterday's value for this mental health item ***
                val yesterdayMentalValue = when (index) {
                    0 -> yesterdayHealthDataFromDb?.depression
                    1 -> yesterdayHealthDataFromDb?.hopelessness
                    else -> null
                }

                NewSliderInput(
                    label = "",
                    value = mentalUISliderValues[index],
                    valueRange = standardValueRange,
                    steps = standardStepLabels.size - 2,
                    labels = standardStepLabels,
                    // *** ADDED: Pass yesterday's value ***
                    yesterdayValue = yesterdayMentalValue,
                    enabled = true,
                    onValueChange = { newValue ->
                        val newList = mentalUISliderValues.toMutableList()
                        newList[index] = newValue
                        mentalUISliderValues = newList
                    }
                )
                if (index < mentalLabels.size - 1) {
                    Spacer(modifier = Modifier.height(2.dp)) // Your custom spacing here
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // --- Sleep Section ---
            Text("Sleep", style = MaterialTheme.typography.titleLarge)
            ValueTile(
                title = "Sleep Length Today", // This is correct for the title
                icon = Icons.Filled.Bedtime,
                iconContentDescription = "Sleep icon",
                isLoading = isLoadingSleepData,
                valueString = authoritativeSleepDurationHours?.let { hours -> // <<< USE authoritativeSleepDurationHours
                    "%.1f hrs".format(Locale.US, hours) // <<< CORRECT FORMATTING FOR HOURS
                } // ?: "N/A" // Add this if you want "N/A" when authoritativeSleepDurationHours is null
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (isLoadingSleepData) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Loading sleep data...")
                }
            } else {
                sleepLabels.forEachIndexed { index, labelString ->
                    // Define Text, valueRange, and stepLabelsList as before
                    Text(
                        text = labelString,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    val valueRange = if (labelString == "Sleep length") 0f..12f else 0f..4f
                    val stepLabelsList = if (labelString == "Reported value: ") {
                        (0..12).map { "$it hrs" }
                    } else {
                        listOf("Very Poor", "Poor", "Okay", "Good", "Excellent")
                    }

                    val isSleepLengthField = labelString == "Sleep length"

                    // *** CORRECTED: Define yesterdaySleepValue INSIDE the loop ***
                    val yesterdaySleepValue =
                        when (labelString) { // Switched to labelString for clarity, index also works
                            "Sleep quality" -> yesterdayHealthDataFromDb?.sleepQuality // Assuming this is 0-4
                            "Sleep length" -> yesterdayHealthDataFromDb?.sleepLength?.let {
                                Duration.ofMillis(
                                    it.toLong()
                                ).toMinutes().toFloat() / 60f
                            }

                            "Sleep readiness" -> yesterdayHealthDataFromDb?.sleepReadiness // Assuming this is 0-4
                            else -> null
                        }?.coerceIn(
                            valueRange.start,
                            valueRange.endInclusive
                        ) // Coerce AFTER potential conversion

                    if (isSleepLengthField) {
                        // "Sleep length" is ALWAYS reported.
                        // Main label for the metric:
                        // No changes to this Text needed, but ensure labelString is used if you remove the outer Text for sleep length name
                        // For consistency, if you always have a Text(labelString,...) above,
                        // this specific one might become redundant or could be more detailed.
                        // Sticking to your current structure:


                        NewSliderInput(
                            label = "", // External label is the Text above. Internal label (displayedLabelText) won't show when disabled.
                            value = (authoritativeSleepDurationHours ?: 0f).coerceIn(0f, 12f),
                            valueRange = valueRange, // 0f..12f
                            steps = 12, // For 0-12 hour labels
                            labels = stepLabelsList, // (0..12).map { "$it hrs" }
                            yesterdayValue = yesterdaySleepValue, // Now correctly defined
                            enabled = false, // ALWAYS disabled
                            onValueChange = { } // No-op
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    } else {
                        // Other sleep sliders (Quality, Readiness) ARE editable
                        // The Text(labelString, ...) above this block already serves as the title.
                        val currentSliderValue =
                            sleepUISliderValues.getOrElse(index) { valueRange.start }
                        NewSliderInput(
                            label = "", // Internal label (displayedLabelText) WILL show when enabled
                            value = currentSliderValue,
                            valueRange = valueRange,
                            steps = stepLabelsList.size - 2, // e.g., for 5 labels, steps = 3
                            labels = stepLabelsList,
                            yesterdayValue = yesterdaySleepValue, // Now correctly defined
                            enabled = true,
                            onValueChange = { newValue ->
                                val newList = sleepUISliderValues.toMutableList()
                                newList[index] = newValue
                                sleepUISliderValues = newList
                            }
                        )
                    }
                    if (index < sleepLabels.size - 1) {
                        // Spacer(modifier = Modifier.height(1.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp)) // Space AFTER the entire Sleep section

            // --- Notes Section ---
            Text("Notes", style = MaterialTheme.typography.titleLarge)
            //Spacer(modifier = Modifier.height(1.dp)) // Space between "Notes" title and TextField
            OutlinedTextField(
                value = notesUI,
                onValueChange = { notesUI = it },
                label = { Text("Daily Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            //Spacer(modifier = Modifier.height(16.dp)) // Space after Notes section
        }
    }
    if (showManageSymptomsDialog) {
        ManageSymptomsDialog(
            onDismissRequest = { showManageSymptomsDialog = false },
            appGlobals = AppGlobals, // Pass the AppGlobals singleton
            context = context
        )
    }
}

@Composable
fun ValueTile(
    title: String,
    icon: ImageVector,
    iconContentDescription: String,
    isLoading: Boolean,
    valueString: String?, // The formatted value to display (e.g., "10,532" or "7.5 hrs")
    modifier: Modifier = Modifier,
    valueStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineSmall, // Allow customization
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary // Allow customization
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = iconContentDescription,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary // Icon tint, can also be a parameter
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text(
                        text = valueString ?: "N/A",
                        style = valueStyle,
                        color = valueColor
                    )
                }
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ManageSymptomsDialog(
    onDismissRequest: () -> Unit,
    appGlobals: AppGlobals,
    context: Context
) {
    val userSymptoms by rememberUpdatedState(newValue = appGlobals.userDefinedSymptomNames)
    var newSymptomText by remember { mutableStateOf("") }
    var showDeleteConfirmationDialog by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Manage Symptoms") },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                Text(
                    "Add or remove your custom symptom trackers.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    OutlinedTextField(
                        value = newSymptomText,
                        onValueChange = { newSymptomText = it },
                        label = { Text("New symptom name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newSymptomText.isNotBlank()) {
                                appGlobals.addUserDefinedSymptom(context, newSymptomText.trim())
                                newSymptomText = ""
                            }
                        },
                        enabled = newSymptomText.isNotBlank()
                    ) {
                        Text("Add")
                    }
                }

                if (userSymptoms.isEmpty()) {
                    Text(
                        "No custom symptoms defined yet.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                } else {
                    Text(
                        "Your Symptoms:",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp) // Added padding
                    )

                    Spacer(Modifier.height(8.dp)) // Space before FlowRow
                    FlowRow(
                        // Experimental API, ensure you have the correct import
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()), // Scroll if content overflows
                    ) {
                        userSymptoms.forEach { symptomName ->
                            InputChip(
                                selected = false,
                                onClick = {
                                    Log.d(
                                        "ManageSymptoms",
                                        "Chip '$symptomName' clicked"
                                    )
                                },
                                label = { Text(symptomName) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Remove $symptomName",
                                        modifier = Modifier
                                            .size(InputChipDefaults.IconSize)
                                            .clickable {
                                                showDeleteConfirmationDialog = symptomName
                                            }
                                    )
                                }
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text("Done") }
        },
        dismissButton = null
    )

    if (showDeleteConfirmationDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmationDialog = null },
            title = { Text("Confirm Delete") },
            text = { Text("Are you sure you want to delete the symptom \"$showDeleteConfirmationDialog\"? The data will persist in the database and can be retrieved by adding the symptom back under the exact same name.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmationDialog?.let { nameToDelete ->
                            appGlobals.deleteUserDefinedSymptom(context, nameToDelete)
                        }
                        showDeleteConfirmationDialog = null
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmationDialog = null }) { Text("Cancel") }
            }
        )
    }
}


@Composable
fun ExpandableSection(
    title: String,
    expanded: Boolean,
    onExpand: () -> Unit,
    content: @Composable () -> Unit,
    contentPadding: PaddingValues = PaddingValues(all = 12.dp)
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(contentPadding)
            .animateContentSize(animationSpec = tween(durationMillis = 300, easing = LinearEasing))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpand() } // Make the whole row clickable to toggle
                .padding(vertical = 4.dp) // Add some padding for click area
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand"
            )
        }
        if (expanded) {
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            content()
        }
    }
}

@Composable
fun NewSliderInput(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int, // Still unused with steps = 0 in Slider
    labels: List<String>,
    yesterdayValue: Float? = null,
    enabled: Boolean = true, // This is the crucial part
    onValueChange: (Float) -> Unit,
    // Optional: Add a parameter to explicitly hide when not enabled
    // showWhenDisabled: Boolean = false
) {
    val nearestIndex = value.roundToInt().coerceIn(0, labels.size - 1)
    val displayedLabelText = labels.getOrNull(nearestIndex) ?: ""

    val fraction = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    val yesterdayFraction = yesterdayValue?.let {
        (it - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    }

    var sliderWidth by remember { mutableStateOf(0) }
    var labelWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    // Decide if the entire Column should be hidden or just its contents disabled
    // If you want to hide it completely when not enabled (and not showWhenDisabled):
    // if (!enabled && !showWhenDisabled) {
    //     Spacer(modifier = Modifier.height(0.dp)) // Effectively hides it
    //     return
    // }

    Column(
        modifier = Modifier.fillMaxWidth()
        // Optional: reduce opacity if not enabled
        // .alpha(if (enabled) 1f else 0.5f)
    ) {
        // The label for the item (e.g., "Malaise") is now typically outside, in the SliderGroup.
        // If you still want an internal label, ensure it also respects the `enabled` state.

        // Only show the Box with Slider and markers if enabled, or if you want to show a disabled state
        // if (enabled || showWhenDisabled) { // Or simply rely on the Slider's enabled state
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    sliderWidth = coordinates.size.width
                }
        ) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = 0,
                enabled = enabled, // Correctly passed
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            )

            if (enabled) { // Only show markers and current value label if enabled
                val thumbX = fraction * sliderWidth.toFloat()
                val yesterdayX = yesterdayFraction?.times(sliderWidth.toFloat())
                val hideYesterdayMarker = yesterdayX != null &&
                        abs(thumbX - yesterdayX) < with(density) { 10.dp.toPx() }

                if (yesterdayFraction != null && !hideYesterdayMarker) {
                    Box(
                        modifier = Modifier
                            .offset(
                                x = with(density) { (yesterdayFraction * sliderWidth - 6).toDp() },
                                y = 28.dp
                            )
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)) // No change needed if Slider handles disabled alpha
                            .size(12.dp)
                    )
                }

                if (displayedLabelText.isNotEmpty()) {
                    Text(
                        text = displayedLabelText,
                        style = MaterialTheme.typography.bodySmall,
                        // Color already handled by your existing logic based on 'enabled'
                        color = if (enabled) LocalContentColor.current else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier
                            .onGloballyPositioned { coordinates ->
                                labelWidth = coordinates.size.width
                            }
                            .offset(
                                x = with(density) { ((fraction * sliderWidth) - labelWidth / 2).toDp() },
                                y = 60.dp
                            )
                    )
                }
            } else {
                // Optional: Display "Not tracked" or similar if the slider is disabled
                // This would go inside the Box, positioned appropriately.
                // Text(
                //     text = "Not tracked",
                //     style = MaterialTheme.typography.bodySmall,
                //     color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                //     modifier = Modifier.align(Alignment.Center) // Example positioning
                // )
            }
        }
        // } // End of if (enabled || showWhenDisabled)
    }
}


// --- Slider Group Composables -----------------------------------------------------

@Composable
fun SymptomSliderGroup(
    symptomLabels: List<String>,
    symptomValues: List<Float>,
    symptomActiveStates: List<Boolean>,
    yesterdayValues: List<Float>? = null,
    onSymptomValueChange: (index: Int, newValue: Float) -> Unit,
    onSymptomActiveChange: (index: Int, isActive: Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) { // No verticalArrangement needed here as items space themselves
        symptomLabels.forEachIndexed { index, labelName ->
            val currentValue = symptomValues.getOrElse(index) { 0f }
            val isActive = symptomActiveStates.getOrElse(index) { false }

            // Container for each symptom item (Label + Checkbox/Slider row)
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)) { // Add some padding between items
                // 1. Symptom Name (Label for the whole item)
                Text(
                    text = labelName,
                    style = MaterialTheme.typography.titleSmall, // Or bodyMedium, bodyLarge etc.
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 4.dp) // Space between label and checkbox/slider
                )

                // 2. Row for Checkbox and Slider Input (or placeholder)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isActive,
                        onCheckedChange = { newActiveState ->
                            onSymptomActiveChange(index, newActiveState)
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    // NewSliderInput is now always present, its 'enabled' state controls its appearance
                    Box(modifier = Modifier.weight(1f)) {
                        NewSliderInput(
                            // The `label` param of NewSliderInput is for the value representation (e.g., "Mild")
                            // not the main item title.
                            label = "", // Or pass labelName if NewSliderInput uses it internally (e.g., for accessibility)
                            value = currentValue,
                            valueRange = 0f..4f,
                            steps = 3, // As per your original
                            labels = listOf("None", "Very Mild", "Mild", "Moderate", "Severe"),
                            yesterdayValue = yesterdayValues?.getOrNull(index),
                            enabled = isActive, // Pass the active state to control enabled/disabled appearance
                            onValueChange = { newValue ->
                                onSymptomValueChange(index, newValue)
                            }
                            // showWhenDisabled = true // If you had this parameter in NewSliderInput
                        )
                    }
                }
            }
            if (index < symptomLabels.size - 1) {
                // Divider(modifier = Modifier.padding(vertical = 8.dp)) // Optional: Divider between items
            }
        }
    }
}


@Composable
fun ExternalSliderGroup(
    items: List<Pair<String, Float>>,
    yesterdayValues: List<Float>? = null,
    onValuesChange: (List<Float>) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEachIndexed { index, item ->
            val labelList = when (item.first) {
                "Exercise Level" -> listOf("Bedbound", "Under 5 km", "Under 10 km", "Above 10 km", "Extraordinary")
                "Stress Level" -> listOf("Serene", "Calm", "Mild", "Moderate", "Severe")
                "Illness Impact" -> listOf("None", "Slight", "Noticeable", "Day-altering", "Extreme")
                else -> listOf("None", "Very Mild", "Mild", "Moderate", "Severe")
            }
            SliderInput(
                label = item.first,
                value = item.second,
                valueRange = 0f..4f,
                steps = 3,
                labels = labelList,
                yesterdayValue = yesterdayValues?.getOrNull(index),
                onValueChange = { newValue ->
                    val updatedValues = items.mapIndexed { i, pair ->
                        if (i == index) newValue else pair.second
                    }
                    onValuesChange(updatedValues)
                }
            )
        }
    }
}

@Composable
fun MentalSliderGroup(
    items: List<Pair<String, Float>>,
    yesterdayValues: List<Float>? = null,
    onValuesChange: (List<Float>) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEachIndexed { index, item ->
            SliderInput(
                label = item.first,
                value = item.second,
                valueRange = 0f..4f,
                steps = 3,
                labels = listOf("None", "Very Mild", "Mild", "Moderate", "Severe"),
                yesterdayValue = yesterdayValues?.getOrNull(index),
                onValueChange = { newValue ->
                    val updatedValues = items.mapIndexed { i, pair ->
                        if (i == index) newValue else pair.second
                    }
                    onValuesChange(updatedValues)
                }
            )
        }
    }
}

@Composable
fun SleepSliderGroup(
    items: List<Pair<String, Float>>,
    yesterdayValues: List<Float>? = null,
    onValuesChange: (List<Float>) -> Unit
) {
    // For Sleep, we want custom labels for each slider.
    // We'll assume the order of items is:
    // 0: Sleep quality, 1: Sleep length, 2: Sleep readiness.
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEachIndexed { index, item ->
            val labelList = when (item.first) {
                "Sleep quality" -> listOf("Insomnia", "Very poor", "Normal", "Solid", "Exceptional")
                "Sleep length" -> listOf("<6h", "6-7h", "7-8h", "8-9h", ">9h")
                "Sleep readiness" -> listOf("Horrible", "Poor", "Normal", "Ideal", "Exceptional")
                else -> listOf("", "", "", "", "")
            }
            SliderInput(
                label = item.first,
                value = item.second,
                valueRange = 0f..4f,
                steps = 3,
                labels = labelList,
                yesterdayValue = yesterdayValues?.getOrNull(index),
                onValueChange = { newValue ->
                    val updatedValues = items.mapIndexed { i, pair ->
                        if (i == index) newValue else pair.second
                    }
                    onValuesChange(updatedValues)
                }
            )
        }
    }
}

// --- Modified SliderInput with Yesterday Marker ------------------------------------

@Composable
fun SliderInput(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int, // Not used here since we use a continuous slider.
    labels: List<String>,
    yesterdayValue: Float? = null, // Optional yesterday value.
    onValueChange: (Float) -> Unit
) {
    // Compute the nearest anchor index for today's value.
    val nearestIndex = value.roundToInt().coerceIn(0, labels.size - 1)
    val displayedLabel = labels[nearestIndex]
    // Compute fraction for today's value.
    val fraction = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    // Compute fraction for yesterday's value (if available).
    val yesterdayFraction = yesterdayValue?.let {
        (it - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    }

    var sliderWidth by remember { mutableStateOf(0) }
    var labelWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    sliderWidth = coordinates.size.width
                }
        ) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = 0, // Continuous slider for high precision.
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            )
            // Compute the pixel positions.
            val thumbX = fraction * sliderWidth
            val yesterdayX = yesterdayFraction?.times(sliderWidth)
            val hideYesterdayMarker = yesterdayX != null &&
                    abs(thumbX - yesterdayX) < with(density) { 10.dp.toPx() }

            // If yesterday's value is provided and the thumb isn't too close, show a gray marker.
            if (yesterdayFraction != null && !hideYesterdayMarker) {
                Box(
                    modifier = Modifier
                        .offset(
                            x = with(density) { (yesterdayFraction * sliderWidth - 6).toDp() },
                            y = 28.dp // Adjusted vertical offset.
                        )
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        .size(12.dp)
                )
            }
            // The label text is measured so that its center aligns with the thumb.
            Text(
                text = displayedLabel,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .onGloballyPositioned { coordinates ->
                        labelWidth = coordinates.size.width
                    }
                    .offset(
                        x = with(density) { ((fraction * sliderWidth) - labelWidth / 2).toDp() },
                        y = 60.dp
                    )
            )
        }
    }
}

// --- TextInputField remains unchanged -------------------------------
@Composable
fun TextInputField(
    text: String,
    onTextChange: (String) -> Unit
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    )
}

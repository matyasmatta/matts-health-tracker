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
import androidx.compose.material.icons.Icons
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MattsHealthTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var openedDay by rememberSaveable { mutableStateOf(AppGlobals.currentDay) }
                    var signedInAccount by rememberSaveable { mutableStateOf<GoogleSignInAccount?>(null) }

                    // Function to update signedInAccount
                    val onSignedInAccountChange: (GoogleSignInAccount?) -> Unit = { account ->
                        signedInAccount = account
                    }

                    LaunchedEffect(Unit) {
                        signedInAccount = GoogleDriveUtils.getExistingAccount(context = this@MainActivity)
                        if (signedInAccount != null) {
                            Log.d("MainActivity", "Existing Google Sign-in found: ${signedInAccount?.email}")
                            // Automatically trigger backup:
                            GoogleDriveUtils.exportDataToCSVZip(this@MainActivity, Uri.EMPTY, signedInAccount)
                        } else {
                            Log.d("MainActivity", "No existing Google Sign-in found on startup.")
                        }
                    }

                    // Call HealthTrackerApp and pass necessary values
                    HealthTrackerApp(
                        currentSignedInAccount = signedInAccount,
                        onSignedInAccountChange = onSignedInAccountChange
                    )
                }
            }
        }
    }
}

sealed class BottomNavItem(val route: String, val label: String, val icon: ImageVector) {
    object AddData : BottomNavItem("add_data", "Tracking", Icons.Default.Add)
    object Statistics: BottomNavItem("statistics", "Statistics", Icons.Default.Star)
    object Exercises : BottomNavItem("exercises", "Exercises", Icons.Default.Refresh) // New tab
    object MedicationTracking : BottomNavItem("medication_tracking", "Medications", Icons.Default.Check) // New Screen
}

@Composable
fun HealthTrackerApp(
    currentSignedInAccount: GoogleSignInAccount?,
    onSignedInAccountChange: (GoogleSignInAccount?) -> Unit
) {
    var currentScreen by remember { mutableStateOf<BottomNavItem>(BottomNavItem.AddData) }
    var openedDay by remember { mutableStateOf(AppGlobals.openedDay) } // Single source of truth for openedDay

    Log.d("HealthTrackerApp", "Recomposing HealthTrackerApp: openedDay=$openedDay")

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf(
                    BottomNavItem.AddData,
                    BottomNavItem.Statistics,
                    BottomNavItem.Exercises,
                    BottomNavItem.MedicationTracking
                ).forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentScreen == item,
                        onClick = {
                            Log.d("HealthTrackerApp", "Switching to screen: ${item.label}")
                            currentScreen = item
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        // Main content area
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)) {

            // DateNavigationBar and SettingsDialog should be inside the Column and above the main content area
            DateNavigationBar(
                openedDay = openedDay,
                onDateChange = { newDate ->
                    openedDay = newDate
                    AppGlobals.openedDay = newDate // Update the global helper for consistency
                },
                currentSignedInAccount = currentSignedInAccount,
                onSignedInAccountChange = onSignedInAccountChange
            )

            // Content based on the currentScreen
            Box(modifier = Modifier.fillMaxSize()) {
                when (currentScreen) {
                    is BottomNavItem.AddData -> HealthTrackerScreen(openedDay)
                    is BottomNavItem.Statistics -> StatisticsScreen(openedDay)
                    is BottomNavItem.Exercises -> ExercisesScreen(openedDay)
                    is BottomNavItem.MedicationTracking -> MedicationScreen(openedDay)
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DateNavigationBar(
    openedDay: String,
    onDateChange: (String) -> Unit,
    currentSignedInAccount: GoogleSignInAccount?, // Add this parameter
    onSignedInAccountChange: (GoogleSignInAccount?) -> Unit // Add this parameter
) {
    var isDatePickerVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val currentDay = AppGlobals.currentDay
    val today = AppGlobals.getCurrentDayAsLocalDate().toString()
    val yesterday = AppGlobals.getCurrentDayAsLocalDate().minusDays(1).toString()
    var showToast by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    Log.d("DateNavigation", "Recomposing DateNavigationBar: openedDay=$openedDay")

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
            currentSignedInAccount = currentSignedInAccount, // Pass the parameter here!
            onSignedInAccountChange = onSignedInAccountChange // Pass the callback here!
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(
                onClick = {
                    showSettingsDialog = true
                },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
            }

            IconButton(
                onClick = {
                    val currentDate = AppGlobals.getOpenedDayAsLocalDate()
                    val previousDay = currentDate.minusDays(1)
                    val newDate = previousDay.toString()
                    onDateChange(newDate)
                    Log.d("DateNavigation", "Changed to previous day: $newDate")
                }
            ) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Previous Day")
            }
        }

        Text(
            text = when (openedDay) {
                yesterday -> "Yesterday"
                today -> "Today"
                else -> openedDay
            },
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier
                .clickable {
                    isDatePickerVisible = true
                }
                .padding(8.dp)
        )

        IconButton(
            onClick = {
                val currentDate = AppGlobals.getOpenedDayAsLocalDate()
                val nextDay = currentDate.plusDays(1)
                if (nextDay <= AppGlobals.getCurrentDayAsLocalDate()) {
                    val newDate = nextDay.toString()
                    onDateChange(newDate)
                    Log.d("DateNavigation", "Changed to next day: $newDate")
                } else {
                    Log.d("DateNavigation", "Cannot go beyond currentDay: $currentDay")
                    Toast.makeText(context, "Cannot go beyond today!", Toast.LENGTH_SHORT).show()
                }
            }
        ) {
            Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Next Day")
        }
    }
}

@Composable
fun SettingsDialog(
    onDismissRequest: () -> Unit,
    currentSignedInAccount: GoogleSignInAccount?, // Receive signedInAccount as parameter
    onSignedInAccountChange: (GoogleSignInAccount?) -> Unit // Receive callback to update signedInAccount
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val appPackageName = context.packageName
    val appVersion = BuildConfig.VERSION_NAME
    val githubLink = "https://github.com/matyasmatta/matts-health-tracker"

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? ->
        uri?.let { exportDataToCSVZip(context, it) }
    }

    var googleDriveSyncEnabled by remember { mutableStateOf(currentSignedInAccount != null) }
    // No longer manage signedInAccount state locally, use parameter:
    val signedInAccount = currentSignedInAccount // Use the parameter passed from MainActivity

    val signInLauncher = GoogleDriveUtils.rememberGoogleSignInLauncher { account ->
        onSignedInAccountChange(account) // Update signedInAccount state in MainActivity using callback
        if (account != null) {
            Toast.makeText(context, "Google Sign-in successful: ${account.email}", Toast.LENGTH_SHORT).show()
            Log.d("SettingsDialog", "Sign-in successful, Account: ${account.email}")
        } else {
            Toast.makeText(context, "Google Sign-in failed.", Toast.LENGTH_SHORT).show()
            Log.w("SettingsDialog", "Sign-in failed")
        }
    }

    val scope = rememberCoroutineScope() // <--- Create a coroutine scope

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = MaterialTheme.shapes.medium) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))

                Text("Data control", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))

                // Google Drive Sync Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Backup data to Drive", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = googleDriveSyncEnabled,
                        onCheckedChange = { isChecked ->
                            googleDriveSyncEnabled = isChecked
                            Log.d("SettingsDialog", "Google Drive Sync toggled: $isChecked")
                            if (isChecked) {
                                if (signedInAccount == null) { // Use the signedInAccount parameter (which reflects state from MainActivity)
                                    Log.d("SettingsDialog", "Initiating Google Sign-in...")
                                    scope.launch { // <--- Launch a coroutine here
                                        GoogleDriveUtils.signInToGoogleDrive(context, signInLauncher) // Call suspend function inside coroutine
                                    }
                                } else {
                                    Log.d("SettingsDialog", "Already signed in: ${signedInAccount?.email}")
                                    Toast.makeText(context, "Background backup to Drive started (TODO)", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Log.d("SettingsDialog", "Google Drive Backup disabled")
                                Toast.makeText(context, "Background sync disabled", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                Button(onClick = {
                    exportLauncher.launch("mht-backup-${AppGlobals.getUtcTimestamp()}.zip")
                }) {
                    Text("Export to CSV")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    onDismissRequest()
                }) {
                    Text("Import from CSV")
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("App info", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = appPackageName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                )
                Text("version: $appVersion", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "GitHub link",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.clickable { uriHandler.openUri(githubLink) }
                )

                Spacer(modifier = Modifier.height(24.dp))
                Text("Made with 💖 in 🇪🇺", style = MaterialTheme.typography.bodySmall)

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StatisticsScreen(openedDay: String) {
    val context = LocalContext.current
    val viewModel: StatisticsViewModel = viewModel(factory = StatisticsViewModelFactory(context))
    val healthData = viewModel.healthData.value
    val selectedTimeframe = viewModel.selectedTimeframe.value

    // Define all available metrics here:
    val allMetrics = listOf(
        "Malaise" to { it: HealthData -> it.malaise },
        "Stress Level" to { it: HealthData -> it.stressLevel },
        "Sleep Quality" to { it: HealthData -> it.sleepQuality },
        "Illness Impact" to { it: HealthData -> it.illnessImpact },
        "Depression" to { it: HealthData -> it.depression },
        "Hopelessness" to { it: HealthData -> it.hopelessness },
        "Sore Throat" to { it: HealthData -> it.soreThroat },
        "Sleep Length" to { it: HealthData -> it.sleepLength },
        "Lymphadenopathy" to { it: HealthData -> it.lymphadenopathy },
        "Exercise Level" to { it: HealthData -> it.exerciseLevel },
        "Sleep Readiness" to { it: HealthData -> it.sleepReadiness }
    )

    // Keep track of selected metrics in state, initialized with some defaults
    val selectedMetrics = remember { mutableStateListOf("Malaise", "Stress Level", "Sleep Quality") }

    // State to control expansion
    var showAllMetrics by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Health Statistics") }) }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            TimeframeSelector(selectedTimeframe) { viewModel.updateTimeframe(it) }
            Spacer(modifier = Modifier.height(16.dp))

            // --- AVERAGES SECTION ---
            if (healthData.isEmpty()) {
                EmptyDataInfo()
            } else {
                HealthMetricsSummary(healthData) // This is your averages section
                Spacer(modifier = Modifier.height(24.dp))
            }

            // --- TOGGLES SECTION (Now with Expand/Collapse) ---
            Column(modifier = Modifier.animateContentSize()) { // Apply animateContentSize here
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAllMetrics = !showAllMetrics } // Toggle on row click
                        .padding(vertical = 2.dp), // Add some padding for click area
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Select Metrics to Display", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { showAllMetrics = !showAllMetrics }) {
                        Icon(
                            imageVector = if (showAllMetrics) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (showAllMetrics) "Collapse metrics" else "Expand metrics"
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))

                // Conditionally display chips
                if (showAllMetrics) {
                    // Expanded view: Show all metrics in FlowRow
                    FlowRow {
                        allMetrics.forEach { (label, _) ->
                            FilterChip(
                                selected = selectedMetrics.contains(label),
                                onClick = {
                                    if (selectedMetrics.contains(label)) {
                                        selectedMetrics.remove(label)
                                    } else {
                                        selectedMetrics.add(label)
                                    }
                                },
                                label = { Text(label) },
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                } else {
                    // Collapsed view: Show only selected metrics (or a default few if none selected)
                    // Take up to 3 selected metrics, or fallback to first 3 allMetrics if selected is empty.
                    val displayedMetrics = if (selectedMetrics.isNotEmpty()) {
                        selectedMetrics.take(3)
                    } else {
                        allMetrics.map { it.first }.take(3) // Display first 3 if no selected metrics
                    }

                    FlowRow {
                        displayedMetrics.forEach { label ->
                            // Find the full metric object to pass to FilterChip
                            val metric = allMetrics.firstOrNull { it.first == label }
                            metric?.let {
                                FilterChip(
                                    selected = selectedMetrics.contains(label),
                                    onClick = {
                                        // On click, expand and then handle selection
                                        showAllMetrics = true
                                        if (selectedMetrics.contains(label)) {
                                            selectedMetrics.remove(label)
                                        } else {
                                            selectedMetrics.add(label)
                                        }
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        // Optionally add a "..." chip if more metrics are hidden
                        if (allMetrics.size > displayedMetrics.size && selectedMetrics.size > 3 || (selectedMetrics.isEmpty() && allMetrics.size > 3)) {
                            FilterChip(
                                selected = false,
                                onClick = { showAllMetrics = true },
                                label = { Text("...") },
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp)) // Space after toggles section

            // --- GRAPHS SECTION ---
            if (healthData.isNotEmpty()) {
                selectedMetrics.forEach { metricLabel ->
                    val extractor = allMetrics.firstOrNull { it.first == metricLabel }?.second
                    extractor?.let {
                        HealthLineChart(
                            chartTitle = metricLabel,
                            dataPoints = healthData.map(it),
                            labels = healthData.map { it.currentDate }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun EmptyDataInfo() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No data available for the selected timeframe.", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Start tracking your health to see statistics here!", style = MaterialTheme.typography.bodyMedium)
        Icon(Icons.Default.Addchart, contentDescription = "Information", modifier = Modifier.size(48.dp))
    }
}

@Composable
fun HealthMetricChart(title: String, dataPoints: List<Float>, labels: List<String>, chartTitle: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(8.dp))
    HealthLineChart(
        dataPoints = dataPoints,
        labels = labels,
        chartTitle = chartTitle
    )
    Spacer(modifier = Modifier.height(24.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeframeSelector(
    selectedTimeframe: Timeframe,
    onTimeframeSelected: (Timeframe) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Timeframe.entries.forEach { timeframe ->
            FilterChip(
                selected = selectedTimeframe == timeframe,
                onClick = { onTimeframeSelected(timeframe) },
                label = { Text(timeframe.label) },
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun HealthMetricsSummary(healthData: List<HealthData>) {
    fun List<Float>.avgOrZero() = average().toFloat().takeIf { !it.isNaN() } ?: 0f

    val stats = listOf(
        "Average Malaise:" to healthData.map { it.malaise }.avgOrZero(),
        "Average Sore Throat:" to healthData.map { it.soreThroat }.avgOrZero(),
        "Average Lymphadenopathy:" to healthData.map { it.lymphadenopathy }.avgOrZero(),
        "Average Exercise Level:" to healthData.map { it.exerciseLevel }.avgOrZero(),
        "Average Stress Level:" to healthData.map { it.stressLevel }.avgOrZero(),
        "Average Illness Impact:" to healthData.map { it.illnessImpact }.avgOrZero(),
        "Average Depression:" to healthData.map { it.depression }.avgOrZero(),
        "Average Hopelessness:" to healthData.map { it.hopelessness }.avgOrZero(),
        "Average Sleep Quality:" to healthData.map { it.sleepQuality }.avgOrZero(),
        "Average Sleep Length (hours):" to healthData.map { it.sleepLength }.avgOrZero(),
        "Average Sleep Readiness:" to healthData.map { it.sleepReadiness }.avgOrZero(),
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Summary Statistics", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            stats.forEach { (label, value) ->
                StatisticRow(label, String.format("%.1f", value))
            }
        }
    }
}

@Composable
fun StatisticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun calculatePointSpacing(count: Int): Dp {
    val maxSpacing = 14f  // max spacing for very few points
    val minSpacing = 2f   // min spacing for many points
    val maxCount = 180f   // above this, spacing stays at min

    val spacing = if (count >= maxCount) {
        minSpacing
    } else {
        maxSpacing - ((count / maxCount) * (maxSpacing - minSpacing))
    }

    return spacing.dp
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun HealthLineChart(
    dataPoints: List<Float>,
    labels: List<String>, // Expected ISO date strings "YYYY-MM-DD"
    chartTitle: String,
    modifier: Modifier = Modifier
) {
    // Shared scroll state to sync horizontal scroll between chart and labels
    val scrollState = rememberScrollState()

    if (dataPoints.isEmpty()) {
        Text("No data to display for $chartTitle.", style = MaterialTheme.typography.bodySmall)
        return
    }

    val pointSpacing = calculatePointSpacing(dataPoints.size)
    val totalWidth = (dataPoints.size - 1) * pointSpacing.value
    val chartContentWidth = totalWidth.dp
    val canvasWidthModifier = if (dataPoints.size > 5) Modifier.width(chartContentWidth) else Modifier.fillMaxWidth()

    val minVal = dataPoints.minOrNull() ?: 0f
    val maxVal = (dataPoints.maxOrNull() ?: 10f) + 0.1f
    val valueRange = maxVal - minVal
    val normalizedData = dataPoints.map { if (valueRange == 0f) 0.5f else (it - minVal) / valueRange }

    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) // Color for the weekly ticks

    // Parse dates from labels, skip invalid
    val dates = labels.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }

    // Indices of weekly ticks (Mondays only)
    val weeklyTickIndices = dates.mapIndexedNotNull { index, date ->
        if (date.dayOfWeek == DayOfWeek.MONDAY) index else null
    }

    // Indices of monthly labels (first day of month or first index)
    val monthlyLabelIndices = dates.mapIndexedNotNull { index, date ->
        if (date.dayOfMonth == 1 || (index == 0 && dates.isNotEmpty())) index else null
    }

    // Formatter for short month names (e.g., "Jan", "Feb")
    // Using Locale.getDefault() explicitly for consistent month formatting
    val monthFormatter = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .horizontalScroll(scrollState)
        ) {
            Canvas(
                modifier = canvasWidthModifier.fillMaxHeight()
            ) {
                val pointRadius = 6.dp.toPx()
                val lineWidth = 2.dp.toPx()
                val xStep = pointSpacing.toPx()
                val tickLength = 8.dp.toPx() // Length of the weekly tick marks

                // Draw Y-axis grid lines
                val numYLabels = 5
                for (i in 0 until numYLabels) {
                    val y = size.height - (i.toFloat() / (numYLabels - 1)) * size.height
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Draw data points and connecting lines
                val pathPoints = mutableListOf<Offset>()
                dataPoints.forEachIndexed { index, _ ->
                    val x = index * xStep
                    val y = size.height - normalizedData[index] * size.height
                    pathPoints.add(Offset(x, y))
                    drawCircle(primaryColor, radius = pointRadius, center = Offset(x, y))
                }

                for (i in 0 until pathPoints.size - 1) {
                    drawLine(
                        color = primaryColor,
                        start = pathPoints[i],
                        end = pathPoints[i + 1],
                        strokeWidth = lineWidth
                    )
                }

                // Draw Weekly Ticks (Mondays only)
                weeklyTickIndices.forEach { index ->
                    val x = index * xStep
                    drawLine(
                        color = tickColor,
                        start = Offset(x, size.height), // Start at the bottom of the canvas
                        end = Offset(x, size.height - tickLength), // Draw tick upwards
                        strokeWidth = 1.5.dp.toPx() // Thicker tick for visibility
                    )
                }

                // Draw Monthly Bars
                monthlyLabelIndices.forEach { index ->
                    val x = index * xStep
                    drawLine(
                        color = labelColor.copy(alpha = 0.7f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }

            }
        }

        Spacer(modifier = Modifier.height(8.dp)) // Space between chart and monthly labels

        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .then(canvasWidthModifier),
            horizontalArrangement = Arrangement.Start
        ) {
            for (i in labels.indices) {
                if (i in monthlyLabelIndices) {
                    val date = dates.getOrNull(i)
                    val rawText = date?.format(monthFormatter) ?: ""
                    val labelText = rawText.replaceFirstChar { it.uppercase() }

                    Box(
                        modifier = Modifier
                            .width(pointSpacing)
                            .padding(horizontal = 0.dp), // keep spacing minimal here
                        contentAlignment = Alignment.Center
                    ) {
                        BoxWithConstraints {
                            Text(
                                text = labelText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Visible,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .widthIn(min = pointSpacing * 1.8f) // Allow label to visually overflow
                                    .align(Alignment.Center)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(pointSpacing))
                }
            }
        }
    }
}

@Composable
fun ExercisesScreen(openedDay: String) {
    val context = LocalContext.current
    val exerciseDbHelper = remember { ExerciseDatabaseHelper(context) }
    val routineDbHelper = remember { RoutineDatabaseHelper(context) }
    DisposableEffect(Unit) {
        onDispose {
            exerciseDbHelper.close()
            routineDbHelper.close()
        }
    }

    val exerciseData = exerciseDbHelper.fetchExerciseDataForDate(openedDay) ?: ExerciseData(
        currentDate = openedDay,
        pushups = 0,
        posture = 0
    )

    var pushUps by remember(openedDay) { mutableStateOf(exerciseData.pushups) }
    var postureCorrections by remember(openedDay) { mutableStateOf(exerciseData.posture) }

    // Fetch routine data
    val routineData = remember(openedDay) {
        routineDbHelper.getRoutineDataForDate(openedDay)
    }

    // State to hold the checked states, initialized with data from the database
    val morningChecks = remember(openedDay) { mutableStateOf(routineData["am"] ?: emptyMap()) }
    val eveningChecks = remember(openedDay) { mutableStateOf(routineData["pm"] ?: emptyMap()) }

    fun updateExerciseData() {
        exerciseDbHelper.insertOrUpdateData(
            ExerciseData(
                currentDate = openedDay,
                pushups = pushUps,
                posture = postureCorrections
            )
        )
        Log.d("ExercisesScreen", "Updated exercise data: Pushups=$pushUps, Posture=$postureCorrections")
        exerciseDbHelper.exportToCSV(context)
    }

    // Function to update routine data in the database
    fun updateRoutineData() {
        val dataToSave = mapOf(
            "am" to morningChecks.value,
            "pm" to eveningChecks.value
        )
        routineDbHelper.insertOrUpdateRoutineData(openedDay, dataToSave)
        Log.d("ExercisesScreen", "Updated routine data for $openedDay: $dataToSave")
        routineDbHelper.exportToCSV(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ExerciseCounter(
            label = "Push-Ups",
            count = pushUps,
            onIncrement = {
                pushUps += 5
                updateExerciseData()
            },
            onDecrement = {
                if (pushUps > 0) {
                    pushUps--
                    updateExerciseData()
                }
            }
        )

        ExerciseCounter(
            label = "Posture Corrections",
            count = postureCorrections,
            onIncrement = {
                postureCorrections++
                updateExerciseData()
            },
            onDecrement = {
                if (postureCorrections > 0) {
                    postureCorrections--
                    updateExerciseData()
                }
            }
        )

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        RoutineChecklist(
            dbHelper = routineDbHelper,
            date = openedDay,
            morningChecks = morningChecks,
            eveningChecks = eveningChecks,
            onMorningCheckChange = { newChecks ->
                morningChecks.value = newChecks
                updateRoutineData() // Save to DB whenever morning checks change
            },
            onEveningCheckChange = { newChecks ->
                eveningChecks.value = newChecks
                updateRoutineData() // Save to DB whenever evening checks change
            }
        )
    }
}

@Composable
fun ExerciseCounter(
    label: String,
    count: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDecrement) {
                Icon(imageVector = Icons.Default.Remove, contentDescription = "Minus One")
            }
            Text("$count", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 8.dp))
            IconButton(onClick = onIncrement) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Plus One")
            }
        }
    }
}

@Composable
fun RoutineChecklist(
    dbHelper: RoutineDatabaseHelper,
    date: String,
    morningChecks: MutableState<Map<String, Boolean>>,
    eveningChecks: MutableState<Map<String, Boolean>>,
    onMorningCheckChange: (Map<String, Boolean>) -> Unit,
    onEveningCheckChange: (Map<String, Boolean>) -> Unit
) {
    // Use rememberSaveable to survive configuration changes, and reset when date changes
    var morningExpanded by rememberSaveable(date) { mutableStateOf(false) }
    var eveningExpanded by rememberSaveable(date) { mutableStateOf(false) }

    // Calculate completed exercises by checking the state in the maps
    val completedMorningExercises = morningChecks.value.count { it.value }
    val completedEveningExercises = eveningChecks.value.count { it.value }

    // Define total exercises for each routine (adjust if your routine structure changes)
    val totalMorningExercises = 19
    val totalEveningExercises = 22

    // Define estimated total minutes for each routine (manual estimate)
    val estimatedMorningMinutes = 15 // Adjust this based on your estimate
    val estimatedEveningMinutes = 20 // Adjust this based on your estimate


    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExpandableRoutineSection(
            title = "☀️  Morning Routine",
            expanded = morningExpanded,
            onExpand = { morningExpanded = !morningExpanded },
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
            totalExercises = totalMorningExercises, // Pass total exercises
            completedExercises = completedMorningExercises, // Pass completed exercises
            totalMinutes = estimatedMorningMinutes, // Pass estimated minutes
            content = {
                PostureChecklist(
                    innerPadding = PaddingValues(vertical = 8.dp),
                    dbHelper = dbHelper,
                    date = date,
                    amPm = "am",
                    checks = morningChecks,
                    onCheckChange = onMorningCheckChange
                )
                TMJReleaseChecklist(
                    innerPadding = PaddingValues(vertical = 8.dp),
                    dbHelper = dbHelper,
                    date = date,
                    amPm = "am",
                    checks = morningChecks,
                    onCheckChange = onMorningCheckChange
                )
                NeckStrengtheningStretchingChecklist(
                    innerPadding = PaddingValues(vertical = 8.dp),
                    dbHelper = dbHelper,
                    date = date,
                    amPm = "am",
                    checks = morningChecks,
                    onCheckChange = onMorningCheckChange
                )
                MorningJournallingChecklist(
                    innerPadding = PaddingValues(vertical = 8.dp),
                    dbHelper = dbHelper,
                    date = date,
                    amPm = "am",
                    checks = morningChecks,
                    onCheckChange = onMorningCheckChange
                )
                MorningStretchesChecklist(
                    innerPadding = PaddingValues(vertical = 8.dp),
                    dbHelper = dbHelper,
                    date = date,
                    amPm = "am",
                    checks = morningChecks,
                    onCheckChange = onMorningCheckChange
                )
            }
        )

        ExpandableRoutineSection(
            title = "🌙  Evening Routine",
            expanded = eveningExpanded,
            onExpand = { eveningExpanded = !eveningExpanded },
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
            totalExercises = totalEveningExercises, // Pass total exercises
            completedExercises = completedEveningExercises, // Pass completed exercises
            totalMinutes = estimatedEveningMinutes, // Pass estimated minutes
            content = {
                PostureChecklist( // Assuming Posture Checklist is also in the evening
                    innerPadding = PaddingValues(vertical = 8.dp),
                    dbHelper = dbHelper,
                    date = date,
                    amPm = "pm",
                    checks = eveningChecks,
                    onCheckChange = onEveningCheckChange
                )
                TMJReleaseChecklist( // Assuming TMJ Release is also in the evening
                    innerPadding = PaddingValues(vertical = 8.dp),
                    dbHelper = dbHelper,
                    date = date,
                    amPm = "pm",
                    checks = eveningChecks,
                    onCheckChange = onEveningCheckChange
                )
                NeckStrengtheningStretchingChecklist( // Assuming Neck exercises are also in the evening
                    innerPadding = PaddingValues(vertical = 8.dp),
                    dbHelper = dbHelper,
                    date = date,
                    amPm = "pm",
                    checks = eveningChecks,
                    onCheckChange = onEveningCheckChange
                )
                EveningJournallingChecklist(
                    innerPadding = PaddingValues(vertical = 8.dp),
                    dbHelper = dbHelper,
                    date = date,
                    amPm = "pm",
                    checks = eveningChecks,
                    onCheckChange = onEveningCheckChange
                )
                VagusNerveDestressingChecklist(
                    innerPadding = PaddingValues(vertical = 8.dp),
                    dbHelper = dbHelper,
                    date = date,
                    amPm = "pm",
                    checks = eveningChecks,
                    onCheckChange = onEveningCheckChange
                )
            }
        )
    }
}

@Composable
fun ExpandableRoutineSection(
    title: String,
    expanded: Boolean,
    onExpand: () -> Unit,
    content: @Composable () -> Unit,
    contentPadding: PaddingValues = PaddingValues(all = 8.dp),
    totalExercises: Int, // Added parameter for total exercises
    completedExercises: Int, // Added parameter for completed exercises
    totalMinutes: Int // Added parameter for total estimated minutes
) {
    // Determine if the routine is completed
    val isCompleted = completedExercises > 0 && completedExercises == totalExercises

    val leftMinutes = if (totalExercises > 0) {
        val remainingProportion = (totalExercises - completedExercises).toFloat() / totalExercises.toFloat()
        (totalMinutes * remainingProportion).roundToInt() // Use roundToInt for a cleaner minute value
    } else {
        0 // Handle the case of zero total exercises to avoid division by zero
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(contentPadding)
            .animateContentSize(animationSpec = tween(durationMillis = 300, easing = LinearEasing)) // Animation for expand/collapse
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(
                    // Apply strike-through and grey color if completed
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (isCompleted) Color.Gray else LocalContentColor.current
                )
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Display the exercise count and estimated time
                val exercisesText = "$completedExercises/$totalExercises exercises"
                val timeText = "~$leftMinutes min"

                Text(
                    "$exercisesText, $timeText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )

                IconButton(onClick = onExpand) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }
        }
        if (expanded) {
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            content()
        }
    }
}

@Composable
fun PostureChecklist(
    innerPadding: PaddingValues = PaddingValues(),
    dbHelper: RoutineDatabaseHelper,
    date: String,
    amPm: String,
    checks: MutableState<Map<String, Boolean>>,
    onCheckChange: (Map<String, Boolean>) -> Unit
) {
    val exerciseLabels = listOf(
        "Wall angels: Palms on ears, slide down along the body like snow angels — 10 reps",
        "Ladder climb: Reach one arm up, then the other, as if climbing a ladder — 10 reps",
        "Wing flaps: Arms out like wings, rotate forward and backward — 10 reps",
        "Scapular retractions: Squeeze shoulder blades back & down — hold 5 sec, repeat 10x"
    )
    Text("🧍‍♂️ Posture Exercises (Repeat 3x whole set)", style = MaterialTheme.typography.bodyMedium)
    Column(modifier = Modifier.padding(start = 16.dp).padding(innerPadding)) {
        exerciseLabels.forEach { label ->
            CheckboxItem(
                label = label,
                dbHelper = dbHelper,
                date = date,
                amPm = amPm,
                isChecked = checks.value[label] ?: false, // Get isChecked from the map
                onCheckedChange = { isChecked ->
                    // Update the map when the checkbox changes
                    val newChecks = checks.value.toMutableMap()
                    newChecks[label] = isChecked
                    onCheckChange(newChecks) // Notify the parent
                }
            )
        }
    }
}

@Composable
fun MorningJournallingChecklist(
    innerPadding: PaddingValues = PaddingValues(),
    dbHelper: RoutineDatabaseHelper,
    date: String,
    amPm: String,
    checks: MutableState<Map<String, Boolean>>,
    onCheckChange: (Map<String, Boolean>) -> Unit
) {
    val exerciseLabels = listOf(
        "Daylio Check-Up: Open the beautiful template and write how you're feeling and what you're sensing",
        "Sleep Tracking: If you haven't done so, update this nights sleep data"
    )
    Text("✍️ Mental Processing", style = MaterialTheme.typography.bodyMedium)
    Column(modifier = Modifier.padding(start = 16.dp).padding(innerPadding)) {
        exerciseLabels.forEach { label ->
            CheckboxItem(
                label = label,
                dbHelper = dbHelper,
                date = date,
                amPm = amPm,
                isChecked = checks.value[label] ?: false,
                onCheckedChange = { isChecked ->
                    val newChecks = checks.value.toMutableMap()
                    newChecks[label] = isChecked
                    onCheckChange(newChecks)
                }
            )
        }
    }
}

@Composable
fun EveningJournallingChecklist(
    innerPadding: PaddingValues = PaddingValues(),
    dbHelper: RoutineDatabaseHelper,
    date: String,
    amPm: String,
    checks: MutableState<Map<String, Boolean>>,
    onCheckChange: (Map<String, Boolean>) -> Unit
) {
    val exerciseLabels = listOf(
        "Daylio Entry: Write how you have been today and what you've experienced",
        "Symptom Tracking: Slide the sliders in the tab nextdoor"
    )
    Text("✍️ Mental Processing", style = MaterialTheme.typography.bodyMedium)
    Column(modifier = Modifier.padding(start = 16.dp).padding(innerPadding)) {
        exerciseLabels.forEach { label ->
            CheckboxItem(
                label = label,
                dbHelper = dbHelper,
                date = date,
                amPm = amPm,
                isChecked = checks.value[label] ?: false,
                onCheckedChange = { isChecked ->
                    val newChecks = checks.value.toMutableMap()
                    newChecks[label] = isChecked
                    onCheckChange(newChecks)
                }
            )
        }
    }
}

@Composable
fun MorningStretchesChecklist(
    innerPadding: PaddingValues = PaddingValues(),
    dbHelper: RoutineDatabaseHelper,
    date: String,
    amPm: String,
    checks: MutableState<Map<String, Boolean>>,
    onCheckChange: (Map<String, Boolean>) -> Unit
) {
    val exerciseLabels = listOf(
        "Back Stretches: Kneel forward from sitting, left, right (2x 5 seconds)",
        "Kneeling Forward: Stand up and let your hands touch your feet (30 seconds)",
        "Cobra Stretch: Lie on belly, lift chest with arms — 10–15 sec, 2x",
        "Child's Pose: Kneel, fold forward, arms stretched — 30–60 sec"
    )
    Text("🧘 General Stretches", style = MaterialTheme.typography.bodyMedium)
    Column(modifier = Modifier.padding(start = 16.dp).padding(innerPadding)) {
        exerciseLabels.forEach { label ->
            CheckboxItem(
                label = label,
                dbHelper = dbHelper,
                date = date,
                amPm = amPm,
                isChecked = checks.value[label] ?: false,
                onCheckedChange = { isChecked ->
                    val newChecks = checks.value.toMutableMap()
                    newChecks[label] = isChecked
                    onCheckChange(newChecks)
                }
            )
        }
    }
}


@Composable
fun TMJReleaseChecklist(
    innerPadding: PaddingValues = PaddingValues(),
    dbHelper: RoutineDatabaseHelper,
    date: String,
    amPm: String,
    checks: MutableState<Map<String, Boolean>>,
    onCheckChange: (Map<String, Boolean>) -> Unit
) {
    val exerciseLabels = listOf(
        "Side pressure: Gently press your jaw from left and right using your hand — 5 seconds each side, 3x",
        "Jaw opening: Place two fingers on lower front teeth, gently pull down — 5 seconds, 3x",
        "Gentle massage (optional): Use fingers to massage the masseter (cheek) and temples for 30 seconds each side",
        "Jaw wiggles: Gently move jaw side to side — 10x to loosen tension"
    )
    Text("😬 TMJ Release Exercises", style = MaterialTheme.typography.bodyMedium)
    Column(modifier = Modifier.padding(start = 16.dp).padding(innerPadding)) {
        exerciseLabels.forEach { label ->
            CheckboxItem(
                label = label,
                dbHelper = dbHelper,
                date = date,
                amPm = amPm,
                isChecked = checks.value[label] ?: false,
                onCheckedChange = { isChecked ->
                    val newChecks = checks.value.toMutableMap()
                    newChecks[label] = isChecked
                    onCheckChange(newChecks)
                }
            )
        }
    }
}

@Composable
fun NeckStrengtheningStretchingChecklist(
    innerPadding: PaddingValues = PaddingValues(),
    dbHelper: RoutineDatabaseHelper,
    date: String,
    amPm: String,
    checks: MutableState<Map<String, Boolean>>,
    onCheckChange: (Map<String, Boolean>) -> Unit
) {
    val exerciseLabels = listOf(
        "Chin tucks: Pull head straight back (like making a double chin) — hold 5 sec, repeat 3x",
        "Tilt head left, hold 10–15 sec",
        "Tilt head right, hold 10–15 sec",
        "Gentle half circles — 3x each direction",
        "Isometric holds: Push head gently into hand in each direction (front, back, left, right) — 5 sec each, 1x daily"
    )
    Text("🧠 Neck Strengthening & Stretching", style = MaterialTheme.typography.bodyMedium)
    Column(modifier = Modifier.padding(start = 16.dp).padding(innerPadding)) {
        exerciseLabels.forEach { label ->
            CheckboxItem(
                label = label,
                dbHelper = dbHelper,
                date = date,
                amPm = amPm,
                isChecked = checks.value[label] ?: false,
                onCheckedChange = { isChecked ->
                    val newChecks = checks.value.toMutableMap()
                    newChecks[label] = isChecked
                    onCheckChange(newChecks)
                }
            )
        }
    }
}

@Composable
fun VagusNerveDestressingChecklist(
    innerPadding: PaddingValues = PaddingValues(),
    dbHelper: RoutineDatabaseHelper,
    date: String,
    amPm: String,
    checks: MutableState<Map<String, Boolean>>,
    onCheckChange: (Map<String, Boolean>) -> Unit
) {
    val exerciseLabels = listOf(
        "Child’s Pose: Kneel, fold forward, arms stretched — 30–60 sec",
        "Cobra Stretch: Lie on belly, lift chest with arms — 10–15 sec, 2x",
        "Lie or sit comfortably",
        "Inhale through nose for 4 sec, feel belly rise",
        "Exhale slowly for 6–8 sec",
        "Repeat for 5–10 breaths",
        "Humming, low and relaxed — 1 min"
    )
    Text("🫁 Vagus Nerve & Destressing Moves", style = MaterialTheme.typography.bodyMedium)
    Column(modifier = Modifier.padding(start = 16.dp).padding(innerPadding)) {
        exerciseLabels.forEach { label ->
            CheckboxItem(
                label = label,
                dbHelper = dbHelper,
                date = date,
                amPm = amPm,
                isChecked = checks.value[label] ?: false,
                onCheckedChange = { isChecked ->
                    val newChecks = checks.value.toMutableMap()
                    newChecks[label] = isChecked
                    onCheckChange(newChecks)
                }
            )
        }
    }
}

@Composable
fun CheckboxItem(
    label: String,
    dbHelper: RoutineDatabaseHelper,
    date: String,
    amPm: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    var checkedState by remember { mutableStateOf(isChecked) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Checkbox(
            checked = checkedState,
            onCheckedChange = { newChecked ->
                checkedState = newChecked
                onCheckedChange(newChecked)
            }
        )
        Text(label, style = MaterialTheme.typography.bodySmall)
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
fun MedicationScreen(openedDay: String) {
    val context = LocalContext.current
    val dbHelper = NewMedicationDatabaseHelper(context)

    val medications = remember { mutableStateListOf<MedicationItem>() }
    var sideEffects by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    // Load initial data when openedDay changes.
    LaunchedEffect(openedDay) {
        val fetchedMedications = dbHelper.fetchMedicationItemsForDateWithDefaults(openedDay)
        medications.clear()
        medications.addAll(fetchedMedications)
        dbHelper.insertOrUpdateMedicationList(openedDay, fetchedMedications)
        sideEffects = dbHelper.fetchSideEffectsForDate(openedDay) ?: ""
    }

    fun saveMedicationData() {
        dbHelper.insertOrUpdateMedicationList(openedDay, medications.toList())
        dbHelper.insertOrUpdateSideEffects(openedDay, sideEffects)
        dbHelper.exportToCSV(context)
    }

    fun updateMedication(medication: MedicationItem, newMedication: MedicationItem) {
        val index = medications.indexOf(medication)
        if (index != -1) {
            medications[index] = newMedication
            saveMedicationData()
        }
    }

    val starredMedications = medications.filter { it.isStarred }
    val nonStarredMedications = medications.filter { !it.isStarred }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Medications",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        starredMedications.forEach { medication ->
            MedicationItemRow(
                medication = medication,
                onIncrement = {
                    updateMedication(medication, medication.copy(dosage = medication.dosage + medication.step))
                },
                onDecrement = {
                    if (medication.dosage - medication.step >= 0)
                        updateMedication(medication, medication.copy(dosage = medication.dosage - medication.step))
                },
                onToggleStar = {
                    updateMedication(medication, medication.copy(isStarred = !medication.isStarred))
                }
            )
            Spacer(modifier = Modifier.height(2.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 16.dp)
        ) {
            Text(
                text = if (expanded) "Close medication tab" else "Add medications",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null
            )
        }

        if (expanded) {
            LazyColumn {
                items(nonStarredMedications) { medication ->
                    MedicationItemRow(
                        medication = medication,
                        onIncrement = {
                            updateMedication(medication, medication.copy(dosage = medication.dosage + medication.step))
                        },
                        onDecrement = {
                            if (medication.dosage - medication.step >= 0)
                                updateMedication(medication, medication.copy(dosage = medication.dosage - medication.step))
                        },
                        onToggleStar = {
                            updateMedication(medication, medication.copy(isStarred = !medication.isStarred))
                        }
                    )
                    Divider()
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
        Text(
            text = "Side effects",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        TextField(
            value = sideEffects,
            onValueChange = {
                sideEffects = it
                saveMedicationData()
            },
            placeholder = { Text("Enter any side effects here...") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun MedicationItemRow(
    medication: MedicationItem,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onToggleStar: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Star icon: tinted with the Material theme’s secondary color if starred.
        Icon(
            imageVector = if (medication.isStarred) Icons.Filled.Star else Icons.Filled.StarBorder,
            contentDescription = "Toggle Star",
            tint = if (medication.isStarred)
                MaterialTheme.colorScheme.secondary
            else LocalContentColor.current,
            modifier = Modifier
                .size(24.dp)
                .clickable { onToggleStar() }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = medication.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        // Dosage counter with value shown between decrement and increment buttons.
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onDecrement() }) {
                Icon(imageVector = Icons.Filled.Remove, contentDescription = "Decrease dosage")
            }
            Text(
                text = "${medication.dosage} ${medication.unit}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            IconButton(onClick = { onIncrement() }) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Increase dosage")
            }
        }
    }
}

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


@Composable
fun HealthTrackerScreen(openedDay: String) {
    val context = LocalContext.current
    val dbHelper = HealthDatabaseHelper(context) // Your existing helper
    val miscellaneousDbHelper = MiscellaneousDatabaseHelper(context) // *** Instantiate the new helper ***

    // Log.d("HealthTrackerScreen", "Recomposing HealthTrackerScreen for openedDay=$openedDay")

    // HealthData still only includes the original fields for now
    var healthData by remember { mutableStateOf(
        HealthData(openedDay, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, "")
    ) }

    // Slider state for each group.
    var symptomValues by remember { mutableStateOf(
        listOf(
            healthData.malaise,
            healthData.soreThroat,
            healthData.lymphadenopathy
        )
    )}
    var externalValues by remember { mutableStateOf(
        listOf(
            healthData.exerciseLevel,
            healthData.stressLevel,
            healthData.illnessImpact
        )
    )}
    var mentalValues by remember { mutableStateOf(
        listOf(
            healthData.depression,
            healthData.hopelessness
        )
    )}
    var sleepValues by remember { mutableStateOf(
        listOf(
            healthData.sleepQuality,
            healthData.sleepLength,
            healthData.sleepReadiness
        )
    )}
    var notes by remember { mutableStateOf(healthData.notes) }

    // *** MiscellaneousData state is now managed internally by MiscellaneousTrackers ***
    // var miscellaneousData by remember { mutableStateOf(MiscellaneousData()) }


    // Define labels.
    val symptomLabels = listOf("Malaise", "Sore Throat", "Lymphadenopathy")
    val externalLabels = listOf("Exercise Level", "Stress Level", "Illness Impact")
    val mentalLabels = listOf("Depression", "Hopelessness")
    val sleepLabels = listOf("Sleep quality", "Sleep length", "Sleep readiness")

    // Scroll state.
    val scrollState = rememberScrollState()

    // Compute yesterday's date from openedDay (assumed format "yyyy-MM-dd").
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val yesterdayDate = try {
        LocalDate.parse(openedDay, formatter).minusDays(1).format(formatter)
    } catch (e: Exception) {
        null
    }

    // State for yesterday's HealthData.
    var yesterdayHealthData by remember { mutableStateOf<HealthData?>(null) }
    LaunchedEffect(openedDay) {
        yesterdayHealthData = yesterdayDate?.let { dbHelper.fetchHealthDataForDate(it) }
        // *** We are NOT fetching yesterday's miscellaneous data here ***
    }

    // Derive yesterday's slider values if available.
    val yesterdaySymptomValues = yesterdayHealthData?.let {
        listOf(it.malaise, it.soreThroat, it.lymphadenopathy)
    }
    val yesterdayExternalValues = yesterdayHealthData?.let {
        listOf(it.exerciseLevel, it.stressLevel, it.illnessImpact)
    }
    val yesterdayMentalValues = yesterdayHealthData?.let {
        listOf(it.depression, it.hopelessness)
    }
    val yesterdaySleepValues = yesterdayHealthData?.let {
        listOf(it.sleepQuality, it.sleepLength, it.sleepReadiness)
    }

    // Function to update the *HealthData* database and healthData state.
    fun updateHealthData() { // Renamed to clarify it only saves HealthData
        val updatedHealthData = HealthData(
            currentDate = openedDay,
            malaise = symptomValues[0],
            soreThroat = symptomValues[1],
            lymphadenopathy = symptomValues[2],
            exerciseLevel = externalValues[0],
            stressLevel = externalValues[1],
            illnessImpact = externalValues[2],
            depression = mentalValues[0],
            hopelessness = mentalValues[1],
            sleepQuality = sleepValues[0],
            sleepLength = sleepValues[1],
            sleepReadiness = sleepValues[2],
            notes = notes
            // MiscellaneousData is NOT included here
        )
        dbHelper.insertOrUpdateHealthData(updatedHealthData)
        healthData = updatedHealthData
        dbHelper.exportToCSV(context) // Consider when and where export should happen
    }

    // LaunchedEffect to fetch today's HealthData.
    LaunchedEffect(openedDay) {
        val fetchedData = dbHelper.fetchHealthDataForDate(openedDay)
            ?: HealthData(openedDay, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, "")
        healthData = fetchedData
        symptomValues = listOf(fetchedData.malaise, fetchedData.soreThroat, fetchedData.lymphadenopathy)
        externalValues = listOf(fetchedData.exerciseLevel, fetchedData.stressLevel, fetchedData.illnessImpact)
        mentalValues = listOf(fetchedData.depression, fetchedData.hopelessness)
        sleepValues = listOf(fetchedData.sleepQuality, fetchedData.sleepLength, fetchedData.sleepReadiness)
        notes = fetchedData.notes
        // We are NOT loading miscellaneousData from the database here
    }

    // Call updateHealthData whenever any *original* slider or field changes.
    // We are NOT triggering database save for miscellaneousData from here
    LaunchedEffect(symptomValues, externalValues, mentalValues, sleepValues, notes) {
        updateHealthData() // Call the renamed function
    }

    // State for the expansion of the Miscellaneous section
    var miscellaneousExpanded by rememberSaveable(openedDay) { mutableStateOf(false) }


    // Main layout.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Symptoms", style = MaterialTheme.typography.titleLarge)
        SymptomSliderGroup(
            items = symptomLabels.zip(symptomValues),
            yesterdayValues = yesterdaySymptomValues,
            onValuesChange = { updatedValues -> symptomValues = updatedValues }
        )
        Spacer(modifier = Modifier.height(16.dp))

        // The Miscellaneous Expandable Section is placed here
        ExpandableSection(
            title = " ✨ Miscellaneous",
            expanded = miscellaneousExpanded,
            onExpand = { miscellaneousExpanded = !miscellaneousExpanded },
            content = {
            // Content of the miscellaneous section
            MiscellaneousTrackers(
                date = openedDay, // Pass the current date
                miscellaneousDbHelper = miscellaneousDbHelper // *** Pass the new helper ***
            )
        }
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text("Externals", style = MaterialTheme.typography.titleLarge)
        ExternalSliderGroup(
            items = externalLabels.zip(externalValues),
            yesterdayValues = yesterdayExternalValues,
            onValuesChange = { updatedValues -> externalValues = updatedValues }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Mental Health", style = MaterialTheme.typography.titleLarge)
        MentalSliderGroup(
            items = mentalLabels.zip(mentalValues),
            yesterdayValues = yesterdayMentalValues,
            onValuesChange = { updatedValues -> mentalValues = updatedValues }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Sleep", style = MaterialTheme.typography.titleLarge)
        SleepSliderGroup(
            items = sleepLabels.zip(sleepValues),
            yesterdayValues = yesterdaySleepValues,
            onValuesChange = { updatedValues -> sleepValues = updatedValues }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Notes", style = MaterialTheme.typography.titleLarge)
        TextInputField(
            text = notes,
            onTextChange = { newText -> notes = newText }
        )
        Spacer(modifier = Modifier.height(10.dp))
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
fun MiscellaneousTrackers(
    date: String, // Need the date to load/save data
    miscellaneousDbHelper: MiscellaneousDatabaseHelper
) {
    // State to hold the list of tracker items
    var miscellaneousItems by remember { mutableStateOf(defaultMiscellaneousItems()) }

    // Load initial data when the date or helper changes
    LaunchedEffect(date, miscellaneousDbHelper) {
        val fetchedItems = miscellaneousDbHelper.fetchMiscellaneousItems(date)
        miscellaneousItems = fetchedItems // fetchMiscellaneousItems returns default list if empty
    }

    // Function to update a specific item in the list and save the whole list
    fun updateItemAndSave(updatedItem: TrackerItem) {
        val updatedList = miscellaneousItems.map { item ->
            if (item.name == updatedItem.name) updatedItem else item
        }
        miscellaneousItems = updatedList // Update local state
        // Trigger a database save for the entire list
        miscellaneousDbHelper.insertOrUpdateMiscellaneousItems(date, updatedList)
        miscellaneousDbHelper.exportToCSV() // Export to CSV after save
    }

    Column(modifier = Modifier.padding(start = 0.dp)) { // Add some leading space

        // Iterate through the list of miscellaneous items to display each tracker
        miscellaneousItems.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth() // Ensure row takes full width
            ) {
                Checkbox(
                    checked = item.isChecked,
                    onCheckedChange = { isChecked ->
                        // Update the isChecked state for this item and save
                        updateItemAndSave(item.copy(isChecked = isChecked))
                    }
                )
                // The text label for the item
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(0.5f) // Give some weight to the text
                )

                // Wrap SliderInput related elements to take remaining horizontal space
                // This Column holds the slider and its value label
                Column(modifier = Modifier.weight(1f)) { // Give remaining weight to the slider column
                    // SliderInput expects value, enabled, etc.
                    // We'll reuse the same label list for all these 0-4 pain/discomfort scales
                    val painLabels = listOf("None", "Very Mild", "Mild", "Moderate", "Severe")
                    NewSliderInput(
                        label = item.name, // Use the item's name as the label (though it's already in the Text)
                        value = item.value,
                        valueRange = 0f..4f, // Assuming 0-4 scale for pain/discomfort
                        steps = 3, // SliderInput uses steps = 0, but labels are based on 0..4 anchors
                        labels = painLabels, // Use the common pain/discomfort labels
                        yesterdayValue = null, // Not fetching yesterday's miscellaneous data
                        enabled = item.isChecked, // *** Enable/disable based on item's checked state ***
                        onValueChange = { newValue ->
                            // Update the value for this item and save
                            updateItemAndSave(item.copy(value = newValue))
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp)) // Space between items
        }
    }
}

@Composable
fun NewSliderInput(
    label: String, // Label for the tracker (e.g., "Malaise", "Energy Level")
    value: Float, // The current value of the slider
    valueRange: ClosedFloatingPointRange<Float>, // The range of values (e.g., 0f..4f)
    steps: Int, // This parameter seems unused in your SliderInput definition, as you use steps = 0 in the internal Slider
    labels: List<String>, // Labels for the steps/anchors (e.g., "None", "Very Mild", ...)
    yesterdayValue: Float? = null, // Optional value from yesterday for comparison
    enabled: Boolean = true, // *** Control the enabled state of the slider ***
    onValueChange: (Float) -> Unit // Callback when the slider value changes
) {
    // Compute the nearest anchor index for today's value to display the corresponding label.
    val nearestIndex = value.roundToInt().coerceIn(0, labels.size - 1)
    val displayedLabel = labels.getOrNull(nearestIndex) ?: "" // Safely get label

    // Compute fraction for today's value relative to the value range.
    val fraction = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    // Compute fraction for yesterday's value if available.
    val yesterdayFraction = yesterdayValue?.let {
        (it - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    }

    var sliderWidth by remember { mutableStateOf(0) }
    var labelWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    Column(modifier = Modifier.fillMaxWidth()) {
        // The label text for the item is now handled in the parent Row (e.g., MiscellaneousTrackers)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    // Get the width of the slider area to position the label/marker
                    sliderWidth = coordinates.size.width
                }
        ) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = 0, // Using a continuous slider
                enabled = enabled, // *** Pass the enabled parameter to the standard Material3 Slider ***
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp) // Add padding around the slider itself
            )
            // Compute the pixel positions for the thumb and marker.
            // We need the width of the slider track, which is the sliderWidth.
            // The position is the fraction multiplied by the available width.
            val thumbX = fraction * sliderWidth.toFloat() // Use float for calculation
            val yesterdayX = yesterdayFraction?.times(sliderWidth.toFloat()) // Use float

            // Determine if the yesterday marker is too close to the current thumb
            val hideYesterdayMarker = yesterdayX != null &&
                    abs(thumbX - yesterdayX) < with(density) { 10.dp.toPx() } // Check distance in pixels

            // If yesterday's value is provided and the thumb isn't too close, show a marker.
            if (yesterdayFraction != null && !hideYesterdayMarker) {
                Box(
                    modifier = Modifier
                        // Offset the marker based on the calculated position, subtracting half its size
                        .offset(
                            x = with(density) { (yesterdayFraction * sliderWidth - 6).toDp() },
                            y = 28.dp // Adjusted vertical offset to place it below the slider track
                        )
                        .clip(CircleShape) // Make it a circle
                        // Adjust marker color/alpha based on enabled state
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if(enabled) 0.3f else 0.1f)) // Semi-transparent grey, less visible when disabled
                        .size(12.dp) // Size of the marker
                )
            }

            // Display the text label corresponding to the slider's current value.
            if (displayedLabel.isNotEmpty()) { // Only display label if available
                Text(
                    text = displayedLabel,
                    style = MaterialTheme.typography.bodySmall,
                    // Grey out label when disabled, otherwise use default content color
                    color = if (enabled) LocalContentColor.current else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier
                        // Measure the text width to center it below the thumb
                        .onGloballyPositioned { coordinates ->
                            labelWidth = coordinates.size.width
                        }
                        // Position the text label based on the thumb's position, centering it
                        .offset(
                            x = with(density) { ((fraction * sliderWidth) - labelWidth / 2).toDp() },
                            y = 60.dp // Adjusted vertical offset to place it below the marker
                        )
                )
            }
        }
    }
}

// --- Slider Group Composables -----------------------------------------------------

@Composable
fun SymptomSliderGroup(
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

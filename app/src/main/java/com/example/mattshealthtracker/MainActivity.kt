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
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration


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
    object AddData : BottomNavItem("add_data", "Tracking", Icons.Default.MonitorHeart)
    object Statistics: BottomNavItem("statistics", "Statistics", Icons.Default.QueryStats)
    object Exercises : BottomNavItem("exercises", "Routines", Icons.Default.FitnessCenter) // New tab
    object MedicationTracking : BottomNavItem("medication_tracking", "Medications", Icons.Default.Medication) // New Screen
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

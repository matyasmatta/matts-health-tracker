package com.example.mattshealthtracker

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import android.content.Context
import androidx.compose.material.icons.filled.Delete
import com.example.mattshealthtracker.ui.theme.MattsHealthTrackerTheme
import com.example.mattshealthtracker.AppGlobals
import android.util.Log
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Wrap the app with MaterialTheme to handle dark/light mode
            MattsHealthTrackerTheme {
                // Provide a Surface for the HealthTrackerApp
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    HealthTrackerApp()
                }
            }
        }
    }
}

sealed class BottomNavItem(val route: String, val label: String, val icon: ImageVector) {
    object AddData : BottomNavItem("add_data", "Tracking", Icons.Default.Add)
    object Exercises : BottomNavItem("exercises", "Exercises", Icons.Default.Refresh) // New tab
    object MedicationTracking : BottomNavItem("medication_tracking", "Medications", Icons.Default.Check) // New Screen
}

@Composable
fun HealthTrackerApp() {
    var currentScreen by remember { mutableStateOf<BottomNavItem>(BottomNavItem.AddData) }
    var openedDay by remember { mutableStateOf(AppGlobals.openedDay) } // Single source of truth for openedDay

    Log.d("HealthTrackerApp", "Recomposing HealthTrackerApp: openedDay=$openedDay")

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf(
                    BottomNavItem.AddData,
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
        Column(modifier = Modifier.padding(innerPadding)) {
            DateNavigationBar(openedDay, onDateChange = { newDate ->
                openedDay = newDate
                AppGlobals.openedDay = newDate // Update the global helper for consistency
            })

            Box(modifier = Modifier.fillMaxSize()) {
                when (currentScreen) {
                    is BottomNavItem.AddData -> HealthTrackerScreen(openedDay)
                    is BottomNavItem.Exercises -> ExercisesScreen(openedDay) // Pass openedDay to the screen
                    is BottomNavItem.MedicationTracking -> MedicationScreen(openedDay) // Pass openedDay to the screen
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
fun DateNavigationBar(openedDay: String, onDateChange: (String) -> Unit) {
    var isDatePickerVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val currentDay = AppGlobals.currentDay // Get current day from AppGlobals
    val today = AppGlobals.getCurrentDayAsLocalDate().toString()
    val yesterday = AppGlobals.getCurrentDayAsLocalDate().minusDays(1).toString()

    // State to show Toast message
    var showToast by remember { mutableStateOf(false) }

    Log.d("DateNavigation", "Recomposing DateNavigationBar: $openedDay")

    if (isDatePickerVisible) {
        // Show DatePickerDialog in a Composable context
        LaunchedEffect(Unit) {
            isDatePickerVisible = true // Trigger the dialog to show
        }
        CustomDatePickerDialog(
            onDateSelected = { selectedDate ->
                // Protection against future dates
                if (selectedDate <= AppGlobals.getCurrentDayAsLocalDate()) {
                    onDateChange(selectedDate.toString())
                } else {
                    // Set showToast to true to trigger the Toast message
                    showToast = true
                }
            },
            onDismissRequest = { isDatePickerVisible = false }
        )
    }

    // Show Toast message if showToast is true
    if (showToast) {
        Toast.makeText(context, "Cannot select a future date!", Toast.LENGTH_SHORT).show()
        // Reset showToast to false after showing the Toast
        showToast = false
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = {
                val currentDate = AppGlobals.getOpenedDayAsLocalDate()
                val previousDay = currentDate.minusDays(1)
                val newDate = previousDay.toString()
                onDateChange(newDate) // Notify parent of the change
                Log.d("DateNavigation", "Changed to previous day: $newDate")
            }
        ) {
            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Previous Day")
        }

        // Show "Yesterday" or "Today" if the opened day matches those days
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

                // Only allow moving to `nextDay` if it's not beyond `currentDay`
                if (nextDay <= AppGlobals.getCurrentDayAsLocalDate()) {
                    val newDate = nextDay.toString()
                    onDateChange(newDate) // Notify parent of the change
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
fun ExercisesScreen(openedDay: String) {
    val context = LocalContext.current
    val dbhelper = remember { ExerciseDatabaseHelper(context) }
    DisposableEffect(Unit) {
        onDispose { dbhelper.close() }
    }

    // Fetch data dynamically whenever `openedDay` changes
    val exerciseData = dbhelper.fetchExerciseDataForDate(openedDay) ?: ExerciseData(
        currentDate = openedDay,
        pushups = 0,
        posture = 0
    )

    // Store state for counters, initialized with `exerciseData`
    var pushUps by remember(openedDay) { mutableStateOf(exerciseData.pushups) }
    var postureCorrections by remember(openedDay) { mutableStateOf(exerciseData.posture) }

    // Helper function to update the database
    fun updateData() {
        dbhelper.insertOrUpdateData(
            ExerciseData(
                currentDate = openedDay,
                pushups = pushUps,
                posture = postureCorrections
            )
        )
        Log.d("ExercisesScreen", "Updated exercise data: Pushups=$pushUps, Posture=$postureCorrections")
        dbhelper.exportToCSV(context)
    }

    Log.d("ExercisesScreen", "Recomposing for openedDay=$openedDay, Pushups=$pushUps, Posture=$postureCorrections")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Text("Exercise Tracker", style = MaterialTheme.typography.titleLarge)

        ExerciseCounter(
            label = "Push-Ups",
            count = pushUps,
            onIncrement = {
                pushUps += 5
                updateData()
            },
            onDecrement = {
                if (pushUps > 0) {
                    pushUps--
                    updateData()
                }
            }
        )

        ExerciseCounter(
            label = "Posture Corrections",
            count = postureCorrections,
            onIncrement = {
                postureCorrections++
                updateData()
            },
            onDecrement = {
                if (postureCorrections > 0) {
                    postureCorrections--
                    updateData()
                }
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
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Minus One")
            }
            Text("$count", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 8.dp))
            IconButton(onClick = onIncrement) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Plus One")
            }
        }
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

    // Default medication list.
    val defaultMedications = listOf(
        MedicationItem("amitriptyline", 0f, 6.25f, "mg"),
        MedicationItem("inosine pranobex", 0f, 500f, "mg"),
        MedicationItem("paracetamol", 0f, 250f, "mg"),
        MedicationItem("ibuprofen", 0f, 200f, "mg"),
        MedicationItem("vitamin D", 0f, 500f, "IU"),
        MedicationItem("bisulepine", 0f, 1f, "mg"),
        MedicationItem("cetirizine", 0f, 5f, "mg"),
        MedicationItem("doxycycline", 0f, 100f, "mg"),
        MedicationItem("corticosteroids", 0f, 5f, "mg-eq")
    )

    // Mutable state for the medications list.
    val medications = remember { mutableStateListOf<MedicationItem>() }
    // Side effects state.
    var sideEffects by remember { mutableStateOf("") }

    // Load initial data when openedDay changes.
    LaunchedEffect(openedDay) {
        val fetchedMedications = dbHelper.fetchMedicationItemsForDate(openedDay)
        if (fetchedMedications.isNotEmpty()) {
            medications.clear()
            medications.addAll(fetchedMedications)
        } else {
            medications.clear()
            medications.addAll(defaultMedications)
            dbHelper.insertOrUpdateMedicationList(openedDay, defaultMedications)
        }
        val dbSideEffects = dbHelper.fetchSideEffectsForDate(openedDay)
        sideEffects = dbSideEffects ?: ""
    }

    // State controlling whether non-starred medications are expanded.
    var expanded by remember { mutableStateOf(false) }

    // Helper function to update a medication in the list.
    fun updateMedication(medication: MedicationItem, newMedication: MedicationItem) {
        val index = medications.indexOf(medication)
        if (index != -1) {
            medications[index] = newMedication
        }
        dbHelper.exportToCSV(context)
    }

    // Save the current medication list and side effects to the database.
    fun saveMedicationData() {
        dbHelper.insertOrUpdateMedicationList(openedDay, medications.toList())
        dbHelper.insertOrUpdateSideEffects(openedDay, sideEffects)
        dbHelper.exportToCSV(context)
    }

    // Derive starred and non-starred lists.
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

        // Display starred medications at the top.
        starredMedications.forEach { medication ->
            MedicationItemRow(
                medication = medication,
                onIncrement = {
                    updateMedication(medication, medication.copy(dosage = medication.dosage + medication.step))
                    saveMedicationData()
                },
                onDecrement = {
                    if (medication.dosage - medication.step >= 0)
                        updateMedication(medication, medication.copy(dosage = medication.dosage - medication.step))
                    saveMedicationData()
                },
                onToggleStar = {
                    updateMedication(medication, medication.copy(isStarred = !medication.isStarred))
                    saveMedicationData()
                }
            )
            Spacer(modifier = Modifier.height(2.dp))
        }

        // Expand/collapse header for non-starred medications.
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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
                contentDescription = if (expanded) "Collapse" else "Expand"
            )
        }

        // Display non-starred medications if expanded.
        if (expanded) {
            LazyColumn {
                items(nonStarredMedications) { medication ->
                    MedicationItemRow(
                        medication = medication,
                        onIncrement = {
                            updateMedication(medication, medication.copy(dosage = medication.dosage + medication.step))
                            saveMedicationData()
                        },
                        onDecrement = {
                            if (medication.dosage - medication.step >= 0)
                                updateMedication(medication, medication.copy(dosage = medication.dosage - medication.step))
                            saveMedicationData()
                        },
                        onToggleStar = {
                            updateMedication(medication, medication.copy(isStarred = !medication.isStarred))
                            saveMedicationData()
                        }
                    )
                    Divider()
                }
            }
        }

        // Side effects section.
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
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
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
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = { onDecrement() }) {
                Icon(imageVector = Icons.Filled.Delete, contentDescription = "Decrease dosage")
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
    val dbHelper = HealthDatabaseHelper(context)
    // Log.d("HealthTrackerScreen", "Recomposing HealthTrackerScreen for openedDay=$openedDay")

    // Updated HealthData now includes only the fields used in this screen.
    var healthData by remember { mutableStateOf(HealthData(openedDay, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, "")) }

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
    var notes by remember { mutableStateOf(healthData.notes) }

    // Define labels.
    val symptomLabels = listOf("Malaise", "Sore Throat", "Lymphadenopathy")
    val externalLabels = listOf("Exercise Level", "Stress Level", "Illness Impact")
    val mentalLabels = listOf("Depression", "Hopelessness")

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

    // Function to update data.
    fun updateData() {
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
            notes = notes
        )
        dbHelper.insertOrUpdateHealthData(updatedHealthData)
        healthData = updatedHealthData
        dbHelper.exportToCSV(context)
    }

    LaunchedEffect(openedDay) {
        val fetchedData = dbHelper.fetchHealthDataForDate(openedDay)
            ?: HealthData(openedDay, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, "")
        healthData = fetchedData
        symptomValues = listOf(fetchedData.malaise, fetchedData.soreThroat, fetchedData.lymphadenopathy)
        externalValues = listOf(fetchedData.exerciseLevel, fetchedData.stressLevel, fetchedData.illnessImpact)
        mentalValues = listOf(fetchedData.depression, fetchedData.hopelessness)
        notes = fetchedData.notes
    }

    LaunchedEffect(symptomValues, externalValues, mentalValues, notes) {
        updateData()
    }

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
        Text("Notes", style = MaterialTheme.typography.titleLarge)
        TextInputField(
            text = notes,
            onTextChange = { newText -> notes = newText }
        )
        Spacer(modifier = Modifier.height(10.dp))
    }
}

// --- Slider Groups with Optional Yesterday Values --------------------------------

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
                steps = 0, // Continuous slider for high precision
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            )

            // Compute the pixel difference between the thumb and yesterday's marker.
            val thumbX = fraction * sliderWidth
            val yesterdayX = yesterdayFraction?.times(sliderWidth)
            val hideYesterdayMarker = yesterdayX != null && abs(thumbX - yesterdayX) < with(density) { 10.dp.toPx() }

            // If yesterday's value is provided and the cursor isn't too close, show the marker.
            if (yesterdayFraction != null && !hideYesterdayMarker) {
                Box(
                    modifier = Modifier
                        .offset(
                            x = with(density) { (yesterdayFraction * sliderWidth - 6).toDp() },
                            y = 28.dp  // Adjusted vertical position
                        )
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        .size(12.dp)
                )
            }

            // Position the label text directly below today's thumb.
            Text(
                text = displayedLabel,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.offset(
                    x = with(density) { (fraction * sliderWidth - 20).toDp() },
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

@Preview(showBackground = true)
@Composable
fun PreviewHealthTrackerScreen() {
    HealthTrackerApp()
}

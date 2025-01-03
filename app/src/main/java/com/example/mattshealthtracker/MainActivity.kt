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

@Composable
fun MedicationScreen(openedDay: String) {
    val context = LocalContext.current
    val dbHelper = MedicationDatabaseHelper(context)

    // Fetch data dynamically whenever `openedDay` changes
    var medicationData = dbHelper.fetchMedicationDataForDate(openedDay) ?: MedicationData(
        currentDate = openedDay,
        doxyLactose = false,
        doxyMeal = false,
        doxyDose = false, // boolean for doxycycline dose
        doxyWater = false,
        prednisoneDose = false,
        prednisoneMeal = false,
        vitamins = false,
        probioticsMorning = false,
        probioticsEvening = false,
        sideEffects = "",
        doxyDosage = 200 // default integer dosage for doxycycline
    )

    // Medication state
    var doxyLactose by remember { mutableStateOf(medicationData.doxyLactose) }
    var doxyMeal by remember { mutableStateOf(medicationData.doxyMeal) }
    var doxyDose by remember { mutableStateOf(medicationData.doxyDose) }
    var doxyWater by remember { mutableStateOf(medicationData.doxyWater) }
    var prednisoneDose by remember { mutableStateOf(medicationData.prednisoneDose) }
    var prednisoneMeal by remember { mutableStateOf(medicationData.prednisoneMeal) }
    var vitamins by remember { mutableStateOf(medicationData.vitamins) }
    var probioticsMorning by remember { mutableStateOf(medicationData.probioticsMorning) }
    var probioticsEvening by remember { mutableStateOf(medicationData.probioticsEvening) }
    var sideEffects by remember { mutableStateOf(medicationData.sideEffects) }
    var doxyDosage by remember { mutableStateOf(medicationData.doxyDosage) }

    LaunchedEffect(medicationData) {
        medicationData?.let {
            doxyLactose = it.doxyLactose
            doxyMeal = it.doxyMeal
            doxyDose = it.doxyDose
            doxyWater = it.doxyWater
            prednisoneDose = it.prednisoneDose
            prednisoneMeal = it.prednisoneMeal
            vitamins = it.vitamins
            probioticsMorning = it.probioticsMorning
            probioticsEvening = it.probioticsEvening
            sideEffects = it.sideEffects
            doxyDosage = it.doxyDosage
        }
    }

    // Save to the database whenever any toggle state changes
    fun saveMedicationData() {
        Log.d("MedicationScreen", "Saving medication data.")
        dbHelper.insertOrUpdateMedicationData(MedicationData(
            currentDate = openedDay,
            doxyLactose = doxyLactose,
            doxyMeal = doxyMeal,
            doxyDose = doxyDose,
            doxyWater = doxyWater,
            prednisoneDose = prednisoneDose,
            prednisoneMeal = prednisoneMeal,
            vitamins = vitamins,
            probioticsMorning = probioticsMorning,
            probioticsEvening = probioticsEvening,
            sideEffects = sideEffects,
            doxyDosage = doxyDosage
        ))
        dbHelper.exportToCSV(context)
    }

    // Define a Composable for Medication Tasks
    @Composable
    fun MedicationTask(description: String, state: Boolean, onToggle: (Boolean) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = state,
                onCheckedChange = { newState ->
                    onToggle(newState)
                    saveMedicationData()
                }
            )
        }
    }

    // Define a Composable for Doxycycline Dosage Counter
    @Composable
    fun DoxycyclineDosageCounter(
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
            Text(label, style = MaterialTheme.typography.bodyMedium)

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDecrement) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Minus One")
                }
                Text("$count mg", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 8.dp))
                IconButton(onClick = onIncrement) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Plus One")
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        /*item {
            Text(
                text = "Medications",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }*/

        // Medication: Doxycycline
        item {
            Text(
                text = "Doxycycline",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        item {
            MedicationTask("No lactose and calcium after 18:00", doxyLactose) {
                doxyLactose = it
            }
        }
        item {
            MedicationTask("Last major meal before 18:00", doxyMeal) {
                doxyMeal = it
            }
        }
        item {
            MedicationTask("Dose around 22:00", doxyDose) {
                doxyDose = it
            }
        }
        item {
            MedicationTask("100 ml water to absorb", doxyWater) {
                doxyWater = it
            }
        }
        item {
            DoxycyclineDosageCounter(
                label = "Daily dosage",
                count = doxyDosage,
                onIncrement = { doxyDosage += 50 },
                onDecrement = { if (doxyDosage > 50) doxyDosage -= 50 }
            )
        }

        // Spacer
        item {
            Spacer(Modifier.height(40.dp))
        }

        // Medication: Methylprednisolone
        item {
            Text(
                text = "Methylprednisolone",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        item {
            MedicationTask("Dose of 4 mg morning", prednisoneDose) {
                prednisoneDose = it
            }
        }
        item {
            MedicationTask("Ingested 5 minutes after meal", prednisoneMeal) {
                prednisoneMeal = it
            }
        }

        // Spacer
        item {
            Spacer(Modifier.height(40.dp))
        }

        // Medication: B-vitamin complex
        item {
            Text(
                text = "B-vitamin complex",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        item {
            MedicationTask("Dose of 100/50/1 mg morning", vitamins) {
                vitamins = it
            }
        }

        // Spacer
        item {
            Spacer(Modifier.height(40.dp))
        }

        // Medication: Probiotics
        item {
            Text(
                text = "Probiotics",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        item {
            MedicationTask("Morning dose", probioticsMorning) {
                probioticsMorning = it
            }
        }
        item {
            MedicationTask("Evening dose", probioticsEvening) {
                probioticsEvening = it
            }
        }

        // Spacer
        item {
            Spacer(Modifier.height(40.dp))
        }

        // Section: Side effects
        item {
            Text(
                text = "Side effects",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        item {
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
        // Spacer
        item {
            Spacer(Modifier.height(40.dp))
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
    Log.d("HealthTrackerScreen", "Recomposing HealthTrackerScreen for openedDay=$openedDay")

    // State for storing health data from the database
    var healthData by remember { mutableStateOf(HealthData(openedDay, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, "")) }

    // Initialize state for slider values based on healthData using mutableStateOf
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

    // Notes field state, using mutableStateOf
    var notes by remember { mutableStateOf(healthData.notes) }

    // Define the labels for each category
    val symptomLabels = listOf("Malaise", "Sore Throat", "Lymphadenopathy")
    val externalLabels = listOf("Exercise Level", "Stress Level", "Illness Impact")
    val mentalLabels = listOf("Depression", "Hopelessness")

    // Add scroll support
    val scrollState = rememberScrollState()

    // Function to update the database and healthData state
    fun updateData() {
        Log.d("HealthTrackerScreen", "updateData() called")
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
        healthData = updatedHealthData // Update healthData state
        dbHelper.exportToCSV(context)
    }

    // LaunchedEffect to update healthData when openedDay changes
    LaunchedEffect(openedDay) {
        Log.d("HealthTrackerScreen", "Fetching health data for openedDay=$openedDay")
        // Fetch health data from the database for the current openedDay
        val fetchedData = dbHelper.fetchHealthDataForDate(openedDay) ?: HealthData(openedDay, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, "")
        healthData = fetchedData

        // Set slider values based on fetched data
        symptomValues = listOf(
            fetchedData.malaise,
            fetchedData.soreThroat,
            fetchedData.lymphadenopathy
        )
        externalValues = listOf(
            fetchedData.exerciseLevel,
            fetchedData.stressLevel,
            fetchedData.illnessImpact
        )
        mentalValues = listOf(
            fetchedData.depression,
            fetchedData.hopelessness
        )
        notes = fetchedData.notes
    }

    // Call updateData whenever any slider or field changes
    LaunchedEffect(symptomValues, externalValues, mentalValues, notes) {
        Log.d("HealthTrackerScreen", "LaunchedEffect triggered")
        updateData()
    }

    // Main layout for the screen
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Symptoms section
        Text("Symptoms", style = MaterialTheme.typography.titleLarge)
        SymptomSliderGroup(
            items = symptomLabels.zip(symptomValues),
            onValuesChange = { updatedValues ->
                symptomValues = updatedValues
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Externals section
        Text("Externals", style = MaterialTheme.typography.titleLarge)
        ExternalSliderGroup(
            items = externalLabels.zip(externalValues),
            onValuesChange = { updatedValues ->
                externalValues = updatedValues
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Mental Health section
        Text("Mental Health", style = MaterialTheme.typography.titleLarge)
        MentalSliderGroup(
            items = mentalLabels.zip(mentalValues),
            onValuesChange = { updatedValues ->
                mentalValues = updatedValues
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Notes section
        Text("Notes", style = MaterialTheme.typography.titleLarge)
        TextInputField(
            text = notes,
            onTextChange = { newText -> notes = newText }
        )

        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
fun SymptomSliderGroup(
    items: List<Pair<String, Float>>,
    onValuesChange: (List<Float>) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items.forEachIndexed { index, item ->
            SliderInput(
                label = item.first,
                value = item.second,
                valueRange = 0f..4f,
                steps = 3,
                labels = listOf("None", "Very Mild", "Mild", "Moderate", "Severe"),
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
    onValuesChange: (List<Float>) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items.forEachIndexed { index, item ->
            SliderInput(
                label = item.first,
                value = item.second,
                valueRange = 0f..4f,
                steps = 3,
                labels = when (item.first) {
                    "Exercise Level" -> listOf("Bedbound", "Under 5 km", "Under 10 km", "Above 10 km", "Extraordinary")
                    "Stress Level" -> listOf("Serene", "Calm", "Mild", "Moderate", "Severe")
                    "Illness Impact" -> listOf("None", "Slight", "Noticeable", "Day-altering", "Extreme")
                    else -> listOf("None", "Very Mild", "Mild", "Moderate", "Severe")
                },
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
    onValuesChange: (List<Float>) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items.forEachIndexed { index, item ->
            SliderInput(
                label = item.first,
                value = item.second,
                valueRange = 0f..4f,
                steps = 3,
                labels = listOf("None", "Very Mild", "Mild", "Moderate", "Severe"),
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
fun SliderInput(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    labels: List<String>,
    onValueChange: (Float) -> Unit
) {
    // Map slider values to descriptive text
    val displayedLabel = labels[value.toInt()] // Display text based on the slider value

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Text(displayedLabel, style = MaterialTheme.typography.bodySmall)
    }
}

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

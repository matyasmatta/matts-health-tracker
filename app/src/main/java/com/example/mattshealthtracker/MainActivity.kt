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


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                // Provide a Surface for the HealthTrackerApp
                Surface {
                    HealthTrackerApp()
                }
            }
        }
    }
}

sealed class BottomNavItem(val route: String, val label: String, val icon: ImageVector) {
    object AddData : BottomNavItem("add_data", "Add Data", Icons.Default.Add)
    object Exercises : BottomNavItem("exercises", "Exercises", Icons.Default.Refresh) // New tab
    object MedicationTracking : BottomNavItem("medication_tracking", "Medication", Icons.Default.Check) // New Screen
}


@Composable
fun HealthTrackerApp() {
    var currentScreen by remember { mutableStateOf<BottomNavItem>(BottomNavItem.AddData) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf(
                    BottomNavItem.AddData,
                    BottomNavItem.Exercises,
                    BottomNavItem.MedicationTracking // Add to navigation bar
                ).forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentScreen == item,
                        onClick = { currentScreen = item }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                is BottomNavItem.AddData -> HealthTrackerScreen() // Add Data Screen
                is BottomNavItem.Exercises -> ExercisesScreen() // Browse Data Screen
                is BottomNavItem.MedicationTracking -> MedicationScreen() // Medication Screen
            }
        }
    }
}

@Composable
fun ExercisesScreen() {
    val context = LocalContext.current
    val dbhelper = ExerciseDatabaseHelper(context)
    val preferences = context.getSharedPreferences("exercise_tracker", Context.MODE_PRIVATE)
    val editor = preferences.edit()

    var pushUps by remember { mutableStateOf(preferences.getInt("pushUps", 0)) }
    var postureCorrections by remember { mutableStateOf(preferences.getInt("postureCorrections", 0)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Exercise Tracker", style = MaterialTheme.typography.headlineMedium)

        ExerciseCounter(
            label = "Push-Ups",
            count = pushUps,
            onIncrement = {
                pushUps += 5
                editor.putInt("pushUps", pushUps).apply()
            },
            onDecrement = {
                if (pushUps > 0) {
                    pushUps--
                    editor.putInt("pushUps", pushUps).apply()
                }
            }
        )

        ExerciseCounter(
            label = "Posture Corrections",
            count = postureCorrections,
            onIncrement = {
                postureCorrections++
                editor.putInt("postureCorrections", postureCorrections).apply()
            },
            onDecrement = {
                if (postureCorrections > 0) {
                    postureCorrections--
                    editor.putInt("postureCorrections", postureCorrections).apply()
                }
            }
        )

        // Submit Button
        Button(
            onClick = {

                // Insert data into the database
                dbhelper.insertData(
                    ExerciseData(
                        id = 0, // Auto-increment ID
                        timestamp = "", // Let the database handle the timestamp
                        pushups = pushUps,
                        posture = postureCorrections
                    )
                )
                dbhelper.exportToCSV(context)

                // Reset input fields
                pushUps = 0
                postureCorrections = 0

                Toast.makeText(context, "Data submitted successfully!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Submit")
        }
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
fun MedicationScreen() {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val currentDate = dateFormat.format(Date())

    var doxyLactose by remember { mutableStateOf(false) }
    var doxyMeal by remember { mutableStateOf(false) }
    var doxyDose by remember { mutableStateOf(false) }
    var doxyWater by remember { mutableStateOf(false) }
    var prednisoneDose by remember { mutableStateOf(false) }
    var prednisoneMeal by remember { mutableStateOf(false) }
    var vitamins by remember { mutableStateOf(false) }
    var probioticsMorning by remember { mutableStateOf(false) }
    var probioticsEvening by remember { mutableStateOf(false) }
    var sideEffects by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text(
                text = "Medications",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Medication: Doxycycline
        item {
            Text(
                text = "Doxycycline",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        item { MedicationTask("No lactose and calcium after 18:00", doxyLactose) { doxyLactose = it } }
        item { MedicationTask("Last major meal before 18:00", doxyMeal) { doxyMeal = it } }
        item { MedicationTask("Dose of 200 mg around 22:00", doxyDose) { doxyDose = it } }
        item { MedicationTask("100 ml water to absorb", doxyWater) { doxyWater = it } }

        // Spacer wrapped in item
        item {
            Spacer(Modifier.height(16.dp))
        }

        // Medication: Methylprednisolone
        item {
            Text(
                text = "Methylprednisolone",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        item { MedicationTask("Dose of 4 mg morning", prednisoneDose) { prednisoneDose = it } }
        item { MedicationTask("Ingested 5 minutes after meal", prednisoneMeal) { prednisoneMeal = it } }

        // Spacer wrapped in item
        item {
            Spacer(Modifier.height(16.dp))
        }

        // Medication: B-vitamin complex
        item {
            Text(
                text = "B-vitamin complex",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        item { MedicationTask("Dose of 100/50/1 mg morning", vitamins) { vitamins = it } }

        // Spacer wrapped in item
        item {
            Spacer(Modifier.height(16.dp))
        }

        // Medication: Probiotics
        item {
            Text(
                text = "Probiotics",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        item { MedicationTask("Morning dose", probioticsMorning) { probioticsMorning = it } }
        item { MedicationTask("Evening dose", probioticsEvening) { probioticsEvening = it } }

        // Spacer wrapped in item
        item {
            Spacer(Modifier.height(16.dp))
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
                onValueChange = { sideEffects = it },
                placeholder = { Text("Enter any side effects here...") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            // Submit button
            Button(
                onClick = {
                    // Create MedicationData object
                    val data = MedicationData(
                        timestamp = currentDate,
                        doxyLactose = doxyLactose,
                        doxyMeal = doxyMeal,
                        doxyDose = doxyDose,
                        doxyWater = doxyWater,
                        prednisoneDose = prednisoneDose,
                        prednisoneMeal = prednisoneMeal,
                        vitamins = vitamins,
                        probioticsMorning = probioticsMorning,
                        probioticsEvening = probioticsEvening,
                        sideEffects = sideEffects
                    )
                    // Insert into SQLite
                    val dbHelper = MedicationDatabaseHelper(context)  // Initialize your database helper
                    dbHelper.insertMedicationData(data)

                    // Show toast
                    Toast.makeText(context, "Medication data added and exported", Toast.LENGTH_SHORT).show()

                    // Save to CSV
                    dbHelper.exportToCSV(context)
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp) // Add padding as needed
            ) {
                Text("Submit")
            }
        }
    }
}

@Composable
fun MedicationTask(task: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Text(
            text = task,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodyMedium
        )
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
fun HealthTrackerScreen() {
    // Initialize state for slider values
    var symptomValues by remember { mutableStateOf(
        listOf(
            0f, // Malaise
            0f, // Sore Throat
            0f  // Lymphadenopathy
        )
    )}

    var externalValues by remember { mutableStateOf(
        listOf(
            0f, // Exercise Level
            0f, // Stress Level
            0f  // Illness Impact
        )
    )}

    var mentalValues by remember { mutableStateOf(
        listOf(
            0f, // Depression
            0f  // Hopelessness
        )
    )}

    // Notes field state
    var notes by remember { mutableStateOf("") }

    // Define the labels for each category
    val symptomLabels = listOf("Malaise", "Sore Throat", "Lymphadenopathy")
    val externalLabels = listOf("Exercise Level", "Stress Level", "Illness Impact")
    val mentalLabels = listOf("Depression", "Hopelessness")

    // Add scroll support
    val scrollState = rememberScrollState()

    // Submit data on button click
    val context = LocalContext.current
    val dbHelper = HealthDatabaseHelper(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState), // Enables scrolling
        verticalArrangement = Arrangement.spacedBy(20.dp) // Increased space between sections
    ) {
        // Symptoms section
        Text("Symptoms", style = MaterialTheme.typography.headlineMedium)
        SymptomSliderGroup(
            items = symptomLabels.zip(symptomValues),
            onValuesChange = { updatedValues ->
                symptomValues = updatedValues
            }
        )

        Spacer(modifier = Modifier.height(16.dp)) // Space after Symptoms section

        // Externals section
        Text("Externals", style = MaterialTheme.typography.headlineMedium)
        ExternalSliderGroup(
            items = externalLabels.zip(externalValues),
            onValuesChange = { updatedValues ->
                externalValues = updatedValues
            }
        )

        Spacer(modifier = Modifier.height(16.dp)) // Space after Externals section

        // Mental Health section
        Text("Mental Health", style = MaterialTheme.typography.headlineMedium)
        MentalSliderGroup(
            items = mentalLabels.zip(mentalValues),
            onValuesChange = { updatedValues ->
                mentalValues = updatedValues
            }
        )

        Spacer(modifier = Modifier.height(16.dp)) // Space after Mental Health section

        // Notes section
        Text("Notes", style = MaterialTheme.typography.headlineMedium)
        TextInputField(
            text = notes,
            onTextChange = { newText -> notes = newText }
        )

        // Submit button
        Button(
            onClick = {
                val currentTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val data = HealthData(
                    timestamp = currentTimestamp,
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
                dbHelper.insertData(data)
                dbHelper.exportToCSV(context)

                // Show a Toast to inform the user that the data was added successfully
                Toast.makeText(context, "Data added and exported successfully", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Submit")
        }
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
            when (item.first) {
                "Exercise Level" -> {
                    SliderInput(
                        label = item.first,
                        value = item.second,
                        valueRange = 0f..4f,
                        steps = 3,
                        labels = listOf("Bedbound", "Under 5 km", "Under 10 km", "Above 10 km", "Extraordinary"),
                        onValueChange = { newValue ->
                            val updatedValues = items.mapIndexed { i, pair ->
                                if (i == index) newValue else pair.second
                            }
                            onValuesChange(updatedValues)
                        }
                    )
                }
                "Stress Level" -> {
                    SliderInput(
                        label = item.first,
                        value = item.second,
                        valueRange = 0f..4f,
                        steps = 3,
                        labels = listOf("Serene", "Calm", "Mild", "Moderate", "Severe"),
                        onValueChange = { newValue ->
                            val updatedValues = items.mapIndexed { i, pair ->
                                if (i == index) newValue else pair.second
                            }
                            onValuesChange(updatedValues)
                        }
                    )
                }
                "Illness Impact" -> {
                    SliderInput(
                        label = item.first,
                        value = item.second,
                        valueRange = 0f..4f,
                        steps = 3,
                        labels = listOf("None", "Slight", "Noticeable", "Day-altering", "Extreme"),
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
fun TextInputField(text: String, onTextChange: (String) -> Unit) {
    Column {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewHealthTrackerScreen() {
    HealthTrackerApp()
}

package com.example.mattshealthtracker

import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// New Enum for CategoryContext
enum class CategoryContext {
    ROUTINE,    // Shows X/Y exercises, ~Z min left
    BREATHING,  // Shows total minutes
    NONE        // No additional context displayed
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

    // Existing exercise data for Push-ups and Posture Corrections
    // Fetch all exercise data for the openedDay
    val exerciseData = remember(openedDay) {
        exerciseDbHelper.fetchExerciseDataForDate(openedDay) ?: ExerciseData(
            currentDate = openedDay,
            pushups = 0,
            posture = 0,
            relaxMinutes = 0, // Initialize with defaults for new day
            sleepMinutes = 0,
            napMinutes = 0,
            focusMinutes = 0
        )
    }

    var pushUps by remember(openedDay) { mutableStateOf(exerciseData.pushups) }
    var postureCorrections by remember(openedDay) { mutableStateOf(exerciseData.posture) }

    // New state variables for Breathing exercises, initialized from fetched data
    var relaxMinutes by rememberSaveable(openedDay) { mutableStateOf(exerciseData.relaxMinutes) }
    var sleepMinutes by rememberSaveable(openedDay) { mutableStateOf(exerciseData.sleepMinutes) }
    var napMinutes by rememberSaveable(openedDay) { mutableStateOf(exerciseData.napMinutes) }
    var focusMinutes by rememberSaveable(openedDay) { mutableStateOf(exerciseData.focusMinutes) }


    // Fetch routine data
    val routineData = remember(openedDay) {
        routineDbHelper.getRoutineDataForDate(openedDay)
    }

    // State to hold the checked states, initialized with data from the database
    val morningChecks = remember(openedDay) { mutableStateOf(routineData["am"] ?: emptyMap()) }
    val eveningChecks = remember(openedDay) { mutableStateOf(routineData["pm"] ?: emptyMap()) }

    // Single function to update ALL exercise data
    fun updateExerciseData() {
        exerciseDbHelper.insertOrUpdateData(
            ExerciseData(
                currentDate = openedDay,
                pushups = pushUps,
                posture = postureCorrections,
                relaxMinutes = relaxMinutes,
                sleepMinutes = sleepMinutes,
                napMinutes = napMinutes,
                focusMinutes = focusMinutes
            )
        )
        Log.d("ExercisesScreen", "Updated exercise data: Pushups=$pushUps, Posture=$postureCorrections, " +
                "Relax=${relaxMinutes}, Sleep=${sleepMinutes}, Nap=${napMinutes}, Focus=${focusMinutes}")
        // exerciseDbHelper.exportToCSV(context) // Consider when and how often to export
    }

    // Function to update routine data in the database
    fun updateRoutineData() {
        val dataToSave = mapOf(
            "am" to morningChecks.value,
            "pm" to eveningChecks.value
        )
        routineDbHelper.insertOrUpdateRoutineData(openedDay, dataToSave)
        Log.d("ExercisesScreen", "Updated routine data for $openedDay: $dataToSave")
        // routineDbHelper.exportToCSV(context) // Consider when and how often to export
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Basic Exercises Section
        BasicExercisesSection(
            pushUps = pushUps,
            onPushUpsIncrement = {
                pushUps += 5
                updateExerciseData() // Call the unified update function
            },
            onPushUpsDecrement = {
                if (pushUps > 0) {
                    pushUps--
                    updateExerciseData() // Call the unified update function
                }
            },
            postureCorrections = postureCorrections,
            onPostureCorrectionsIncrement = {
                postureCorrections++
                updateExerciseData() // Call the unified update function
            },
            onPostureCorrectionsDecrement = {
                if (postureCorrections > 0) {
                    postureCorrections--
                    updateExerciseData() // Call the unified update function
                }
            }
        )

        //Divider(modifier = Modifier.padding(vertical = 16.dp))

        // New Breathing Section
        BreathingSection(
            relaxMinutes = relaxMinutes,
            onRelaxIncrement = {
                relaxMinutes++
                updateExerciseData() // Call the unified update function
            },
            onRelaxDecrement = {
                if (relaxMinutes > 0) {
                    relaxMinutes--
                    updateExerciseData() // Call the unified update function
                }
            },
            sleepMinutes = sleepMinutes,
            onSleepIncrement = {
                sleepMinutes++
                updateExerciseData() // Call the unified update function
            },
            onSleepDecrement = {
                if (sleepMinutes > 0) {
                    sleepMinutes--
                    updateExerciseData() // Call the unified update function
                }
            },
            napMinutes = napMinutes,
            onNapIncrement = {
                napMinutes++
                updateExerciseData() // Call the unified update function
            },
            onNapDecrement = {
                if (napMinutes > 0) {
                    napMinutes--
                    updateExerciseData() // Call the unified update function
                }
            },
            focusMinutes = focusMinutes,
            onFocusIncrement = {
                focusMinutes++
                updateExerciseData() // Call the unified update function
            },
            onFocusDecrement = {
                if (focusMinutes > 0) {
                    focusMinutes--
                    updateExerciseData() // Call the unified update function
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
fun MinuteCounter(
    label: String,
    minutes: Int,
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
                Icon(imageVector = Icons.Default.Remove, contentDescription = "Minus One Minute")
            }
            Text("$minutes min", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 8.dp))
            IconButton(onClick = onIncrement) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Plus One Minute")
            }
        }
    }
}


@Composable
fun BasicExercisesSection(
    pushUps: Int,
    onPushUpsIncrement: () -> Unit,
    onPushUpsDecrement: () -> Unit,
    postureCorrections: Int,
    onPostureCorrectionsIncrement: () -> Unit,
    onPostureCorrectionsDecrement: () -> Unit
) {
    val completedBasics = (if (pushUps > 0) 1 else 0) + (if (postureCorrections > 0) 1 else 0)
    val totalBasics = 2
    val estimatedMinutesBasics = 0 // Not relevant for this section's display

    ExpandableRoutineSection(
        title = "💫  Basics",
        defaultExpanded = true,
        contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 8.dp),
        totalExercises = totalBasics,
        completedExercises = completedBasics,
        totalMinutes = estimatedMinutesBasics,
        categoryContext = CategoryContext.NONE, // No specific context display for Basics
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ExerciseCounter(
                    label = "Push-Ups",
                    count = pushUps,
                    onIncrement = onPushUpsIncrement,
                    onDecrement = onPushUpsDecrement
                )
                ExerciseCounter(
                    label = "Posture Corrections",
                    count = postureCorrections,
                    onIncrement = onPostureCorrectionsIncrement,
                    onDecrement = onPostureCorrectionsDecrement
                )
            }
        }
    )
}

@Composable
fun BreathingSection(
    relaxMinutes: Int,
    onRelaxIncrement: () -> Unit,
    onRelaxDecrement: () -> Unit,
    sleepMinutes: Int,
    onSleepIncrement: () -> Unit,
    onSleepDecrement: () -> Unit,
    napMinutes: Int,
    onNapIncrement: () -> Unit,
    onNapDecrement: () -> Unit,
    focusMinutes: Int,
    onFocusIncrement: () -> Unit,
    onFocusDecrement: () -> Unit
) {
    // Total estimated minutes will be the sum of all entered minutes for display purposes
    val totalMinutes = relaxMinutes + sleepMinutes +
            napMinutes + focusMinutes

    val isBreathingCompleted = totalMinutes >= 20

    ExpandableRoutineSection(
        title = "🫁  Breathing",
        defaultExpanded = true,
        contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 8.dp),
        totalExercises = 0, // Not used for completion display in this context
        completedExercises = if (isBreathingCompleted) 1 else 0, // Pass 1 if completed, 0 otherwise
        totalMinutes = totalMinutes,
        categoryContext = CategoryContext.BREATHING, // Display total minutes for breathing
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MinuteCounter(
                    label = "Relax",
                    minutes = relaxMinutes,
                    onIncrement = onRelaxIncrement,
                    onDecrement = onRelaxDecrement
                )
                MinuteCounter(
                    label = "Sleep",
                    minutes = sleepMinutes,
                    onIncrement = onSleepIncrement,
                    onDecrement = onSleepDecrement
                )
                MinuteCounter(
                    label = "Nap",
                    minutes = napMinutes,
                    onIncrement = onNapIncrement,
                    onDecrement = onNapDecrement
                )
                MinuteCounter(
                    label = "Focus",
                    minutes = focusMinutes,
                    onIncrement = onFocusIncrement,
                    onDecrement = onFocusDecrement
                )
            }
        }
    )
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
    val completedMorningExercises = morningChecks.value.count { it.value }
    val completedEveningExercises = eveningChecks.value.count { it.value }

    val totalMorningExercises = 20
    val totalEveningExercises = 22

    val estimatedMorningMinutes = 15
    val estimatedEveningMinutes = 20


    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExpandableRoutineSection(
            title = "☀️  Morning Routine",
            defaultExpanded = false,
            contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 8.dp),
            totalExercises = totalMorningExercises,
            completedExercises = completedMorningExercises,
            totalMinutes = estimatedMorningMinutes,
            categoryContext = CategoryContext.ROUTINE, // Display exercise progress and time left
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
            defaultExpanded = false,
            contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 8.dp),
            totalExercises = totalEveningExercises,
            completedExercises = completedEveningExercises,
            totalMinutes = estimatedEveningMinutes,
            categoryContext = CategoryContext.ROUTINE, // Display exercise progress and time left
            content = {
                PostureChecklist(
                    innerPadding = PaddingValues(vertical = 8.dp),
                    dbHelper = dbHelper,
                    date = date,
                    amPm = "pm",
                    checks = eveningChecks,
                    onCheckChange = onEveningCheckChange
                )
                TMJReleaseChecklist(
                    innerPadding = PaddingValues(vertical = 8.dp),
                    dbHelper = dbHelper,
                    date = date,
                    amPm = "pm",
                    checks = eveningChecks,
                    onCheckChange = onEveningCheckChange
                )
                NeckStrengtheningStretchingChecklist(
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
    defaultExpanded: Boolean = false,
    content: @Composable () -> Unit,
    contentPadding: PaddingValues = PaddingValues(all = 8.dp),
    totalExercises: Int,
    completedExercises: Int,
    totalMinutes: Int,
    // New parameter to control the context displayed
    categoryContext: CategoryContext = CategoryContext.ROUTINE
) {
    var expanded by rememberSaveable { mutableStateOf(defaultExpanded) }

    // How completion is determined depends on the category context
    val isCompleted = when (categoryContext) {
        CategoryContext.ROUTINE -> completedExercises > 0 && completedExercises == totalExercises
        CategoryContext.BREATHING -> totalMinutes >= 20 // Breathing is complete if total minutes >= 20
        CategoryContext.NONE -> completedExercises > 0 && completedExercises == totalExercises // Fallback for Basic Exercises
    }

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
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (isCompleted) Color.Gray else LocalContentColor.current
                )
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Display context based on categoryContext
                when (categoryContext) {
                    CategoryContext.ROUTINE -> {
                        // Re-calculate leftMinutes specifically for ROUTINE context
                        val leftMinutes = if (totalExercises > 0) {
                            val remainingProportion = (totalExercises - completedExercises).toFloat() / totalExercises.toFloat()
                            (totalMinutes * remainingProportion).roundToInt()
                        } else {
                            0
                        }
                        val exercisesText = "$completedExercises/$totalExercises done"
                        val timeText = "~$leftMinutes min"
                        Text(
                            "$exercisesText, $timeText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    CategoryContext.BREATHING -> {
                        // For breathing, show the total accumulated minutes
                        Text(
                            "$totalMinutes min",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    CategoryContext.NONE -> {
                        // For Basic exercises, show X/X exercises if some progress is made
                        if (completedExercises > 0) {
                            Text(
                                "$completedExercises/$totalExercises exercises",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                }

                IconButton(onClick = { expanded = !expanded }) {
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
    Column(modifier = Modifier
        .padding(start = 16.dp)
        .padding(innerPadding)) {
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
    Column(modifier = Modifier
        .padding(start = 16.dp)
        .padding(innerPadding)) {
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
    Column(modifier = Modifier
        .padding(start = 16.dp)
        .padding(innerPadding)) {
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
        "Child's Pose: Kneel, fold forward, arms stretched — 30–60 sec",
        "Body Cross: Like I was taught on my physiotherapy class — 15 sec"
    )
    Text("🧘 General Stretches", style = MaterialTheme.typography.bodyMedium)
    Column(modifier = Modifier
        .padding(start = 16.dp)
        .padding(innerPadding)) {
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
    Column(modifier = Modifier
        .padding(start = 16.dp)
        .padding(innerPadding)) {
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
    Column(modifier = Modifier
        .padding(start = 16.dp)
        .padding(innerPadding)) {
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
    Column(modifier = Modifier
        .padding(start = 16.dp)
        .padding(innerPadding)) {
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
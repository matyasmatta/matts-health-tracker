package com.example.mattshealthtracker

import android.content.Context
import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height // Added import
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card // Added import
import androidx.compose.material3.CardDefaults // Added import
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection // Added import
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mattshealthtracker.AppUiElements.CollapsibleCard
import com.example.mattshealthtracker.AppUiElements.QuickStatsCard
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// New Enum for CategoryContext
/* NO LONGER NEEDED
enum class CategoryContext {
    ROUTINE,    // Shows X/Y exercises, ~Z min left
    BREATHING,  // Shows total minutes
    NONE        // No additional context displayed
}
*/

@OptIn(ExperimentalMaterial3Api::class) // For TopAppBar
@Composable
fun ExercisesScreen(openedDay: String) {
    val context = LocalContext.current
    val exerciseDbHelper = remember { ExerciseDatabaseHelper(context) }
    val routineDbHelper = remember { RoutineDatabaseHelper(context) }

    var showCustomizeSectionsDialog by remember { mutableStateOf(false) }
    val visibleSectionIdsFromAppGlobals by rememberUpdatedState(AppGlobals.visibleExerciseSectionIds)
    var trendsCardExpanded by rememberSaveable { mutableStateOf(false) }
    val healthConnectViewModel: HealthConnectViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(HealthConnectViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    // Pass applicationContext to the ViewModel constructor
                    return HealthConnectViewModel(context.applicationContext) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    )

    val coroutineScope = rememberCoroutineScope()

    // --- State for QuickStatsCard ---
    var stepsToday by remember(openedDay) { mutableStateOf<Long?>(null) }
    var isLoadingSteps by remember(openedDay) { mutableStateOf(true) }

    var currentWeight by remember(openedDay) { mutableStateOf<Double?>(null) }
    var isLoadingWeight by remember(openedDay) { mutableStateOf(true) }

    var exerciseMinutesToday by remember(openedDay) { mutableStateOf<Long?>(null) }
    var isLoadingExercise by remember(openedDay) { mutableStateOf(true) }

    // --- Updated LaunchedEffect to load all data ---
    LaunchedEffect(openedDay) {
        // This is the original call
        healthConnectViewModel.fetchDataForDay(openedDay)

        // Reset loading states for QuickStatsCard
        isLoadingSteps = true
        isLoadingWeight = true
        isLoadingExercise = true

        // Fetch steps
        coroutineScope.launch {
            try {
                // ASSUMPTION: You have a method 'getStepsForDay'
                stepsToday =
                    healthConnectViewModel.healthConnectIntegrator.getStepsForDay(openedDay)
            } catch (e: Exception) {
                Log.e("ExercisesScreen", "Failed to load steps", e)
            } finally {
                isLoadingSteps = false
            }
        }

        // Fetch weight
        coroutineScope.launch {
            try {
                // This method is used by TrendsCard, so it should exist
                currentWeight =
                    healthConnectViewModel.healthConnectIntegrator.getWeightForDay(openedDay)
            } catch (e: Exception) {
                Log.e("ExercisesScreen", "Failed to load weight", e)
            } finally {
                isLoadingWeight = false
            }
        }

        // Fetch exercise minutes
        coroutineScope.launch {
            try {
                // ASSUMPTION: You have a method 'getTotalExerciseMinutesForDay'
                exerciseMinutesToday =
                    healthConnectViewModel.healthConnectIntegrator.getTotalExerciseMinutesForDay(
                        openedDay
                    )
            } catch (e: Exception) {
                Log.e("ExercisesScreen", "Failed to load exercise minutes", e)
            } finally {
                isLoadingExercise = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exerciseDbHelper.close()
            routineDbHelper.close()
        }
    }

    val exerciseData = remember(openedDay, visibleSectionIdsFromAppGlobals) {
        Log.d("ExercisesScreen", "Fetching exerciseData for $openedDay")
        exerciseDbHelper.fetchExerciseDataForDate(openedDay) ?: ExerciseData(
            currentDate = openedDay, pushups = 0, posture = 0,
            relaxMinutes = 0, sleepMinutes = 0, napMinutes = 0, focusMinutes = 0
        )
    }

    var pushUps by remember(exerciseData) { mutableStateOf(exerciseData.pushups) }
    var postureCorrections by remember(exerciseData) { mutableStateOf(exerciseData.posture) }
    var relaxMinutes by rememberSaveable(
        exerciseData.relaxMinutes,
        key = "relax_$openedDay"
    ) { mutableStateOf(exerciseData.relaxMinutes) }
    var sleepMinutes by rememberSaveable(
        exerciseData.sleepMinutes,
        key = "sleep_$openedDay"
    ) { mutableStateOf(exerciseData.sleepMinutes) }
    var napMinutes by rememberSaveable(
        exerciseData.napMinutes,
        key = "nap_$openedDay"
    ) { mutableStateOf(exerciseData.napMinutes) }
    var focusMinutes by rememberSaveable(
        exerciseData.focusMinutes,
        key = "focus_$openedDay"
    ) { mutableStateOf(exerciseData.focusMinutes) }

    val routineRawData = remember(openedDay, visibleSectionIdsFromAppGlobals) {
        Log.d("ExercisesScreen", "Fetching routineData for $openedDay")
        routineDbHelper.getRoutineDataForDate(openedDay)
    }
    val morningChecks =
        remember(routineRawData) { mutableStateOf(routineRawData["am"] ?: emptyMap()) }
    val eveningChecks =
        remember(routineRawData) { mutableStateOf(routineRawData["pm"] ?: emptyMap()) }

    fun updateExerciseData() {
        val currentExerciseData = ExerciseData(
            currentDate = openedDay,
            pushups = pushUps,
            posture = postureCorrections,
            relaxMinutes = relaxMinutes,
            sleepMinutes = sleepMinutes,
            napMinutes = napMinutes,
            focusMinutes = focusMinutes
        )
        exerciseDbHelper.insertOrUpdateData(currentExerciseData)
        Log.d("ExercisesScreen", "Updated exercise data: $currentExerciseData")
    }

    fun updateRoutineData() {
        val dataToSave = mapOf(
            "am" to morningChecks.value,
            "pm" to eveningChecks.value
        )
        routineDbHelper.insertOrUpdateRoutineData(openedDay, dataToSave)
        Log.d("ExercisesScreen", "Updated routine data for $openedDay: $dataToSave")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Routines and Exercises") }, // Your desired title
                actions = {
                    IconButton(onClick = { showCustomizeSectionsDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Customize Exercise Sections"
                        )
                    }
                }
            )
        }
    ) { paddingValues -> // Content lambda for Scaffold, provides paddingValues
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // Apply padding from the Scaffold
                .padding(horizontal = 16.dp, vertical = 16.dp) // Your additional content padding
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // --- ADDED QUICK STATS CARD ---
            QuickStatsCard(
                stat1 = StatItem(
                    title = "Steps",
                    icon = Icons.Default.DirectionsWalk,
                    valueString = stepsToday?.toString() ?: (if (isLoadingSteps) null else "0"),
                    isLoading = isLoadingSteps,
                    iconContentDescription = "Steps today"
                ),
                stat2 = StatItem(
                    title = "Weight",
                    icon = Icons.Default.MonitorWeight,
                    valueString = currentWeight?.let { String.format("%.1f kg", it) }
                        ?: (if (isLoadingWeight) null else "N/A"),
                    isLoading = isLoadingWeight,
                    iconContentDescription = "Current weight"
                ),
                stat3 = StatItem(
                    title = "Exercise",
                    icon = Icons.Default.FitnessCenter, // Changed from Fire to Fitness
                    valueString = exerciseMinutesToday?.let { "$it min" }
                        ?: (if (isLoadingExercise) null else "0 min"),
                    isLoading = isLoadingExercise,
                    iconContentDescription = "Exercise minutes today"
                )
            )
            // --- END OF QUICK STATS CARD ---

            var sectionsRendered = 0

            if (AppGlobals.isExerciseSectionVisible(ExerciseScreenSectionInfo.Basics.id)) {
                BasicExercisesSection(
                    pushUps = pushUps,
                    onPushUpsIncrement = { pushUps += 5; updateExerciseData() },
                    onPushUpsDecrement = {
                        if (pushUps > 0) {
                            pushUps--; updateExerciseData()
                        }
                    },
                    postureCorrections = postureCorrections,
                    onPostureCorrectionsIncrement = { postureCorrections++; updateExerciseData() },
                    onPostureCorrectionsDecrement = {
                        if (postureCorrections > 0) {
                            postureCorrections--; updateExerciseData()
                        }
                    }
                )
                sectionsRendered++
            }

            if (AppGlobals.isExerciseSectionVisible(ExerciseScreenSectionInfo.Breathing.id)) {
                if (sectionsRendered > 0 && AppGlobals.isExerciseSectionVisible(
                        ExerciseScreenSectionInfo.Basics.id
                    )
                ) {
                    // Optional: Add divider if both Basics and Breathing are visible and Basics was rendered first
                    // Or manage dividers more granularly
                }
                BreathingSection(
                    relaxMinutes = relaxMinutes,
                    onRelaxIncrement = { relaxMinutes++; updateExerciseData() },
                    onRelaxDecrement = {
                        if (relaxMinutes > 0) {
                            relaxMinutes--; updateExerciseData()
                        }
                    },
                    sleepMinutes = sleepMinutes,
                    onSleepIncrement = { sleepMinutes++; updateExerciseData() },
                    onSleepDecrement = {
                        if (sleepMinutes > 0) {
                            sleepMinutes--; updateExerciseData()
                        }
                    },
                    napMinutes = napMinutes,
                    onNapIncrement = { napMinutes++; updateExerciseData() },
                    onNapDecrement = {
                        if (napMinutes > 0) {
                            napMinutes--; updateExerciseData()
                        }
                    },
                    focusMinutes = focusMinutes,
                    onFocusIncrement = { focusMinutes++; updateExerciseData() },
                    onFocusDecrement = {
                        if (focusMinutes > 0) {
                            focusMinutes--; updateExerciseData()
                        }
                    }
                )
                sectionsRendered++
            }

            if (AppGlobals.isExerciseSectionVisible(ExerciseScreenSectionInfo.Routines.id)) {
                RoutineChecklist(
                    dbHelper = routineDbHelper,
                    date = openedDay,
                    morningChecks = morningChecks,
                    eveningChecks = eveningChecks,
                    onMorningCheckChange = { newChecks ->
                        morningChecks.value = newChecks; updateRoutineData()
                    },
                    onEveningCheckChange = { newChecks ->
                        eveningChecks.value = newChecks; updateRoutineData()
                    }
                )
                sectionsRendered++
            }

            if (AppGlobals.isExerciseSectionVisible(ExerciseScreenSectionInfo.Weight.id)) {
                TrendsCard(
                    expanded = trendsCardExpanded,
                    onExpandedChange = { trendsCardExpanded = it },
                    healthConnectViewModel = healthConnectViewModel,
                    openedDay = openedDay
                )
                sectionsRendered++
            }

            if (sectionsRendered == 0) {
                Text(
                    "No exercise sections are currently visible. Click the pencil icon (top-right) to customize.",
                    modifier = Modifier
                        .padding(16.dp) // Additional padding for this message within the Column
                        .fillMaxWidth(), // Make it take full width for centering text
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, // Center the text
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    // Dialog remains outside the Scaffold's content lambda
    if (showCustomizeSectionsDialog) {
        CustomizeExerciseSectionsDialog(
            onDismissRequest = { showCustomizeSectionsDialog = false },
            appGlobals = AppGlobals,
            context = context
        )
    }
}

// --- Dialog Composable for Customizing Exercise Sections ---
@OptIn(ExperimentalMaterial3Api::class) // For AlertDialog
@Composable
fun CustomizeExerciseSectionsDialog(
    onDismissRequest: () -> Unit,
    appGlobals: AppGlobals, // Type is AppGlobals object
    context: Context
) {
    val allExerciseSections = remember { ExerciseScreenSectionInfo.getAllSections() }

    // Use AppGlobals.visibleExerciseSectionIds as key for remember to re-initialize
    // if the global state changes while dialog is not shown.
    var tempSelectedIds by remember(appGlobals.visibleExerciseSectionIds) {
        mutableStateOf(appGlobals.visibleExerciseSectionIds)
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Customize Exercise Sections") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Select the sections you want to see on this screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                allExerciseSections.forEach { sectionInfo ->
                    val isChecked = sectionInfo.id in tempSelectedIds
                    // Core sections are visually toggleable but AppGlobals enforces them on save
                    val canToggleSwitch = !sectionInfo.isCoreSection

                    val updateSelection = { newCheckedState: Boolean ->
                        // Allow toggling core features visually in the dialog,
                        // AppGlobals.updateVisibleExerciseSectionIds will enforce core features are kept.
                        val newSelected = tempSelectedIds.toMutableSet()
                        if (newCheckedState) {
                            newSelected.add(sectionInfo.id)
                        } else {
                            // Only allow removing if it's not a core section
                            if (!sectionInfo.isCoreSection) {
                                newSelected.remove(sectionInfo.id)
                            } else {
                                Log.d(
                                    "CustomizeDialog",
                                    "Core section ${sectionInfo.defaultLabel} cannot be unchecked here, will be enforced on Apply."
                                )
                                // Optionally show a Toast or keep it checked visually
                                // For simplicity, we let it be visually unchecked, AppGlobals fixes it.
                                newSelected.remove(sectionInfo.id) // Allow visual uncheck
                            }
                        }
                        tempSelectedIds = newSelected
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable( // Row click toggles the state
                                onClick = {
                                    // If it's a core section and currently checked, clicking row won't uncheck it.
                                    // If it's not core, or if it's core and unchecked (to check it), then toggle.
                                    if (!sectionInfo.isCoreSection || !isChecked) {
                                        updateSelection(!isChecked)
                                    } else {
                                        Log.d(
                                            "CustomizeDialog",
                                            "Clicked row of core section ${sectionInfo.defaultLabel}, no change as it's already checked."
                                        )
                                    }
                                }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = sectionInfo.defaultIcon,
                            contentDescription = sectionInfo.defaultLabel,
                            modifier = Modifier.size(24.dp),
                            tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = sectionInfo.defaultLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = isChecked,
                            onCheckedChange = { newCheckedState ->
                                updateSelection(newCheckedState)
                            },
                            // Switch for core sections is enabled but its "off" state won't persist if it's core
                            enabled = true, // Always enable switch for visual feedback
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                            )
                        )
                    }
                    if (sectionInfo != allExerciseSections.last()) {
                        Divider(modifier = Modifier.padding(start = 40.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    AppGlobals.updateVisibleExerciseSectionIds(context, tempSelectedIds)
                    onDismissRequest()
                }
            ) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text("Cancel") }
        }
    )
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
    // val estimatedMinutesBasics = 0 // Not relevant for this section's display

    var expanded by rememberSaveable { mutableStateOf(true) } // State is now managed here

    CollapsibleCard(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        titleContent = {
            val isCompleted = completedBasics > 0 && completedBasics == totalBasics
            Text(
                "💫  Basics",
                style = MaterialTheme.typography.titleMedium.copy(
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (isCompleted) Color.Gray else LocalContentColor.current
                )
            )
        },
        quickGlanceInfo = {
            if (completedBasics > 0) {
                Text(
                    "$completedBasics/$totalBasics exercises",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        },
        expandableContent = {
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

    // val isBreathingCompleted = totalMinutes >= 20 // This logic is now in titleContent

    var expanded by rememberSaveable { mutableStateOf(true) } // State is now managed here

    CollapsibleCard(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        titleContent = {
            val isCompleted = totalMinutes >= 20
            Text(
                "🫁  Breathing",
                style = MaterialTheme.typography.titleMedium.copy(
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (isCompleted) Color.Gray else LocalContentColor.current
                )
            )
        },
        quickGlanceInfo = {
            Text(
                "$totalMinutes min",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp)
            )
        },
        expandableContent = {
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

    // State for each card
    var morningExpanded by rememberSaveable { mutableStateOf(false) }
    var eveningExpanded by rememberSaveable { mutableStateOf(false) }


    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CollapsibleCard(
            expanded = morningExpanded,
            onExpandedChange = { morningExpanded = it },
            titleContent = {
                val isCompleted =
                    completedMorningExercises > 0 && completedMorningExercises == totalMorningExercises
                Text(
                    "☀️  Morning Routine",
                    style = MaterialTheme.typography.titleMedium.copy(
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (isCompleted) Color.Gray else LocalContentColor.current
                    )
                )
            },
            quickGlanceInfo = {
                val leftMinutes = if (totalMorningExercises > 0) {
                    val remainingProportion =
                        (totalMorningExercises - completedMorningExercises).toFloat() / totalMorningExercises.toFloat()
                    (estimatedMorningMinutes * remainingProportion).roundToInt()
                } else {
                    0
                }
                val exercisesText = "$completedMorningExercises/$totalMorningExercises done"
                val timeText = "~$leftMinutes min"
                Text(
                    "$exercisesText, $timeText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )
            },
            expandableContent = {
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

        CollapsibleCard(
            expanded = eveningExpanded,
            onExpandedChange = { eveningExpanded = it },
            titleContent = {
                val isCompleted =
                    completedEveningExercises > 0 && completedEveningExercises == totalEveningExercises
                Text(
                    "🌙  Evening Routine",
                    style = MaterialTheme.typography.titleMedium.copy(
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (isCompleted) Color.Gray else LocalContentColor.current
                    )
                )
            },
            quickGlanceInfo = {
                val leftMinutes = if (totalEveningExercises > 0) {
                    val remainingProportion =
                        (totalEveningExercises - completedEveningExercises).toFloat() / totalEveningExercises.toFloat()
                    (estimatedEveningMinutes * remainingProportion).roundToInt()
                } else {
                    0
                }
                val exercisesText = "$completedEveningExercises/$totalEveningExercises done"
                val timeText = "~$leftMinutes min"
                Text(
                    "$exercisesText, $timeText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )
            },
            expandableContent = {
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

// --- ExpandableRoutineSection has been REMOVED ---

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
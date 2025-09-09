package com.example.mattshealthtracker

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp

// Imports for icons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// Imports for drag and drop
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import java.util.UUID

// For making the whole page scrollable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Close // For delete button in FoodItemInput
import androidx.compose.material.icons.filled.Done // For check button in FoodItemInput
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.outlined.StarHalf
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.StarHalf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButtonDefaults.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.requestFocus
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.units.calories
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.mattshealthtracker.AppUiElements.CollapsibleCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.collections.toTypedArray
import kotlin.math.floor
import kotlin.math.roundToInt
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date // For formatting timestamp

// Data class to represent each card's properties
/*data class HealthCard(
    val id: String,
    val title: String,
    var isVisible: Boolean,
    val defaultExpanded: Boolean = false
)*/

/*// --- NEW DATA MODELS FOR FOOD TRACKING ---
enum class MealType(val displayName: String, val emoji: String) {
    BREAKFAST("Breakfast", "🍳"),
    SNACK1("Morning Snack", "☀️"), // Morning Snack
    LUNCH("Lunch", "🥗"),
    SNACK2("Afternoon Snack", "🧃"), // Afternoon Snack - used a juice box emoji, pick your favorite!
    DINNER("Dinner", "🥘"),
    SNACK3("Nightly Snack", "🌙"); // Nightly Snack

    // Optional: override toString if you were using it directly for display before
    // override fun toString(): String {
    //     return "$emoji $displayName"
    // }
}*/

/*data class FoodItem(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "New Food Item",
    var calories: Int = 0,
    var healthyRating: Float = 0.5f, // 0.0 to 1.0
    var lprFriendlyRating: Float = 0.5f, // 0.0 to 1.0
    var ingredients: String = "" // For now, a simple string, later a list or search result
)*/

// Data Class for LPR Meal Details
data class LprMealDetail(
    val date: String, // "yyyy-MM-dd"
    val mealType: MealType, // Enum: BREAKFAST, LUNCH, etc.
    val tookItopride: Boolean = false,
    val itoprideTimestamp: Long? = null, // Store as Epoch millis when checked
    val startedUprightTimer: Boolean = false, // To know if timer was ever started
    val completedUprightRequirement: Boolean = false
    // You could add a primary key if needed, e.g., (date, mealType) combined or a separate ID
)


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DietScreen(openedDay: String) {
    val context = LocalContext.current
    val mealTrackerHelper = remember { MealTrackerHelper(context) }
    val groceryDatabaseHelper = remember { GroceryDatabaseHelper(context) }
    val lprMealDetailHelper = remember { LprMealDetailHelper(context) } // New helper
    val coroutineScope = rememberCoroutineScope()

    val foodItemsByMeal = remember {
        mutableStateMapOf<MealType, SnapshotStateList<FoodItem>>().apply {
            MealType.entries.forEach { mealType ->
                put(mealType, mutableStateListOf())
            }
        }
    }
    val lprDetailsByMeal =
        remember { mutableStateMapOf<MealType, LprMealDetail>() } // New state for LPR details

    // Timer states for "15 minutes upright" - will be per meal type
    val uprightTimerRunning = remember { mutableStateMapOf<MealType, Boolean>() }
    val uprightTimerRemainingMillis = remember { mutableStateMapOf<MealType, Long>() }

    var showEditCardsDialog by rememberSaveable { mutableStateOf(false) }


    val cards = remember {
        mutableStateListOf(
            HealthCard("energy", "🔥  Energy Use Today", true, false),
            HealthCard("trends", "📊  Weight Trends", true, false),
            HealthCard("food_input", "🍎  Food", true, true)
        )
    }

    val cardExpandedStates = remember {
        mutableStateMapOf<String, Boolean>().apply {
            cards.forEach { card ->
                this[card.id] = card.defaultExpanded
            }
        }
    }

    // Initialize HealthConnectViewModel
    val healthConnectViewModel: HealthConnectViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(HealthConnectViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    // Pass applicationContext to the ViewModel constructor
                    return HealthConnectViewModel(context.applicationContext) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    )

    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissionsResultMap ->
            val allRequiredPermissionsGranted =
                healthConnectViewModel.permissions.all { permissionString ->
                    permissionsResultMap[permissionString] == true
                }

            if (allRequiredPermissionsGranted) {
                Log.d("FoodScreen", "All Health Connect permissions granted via launcher.")
                healthConnectViewModel.permissionsGranted = true
                coroutineScope.launch {
                    Log.d("FoodScreen", "Permissions granted, fetching data for day: $openedDay")
                    healthConnectViewModel.fetchDataForDay(openedDay)
                }
            } else {
                Log.w("FoodScreen", "Not all Health Connect permissions granted via launcher.")
                healthConnectViewModel.permissionsGranted = false
                healthConnectViewModel.errorMessage =
                    "Not all necessary Health Connect permissions were granted. Some features might be unavailable."
            }
        }
    )

    // NEW LaunchedEffect for loading LPR meal details
    LaunchedEffect(openedDay, lprMealDetailHelper) {
        Log.d("DietScreen", "LaunchedEffect for openedDay: $openedDay. Loading LPR details.")
        lprDetailsByMeal.clear() // Clear previous day's details
        val loadedLprDetails = lprMealDetailHelper.getAllLprMealDetailsForDate(openedDay)
        lprDetailsByMeal.putAll(loadedLprDetails)
        Log.d("DietScreen", "Loaded ${loadedLprDetails.size} LPR details for $openedDay")
        // Reset timer states
        MealType.entries.forEach { mealType ->
            uprightTimerRunning[mealType] = false
            uprightTimerRemainingMillis[mealType] = 15 * 60 * 1000L // 15 minutes in millis
        }
    }

    // LaunchedEffect for loading meal items when openedDay or mealTrackerHelper changes
    LaunchedEffect(openedDay, mealTrackerHelper) {
        Log.d("FoodScreen", "LaunchedEffect for openedDay: $openedDay. Loading food items.")
        MealType.entries.forEach { mealType ->
            foodItemsByMeal[mealType]?.clear()
            coroutineScope.launch {
                try {
                    val loadedItems = mealTrackerHelper.fetchFoodItemsForMeal(openedDay, mealType)
                    foodItemsByMeal[mealType]?.addAll(loadedItems)
                    Log.d(
                        "FoodScreen",
                        "Loaded ${loadedItems.size} items for $mealType on $openedDay"
                    )
                } catch (e: Exception) {
                    Log.e("FoodScreen", "Error loading food items for $mealType on $openedDay", e)
                }
            }
        }
    }

    // Initial check for Health Connect availability and permissions when the screen is first composed
    // This will also re-run if healthConnectViewModel.healthConnectAvailable state changes (though unlikely after init)
    // or if the key `Unit` changes (which it doesn't, so effectively runs once unless FoodScreen is removed and recomposed)
    LaunchedEffect(key1 = healthConnectViewModel.healthConnectAvailable, key2 = openedDay) {
        if (!healthConnectViewModel.healthConnectAvailable) {
            healthConnectViewModel.errorMessage =
                "Health Connect App is not available or not installed on this device."
            Log.w("FoodScreen", "Health Connect not available on this device.")
            return@LaunchedEffect
        }

        // Health Connect is available, now check permissions
        Log.d(
            "FoodScreen",
            "Health Connect is available. Checking permissions status for day: $openedDay."
        )

        // Check if permissions were already explicitly granted in the ViewModel
        if (healthConnectViewModel.permissionsGranted) {
            Log.d(
                "FoodScreen",
                "Permissions already marked as granted in ViewModel. Fetching data for $openedDay."
            )
            healthConnectViewModel.fetchDataForDay(openedDay) // Fetch data for the current openedDay
        } else {
            // Permissions not marked as granted in ViewModel, check with integrator and then request if needed
            val previouslyGranted = healthConnectViewModel.healthConnectIntegrator.hasPermissions()
            if (previouslyGranted) {
                Log.d(
                    "FoodScreen",
                    "Permissions previously granted (checked with integrator). Fetching data for $openedDay."
                )
                healthConnectViewModel.permissionsGranted = true // Update ViewModel state
                healthConnectViewModel.fetchDataForDay(openedDay)
            } else {
                Log.d("FoodScreen", "Permissions not granted. Requesting permissions.")
                if (healthConnectViewModel.permissions.isNotEmpty()) {
                    requestPermissionsLauncher.launch(healthConnectViewModel.permissions)
                } else {
                    Log.e("FoodScreen", "Permissions array in ViewModel is empty. Cannot request.")
                    healthConnectViewModel.errorMessage =
                        "Could not request Health Connect permissions: configuration error."
                }
            }
        }
    }


    // Dialog for editing visible cards
    if (showEditCardsDialog) {
        Dialog(onDismissRequest = { showEditCardsDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Edit Visible Cards",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    cards.forEach { card ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(card.title, style = MaterialTheme.typography.bodyLarge)
                            Switch(
                                checked = card.isVisible,
                                onCheckedChange = { isChecked ->
                                    val index = cards.indexOfFirst { it.id == card.id }
                                    if (index != -1) {
                                        cards[index] = card.copy(isVisible = isChecked)
                                        // TODO: Persist this change to SharedPreferences or database
                                    }
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { showEditCardsDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }

    val screenScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(screenScrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Food Dashboard", // Example Title for FoodScreen
                style = MaterialTheme.typography.titleLarge
            )
            IconButton(onClick = { showEditCardsDialog = true }) {
                Icon(Icons.Filled.Settings, contentDescription = "Edit Visible Cards")
            }
        }

        healthConnectViewModel.errorMessage?.let {
            if (it.isNotEmpty()) {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        cards.forEach { card ->
            if (card.isVisible) {
                val currentExpandedState = cardExpandedStates[card.id] ?: card.defaultExpanded
                val onExpansionChangedByChild = { newExpandedState: Boolean ->
                    cardExpandedStates[card.id] = newExpandedState
                    // TODO: Persist expansion state if needed
                }

                when (card.id) {
                    "energy" -> {
                        EnergyCard(
                            expanded = currentExpandedState,
                            onExpandedChange = onExpansionChangedByChild,
                            healthConnectViewModel = healthConnectViewModel,
                            openedDay = openedDay, // Pass the current openedDay
                            mealTrackerHelper = mealTrackerHelper // Pass mealTrackerHelper
                        )
                    }

                    "trends" -> {
                        TrendsCard(
                            expanded = currentExpandedState,
                            onExpandedChange = onExpansionChangedByChild,
                            healthConnectViewModel = healthConnectViewModel,
                            openedDay = openedDay, // Pass the current openedDay
                        )
                    }

                    "food_input" -> {
                        // Replace FoodInputCard with DietInputCard or modify existing
                        DietSpecificInputCard( // Or rename your existing FoodInputCard and modify it
                            expanded = currentExpandedState,
                            mealTrackerHelper = mealTrackerHelper,
                            groceryDatabaseHelper = groceryDatabaseHelper,
                            lprMealDetailHelper = lprMealDetailHelper, // Pass the new helper
                            openedDay = openedDay,
                            foodItemsByMeal = foodItemsByMeal,
                            lprDetailsByMeal = lprDetailsByMeal, // Pass LPR details state
                            uprightTimerRunning = uprightTimerRunning,
                            uprightTimerRemainingMillis = uprightTimerRemainingMillis,
                            coroutineScope = coroutineScope,
                            onExpandedChange = onExpansionChangedByChild
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietItemEditDialog(
    foodItemToEdit: FoodItem,
    mealTrackerHelper: MealTrackerHelper,         // For loading from meal history
    groceryDatabaseHelper: GroceryDatabaseHelper, // For grocery autocomplete
    onDismissRequest: () -> Unit,
    onSaveChanges: (FoodItem) -> Unit
) {
    var title by remember(foodItemToEdit.title) { mutableStateOf(foodItemToEdit.title) }
    var calories by remember(foodItemToEdit.calories) { mutableStateOf(foodItemToEdit.calories.toString()) }
    var healthyStarRating by remember(foodItemToEdit.healthyRating) { mutableStateOf(foodItemToEdit.healthyRating * 5f) }
    var lprFriendlyStarRating by remember(foodItemToEdit.lprFriendlyRating) {
        mutableStateOf(
            foodItemToEdit.lprFriendlyRating * 5f
        )
    }
    var ingredientsText by remember(foodItemToEdit.ingredients) { mutableStateOf(foodItemToEdit.ingredients) }

    // Autocomplete state
    var autocompleteSuggestions by remember { mutableStateOf<List<GroceryItem>>(emptyList()) }
    var showAutocompleteDropdown by remember { mutableStateOf(false) }

    // Meal history dialog state
    var showMealHistoryDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(if (foodItemToEdit.title.isBlank() || foodItemToEdit.title == "New Food Item") "Add Food Item" else "Edit Food Item") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

                // Button to Load from Meal History
                OutlinedButton(
                    onClick = { showMealHistoryDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Icon(
                        Icons.Filled.MenuBook,
                        contentDescription = "Load from Meal History",
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Load from Meal History")
                }

                // Title TextField with Autocomplete
                OutlinedTextField(
                    value = title,
                    onValueChange = { newTitle ->
                        title = newTitle
                        if (newTitle.length > 1) { // Trigger search after 2+ characters
                            autocompleteSuggestions =
                                groceryDatabaseHelper.searchGroceries(newTitle)
                            showAutocompleteDropdown = autocompleteSuggestions.isNotEmpty()
                        } else {
                            autocompleteSuggestions = emptyList()
                            showAutocompleteDropdown = false
                        }
                    },
                    label = { Text("Food Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (showAutocompleteDropdown) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) { // Constrain height
                        Card(elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)) { // Add elevation for dropdown appearance
                            LazyColumn {
                                items(autocompleteSuggestions, key = { it.id }) { grocery ->
                                    Text(
                                        text = grocery.name,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                title = grocery.name
                                                // Pre-fill other fields from the selected grocery item
                                                calories =
                                                    grocery.averageCaloriesPer100g?.toString() ?: ""
                                                // You might want to map grocery.isHealthy/isLPRFriendly to star ratings
                                                // For example:
                                                healthyStarRating =
                                                    if (grocery.isHealthy) 5f else 2.5f // Default or map
                                                lprFriendlyStarRating =
                                                    if (grocery.isLPRFriendly) 5f else 2.5f // Default or map
                                                // ingredientsText could be set if grocery.commonUnits or similar is relevant
                                                ingredientsText = grocery.commonUnits ?: ""

                                                autocompleteSuggestions = emptyList()
                                                showAutocompleteDropdown = false
                                            }
                                            .padding(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = calories,
                    onValueChange = { calories = it.filter { char -> char.isDigit() } },
                    label = { Text("Calories (kcal)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text("Healthy Rating:", style = MaterialTheme.typography.labelMedium)
                StarRatingInput(
                    rating = healthyStarRating,
                    onRatingChange = { healthyStarRating = it })

                Spacer(modifier = Modifier.height(12.dp))
                Text("LPR Friendly Rating:", style = MaterialTheme.typography.labelMedium)
                StarRatingInput(
                    rating = lprFriendlyStarRating,
                    onRatingChange = { lprFriendlyStarRating = it })

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = ingredientsText,
                    onValueChange = { ingredientsText = it },
                    label = { Text("Ingredients / Notes") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp),
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val finalCalories = calories.toIntOrNull() ?: 0
                val currentTitleInput =
                    title.trim() // Use a different name to avoid confusion with the original 'foodItemToEdit.title'

                // Determine the actual title after user input and default handling
                val processedTitle = currentTitleInput.ifBlank {
                    // If the user left the input blank:
                    // Check if it was an edit of an existing item that originally had a different, valid name.
                    // If so, we respect that original name IF the user blanked out the field.
                    // Otherwise, it becomes "Unnamed Food".
                    if (foodItemToEdit.title.isNotBlank() && foodItemToEdit.title != "New Food Item" && foodItemToEdit.title != "Unnamed Food") {
                        foodItemToEdit.title // Keep the original valid title if user blanked it
                    } else {
                        "Unnamed Food" // If it was new, or already "New Food Item"/"Unnamed Food", and now blank, make it "Unnamed Food"
                    }
                }

                // Now, apply toTitleCaseImproved to the processedTitle
                val finalActualTitle = processedTitle.toTitleCaseImproved()

                // --- MODIFICATION START: Check if the final title is "Unnamed Food" ---
                if (finalActualTitle == "Unnamed Food".toTitleCaseImproved() || finalActualTitle.isBlank()) {
                    // If the final title resolves to "Unnamed Food" (after toTitleCaseImproved)
                    // or is somehow still blank, dismiss without saving.
                    // ".toTitleCaseImproved()" is applied to "Unnamed Food" for a consistent comparison.
                    onDismissRequest()
                } else {
                    // Title is valid, proceed to create and save the FoodItem
                    val updatedFoodItem = foodItemToEdit.copy(
                        // id is preserved from foodItemToEdit
                        title = finalActualTitle, // Use the title-cased and validated title
                        calories = finalCalories,
                        healthyRating = (healthyStarRating / 5f).coerceIn(0f, 1f),
                        lprFriendlyRating = (lprFriendlyStarRating / 5f).coerceIn(0f, 1f),
                        ingredients = ingredientsText.trim()
                    )
                    onSaveChanges(updatedFoodItem)
                    onDismissRequest()
                }
                // --- MODIFICATION END ---
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text("Cancel") }
        }
    )

    // Modal MealHistoryDialog
    if (showMealHistoryDialog) {
        MealHistoryDialog( // Assuming MealHistoryDialog is defined as previously discussed
            mealTrackerHelper = mealTrackerHelper,
            onDismiss = { showMealHistoryDialog = false },
            onMealSelectedForReAdd = { foodItemTemplate ->
                // User selected an item from meal history. Update the current dialog's fields.
                // The 'foodItemTemplate' has historical data from a logged FoodItem
                // (which itself might have originally come from a GroceryItem or manual input).
                title = foodItemTemplate.title
                calories = foodItemTemplate.calories.toString()
                healthyStarRating = foodItemTemplate.healthyRating * 5f
                lprFriendlyStarRating = foodItemTemplate.lprFriendlyRating * 5f
                ingredientsText = foodItemTemplate.ingredients

                showMealHistoryDialog = false // Close the history dialog
                // Ensure autocomplete is hidden as we've just loaded full data
                autocompleteSuggestions = emptyList()
                showAutocompleteDropdown = false
            }
        )
    }
}
/*
fun String.toTitleCaseImproved(): String {
    if (this.isBlank()) return ""
    return this.trim().split(Regex("\\s+")).joinToString(" ") { word ->
        // Standard library's replaceFirstChar only works if it's lowercase.
        // For more robust title casing, especially with locales, it's better to lowercase first.
        word.lowercase(Locale.getDefault())
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}*/

@Composable
fun DietItemCard(
    foodItem: FoodItem,
    mealType: MealType, // Keep if your structure requires it, otherwise can be removed
    onDelete: () -> Unit,
    onSaveChanges: (FoodItem) -> Unit,
    modifier: Modifier = Modifier,
    mealTrackerHelper: MealTrackerHelper,     // <-- New parameter
    groceryDatabaseHelper: GroceryDatabaseHelper
) {
    var showEditDialog by remember { mutableStateOf(false) }

    if (showEditDialog) {
        FoodItemEditDialog(
            foodItemToEdit = foodItem,
            onDismissRequest = { showEditDialog = false },
            onSaveChanges = { updatedFoodItem ->
                onSaveChanges(updatedFoodItem)
                showEditDialog = false
            },
            mealTrackerHelper = mealTrackerHelper,
            groceryDatabaseHelper = groceryDatabaseHelper
        )
    }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp, pressedElevation = 6.dp),
        shape = MaterialTheme.shapes.medium, // Softer corners
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp) // Slightly more vertical space between cards
        // .clip(MaterialTheme.shapes.medium) // Already applied by Card's shape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp), // Increased padding
            verticalAlignment = Alignment.Top // Align to top for better title alignment if text wraps
        ) {
            // Main content column
            Column(
                modifier = Modifier.weight(1f), // Takes available space
                verticalArrangement = Arrangement.spacedBy(6.dp) // Space between elements in this column
            ) {
                Text(
                    text = foodItem.title.ifBlank { "Unnamed Food" }, // Handle blank title
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2, // Allow title to wrap
                    overflow = TextOverflow.Ellipsis // Add ... if title is too long
                )

                // Calories with Icon
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.FitnessCenter,
                        contentDescription = "Calories",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "${foodItem.calories} kcal",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Ratings Section
                RatingRowDisplay(
                    label = "🌿 Healthy",
                    ratingFraction = foodItem.healthyRating // Pass the 0.0-1.0 float
                )
                RatingRowDisplay(
                    label = "🧘 LPR Friendly",
                    ratingFraction = foodItem.lprFriendlyRating // Pass the 0.0-1.0 float
                )

                if (foodItem.ingredients.isNotBlank()) {
                    Row(verticalAlignment = Alignment.Top) { // Align icon to top of text if it wraps
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = "Notes",
                            modifier = Modifier
                                .size(18.dp)
                                .padding(top = 2.dp), // Adjust icon position slightly
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = foodItem.ingredients,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Action buttons column (Edit, Delete)
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.height(IntrinsicSize.Min) // To align buttons if info column grows
            ) {
                IconButton(onClick = { showEditDialog = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit ${foodItem.title}")
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "Delete ${foodItem.title}"
                    ) // Using outlined version
                }
            }
        }
    }
}

@Composable
private fun RatingRowDisplay(label: String, ratingFraction: Float, starCount: Int = 5) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(130.dp) // Give label fixed width to align stars
        )
        Row {
            val filledStars = floor(ratingFraction * starCount).toInt()
            val hasHalfStar =
                (ratingFraction * starCount) - filledStars >= 0.4f // Threshold for half star
            val emptyStars = starCount - filledStars - if (hasHalfStar) 1 else 0

            repeat(filledStars) {
                Icon(
                    Icons.Outlined.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (hasHalfStar) {
                Icon(
                    Icons.Outlined.StarHalf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            repeat(emptyStars.coerceAtLeast(0)) { // Ensure emptyStars isn't negative
                Icon(
                    Icons.Outlined.StarBorder,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DietSpecificInputCard(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    mealTrackerHelper: MealTrackerHelper,
    groceryDatabaseHelper: GroceryDatabaseHelper,
    openedDay: String,
    foodItemsByMeal: MutableMap<MealType, SnapshotStateList<FoodItem>>,
    lprMealDetailHelper: LprMealDetailHelper,
    lprDetailsByMeal: MutableMap<MealType, LprMealDetail>,
    uprightTimerRunning: MutableMap<MealType, Boolean>,
    uprightTimerRemainingMillis: MutableMap<MealType, Long>,
    coroutineScope: CoroutineScope
) {
    val simpleDateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val context = LocalContext.current

    CollapsibleCard(
        titleContent = {
            Text(
                "🍎 LPR Diet Intake",
                style = MaterialTheme.typography.titleMedium
            )
        },
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        isExpandable = true,
        quickGlanceInfo = {
            if (!expanded) { // Show total items only when collapsed
                val totalItems = foodItemsByMeal.values.sumOf { it.size }
                if (totalItems > 0) {
                    Text("$totalItems items logged", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("No items logged yet", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        defaultContent = { /* No default content when collapsed if quickGlanceInfo is used like this */ },
        hideDefaultWhenExpanded = true,
        expandableContent = {
            Column(modifier = Modifier.padding(top = 8.dp)) { // Added a little top padding
                MealType.entries.forEach { mealType ->
                    val itemsForMeal = foodItemsByMeal.getOrPut(mealType) { mutableStateListOf() }
                    val currentLprDetail = lprDetailsByMeal.getOrPut(mealType) {
                        LprMealDetail(date = openedDay, mealType = mealType)
                    }
                    val isTimerCurrentlyRunning = uprightTimerRunning[mealType] ?: false
                    val remainingMillis = uprightTimerRemainingMillis[mealType] ?: (15 * 60 * 1000L)

                    fun saveCurrentMealListAndLprDetail() {
                        coroutineScope.launch {
                            mealTrackerHelper.saveFoodItemsForMeal(
                                openedDay,
                                mealType,
                                itemsForMeal.toList()
                            )
                            val detailToSave = lprDetailsByMeal[mealType]
                                ?: LprMealDetail(date = openedDay, mealType = mealType)
                            lprMealDetailHelper.saveOrUpdateLprMealDetail(detailToSave)
                            val freshlyLoadedDetail =
                                lprMealDetailHelper.getLprMealDetail(openedDay, mealType)
                                    ?: LprMealDetail(date = openedDay, mealType = mealType)
                            lprDetailsByMeal[mealType] = freshlyLoadedDetail
                        }
                    }

                    // Section for each Meal Type
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp) // Space below each meal section
                    ) {
                        // Header Row: Meal Name and Add Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${mealType.emoji} ${mealType.displayName}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            IconButton(
                                onClick = {
                                    val newFoodItem =
                                        FoodItem(id = UUID.randomUUID().toString(), title = "")
                                    itemsForMeal.add(newFoodItem)
                                    saveCurrentMealListAndLprDetail()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = "Add Food to ${mealType.displayName}"
                                )
                            }
                        }

                        // Display Food Items
                        if (itemsForMeal.isNotEmpty()) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp)) { // Indent food items slightly
                                itemsForMeal.forEachIndexed { index, foodItem ->
                                    DietItemCard(
                                        foodItem = foodItem,
                                        mealType = mealType,
                                        groceryDatabaseHelper = groceryDatabaseHelper,
                                        mealTrackerHelper = mealTrackerHelper,
                                        onDelete = {
                                            itemsForMeal.remove(foodItem)
                                            saveCurrentMealListAndLprDetail()
                                        },
                                        onSaveChanges = { updatedFoodItem ->
                                            val itemIndex =
                                                itemsForMeal.indexOfFirst { it.id == updatedFoodItem.id }
                                            if (itemIndex != -1) {
                                                itemsForMeal[itemIndex] = updatedFoodItem
                                                saveCurrentMealListAndLprDetail()
                                            }
                                        }
                                    )
                                    if (index < itemsForMeal.size - 1) {
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                                    }
                                }
                            }
                        } else {
                            Text(
                                "No items added for this meal.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    top = 4.dp,
                                    bottom = 8.dp
                                )
                            )
                        }

                        // --- LPR Specific UI (Conditional Visibility) ---
                        // --- LPR Specific UI (Conditional Visibility) ---
                        // --- LPR Specific UI (Conditional Visibility) ---
                        AnimatedVisibility(
                            visible = itemsForMeal.isNotEmpty(),
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            // This Column wraps both LPR rows and provides overall padding for the LPR section
                            Column(modifier = Modifier.padding(top = 8.dp)) { // Add space before LPR details
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = 12.dp,
                                            vertical = 4.dp
                                        ),// Indent LPR details
                                    verticalArrangement = Arrangement.spacedBy(10.dp) // Space between LPR rows
                                ) {
                                    // Itopride Checkbox
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Box(modifier = Modifier.size(48.dp)) { // Fixed size container for checkbox
                                                Checkbox(
                                                    checked = currentLprDetail.tookItopride,
                                                    onCheckedChange = { isChecked ->
                                                        val updatedDetail = currentLprDetail.copy(
                                                            tookItopride = isChecked,
                                                            itoprideTimestamp = if (isChecked) System.currentTimeMillis() else null
                                                        )
                                                        lprDetailsByMeal[mealType] = updatedDetail
                                                        saveCurrentMealListAndLprDetail()

                                                        // Update medication tracker when checking Itopride
                                                        if (isChecked) {
                                                            coroutineScope.launch {
                                                                val medicationHelper =
                                                                    NewMedicationDatabaseHelper(
                                                                        context
                                                                    )
                                                                val currentMedications =
                                                                    medicationHelper.fetchMedicationItemsForDateWithDefaults(
                                                                        openedDay
                                                                    )
                                                                val itoprideIndex =
                                                                    currentMedications.indexOfFirst { it.name == "itopride" }

                                                                if (itoprideIndex != -1) {
                                                                    val currentItopride =
                                                                        currentMedications[itoprideIndex]
                                                                    val updatedItopride =
                                                                        currentItopride.copy(
                                                                            dosage = currentItopride.dosage + 50f // Add 50mg
                                                                        )
                                                                    medicationHelper.insertOrUpdateMedicationItem(
                                                                        openedDay,
                                                                        updatedItopride
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.align(Alignment.Center) // Center in the fixed box
                                                )
                                            }
                                            Text("Took Itopride")
                                        }

                                        // Timestamp positioned absolutely to the right
                                        if (currentLprDetail.tookItopride && currentLprDetail.itoprideTimestamp != null) {
                                            Text(
                                                "at ${simpleDateFormat.format(Date(currentLprDetail.itoprideTimestamp))}",
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.align(Alignment.CenterEnd)
                                            )
                                        }
                                    }

                                    // 15 Minutes Upright
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Box(modifier = Modifier.size(48.dp)) { // Same fixed size container for checkbox
                                                Checkbox(
                                                    checked = currentLprDetail.completedUprightRequirement,
                                                    onCheckedChange = null,
                                                    enabled = false,
                                                    modifier = Modifier.align(Alignment.Center) // Center in the fixed box
                                                )
                                            }
                                            Text("15 Min Upright")
                                        }

                                        // Timer/Button positioned absolutely to the right
                                        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                                            if (currentLprDetail.completedUprightRequirement) {
                                                Icon(
                                                    Icons.Filled.Done,
                                                    "Completed",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            } else if (isTimerCurrentlyRunning) {
                                                Text(
                                                    String.format(
                                                        "%02d:%02d",
                                                        (remainingMillis / 1000) / 60,
                                                        (remainingMillis / 1000) % 60
                                                    ),
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            } else {
                                                Button(
                                                    onClick = {
                                                        if (!isTimerCurrentlyRunning && !currentLprDetail.completedUprightRequirement) {
                                                            uprightTimerRunning[mealType] = true
                                                            uprightTimerRemainingMillis[mealType] =
                                                                15 * 60 * 1000L
                                                            val updatedDetail =
                                                                currentLprDetail.copy(
                                                                    startedUprightTimer = true
                                                                )
                                                            lprDetailsByMeal[mealType] =
                                                                updatedDetail

                                                            coroutineScope.launch {
                                                                var currentRemaining =
                                                                    uprightTimerRemainingMillis[mealType]!!
                                                                while (currentRemaining > 0 && (uprightTimerRunning[mealType] == true)) {
                                                                    delay(1000)
                                                                    currentRemaining -= 1000
                                                                    uprightTimerRemainingMillis[mealType] =
                                                                        currentRemaining
                                                                    if (currentRemaining <= 0) break
                                                                }
                                                                if (uprightTimerRunning[mealType] == true) {
                                                                    uprightTimerRunning[mealType] =
                                                                        false
                                                                    val finalDetail =
                                                                        lprDetailsByMeal[mealType]?.copy(
                                                                            completedUprightRequirement = true
                                                                        )
                                                                            ?: LprMealDetail(
                                                                                date = openedDay,
                                                                                mealType = mealType,
                                                                                completedUprightRequirement = true
                                                                            )
                                                                    lprDetailsByMeal[mealType] =
                                                                        finalDetail
                                                                    saveCurrentMealListAndLprDetail()
                                                                }
                                                            }
                                                        }
                                                    },
                                                    enabled = !currentLprDetail.completedUprightRequirement,
                                                    contentPadding = PaddingValues(
                                                        horizontal = 12.dp,
                                                        vertical = 8.dp
                                                    )
                                                ) {
                                                    Text(if (currentLprDetail.startedUprightTimer && !currentLprDetail.completedUprightRequirement) "Resume?" else "Start 15m")
                                                }
                                            }
                                        }
                                    }

                                    if (currentLprDetail.startedUprightTimer && !currentLprDetail.completedUprightRequirement && !isTimerCurrentlyRunning) {
                                        Text(
                                            "Timer paused or not completed.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(start = 48.dp) // Align with text after checkbox
                                        )
                                    }
                                }
                            }
                        } // End AnimatedVisibility
                    } // End Column for each Meal Type

                    // Divider between meal sections
                    if (mealType != MealType.entries.last()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                        )
                    }
                } // End of MealType.entries.forEach
            } // End of Column for CollapsibleCard's expandableContent
        } // End of CollapsibleCard
    )
}
// Ensure FoodItemEditDialog and FoodItemCard are defined as in your provided code
// No direct changes needed in them for this specific LPR feature, as the LPR details
// are at the meal level, not the individual food item level.

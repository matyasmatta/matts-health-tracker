package com.example.mattshealthtracker

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.lifecycle.ViewModelProvider
import com.example.mattshealthtracker.AppUiElements.CollapsibleCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.collections.toTypedArray
import kotlin.math.floor
import kotlin.math.roundToInt
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import java.time.format.DateTimeParseException


// Data class to represent each card's properties
data class HealthCard(
    val id: String,
    val title: String,
    var isVisible: Boolean,
    val defaultExpanded: Boolean = false
)

// --- NEW DATA MODELS FOR FOOD TRACKING ---
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
}

data class FoodItem(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "New Food Item",
    var calories: Int = 0,
    var healthyRating: Float = 0.5f, // 0.0 to 1.0
    var lprFriendlyRating: Float = 0.5f, // 0.0 to 1.0
    var ingredients: String = "" // For now, a simple string, later a list or search result
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FoodScreen(openedDay: String) {
    val context = LocalContext.current
    val mealTrackerHelper = remember { MealTrackerHelper(context) }
    val groceryDatabaseHelper = remember { GroceryDatabaseHelper(context) }
    val coroutineScope = rememberCoroutineScope()

    val foodItemsByMeal = remember {
        mutableStateMapOf<MealType, SnapshotStateList<FoodItem>>().apply {
            MealType.entries.forEach { mealType ->
                put(mealType, mutableStateListOf())
            }
        }
    }

    var showEditCardsDialog by rememberSaveable { mutableStateOf(false) }

    val cards = remember {
        mutableStateListOf(
            HealthCard("energy", "🔥 Energy Use Today", true, false),
            HealthCard("trends", "📊 Dietary Trends", false, false),
            HealthCard("food_input", "🍎 Food", true, true)
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
                healthConnectViewModel.errorMessage = "Not all necessary Health Connect permissions were granted. Some features might be unavailable."
            }
        }
    )

    // LaunchedEffect for loading meal items when openedDay or mealTrackerHelper changes
    LaunchedEffect(openedDay, mealTrackerHelper) {
        Log.d("FoodScreen", "LaunchedEffect for openedDay: $openedDay. Loading food items.")
        MealType.entries.forEach { mealType ->
            foodItemsByMeal[mealType]?.clear()
            coroutineScope.launch {
                try {
                    val loadedItems = mealTrackerHelper.fetchFoodItemsForMeal(openedDay, mealType)
                    foodItemsByMeal[mealType]?.addAll(loadedItems)
                    Log.d("FoodScreen", "Loaded ${loadedItems.size} items for $mealType on $openedDay")
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
            healthConnectViewModel.errorMessage = "Health Connect App is not available or not installed on this device."
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
                            onExpandedChange = onExpansionChangedByChild
                            // Pass any necessary data for TrendsCard
                        )
                    }
                    "food_input" -> {
                        FoodInputCard(
                            expanded = currentExpandedState,
                            onExpandedChange = onExpansionChangedByChild,
                            mealTrackerHelper = mealTrackerHelper,
                            groceryDatabaseHelper = groceryDatabaseHelper,
                            openedDay = openedDay,
                            foodItemsByMeal = foodItemsByMeal
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
fun FoodItemEditDialog(
    foodItemToEdit: FoodItem,
    mealTrackerHelper: MealTrackerHelper,         // For loading from meal history
    groceryDatabaseHelper: GroceryDatabaseHelper, // For grocery autocomplete
    onDismissRequest: () -> Unit,
    onSaveChanges: (FoodItem) -> Unit
) {
    var title by remember(foodItemToEdit.title) { mutableStateOf(foodItemToEdit.title) }
    var calories by remember(foodItemToEdit.calories) { mutableStateOf(foodItemToEdit.calories.toString()) }
    var healthyStarRating by remember(foodItemToEdit.healthyRating) { mutableStateOf(foodItemToEdit.healthyRating * 5f) }
    var lprFriendlyStarRating by remember(foodItemToEdit.lprFriendlyRating) { mutableStateOf(foodItemToEdit.lprFriendlyRating * 5f) }
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
                    androidx.compose.material3.Icon(Icons.Filled.MenuBook, contentDescription = "Load from Meal History", modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Load from Meal History")
                }

                // Title TextField with Autocomplete
                OutlinedTextField(
                    value = title,
                    onValueChange = { newTitle ->
                        title = newTitle
                        if (newTitle.length > 1) { // Trigger search after 2+ characters
                            autocompleteSuggestions = groceryDatabaseHelper.searchGroceries(newTitle)
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
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)) { // Constrain height
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
                StarRatingInput(rating = healthyStarRating, onRatingChange = { healthyStarRating = it })

                Spacer(modifier = Modifier.height(12.dp))
                Text("LPR Friendly Rating:", style = MaterialTheme.typography.labelMedium)
                StarRatingInput(rating = lprFriendlyStarRating, onRatingChange = { lprFriendlyStarRating = it })

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
                val currentTitleInput = title.trim() // Use a different name to avoid confusion with the original 'foodItemToEdit.title'

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
fun String.toTitleCaseImproved(): String {
    if (this.isBlank()) return ""
    return this.trim().split(Regex("\\s+")).joinToString(" ") { word ->
        // Standard library's replaceFirstChar only works if it's lowercase.
        // For more robust title casing, especially with locales, it's better to lowercase first.
        word.lowercase(Locale.getDefault())
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}
@Composable
fun StarRatingInput(
    maxStars: Int = 5,
    rating: Float, // Changed to Float: e.g., 0.0, 0.5, 1.0, ... 5.0
    onRatingChange: (Float) -> Unit,
    starSize: Dp = 36.dp, // Make star size configurable
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    unselectedColor: Color = Color.Gray
) {
    Row {
        for (i in 1..maxStars) {
            val starValue = i.toFloat()
            val isSelectedFull = rating >= starValue
            val isSelectedHalf = rating >= (starValue - 0.5f) && rating < starValue

            Icon(
                imageVector = when {
                    isSelectedFull -> Icons.Filled.Star
                    isSelectedHalf -> Icons.Outlined.StarHalf
                    else -> Icons.Filled.StarBorder
                },
                contentDescription = "Star $i",
                tint = if (isSelectedFull || isSelectedHalf) selectedColor else unselectedColor,
                modifier = Modifier
                    .size(starSize) // Use configurable star size
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                val starWidth = size.width.toFloat() // Actual width of the icon
                                val isTapOnFirstHalf = offset.x < starWidth / 2

                                val newRating = if (isTapOnFirstHalf) {
                                    // If current star is full and tapped on first half, make it half
                                    // If current star is half and tapped on first half, make it empty (i - 1)
                                    // Otherwise, make it half
                                    if (rating == starValue && isTapOnFirstHalf) { // Was full, tap on first half
                                        starValue - 0.5f
                                    } else if (rating == (starValue - 0.5f) && isTapOnFirstHalf) { // Was half, tap on first half
                                        (starValue - 1.0f).coerceAtLeast(0.0f)
                                    } else { // General case, make it half
                                        (starValue - 0.5f).coerceAtLeast(0.0f)
                                    }
                                } else { // Tap on second half
                                    // If current star is half or empty, make it full
                                    // If current star is full and tapped on second half, it remains full
                                    starValue
                                }
                                onRatingChange(newRating)
                            }
                        )
                    }
                // .clickable is simpler but doesn't give tap position.
                // We use pointerInput for tap position.
            )
        }
    }
}


@Composable
fun FoodItemCard(
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
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete ${foodItem.title}") // Using outlined version
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
            val hasHalfStar = (ratingFraction * starCount) - filledStars >= 0.4f // Threshold for half star
            val emptyStars = starCount - filledStars - if (hasHalfStar) 1 else 0

            repeat(filledStars) {
                Icon(Icons.Outlined.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            if (hasHalfStar) {
                Icon(Icons.Outlined.StarHalf, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            repeat(emptyStars.coerceAtLeast(0)) { // Ensure emptyStars isn't negative
                Icon(Icons.Outlined.StarBorder, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// --- Updated TrendsCard using AppUiElements.CollapsibleCard ---
@Composable
fun TrendsCard(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    AppUiElements.CollapsibleCard(
        titleContent = { Text("📊 Dietary Trends", style = MaterialTheme.typography.titleMedium) },
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        isExpandable = true,
        defaultContent = {
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)) { // Added padding if content is directly under title
                Text("Trends data will be shown here.")
                // Add more UI for trends data
            }
        },
        expandableContent = {
            // If TrendsCard has more details upon expansion, put them here.
            // Otherwise, defaultContent will just show.
        }
        // hideDefaultWhenExpanded = false (default)
    )
}
@OptIn(ExperimentalFoundationApi::class) // If FoodItemCard or other internal elements use it
@Composable
fun FoodInputCard(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    mealTrackerHelper: MealTrackerHelper,
    groceryDatabaseHelper: GroceryDatabaseHelper, // Passed, can be used if needed
    openedDay: String,
    foodItemsByMeal: MutableMap<MealType, SnapshotStateList<FoodItem>>
) {
    val coroutineScope = rememberCoroutineScope()

    AppUiElements.CollapsibleCard(
        titleContent = {
            Text(
                "🍎 Food Intake", // Emoji for the card title itself
                style = MaterialTheme.typography.titleMedium
            )
        },
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        isExpandable = true,
        quickGlanceInfo = {
            if (!expanded) {
                val totalItems = foodItemsByMeal.values.sumOf { it.size }
                if (totalItems > 0) {
                    Text("$totalItems items", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        defaultContent = {
            if (!expanded) {
                Column(modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) {
                    Text("Tap to view or add food items for each meal.", style = MaterialTheme.typography.bodySmall)
                    // Optionally, calculate and show total calories for the day from 'foodItemsByMeal'
                    // val totalCaloriesToday = foodItemsByMeal.values.flatten().sumOf { it.calories }
                    // if (totalCaloriesToday > 0) {
                    //    Text("Total daily calories: $totalCaloriesToday kcal", style = MaterialTheme.typography.bodySmall)
                    // }
                }
            }
        },
        expandableContent = {
            Column(modifier = Modifier.padding(top = 0.dp)) { // No extra top padding if it's the only content when expanded
                MealType.entries.forEach { mealType ->
                    val itemsForMeal = foodItemsByMeal.getOrPut(mealType) { mutableStateListOf() }

                    fun saveCurrentMealList() {
                        coroutineScope.launch {
                            mealTrackerHelper.saveFoodItemsForMeal(openedDay, mealType, itemsForMeal.toList())
                        }
                    }

                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${mealType.emoji} ${mealType.displayName}", // Using updated MealType properties
                                style = MaterialTheme.typography.titleSmall
                            )
                            IconButton(
                                onClick = {
                                    val newFoodItem = FoodItem(
                                        id = UUID.randomUUID().toString(),
                                        title = "" // Start with an empty title, user fills in via FoodItemCard
                                        // Initialize other properties like calories, ratings if needed
                                    )
                                    itemsForMeal.add(newFoodItem)
                                    saveCurrentMealList()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = "Add Food to ${mealType.displayName}" // Use displayName for accessibility
                                )
                            }
                        }

                        if (itemsForMeal.isNotEmpty()) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                                itemsForMeal.forEachIndexed { index, foodItem ->
                                    FoodItemCard( // This is your composable for a single food item
                                        foodItem = foodItem,
                                        groceryDatabaseHelper = groceryDatabaseHelper,
                                        mealTrackerHelper = mealTrackerHelper,
                                        mealType = mealType, // Pass if FoodItemCard needs it for context
                                        onDelete = {
                                            itemsForMeal.remove(foodItem)
                                            saveCurrentMealList()
                                        },
                                        onSaveChanges = { updatedFoodItem ->
                                            val itemIndex = itemsForMeal.indexOfFirst { it.id == updatedFoodItem.id }
                                            if (itemIndex != -1) {
                                                itemsForMeal[itemIndex] = updatedFoodItem
                                                saveCurrentMealList()
                                            }
                                        }
                                        // Ensure FoodItemCard is responsible for displaying ratings as "X/10"
                                    )
                                    if (index < itemsForMeal.size - 1) {
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    }
                                }
                            }
                        } else {
                            Text(
                                "No items added for this meal.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, top = 4.dp)
                            )
                        }
                    }

                    if (mealType != MealType.entries.last()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp))
                    }
                }
            }
        },
        hideDefaultWhenExpanded = true
    )
}

// --- EXISTING UTILITY COMPONENTS (KEEP AS IS) ---
@Composable
fun MealHistoryDialog(
    mealTrackerHelper: MealTrackerHelper,
    onDismiss: () -> Unit,
    // Callback when a user wants to re-add a historical meal.
    // Passes a FoodItem template (with a new ID) ready to be added to the current day.
    onMealSelectedForReAdd: (foodItemTemplate: FoodItem) -> Unit
) {
    var recentMealTitles by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) { // Load data when the dialog is first composed
        isLoading = true
        withContext(Dispatchers.IO) { // Perform database operation on a background thread
            recentMealTitles = mealTrackerHelper.getUniqueFoodTitlesFromLastNDays(10)
        }
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recent Meals (Last 10 Days)") },
        text = {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            } else if (recentMealTitles.isEmpty()) {
                Text("No meals found in the last 10 days.")
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(recentMealTitles) { title ->
                        Text(
                            text = title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        isLoading = true // Show loading while fetching details
                                        val foodItemTemplate =
                                            withContext(Dispatchers.IO) {
                                                mealTrackerHelper.getMostRecentInstanceOfFoodByTitle(
                                                    title,
                                                    10
                                                )
                                            }
                                        isLoading = false
                                        foodItemTemplate?.let {
                                            onMealSelectedForReAdd(it) // Pass the template
                                        }
                                        onDismiss() // Dismiss dialog after selection
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun EnergyCard(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    healthConnectViewModel: HealthConnectViewModel,
    openedDay: String, // Expects "yyyy-MM-dd"
    mealTrackerHelper: MealTrackerHelper
) {
    // Health Connect Data (from ViewModel)
    val activeCaloriesHc by healthConnectViewModel::activeCaloriesBurned
    val bmrCaloriesHc by healthConnectViewModel::bmr
    val isLoadingHc by healthConnectViewModel::isLoading // isLoading from Health Connect
    val errorMessageHc by healthConnectViewModel::errorMessage // errorMessage from Health Connect

    // State for Food Calories (fetched within this Composable)
    var foodCaloriesToday by remember(openedDay) { mutableStateOf<Int?>(null) } // Reset if openedDay changes
    var isLoadingFood by remember(openedDay) { mutableStateOf(true) }
    var foodError by remember(openedDay) { mutableStateOf<String?>(null) }

    // Coroutine scope for launching data fetching
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(openedDay) {
        healthConnectViewModel.fetchDataForDay(openedDay)
    }

    // --- Data Fetching for Food Calories ---
    LaunchedEffect(openedDay, mealTrackerHelper) {
        isLoadingFood = true
        foodError = null
        foodCaloriesToday = null // Clear previous data

        // It's crucial that getTotalFoodCaloriesForDay can handle the 'openedDay' string
        // or that we parse 'openedDay' to LocalDate here if the helper expects LocalDate.
        // For this example, let's assume getTotalFoodCaloriesForDay is modified to take String
        // OR we parse it. The latter is preferred.

        if (openedDay != null) {
            coroutineScope.launch { // Launch a new coroutine for the background task
                try {
                    val calories = withContext(Dispatchers.IO) {
                        // If mealTrackerHelper.getTotalFoodCaloriesForDay expects LocalDate:
                        mealTrackerHelper.getTotalFoodCaloriesForDay(openedDay)
                        // If mealTrackerHelper.getTotalFoodCaloriesForDay was changed to expect String:
                        // mealTrackerHelper.getTotalFoodCaloriesForDay(openedDay)
                    }
                    foodCaloriesToday = calories
                } catch (e: Exception) {
                    foodError = "Failed to load food calories."
                    // Log.e("EnergyCard", "Error fetching food calories for $openedDay", e)
                } finally {
                    isLoadingFood = false
                }
            }
        } else {
            foodError = "Invalid date format: $openedDay"
            isLoadingFood = false
        }
    }

    // --- Combined Loading and Error States ---
    val isOverallLoading = isLoadingHc || isLoadingFood
    // Prioritize showing specific errors
    val displayError = foodError ?: errorMessageHc

    // --- Calculations (dependent on fetched data) ---
    val totalEnergyBurnedHc by remember(activeCaloriesHc, bmrCaloriesHc) {
        derivedStateOf<Double?> { // Explicitly define the return type of derivedStateOf as Double?
            val active = activeCaloriesHc // Assuming activeCaloriesHc is Double?
            val bmr = bmrCaloriesHc     // Assuming bmrCaloriesHc is Double?

            (if (active != null && bmr != null) {
                active + bmr
            } else if (active != null) {
                active // active is Double, so this branch returns Double
            } else if (bmr != null) {
                bmr    // bmr is Double, so this branch returns Double
            } else {
                null   // All components are null, so the total is null (returns Double?)
            }) as Double?
        }
    }


    val netEnergy by remember(totalEnergyBurnedHc, foodCaloriesToday) {
        derivedStateOf {
            val burned = totalEnergyBurnedHc
            val food = foodCaloriesToday
            if (burned != null && food != null) {
                 food.toDouble() - burned// foodCaloriesToday is Int?
            } else {
                null
            }
        }
    }

    AppUiElements.CollapsibleCard(
        titleContent = {
            Text(
                "🔥 Energy Balance Today",
                style = MaterialTheme.typography.titleMedium
            )
        },
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        isExpandable = true,
        hideDefaultWhenExpanded = true, // Key change: This hides defaultContent when expanded

        // --- QUICK GLANCE: JUST NET CALORIES ---
        quickGlanceInfo = {
            // Visible ONLY when collapsed and hideDefaultWhenExpanded = false (or not set)
            // AND defaultContent is not present (or defaultContent is used).
            // For your use case, this is THE content for the collapsed state.
            val textStyle = MaterialTheme.typography.bodySmall
            val modifier = Modifier.padding(start = 8.dp)

            when {
                isOverallLoading -> {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Text(" Loading...", style = textStyle, modifier = Modifier.padding(start = 4.dp))
                    }
                }
                displayError != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(" Data issue", style = textStyle, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 4.dp))
                    }
                }
                netEnergy != null -> {
                    Text(
                        text = "${String.format("%.0f", netEnergy)} kcal net",
                        style = textStyle,
                        modifier = modifier
                    )
                }
                else -> {
                    // Fallback if net energy can't be calculated yet but not loading/error
                    // (e.g., only food or only burned data is available)
                    Text("Calculating...", style = textStyle, modifier = modifier)
                }
            }
        },

        // --- DEFAULT CONTENT: CLEVER ONE-SENTENCE OVERVIEW ---
        defaultContent = {
            // This is visible when collapsed, below titleContent, IF quickGlanceInfo is not used
            // OR if quickGlanceInfo IS used AND hideDefaultWhenExpanded is false (then this appears below quickGlanceInfo).
            // **With hideDefaultWhenExpanded = true, this defaultContent effectively becomes the main summary
            // for the collapsed state if you intend it to be the "one-sentence overview".**
            // If CollapsibleCard is changed to ONLY show quickGlanceInfo OR defaultContent when collapsed,
            // then you'd pick one. Assuming it shows both if both provided and hideDefault is false.
            // For your explicit request (hideDefaultWhenExpanded = true, defaultContent = one sentence),
            // it means this defaultContent is the content for the collapsed state.

            val textStyle = MaterialTheme.typography.bodySmall
            val modifier = Modifier.padding(start = 8.dp, end = 8.dp) // Give it some horizontal padding
            val defaultMessage = "Tap to see detailed energy breakdown."

            if (isOverallLoading) {
                // Keep defaultContent clean during loading, quickGlanceInfo handles "Loading..."
                Text("", style = textStyle, modifier = modifier) // Or some very minimal placeholder
            } else if (displayError != null) {
                Text(
                    "There was an issue fetching all data.",
                    style = textStyle,
                    color = MaterialTheme.colorScheme.error,
                    modifier = modifier
                )
            } else if (netEnergy != null) {
                val net = netEnergy!!
                val balanceText = when {
                    net > 250 -> "good surplus" // Adjusted thresholds for "clever" sentence
                    net > 0 -> "slight surplus"
                    net < -250 -> "notable deficit"
                    net < 0 -> "slight deficit"
                    else -> "perfectly balanced"
                }
                Text(
                    text = "Today, you're running a $balanceText.",
                    style = textStyle,
                    modifier = modifier
                )
            } else if (foodCaloriesToday != null && totalEnergyBurnedHc == null) {
                Text(
                    text = "Tracked $foodCaloriesToday kcal intake. Awaiting energy burn data...",
                    style = textStyle,
                    modifier = modifier
                )
            } else if (totalEnergyBurnedHc != null && foodCaloriesToday == null) {
                Text(
                    text = "Burned ${String.format("%.0f", totalEnergyBurnedHc)} kcal. What did you eat?",
                    style = textStyle,
                    modifier = modifier
                )
            }
            else {
                Text(defaultMessage, style = textStyle, modifier = modifier)
            }
        },


        // --- EXPANDABLE CONTENT: ALL THE DETAILED DATA ---
        expandableContent = {
            // This Column contains everything for the expanded view.
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isOverallLoading) { // Separate loading state for expanded view if needed for more detail
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Loading energy details...", style = MaterialTheme.typography.bodyLarge)
                    }
                } else if (displayError != null) { // More detailed error for expanded view
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(all = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Data Retrieval Issue",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = displayError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        // Optionally, add a retry button here
                    }
                } else if (totalEnergyBurnedHc != null || foodCaloriesToday != null || netEnergy != null) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Section: Energy Intake
                        Text("🍎 Food Intake", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
                        EnergyDataRow(label = "Calories Consumed:", value = foodCaloriesToday?.toDouble(), unit = "kcal")

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // Section: Energy Expenditure
                        Text("🔥 Energy Burned (HC)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
                        EnergyDataRow(label = "Total Burned:", value = totalEnergyBurnedHc, unit = "kcal")
                        EnergyDataRow(label = "  Basal (BMR):", value = bmrCaloriesHc, unit = "kcal", isSubtle = true)
                        EnergyDataRow(label = "  Active Burned:", value = activeCaloriesHc, unit = "kcal", isSubtle = true)

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // Section: Net Balance
                        Text("⚖️ Net Calorie Balance", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
                        EnergyDataRow(
                            label = "Balance (Food - Burned):", // Clarified label
                            value = netEnergy,
                            unit = "kcal",
                            valueColor = when {
                                netEnergy == null -> MaterialTheme.colorScheme.onSurface
                                netEnergy!! > 50 -> Color(0xFF4CAF50)
                                netEnergy!! < -50 -> Color(0xFFF44336)
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                        netEnergy?.let {
                            Text(
                                text = when {
                                    it > 500 -> "Significant Surplus"
                                    it > 50 -> "Slight Surplus"
                                    it < -500 -> "Significant Deficit"
                                    it < -50 -> "Slight Deficit"
                                    else -> "Balanced"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                            )
                        }

                        if (bmrCaloriesHc != null && (healthConnectViewModel.latestWeight == null)) { // Assuming latestWeight is StateFlow
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            InfoMessage(text = "BMR accuracy depends on your profile. Please keep it updated.")
                        }
                        if (foodError != null && displayError != foodError) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            InfoMessage(text = "Food Data Error: $foodError", isError = true)
                        }
                    }
                } else {
                    Text(
                        "No energy data available to display.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .padding(all = 16.dp)
                            .fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    )
}

@Composable
fun EnergyDataRow(
    label: String,
    value: Double?,
    unit: String = "kcal",
    modifier: Modifier = Modifier, // Allow passing custom modifiers
    labelStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    valueStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    valueColor: Color? = null, // Allow overriding the default value color
    isSubtle: Boolean = false // If true, use onSurfaceVariant for both label and value by default
) {
    val currentLabelStyle = if (isSubtle) MaterialTheme.typography.bodySmall else labelStyle
    val currentValueStyle = if (isSubtle) MaterialTheme.typography.bodySmall else valueStyle

    // Determine the color for the value text
    val finalValueColor = when {
        isSubtle -> MaterialTheme.colorScheme.onSurfaceVariant // Subtle items use variant color
        valueColor != null -> valueColor                         // Explicit color override
        value != null -> MaterialTheme.colorScheme.primary       // Default for actual values
        else -> MaterialTheme.colorScheme.onSurfaceVariant     // Default for "N/A"
    }
    val finalLabelColor = if (isSubtle) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface


    Row(
        modifier = modifier // Apply the passed modifier first
            .fillMaxWidth()
            .padding(vertical = 4.dp), // Then apply internal padding
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = currentLabelStyle,
            color = finalLabelColor,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(
                text = "${String.format("%.0f", value)} $unit",
                style = currentValueStyle,
                color = finalValueColor
            )
        } else {
            Text(
                text = "N/A",
                style = currentValueStyle,
                color = finalValueColor // N/A also uses the determined value color logic
            )
        }
    }
}

@Composable
fun EnergyProgressBar(
    currentEnergy: Long,
    minEnergy: Long = 1200L,
    optimalEnergy: Long = 2200L,
    maxEnergy: Long = 3000L,
    height: Dp = 14.dp,
    modifier: Modifier = Modifier
) {
    val progress = ((currentEnergy - minEnergy).toFloat() / (maxEnergy - minEnergy).toFloat()).coerceIn(0f, 1f)

    val lowColor = Color(0xFF1976D2) // Blue
    val midColor = Color(0xFF4CAF50) // Green
    val highColor = Color(0xFFD32F2F) // Red

    val targetColor = if (currentEnergy <= optimalEnergy) {
        lerp(lowColor, midColor, ((currentEnergy - minEnergy).toFloat() / (optimalEnergy - minEnergy).toFloat()).coerceIn(0f, 1f))
    } else {
        lerp(midColor, highColor, ((currentEnergy - optimalEnergy).toFloat() / (maxEnergy - optimalEnergy).toFloat()).coerceIn(0f, 1f))
    }

    val animatedColor by animateColorAsState(targetColor, label = "ProgressBarColorAnimation")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(Color.LightGray.copy(alpha = 0.3f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(animatedColor)
        )
    }
}

@Composable
private fun InfoMessage(text: String, isError: Boolean = false) {
    Row(
        modifier = Modifier
            .padding(top = 8.dp, bottom = 4.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isError) Icons.Outlined.ReportProblem else Icons.Outlined.Info,
            contentDescription = if (isError) "Error" else "Information",
            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
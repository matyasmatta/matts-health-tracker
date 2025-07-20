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
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.units.calories
import com.example.mattshealthtracker.AppUiElements.CollapsibleCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.collections.toTypedArray
import kotlin.math.floor
import kotlin.math.roundToInt


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

// --- END NEW DATA MODELS ---

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FoodScreen(openedDay: String) {
    val context = LocalContext.current
    val mealTrackerHelper = remember { MealTrackerHelper(context) }
    val groceryDatabaseHelper = remember { GroceryDatabaseHelper(context) } // Ensure this is initialized
    val coroutineScope = rememberCoroutineScope()

    val foodItemsByMeal = remember {
        mutableStateMapOf<MealType, SnapshotStateList<FoodItem>>().apply {
            MealType.entries.forEach { mealType ->
                put(mealType, mutableStateListOf()) // Initialize with empty lists
            }
        }
    }

    // State for managing dialog visibility for editing cards
    var showEditCardsDialog by rememberSaveable { mutableStateOf(false) }

    // Use a mutableStateListOf to manage card order and visibility
    // Default visibility and expansion should ideally be loaded from preferences/settings
    val cards = remember {
        mutableStateListOf(
            HealthCard("energy", "🔥 Energy Use Today", true, false),
            HealthCard("trends", "📊 Dietary Trends", false, false),
            HealthCard("food_input", "🍎 Food", true, true) // Food card initially visible and expanded
        )
    }

    // State for individual card expansion (map card ID to its expanded state)
    val cardExpandedStates = remember {
        mutableStateMapOf<String, Boolean>().apply {
            cards.forEach { card ->
                this[card.id] = card.defaultExpanded
            }
        }
    }
    // Convenience accessors for specific cards if needed frequently, otherwise use the map
    val foodExpanded = remember {
        derivedStateOf { cardExpandedStates["food_input"] ?: true }
    }
    // Add similar derivedStateOf for energyExpanded and trendsExpanded if you use them directly elsewhere

    // Initialize HealthConnectViewModel
    val healthConnectViewModel: HealthConnectViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(HealthConnectViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return HealthConnectViewModel(context.applicationContext) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    )

    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissionsResult ->
            val allPermissionsGranted = healthConnectViewModel.permissions.all { permissionString ->
                permissionsResult[permissionString] == true
            }

            if (allPermissionsGranted) {
                Log.d("FoodScreen", "All Health Connect permissions granted.")
                healthConnectViewModel.permissionsGranted = true
                coroutineScope.launch {
                    healthConnectViewModel.checkPermissionsAndFetchData()
                }
            } else {
                Log.w("FoodScreen", "Not all Health Connect permissions granted.")
                healthConnectViewModel.permissionsGranted = false
                // You might want to show a Snackbar or a more persistent message here
                healthConnectViewModel.errorMessage = "Not all necessary Health Connect permissions were granted. Some features might be unavailable."
            }
        }
    )

    LaunchedEffect(openedDay, mealTrackerHelper) { // Re-run if openedDay or helper changes
        Log.d("FoodScreen", "LaunchedEffect for openedDay: $openedDay. Loading food items.")
        MealType.entries.forEach { mealType ->
            foodItemsByMeal[mealType]?.clear() // Clear previous day's items
            coroutineScope.launch { // Launch DB operations in a coroutine
                try {
                    val loadedItems = mealTrackerHelper.fetchFoodItemsForMeal(openedDay, mealType)
                    foodItemsByMeal[mealType]?.addAll(loadedItems)
                    Log.d("FoodScreen", "Loaded ${loadedItems.size} items for $mealType on $openedDay")
                } catch (e: Exception) {
                    Log.e("FoodScreen", "Error loading food items for $mealType on $openedDay", e)
                    // Optionally show an error message to the user
                }
            }
        }
    }

    // Initial check for permissions when the screen is first composed
    LaunchedEffect(Unit) {
        // First, check if Health Connect itself is available on the device
        // healthConnectViewModel.healthConnectAvailable is updated in ViewModel's init
        if (!healthConnectViewModel.healthConnectAvailable) {
            healthConnectViewModel.errorMessage = "Health Connect App is not available or not installed on this device."
            Log.w("FoodScreen", "Health Connect not available on this device.")
            // Optionally, guide the user to install Health Connect via openHealthConnectSettings()
            // if they try to interact with a feature that needs it.
            // For now, we just set an error message.
            return@LaunchedEffect // Exit early if HC is not available
        }

        // If Health Connect is available, then proceed to check/request permissions
        if (!healthConnectViewModel.permissionsGranted) {
            // Check if we should show rationale. For simplicity, directly requesting here.
            // In a real app, you might show a rationale dialog before this.
            Log.d("FoodScreen", "Health Connect available, permissions not granted. Requesting permissions.")
            // The permissions array is already prepared in the ViewModel
            requestPermissionsLauncher.launch(healthConnectViewModel.permissions)
        } else {
            // Permissions are already granted, fetch data
            Log.d("FoodScreen", "Health Connect available and permissions already granted. Fetching data.")
            coroutineScope.launch {
                healthConnectViewModel.checkPermissionsAndFetchData()
            }
        }
    }


    // Dialog for editing visible cards
    if (showEditCardsDialog) {
        Dialog(onDismissRequest = { showEditCardsDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp), // Add padding around the card itself
                shape = RoundedCornerShape(16.dp) // Softer corners for the dialog
            ) {
                Column(modifier = Modifier.padding(16.dp)) { // Padding inside the card
                    Text(
                        "Edit Visible Cards",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    cards.forEach { card ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp), // Spacing between items
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
                                        // Persist this change to SharedPreferences or database
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
            .padding(horizontal = 16.dp, vertical = 8.dp) // Consistent padding
            .verticalScroll(screenScrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp) // Space between cards
    ) {
        // Optional: Header Row with a button to open "Edit Visible Cards" dialog
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Food Dashboard", // Example Title
                style = MaterialTheme.typography.titleLarge
            )
            IconButton(onClick = { showEditCardsDialog = true }) {
                Icon(Icons.Filled.Settings, contentDescription = "Edit Visible Cards") // Using a settings icon
            }
        }

        // Display error message from HealthConnectViewModel if any
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


        // --- Render cards based on the `cards` list and their `isVisible` property ---
        cards.forEach { card ->
            if (card.isVisible) {
                // Get current expanded state, defaulting to card's default if not in map yet
                // This 'currentExpandedState' IS the Boolean value.
                val currentExpandedState = cardExpandedStates[card.id] ?: card.defaultExpanded

                // This lambda now correctly matches Function1<Boolean, Unit>
                // It's called by the child card (e.g., CollapsibleCard) with the new state.
                val onExpansionChangedByChild = { newExpandedState: Boolean ->
                    cardExpandedStates[card.id] = newExpandedState
                }

                when (card.id) {
                    "energy" -> {
                        EnergyCard(
                            expanded = currentExpandedState, // Pass the Boolean directly
                            onExpandedChange = onExpansionChangedByChild, // Pass the correct lambda type
                            healthConnectViewModel = healthConnectViewModel
                        )
                    }
                    "trends" -> {
                        TrendsCard(
                            expanded = currentExpandedState, // Pass the Boolean directly
                            onExpandedChange = onExpansionChangedByChild // Pass the correct lambda type
                            // Pass any necessary data
                        )
                    }
                    "food_input" -> {
                        FoodInputCard(
                            expanded = currentExpandedState, // Pass the Boolean directly
                            onExpandedChange = onExpansionChangedByChild, // Pass the correct lambda type
                            mealTrackerHelper = mealTrackerHelper,
                            groceryDatabaseHelper = groceryDatabaseHelper,
                            openedDay = openedDay,
                            foodItemsByMeal = foodItemsByMeal
                        )
                    }
                    // Add cases for other cards if you have them
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp)) // Extra space at the bottom
    }
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
    healthConnectViewModel: HealthConnectViewModel
) {
    // Directly use the properties from the ViewModel
    val activeCalories by healthConnectViewModel::activeCaloriesBurned
    val bmrCalories by healthConnectViewModel::bmr // Basal Metabolic Rate
    val isLoading by healthConnectViewModel::isLoading
    val errorMessage by healthConnectViewModel::errorMessage

    // Calculate Total Energy Burned (BMR for the day + Active Calories)
    // Note: BMR is a rate (kcal/day). If your 'bmr' value is already daily, this is correct.
    // If 'bmr' is an instantaneous rate, you'd need to integrate it over the day so far.
    // Assuming 'bmr' in ViewModel is daily BMR.
    val totalEnergyBurned by remember(activeCalories, bmrCalories) {
        derivedStateOf {
            if (activeCalories != null && bmrCalories != null) {
                activeCalories!! + bmrCalories!!
            } else if (activeCalories != null) {
                activeCalories // Only active is available
            } else if (bmrCalories != null) {
                bmrCalories // Only BMR is available (less common for "total burned")
            } else {
                null // Neither is available
            }
        }
    }

    AppUiElements.CollapsibleCard(
        titleContent = {
            Text(
                "🔥 Energy Use Today",
                style = MaterialTheme.typography.titleMedium
            )
        },
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        isExpandable = true,
        quickGlanceInfo = {
            if (!expanded) {
                when {
                    isLoading -> CircularProgressIndicator(modifier = Modifier
                        .size(18.dp)
                        .padding(start = 8.dp))
                    totalEnergyBurned != null -> Text(
                        text = "${String.format("%.0f", totalEnergyBurned)} kcal",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    // Optionally show active if total isn't available
                    activeCalories != null -> Text(
                        text = "${String.format("%.0f", activeCalories)} kcal (Active)",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        },
        defaultContent = {
            // This content is shown when collapsed OR if expanded and hideDefaultWhenExpanded = false.
            if (isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Loading energy data...", style = MaterialTheme.typography.bodyMedium)
                }
            } else if (totalEnergyBurned != null || bmrCalories != null || activeCalories != null) {
                Column(modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)) {
                    EnergyDataRow("Total Burned:", totalEnergyBurned)
                    EnergyDataRow("Basal (BMR):", bmrCalories) // Displaying BMR as "Basal"
                    EnergyDataRow("Active Burned:", activeCalories)
                }
            } else if (!errorMessage.isNullOrEmpty() && errorMessage?.contains("Health Connect", ignoreCase = true) != true) {
                // Show specific error if not a general HC error already shown in FoodScreen
                Text(
                    errorMessage ?: "Could not load energy data.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(all = 8.dp)
                )
            } else if (!expanded) {
                // If collapsed and no data yet (and not loading or error), show a placeholder
                Text(
                    "Tap to view energy details.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(all = 8.dp)
                )
            }
        },
        expandableContent = {
            // This content appears below defaultContent when expanded (as hideDefaultWhenExpanded = false)
            // You can add more detailed information here, like charts or explanations.
            if (bmrCalories != null && (healthConnectViewModel.latestWeight == null /* or other profile data is missing */)) {
                Text(
                    "BMR is calculated using stored or default user profile data. Ensure your profile (weight, height, age, gender) is up to date for accuracy.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            // Display general error message if relevant and not already covered.
            // The main errorMessage from ViewModel is already shown at the top of FoodScreen.
            // This could be for more specific errors related to this card if needed.
        },
        hideDefaultWhenExpanded = false // Set to true if defaultContent should disappear when expanded
    )
}

@Composable
private fun EnergyDataRow(label: String, value: Double?, unit: String = "kcal") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(
                text = "${String.format("%.0f", value)} $unit",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary // Or adjust as needed
            )
        } else {
            Text(
                text = "N/A",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CardItem(card: HealthCard, onToggleVisibility: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = "Drag to reorder",
            modifier = Modifier.padding(end = 8.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = card.title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Checkbox(
            checked = card.isVisible,
            onCheckedChange = onToggleVisibility,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.dragAndDrop(
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    items: SnapshotStateList<HealthCard>,
    index: Int
) = composed {
    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(0f) }
    var itemHeight by remember { mutableStateOf(0f) }

    val density = LocalDensity.current

    this
        .onGloballyPositioned { coordinates ->
            if (itemHeight == 0f) {
                itemHeight = coordinates.size.height.toFloat()
            }
        }
        .graphicsLayer {
            if (isDragging) {
                translationY = dragOffset
                alpha = 0.8f
                shadowElevation = with(density) { 8.dp.toPx() }
                scaleX = 1.05f
                scaleY = 1.05f
            }
        }
        .pointerInput(items.size) {
            detectDragGesturesAfterLongPress(
                onDragStart = { _ ->
                    isDragging = true
                    dragOffset = 0f
                },
                onDragEnd = {
                    isDragging = false
                    dragOffset = 0f
                },
                onDragCancel = {
                    isDragging = false
                    dragOffset = 0f
                },
                onDrag = { _, dragAmount ->
                    if (isDragging) {
                        dragOffset += dragAmount.y

                        val currentActualIndex = items.indexOfFirst { it.id == items[index].id }
                        if (currentActualIndex != -1) {
                            val targetIndex = calculateTargetIndex(
                                dragOffset,
                                itemHeight,
                                currentActualIndex,
                                items.size
                            )

                            if (targetIndex != currentActualIndex && targetIndex in 0 until items.size) {
                                val draggedItem = items[currentActualIndex]
                                items.removeAt(currentActualIndex)
                                items.add(targetIndex, draggedItem)

                                dragOffset += (currentActualIndex - targetIndex) * itemHeight

                                Log.d(
                                    "DragAndDrop",
                                    "Moved item from $currentActualIndex to $targetIndex"
                                )
                            }
                        }
                    }
                }
            )
        }
}

private fun calculateTargetIndex(
    dragOffset: Float,
    itemHeight: Float,
    currentIndex: Int,
    totalItems: Int
): Int {
    val threshold = itemHeight / 2

    return when {
        dragOffset > threshold -> {
            (currentIndex + 1).coerceAtMost(totalItems - 1)
        }
        dragOffset < -threshold -> {
            (currentIndex - 1).coerceAtLeast(0)
        }
        else -> currentIndex
    }
}


/**
 * A custom progress bar that visually represents energy levels with a blue-green-red gradient.
 * The bar fills based on `currentEnergy` relative to `minEnergy` and `maxEnergy`,
 * and its color changes from blue (low) to green (optimal) to red (high).
 *
 * @param currentEnergy The current energy value in kcal.
 * @param minEnergy The minimum energy value for the scale (default: 1200 kcal).
 * @param optimalEnergy The optimal energy value for the scale, where the color transitions to green (default: 2200 kcal).
 * @param maxEnergy The maximum energy value for the scale (default: 3000 kcal).
 * @param height The height of the progress bar.
 * @param modifier Modifier to be applied to the top-level Box of the progress bar.
 */
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

// --- NEW FOOD UI COMPONENTS ---

@Composable
fun MealSection(
    mealType: MealType,
    foodItems: SnapshotStateList<FoodItem>,
    onAddItem: () -> Unit,
    onUpdateItem: (FoodItem) -> Unit,
    onDeleteItem: (FoodItem) -> Unit,
    groceryDatabaseHelper: GroceryDatabaseHelper
) {
    // Determine the emoji based on the MealType
    val emoji = when (mealType) {
        MealType.BREAKFAST -> "🍳  Breakfast"
        MealType.SNACK1 -> "🥜  Morning Snack"
        MealType.LUNCH -> "🍝  Lunch"
        MealType.SNACK2 -> "🍏  Afternoon Snack"
        MealType.DINNER -> "🍲  Dinner"
        MealType.SNACK3 -> "🍪  Nighly Snack"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row( // New Row to contain the title and the add button
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = emoji, // Added emoji here!
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal), // Less bold
                modifier = Modifier.weight(1f) // Makes text take up available space
            )
            // Show "Add Food Item" button next to the title
            if (foodItems.isEmpty()) { // Only show "Add Food Item" if the list is empty
                Button(
                    onClick = onAddItem,
                    modifier = Modifier.wrapContentWidth(Alignment.End), // Aligns to the right
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), // Make button smaller
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Food", modifier = Modifier.size(18.dp)) // Smaller icon
                    Spacer(Modifier.width(4.dp)) // Smaller spacer
                    Text("Add Food Item", style = MaterialTheme.typography.labelMedium) // Smaller text style
                }
            }
        }

        // List existing food items
        if (foodItems.isNotEmpty()) {
            foodItems.forEach { foodItem ->
                FoodItemInput(
                    foodItem = foodItem,
                    onUpdate = onUpdateItem,
                    onDelete = onDeleteItem,
                    groceryDatabaseHelper = groceryDatabaseHelper // Pass the helper here!
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            // Show "Add Another Item" button below existing items
            Button(
                onClick = onAddItem,
                modifier = Modifier
                    .wrapContentWidth(Alignment.Start) // Retain left alignment
                    .padding(vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Food")
                Spacer(Modifier.width(8.dp))
                Text("Add Another Item")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FoodItemInput(
    foodItem: FoodItem,
    onUpdate: (FoodItem) -> Unit,
    onDelete: (FoodItem) -> Unit,
    groceryDatabaseHelper: GroceryDatabaseHelper // Pass the new helper here
) {
    var title by rememberSaveable { mutableStateOf(foodItem.title) }
    var calories by rememberSaveable { mutableStateOf(foodItem.calories.toString()) }
    var healthyRating by rememberSaveable { mutableStateOf(foodItem.healthyRating) }
    var lprFriendlyRating by rememberSaveable { mutableStateOf(foodItem.lprFriendlyRating) }

    // Convert ingredients string to a mutable list of strings for UI display
    // Make sure to trim whitespace and filter out empty strings
    val ingredientsList = remember {
        mutableStateListOf<String>().apply {
            addAll(foodItem.ingredients.split(",").map { it.trim() }.filter { it.isNotBlank() })
        }
    }
    var currentIngredientInput by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    // State for search suggestions
    var showSuggestions by remember { mutableStateOf(false) }
    val suggestions = remember(currentIngredientInput) {
        if (currentIngredientInput.isBlank()) {
            emptyList()
        } else {
            groceryDatabaseHelper.searchGroceries(currentIngredientInput)
        }
    }

    var isEditing by rememberSaveable { mutableStateOf(foodItem.title == "New Food Item" && foodItem.calories == 0) }

    val applyChanges = {
        val parsedCalories = calories.toIntOrNull() ?: 0
        // Convert the ingredients list back to a comma-separated string for saving
        val updatedIngredientsString = ingredientsList.joinToString(", ") { it.trim() }

        onUpdate(foodItem.copy(
            title = title,
            calories = parsedCalories,
            healthyRating = healthyRating,
            lprFriendlyRating = lprFriendlyRating,
            ingredients = updatedIngredientsString
        ))
        isEditing = false
    }

    // Function to add a chip
    val addIngredientChip: (String) -> Unit = { ingredient ->
        if (ingredient.isNotBlank() && ingredient !in ingredientsList) {
            ingredientsList.add(ingredient)
            currentIngredientInput = "" // Clear input after adding
            focusManager.clearFocus() // Clear focus after adding
        }
    }

    // Function to remove a chip
    val removeIngredientChip: (String) -> Unit = { ingredient ->
        ingredientsList.remove(ingredient)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (isEditing) {
                // Editable fields
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Food Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = calories,
                    onValueChange = { newValue ->
                        calories = newValue.filter { it.isDigit() }
                    },
                    label = { Text("Calories") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Healthy Rating: ${(healthyRating * 10).roundToInt()}/10", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = healthyRating,
                    onValueChange = { healthyRating = it },
                    steps = 9,
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("LPR Friendly Rating: ${(lprFriendlyRating * 10).roundToInt()}/10", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = lprFriendlyRating,
                    onValueChange = { lprFriendlyRating = it },
                    steps = 9,
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Ingredient Input with Chip display and Suggestions
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = currentIngredientInput,
                        onValueChange = {
                            currentIngredientInput = it
                            showSuggestions = it.isNotBlank() // Show suggestions if input is not blank
                        },
                        label = { Text("Add Ingredients (comma or Enter separated)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                addIngredientChip(currentIngredientInput.trim())
                                showSuggestions = false // Hide suggestions on Done
                            }
                        ),
                        trailingIcon = {
                            if (currentIngredientInput.isNotBlank()) {
                                IconButton(onClick = { currentIngredientInput = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear input")
                                }
                            }
                        }
                    )

                    // Display search suggestions if applicable
                    if (showSuggestions && suggestions.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column {
                                suggestions.take(5).forEach { grocery -> // Limit to top 5 suggestions
                                    Text(
                                        text = grocery.name,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null // No ripple on suggestion click
                                            ) {
                                                addIngredientChip(grocery.name)
                                                showSuggestions =
                                                    false // Hide suggestions after selection
                                            }
                                            .padding(8.dp)
                                    )
                                    if (suggestions.indexOf(grocery) < suggestions.size - 1 && suggestions.indexOf(grocery) < 4) {
                                        Divider()
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Display ingredients as chips
                    // Display ingredients as chips with close buttons
                    if (ingredientsList.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ingredientsList.forEach { ingredient ->
                                SuggestionChip(
                                    // onClick for the chip itself (optional, could make it editable)
                                    onClick = { /* No specific action for clicking the chip itself for now */ },
                                    label = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(ingredient)
                                            // THIS IS THE CORRECTED PART: Call removeIngredientChip
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove $ingredient",
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable { // <-- The clickable modifier
                                                        removeIngredientChip(ingredient) // Call the local function
                                                    }
                                            )
                                        }
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = { onDelete(foodItem) }) {
                        Icon(Icons.Default.Close, contentDescription = "Delete Food Item")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = applyChanges) {
                        Icon(Icons.Default.Done, contentDescription = "Apply Changes")
                    }
                }
            } else {
                // Display mode (unchanged from your previous code)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(foodItem.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${foodItem.calories} kcal", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row {
                        IconButton(onClick = { isEditing = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Food Item")
                        }
                        IconButton(onClick = { onDelete(foodItem) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Food Item")
                        }
                    }
                }
                // Display chips in display mode too
                if (ingredientsList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Ingredients:", style = MaterialTheme.typography.bodySmall)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ingredientsList.forEach { ingredient ->
                            SuggestionChip(
                                onClick = { /* No action in display mode */ },
                                label = { Text(ingredient) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Healthy: ", style = MaterialTheme.typography.bodySmall)
                    repeat(foodItem.healthyRating.times(10).roundToInt()) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFA726), modifier = Modifier.size(16.dp))
                    }
                    repeat(10 - foodItem.healthyRating.times(10).roundToInt()) {
                        Icon(Icons.Filled.StarBorder, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("LPR Friendly: ", style = MaterialTheme.typography.bodySmall)
                    repeat(foodItem.lprFriendlyRating.times(10).roundToInt()) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFF66BB6A), modifier = Modifier.size(16.dp))
                    }
                    repeat(10 - foodItem.lprFriendlyRating.times(10).roundToInt()) {
                        Icon(Icons.Filled.StarBorder, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

// Helper function to format MealType for display
@Composable
fun formatMealTypeName(mealType: MealType): String {
    return when (mealType) {
        MealType.BREAKFAST -> "Breakfast"
        MealType.SNACK1 -> "Snack 1"
        MealType.LUNCH -> "Lunch"
        MealType.SNACK2 -> "Snack 2"
        MealType.DINNER -> "Dinner"
        MealType.SNACK3 -> "Snack 3"
    }
}


// --- End NEW FOOD UI COMPONENTS ---
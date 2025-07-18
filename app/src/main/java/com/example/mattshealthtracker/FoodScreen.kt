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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import kotlin.math.roundToInt


// Data class to represent each card's properties
data class HealthCard(
    val id: String,
    val title: String,
    var isVisible: Boolean,
    val defaultExpanded: Boolean = false
)

// --- NEW DATA MODELS FOR FOOD TRACKING ---
enum class MealType {
    BREAKFAST, SNACK1, LUNCH, SNACK2, DINNER, SNACK3
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


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FoodScreen(openedDay: String) {
    val context = LocalContext.current
    val mealTrackerHelper = remember { MealTrackerHelper(context) }
    // Initialize groceryDatabaseHelper here at the top level of FoodScreen
    val groceryDatabaseHelper = remember { GroceryDatabaseHelper(context) }
    val coroutineScope = rememberCoroutineScope()
    val foodItemsByMeal = remember {
        mutableStateMapOf<MealType, SnapshotStateList<FoodItem>>().apply {
            MealType.entries.forEach { mealType ->
                // Initialize each meal type with an empty SnapshotStateList
                put(mealType, mutableStateListOf())
            }
        }
    }
    // In your FoodScreen.kt, near other state declarations
    var showFoodCardSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(openedDay) { // This runs whenever `openedDay` changes
        Log.d("FoodScreen", "LaunchedEffect for openedDay: $openedDay triggered. Reloading data.")

        MealType.entries.forEach { mealType ->
            // Clear the existing items for this meal type before loading new ones
            foodItemsByMeal[mealType]?.clear() // Clear existing items

            coroutineScope.launch { // Launch in a coroutine as DB operations can be long
                val loadedItems = mealTrackerHelper.fetchFoodItemsForMeal(openedDay, mealType)
                // Update the SnapshotStateList directly
                foodItemsByMeal[mealType]?.addAll(loadedItems)
                Log.d("FoodScreen", "Loaded ${loadedItems.size} items for $mealType on $openedDay")
            }
        }
    }

    // State for managing dialog visibility
    var showEditCardsDialog by remember { mutableStateOf(false) }

    // Use a mutableStateListOf to manage card order and visibility
    val cards = remember {
        mutableStateListOf(
            HealthCard("energy", "🔥 Energy Use Today", true, false),
            HealthCard("trends", "📊 Dietary Trends", false, false),
            HealthCard("food_input", "🍎 Food", true, true)
        )
    }

    // State for individual card expansion (kept separate for UI logic)
    val energyExpanded = remember { mutableStateOf(cards.first { it.id == "energy" }.defaultExpanded) }
    val trendsExpanded = remember { mutableStateOf(cards.first { it.id == "trends" }.defaultExpanded) }
    val foodExpanded = remember { mutableStateOf(cards.first { it.id == "food_input" }.defaultExpanded) }

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
                healthConnectViewModel.errorMessage = "Not all necessary Health Connect permissions were granted."
            }
        }
    )

    // Dialog for editing visible cards
    if (showEditCardsDialog) {
        Dialog(onDismissRequest = { showEditCardsDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
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
                                .padding(vertical = 4.dp),
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
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
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

    LaunchedEffect(Unit) {
        healthConnectViewModel.checkPermissionsAndFetchData()

        // --- LOAD FOOD ITEMS FROM DATABASE ON INITIAL COMPOSITION ---
        MealType.entries.forEach { mealType ->
            coroutineScope.launch { // Launch in a coroutine as DB operations can be long
                val loadedItems = mealTrackerHelper.fetchFoodItemsForMeal(openedDay, mealType)
                foodItemsByMeal[mealType] = loadedItems.toMutableStateList() // Convert to SnapshotStateList
                Log.d("FoodScreen", "Loaded ${loadedItems.size} items for $mealType on $openedDay")
            }
        }
        // --- END LOAD FOOD ITEMS ---
    }

    val isLoading = healthConnectViewModel.isLoading
    val healthConnectAvailable = healthConnectViewModel.healthConnectAvailable
    val permissionsGranted = healthConnectViewModel.permissionsGranted
    val errorMessage = healthConnectViewModel.errorMessage

    val bmr = healthConnectViewModel.bmr
    val activeCaloriesBurned = healthConnectViewModel.activeCaloriesBurned

    val totalEnergy = FoodHelper.calculateTotalEnergy(bmr, activeCaloriesBurned)
    val energyStatusPhrase = FoodHelper.getEnergyStatusPhrase(totalEnergy)
    val energyStatusDetailed = FoodHelper.getEnergyStatus(totalEnergy)

    // NEW: Scroll state for the entire screen
    val screenScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(screenScrollState), // Make the entire column scrollable
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // --- Header Row with Title and Edit Icon ---
        // In your FoodScreen.kt, below your main Column, but still within the @Composable FoodScreen function:

// Dialog for editing food card settings
        if (showFoodCardSettingsDialog) {
            Dialog(onDismissRequest = { showFoodCardSettingsDialog = false }) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Edit Food Card Settings",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Find the actual food_input card from your mutableStateListOf `cards`
                        val foodInputCard = cards.find { it.id == "food_input" }

                        if (foodInputCard != null) {
                            // Checkbox for visibility
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Show Food Card", style = MaterialTheme.typography.bodyLarge)
                                Switch(
                                    checked = foodInputCard.isVisible,
                                    onCheckedChange = { isChecked ->
                                        // Update the card's visibility in the mutable list
                                        val index = cards.indexOfFirst { it.id == foodInputCard.id }
                                        if (index != -1) {
                                            cards[index] = foodInputCard.copy(isVisible = isChecked)
                                        }
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            // Checkbox for default expanded state
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Expanded by Default", style = MaterialTheme.typography.bodyLarge)
                                Switch(
                                    checked = foodInputCard.defaultExpanded,
                                    onCheckedChange = { isChecked ->
                                        val index = cards.indexOfFirst { it.id == foodInputCard.id }
                                        if (index != -1) {
                                            cards[index] = foodInputCard.copy(defaultExpanded = isChecked)
                                            // Also update the current expansion state immediately if changed
                                            foodExpanded.value = isChecked
                                        }
                                    }
                                )
                            }
                        } else {
                            Text("Food card not found.", color = MaterialTheme.colorScheme.error)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showFoodCardSettingsDialog = false },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Food Tracking",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { showEditCardsDialog = true },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit visible cards",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // --- End Header Row ---

        when {
            isLoading -> {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                Text("Loading health data...", style = MaterialTheme.typography.bodyLarge)
            }
            !healthConnectAvailable -> {
                Text(
                    "Health Connect is not available on this device.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(onClick = { healthConnectViewModel.openHealthConnectSettings() }) {
                    Text("Open Health Connect Store Page")
                }
            }
            errorMessage != null -> {
                Text(
                    "Error: $errorMessage",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { healthConnectViewModel.openHealthConnectSettings() }) {
                    Text("Open Health Connect Settings")
                }
                Button(onClick = { healthConnectViewModel.requestPermissions(requestPermissionsLauncher) }) {
                    Text("Request Permissions Again")
                }
            }
            !permissionsGranted -> {
                Text(
                    "Permissions are required to access health data.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(onClick = { healthConnectViewModel.requestPermissions(requestPermissionsLauncher) }) {
                    Text("Grant Permissions")
                }
                Button(onClick = { healthConnectViewModel.openHealthConnectSettings() }) {
                    Text("Open Health Connect Settings")
                }
            }
            else -> {
                // Render cards based on the 'cards' state and their visibility
                cards.forEach { card ->
                    if (card.isVisible) {
                        when (card.id) {
                            "energy" -> AppUiElements.CollapsibleCard(
                                titleContent = { Text(text = card.title, style = MaterialTheme.typography.titleMedium) },
                                isExpandable = true,
                                expanded = energyExpanded.value,
                                onExpandedChange = { energyExpanded.value = it },
                                hideDefaultWhenExpanded = true,
                                defaultContent = {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = energyStatusPhrase,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Normal,
                                                textAlign = TextAlign.Center
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))

                                        if (totalEnergy != null) {
                                            EnergyProgressBar(
                                                currentEnergy = totalEnergy,
                                                modifier = Modifier.padding(horizontal = 8.dp),
                                                height = 14.dp
                                            )
                                        } else {
                                            Text(
                                                text = "Energy data not available for progress bar.",
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.fillMaxWidth(),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                },
                                expandableContent = {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Basal Subtotal:", style = MaterialTheme.typography.bodyLarge)
                                            Text(FoodHelper.formatEnergyValue(bmr), style = MaterialTheme.typography.bodyLarge)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Active Subtotal:", style = MaterialTheme.typography.bodyLarge)
                                            Text(FoodHelper.formatEnergyValue(activeCaloriesBurned), style = MaterialTheme.typography.bodyLarge)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))

                                        Divider(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(1.dp),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Total:", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                            Text(FoodHelper.formatTotalEnergy(totalEnergy), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Status: $energyStatusDetailed",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                },
                                defaultContentModifier = Modifier.padding(bottom = 4.dp),
                                expandableContentModifier = Modifier.padding(top = 4.dp)
                            )
                            "trends" -> AppUiElements.CollapsibleCard(
                                titleContent = { Text(text = card.title, style = MaterialTheme.typography.titleMedium) },
                                isExpandable = true,
                                expanded = trendsExpanded.value,
                                onExpandedChange = { trendsExpanded.value = it },
                                hideDefaultWhenExpanded = true,
                                defaultContent = {
                                    Text(
                                        "You have a healthy surplus of energy",
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            color = MaterialTheme.colorScheme.secondary,
                                            textAlign = TextAlign.Center
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                expandableContent = {
                                    Text(
                                        "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            )
                            "food_input" -> AppUiElements.CollapsibleCard(
                                titleContent = { Text(text = card.title, style = MaterialTheme.typography.titleMedium) },
                                isExpandable = true,
                                expanded = foodExpanded.value,
                                onExpandedChange = { foodExpanded.value = it },
                                hideDefaultWhenExpanded = true,
                                defaultContent = {
                                    Text(
                                        "Track your food intake for the day!",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                // ADD THIS NEW BLOCK:
                                trailingContent = {
                                    IconButton(onClick = { showFoodCardSettingsDialog = true }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit Food Card Settings")
                                    }
                                },
                                // End of new block
                                expandableContent = {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(16.dp) // Spacing between meal sections
                                    ) {
                                        // Breakfast Section
                                        MealSection(
                                            mealType = MealType.BREAKFAST,
                                            foodItems = foodItemsByMeal.getOrDefault(MealType.BREAKFAST, mutableStateListOf()),
                                            onAddItem = {
                                                val newList = foodItemsByMeal[MealType.BREAKFAST] ?: mutableStateListOf()
                                                newList.add(FoodItem())
                                                foodItemsByMeal[MealType.BREAKFAST] = newList
                                                coroutineScope.launch {
                                                    mealTrackerHelper.saveFoodItemsForMeal(openedDay, MealType.BREAKFAST, newList.toList())
                                                }
                                            },
                                            onUpdateItem = { updatedItem ->
                                                val list = foodItemsByMeal.getValue(MealType.BREAKFAST)
                                                val index = list.indexOfFirst { it.id == updatedItem.id }
                                                if (index != -1) {
                                                    list[index] = updatedItem
                                                    coroutineScope.launch {
                                                        mealTrackerHelper.saveFoodItemsForMeal(openedDay, MealType.BREAKFAST, list.toList())
                                                    }
                                                }
                                            },
                                            onDeleteItem = { itemToDelete ->
                                                val list = foodItemsByMeal.getValue(MealType.BREAKFAST)
                                                list.remove(itemToDelete)
                                                coroutineScope.launch {
                                                    mealTrackerHelper.saveFoodItemsForMeal(openedDay, MealType.BREAKFAST, list.toList())
                                                }
                                            },
                                            groceryDatabaseHelper = groceryDatabaseHelper // PASSING IT HERE!
                                        )
                                        // Snack 1 Section (After Breakfast)
                                        MealSection(
                                            mealType = MealType.SNACK1,
                                            foodItems = foodItemsByMeal.getOrDefault(MealType.SNACK1, mutableStateListOf()),
                                            onAddItem = {
                                                val newList = foodItemsByMeal[MealType.SNACK1] ?: mutableStateListOf()
                                                newList.add(FoodItem())
                                                foodItemsByMeal[MealType.SNACK1] = newList
                                                coroutineScope.launch {
                                                    mealTrackerHelper.saveFoodItemsForMeal(openedDay, MealType.SNACK1, newList.toList())
                                                }
                                            },
                                            onUpdateItem = { updatedItem ->
                                                val list = foodItemsByMeal.getValue(MealType.SNACK1)
                                                val index = list.indexOfFirst { it.id == updatedItem.id }
                                                if (index != -1) {
                                                    list[index] = updatedItem
                                                    coroutineScope.launch {
                                                        mealTrackerHelper.saveFoodItemsForMeal(openedDay, MealType.SNACK1, list.toList())
                                                    }
                                                }
                                            },
                                            onDeleteItem = { itemToDelete ->
                                                val list = foodItemsByMeal.getValue(MealType.SNACK1)
                                                list.remove(itemToDelete)
                                                coroutineScope.launch {
                                                    mealTrackerHelper.saveFoodItemsForMeal(openedDay, MealType.SNACK1, list.toList())
                                                }
                                            },
                                            groceryDatabaseHelper = groceryDatabaseHelper // PASSING IT HERE!
                                        )
                                        Divider() // Simple divider between sections


                                        // Lunch Section
                                        MealSection(
                                            mealType = MealType.LUNCH,
                                            foodItems = foodItemsByMeal.getOrDefault(MealType.LUNCH, mutableStateListOf()),
                                            onAddItem = {
                                                val newList = foodItemsByMeal[MealType.LUNCH] ?: mutableStateListOf()
                                                newList.add(FoodItem())
                                                foodItemsByMeal[MealType.LUNCH] = newList
                                                coroutineScope.launch {
                                                    mealTrackerHelper.saveFoodItemsForMeal(openedDay, MealType.LUNCH, newList.toList())
                                                }
                                            },
                                            onUpdateItem = { updatedItem ->
                                                val list = foodItemsByMeal.getValue(MealType.LUNCH)
                                                val index = list.indexOfFirst { it.id == updatedItem.id }
                                                if (index != -1) {
                                                    list[index] = updatedItem
                                                    coroutineScope.launch {
                                                        mealTrackerHelper.saveFoodItemsForMeal(openedDay, MealType.LUNCH, list.toList())
                                                    }
                                                }
                                            },
                                            onDeleteItem = { itemToDelete ->
                                                val list = foodItemsByMeal.getValue(MealType.LUNCH)
                                                list.remove(itemToDelete)
                                                coroutineScope.launch {
                                                    mealTrackerHelper.saveFoodItemsForMeal(openedDay, MealType.LUNCH, list.toList())
                                                }
                                            },
                                            groceryDatabaseHelper = groceryDatabaseHelper // PASSING IT HERE!
                                        )
                                        // Snack 2 Section (After Lunch)
                                        MealSection(
                                            mealType = MealType.SNACK2,
                                            foodItems = foodItemsByMeal.getOrDefault(MealType.SNACK2, mutableStateListOf()),
                                            onAddItem = {
                                                val newList = foodItemsByMeal[MealType.SNACK2] ?: mutableStateListOf()
                                                newList.add(FoodItem())
                                                foodItemsByMeal[MealType.SNACK2] = newList
                                                coroutineScope.launch {
                                                    mealTrackerHelper.saveFoodItemsForMeal(openedDay, MealType.SNACK2, newList.toList())
                                                }
                                            },
                                            onUpdateItem = { updatedItem ->
                                                val list = foodItemsByMeal.getValue(MealType.SNACK2)
                                                val index = list.indexOfFirst { it.id == updatedItem.id }
                                                if (index != -1) {
                                                    list[index] = updatedItem
                                                    coroutineScope.launch {
                                                        mealTrackerHelper.saveFoodItemsForMeal(openedDay, MealType.SNACK2, list.toList())
                                                    }
                                                }
                                            },
                                            onDeleteItem = { itemToDelete ->
                                                val list = foodItemsByMeal.getValue(MealType.SNACK2)
                                                list.remove(itemToDelete)
                                                coroutineScope.launch {
                                                    mealTrackerHelper.saveFoodItemsForMeal(openedDay, MealType.SNACK2, list.toList())
                                                }
                                            },
                                            groceryDatabaseHelper = groceryDatabaseHelper // PASSING IT HERE!
                                        )
                                        Divider()

                                        // Dinner Section
                                        MealSection(
                                            mealType = MealType.DINNER,
                                            foodItems = foodItemsByMeal.getOrDefault(MealType.DINNER, mutableStateListOf()),
                                            onAddItem = {
                                                val newList = foodItemsByMeal[MealType.DINNER] ?: mutableStateListOf()
                                                newList.add(FoodItem())
                                                foodItemsByMeal[MealType.DINNER] = newList
                                                coroutineScope.launch {
                                                    mealTrackerHelper.saveFoodItemsForMeal(openedDay, MealType.DINNER, newList.toList())
                                                }
                                            },
                                            onUpdateItem = { updatedItem ->
                                                val list = foodItemsByMeal.getValue(MealType.DINNER)
                                                val index = list.indexOfFirst { it.id == updatedItem.id }
                                                if (index != -1) {
                                                    list[index] = updatedItem
                                                    coroutineScope.launch {
                                                        mealTrackerHelper.saveFoodItemsForMeal(openedDay, MealType.DINNER, list.toList())
                                                    }
                                                }
                                            },
                                            onDeleteItem = { itemToDelete ->
                                                val list = foodItemsByMeal.getValue(MealType.DINNER)
                                                list.remove(itemToDelete)
                                                coroutineScope.launch {
                                                    mealTrackerHelper.saveFoodItemsForMeal(openedDay, MealType.DINNER, list.toList())
                                                }
                                            },
                                            groceryDatabaseHelper = groceryDatabaseHelper // PASSING IT HERE!
                                        )
                                        // Snack 3 Section (After Dinner)
                                        MealSection(
                                            mealType = MealType.SNACK3,
                                            foodItems = foodItemsByMeal.getOrDefault(MealType.SNACK3, mutableStateListOf()),
                                            onAddItem = {
                                                val newList = foodItemsByMeal[MealType.SNACK3] ?: mutableStateListOf()
                                                newList.add(FoodItem())
                                                foodItemsByMeal[MealType.SNACK3] = newList
                                                coroutineScope.launch {
                                                    mealTrackerHelper.saveFoodItemsForMeal(openedDay, MealType.SNACK3, newList.toList())
                                                }
                                            },
                                            onUpdateItem = { updatedItem ->
                                                val list = foodItemsByMeal.getValue(MealType.SNACK3)
                                                val index = list.indexOfFirst { it.id == updatedItem.id }
                                                if (index != -1) {
                                                    list[index] = updatedItem
                                                    coroutineScope.launch {
                                                        mealTrackerHelper.saveFoodItemsForMeal(openedDay, MealType.SNACK3, list.toList())
                                                    }
                                                }
                                            },
                                            onDeleteItem = { itemToDelete ->
                                                val list = foodItemsByMeal.getValue(MealType.SNACK3)
                                                list.remove(itemToDelete)
                                                coroutineScope.launch {
                                                    mealTrackerHelper.saveFoodItemsForMeal(openedDay, MealType.SNACK3, list.toList())
                                                }
                                            },
                                            groceryDatabaseHelper = groceryDatabaseHelper // PASSING IT HERE!
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
// --- EXISTING UTILITY COMPONENTS (KEEP AS IS) ---

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

                                Log.d("DragAndDrop", "Moved item from $currentActualIndex to $targetIndex")
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
                                                showSuggestions = false // Hide suggestions after selection
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
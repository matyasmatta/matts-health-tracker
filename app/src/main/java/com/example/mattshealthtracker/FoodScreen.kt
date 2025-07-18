package com.example.mattshealthtracker

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel // Import for ViewModel
import kotlinx.coroutines.launch

@Composable
fun FoodScreen(openedDay: String) {
    var energyExpanded by remember { mutableStateOf(false) }
    var trendsExpanded by remember { mutableStateOf(false) }

    // Obtain the context
    val context = LocalContext.current
    // Obtain the ViewModel
    val healthConnectViewModel: HealthConnectViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HealthConnectViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return HealthConnectViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    })

    // Coroutine scope for launching suspend functions
    val coroutineScope = rememberCoroutineScope()

    // Activity Result Launcher for Health Connect permissions
    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissionsResult ->
            // Check if all required permissions were granted
            val allPermissionsGranted = healthConnectViewModel.permissions?.all { permissionsResult[it] == true }
            if (allPermissionsGranted == true) {
                Log.d("FoodScreen", "All Health Connect permissions granted.")
                healthConnectViewModel.permissionsGranted = true // Update ViewModel state
                coroutineScope.launch {
                    healthConnectViewModel.checkPermissionsAndFetchData() // Fetch data after permissions granted
                }
            } else {
                Log.w("FoodScreen", "Not all Health Connect permissions granted.")
                healthConnectViewModel.permissionsGranted = false // Update ViewModel state
                healthConnectViewModel.errorMessage = "Not all necessary Health Connect permissions were granted."
            }
        }
    )

    // LaunchedEffect to check permissions and fetch data on initial composition or when dependencies change
    LaunchedEffect(Unit) {
        healthConnectViewModel.checkPermissionsAndFetchData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Food Tracking for $openedDay",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Card 1: Energy use today (existing)
        AppUiElements.CollapsibleCard(
            titleContent = {
                Text(
                    text = "🔥  Energy Use Today",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            isExpandable = true,
            expanded = energyExpanded,
            onExpandedChange = { energyExpanded = it },
            hideDefaultWhenExpanded = true,
            defaultContent = {
                Text(
                    "Your energy use is high today",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            expandableContent = {
                Text(
                    "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            defaultContentModifier = Modifier.padding(bottom = 4.dp),
            expandableContentModifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Card 2: Dietary trends (existing)
        AppUiElements.CollapsibleCard(
            titleContent = {
                Text(
                    text = "📊  Dietary Trends",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            isExpandable = true,
            expanded = trendsExpanded,
            onExpandedChange = { trendsExpanded = it },
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

        Spacer(modifier = Modifier.height(8.dp))

        // Card 4: Food input (existing) - Moved down to make space for Health Connect
        AppUiElements.CollapsibleCard(
            titleContent = {
                Text(
                    text = "🍎  Food",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            isExpandable = false,
            hideDefaultWhenExpanded = true,
            defaultContent = {
                Text(
                    "This is where the food input/list UI will go.",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}
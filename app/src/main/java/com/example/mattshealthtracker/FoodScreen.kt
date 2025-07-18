package com.example.mattshealthtracker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth // Import fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign // Import TextAlign
import androidx.compose.ui.unit.dp
import com.example.mattshealthtracker.AppUiElements

@Composable
fun FoodScreen(openedDay: String) {
    var energyExpanded by remember { mutableStateOf(false) }
    var trendsExpanded by remember { mutableStateOf(false) }

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

        // Card 1: Energy use today
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
                    "High",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center // Apply text alignment here
                    ),
                    modifier = Modifier.fillMaxWidth() // Make Text fill width for centering
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

        // Card 2: Dietary trends
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
                    "Healthy surplus",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center // Apply text alignment here
                    ),
                    modifier = Modifier.fillMaxWidth() // Make Text fill width for centering
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

        // Card 3: Food input
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
                        textAlign = TextAlign.Center // Apply text alignment here
                    ),
                    modifier = Modifier.fillMaxWidth() // Make Text fill width for centering
                )
            }
        )
    }
}
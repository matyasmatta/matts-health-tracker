// In a new file, e.g., GroceryItem.kt or within your existing models
package com.example.mattshealthtracker

data class GroceryItem(
    val id: String, // Unique ID for the grocery item
    val name: String,
    val isHealthy: Boolean = false,
    val isLPRFriendly: Boolean = false,
    val averageCaloriesPer100g: Int? = null, // Optional, for more advanced features
    val commonUnits: String = "" // e.g., "g, ml, piece"
)
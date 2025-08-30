package com.example.mattshealthtracker // Make sure this matches your package name

// Data class representing a single miscellaneous tracker item
data class TrackerItem(
    val name: String,
    val value: Float,
    val isChecked: Boolean
)

// Data class holding the list of all miscellaneous tracker items for a date
data class MiscellaneousData(
    val items: List<TrackerItem>
)

// Function to provide the default list of miscellaneous trackers with their initial state
fun defaultMiscellaneousItems(): List<TrackerItem> {
    return listOf(
        TrackerItem("TMJ pain", 0f, false),
        TrackerItem("Neck clenching", 0f, false),
        TrackerItem("Ear discomfort", 0f, false),
        TrackerItem("Testicle pain", 0f, false),
        TrackerItem("Teeth pain", 0f, false),
        TrackerItem("Aura migraines", 0f, false),
        TrackerItem("Nausea", 0f, false),
        TrackerItem("Dizziness", 0f, false),
        TrackerItem("Acne", 0f, false),
        TrackerItem("Back pain", 0f, false),
        TrackerItem("Tendon pain", 0f, false),
        TrackerItem("Carpal tunnel", 0f, false),
        TrackerItem("Limb weakness", 0f, false),
        TrackerItem("Fatigue", 0f, false)
    )
}
package com.example.mattshealthtracker

// Data class representing a single miscellaneous tracker item
data class TrackerItem(
    val name: String,
    val value: Float,
    val isChecked: Boolean
)

// Data class holding the list of all miscellaneous tracker items for a date
data class MiscellaneousData(
    val items: List<TrackerItem> // This will now hold items based on user-defined symptoms + daily data
)

// The function defaultMiscellaneousItems() is no longer the primary source
// for the UI. AppGlobals.getTrackerItemsForDay() takes over that role conceptually.
// You might remove defaultMiscellaneousItems() or keep it as a reference for initial defaults
// if AppGlobals didn't handle that.
/*
fun defaultMiscellaneousItems(): List<TrackerItem> {
    // This list is now managed by AppGlobals.userDefinedSymptomNames
    // and combined with daily data.
    return listOf() // Or remove entirely
}
*/

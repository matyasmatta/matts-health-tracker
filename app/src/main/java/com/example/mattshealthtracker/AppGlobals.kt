package com.example.mattshealthtracker

import java.text.SimpleDateFormat
import java.util.*

object AppGlobals {
    val currentDay: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val openedDay: String = currentDay
}
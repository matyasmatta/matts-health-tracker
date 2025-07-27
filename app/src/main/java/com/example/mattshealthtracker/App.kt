package com.example.mattshealthtracker // Ensure this matches your package name

import android.app.Application

class MyHealthTrackerApplication : Application() {

    override fun onCreate() {
        super.onCreate() // Always call the superclass's onCreate first

        // Initialize your AppGlobals singleton here
        AppGlobals.initialize(applicationContext)

        // You can add other global, one-time initializations here if needed
        // For example, setting up a logging library, dependency injection, etc.
    }
}
package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class HayyliveApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                Log.d("HayyliveApp", "FirebaseApp initialized successfully")
            }
        } catch (e: Exception) {
            Log.e("HayyliveApp", "Failed to initialize FirebaseApp in onCreate", e)
        }
    }
}

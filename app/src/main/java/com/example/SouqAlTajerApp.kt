package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class SouqAlTajerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                Log.d("SouqAlTajerApp", "FirebaseApp initialized successfully")
            }
        } catch (e: Exception) {
            Log.e("SouqAlTajerApp", "Failed to initialize FirebaseApp in onCreate", e)
        }
    }
}

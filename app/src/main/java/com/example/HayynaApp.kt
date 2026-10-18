package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class HayynaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                Log.d("HayynaApp", "FirebaseApp initialized successfully")
            }
        } catch (e: Exception) {
            Log.e("HayynaApp", "Failed to initialize FirebaseApp in onCreate", e)
        }
        // حماية App Check (مطلوبة للذكاء الاصطناعي). نسخة التجربة تستخدم "رمز تجربة"
        // يظهر للأدمن في لوحة الإدارة، ويُسجَّل مرة وحدة في Firebase.
        try {
            com.google.firebase.appcheck.FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory.getInstance()
            )
        } catch (e: Exception) {
            Log.e("HayynaApp", "App Check init failed", e)
        }
    }
}

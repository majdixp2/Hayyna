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
        // حماية App Check (مطلوبة للذكاء الاصطناعي):
        // - نسخة التجربة (debug): "رمز تجربة" يظهر للأدمن في لوحة الإدارة ويُسجَّل في Firebase.
        // - نسخة المتجر (release): Play Integrity — تشتغل تلقائياً للي نزّل التطبيق من قوقل بلاي.
        try {
            val factory = if (BuildConfig.DEBUG) {
                com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory.getInstance()
            } else {
                com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory.getInstance()
            }
            com.google.firebase.appcheck.FirebaseAppCheck.getInstance().installAppCheckProviderFactory(factory)
        } catch (e: Exception) {
            Log.e("HayynaApp", "App Check init failed", e)
        }
    }
}

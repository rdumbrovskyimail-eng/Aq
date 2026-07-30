package com.aquarium.neon

import android.app.Application
import android.util.Log

class NeonApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("NeonAquarium", "Critical Exception in thread ${thread.name}", throwable)
        }
    }
}
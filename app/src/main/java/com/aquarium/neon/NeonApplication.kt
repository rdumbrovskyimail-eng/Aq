package com.aquarium.neon

import android.app.Application
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NeonApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = this
        AppLogger.init(this)
        AppLogger.log("=== NeonApplication onCreate completed ===")
        setupGlobalCrashHandler()
    }

    private fun setupGlobalCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val crashMsg = "CRITICAL CRASH in thread ${thread.name}: ${throwable.localizedMessage}"
            Log.e("NeonAquarium", crashMsg, throwable)
            AppLogger.log(crashMsg)

            try {
                val dir = cacheDir
                if (dir != null) {
                    val logFile = File(dir, AppLogger.LOG_FILE_NAME)
                    PrintWriter(FileOutputStream(logFile, true)).use { pw ->
                        pw.println("\n--- UNCAUGHT CRASH AT ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} ---")
                        throwable.printStackTrace(pw)
                    }
                }
            } catch (e: Exception) {
                Log.e("NeonAquarium", "Failed to write crash log to file", e)
            }
            
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        var appContext: Application? = null
            private set
    }
}

object AppLogger {
    const val LOG_FILE_NAME = "aquarium_diagnostic_log.txt"
    private val logMemoryBuffer = StringBuilder()
    private var cacheDirFile: File? = null

    fun init(app: Application) {
        try {
            cacheDirFile = app.cacheDir
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun log(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val formattedMsg = "[$timestamp] $message"
        Log.d("NeonAquarium", formattedMsg)
        logMemoryBuffer.append(formattedMsg).append("\n")

        try {
            val dir = cacheDirFile ?: NeonApplication.appContext?.cacheDir
            if (dir != null) {
                val file = File(dir, LOG_FILE_NAME)
                FileOutputStream(file, true).use { fos ->
                    fos.write((formattedMsg + "\n").toByteArray())
                }
            }
        } catch (e: Exception) {
            Log.e("NeonAquarium", "AppLogger log file error: ${e.message}")
        }
    }

    @Synchronized
    fun getFullLog(): String {
        return try {
            val dir = cacheDirFile ?: NeonApplication.appContext?.cacheDir
            val file = if (dir != null) File(dir, LOG_FILE_NAME) else null
            if (file != null && file.exists()) {
                file.readText()
            } else {
                logMemoryBuffer.toString()
            }
        } catch (e: Exception) {
            logMemoryBuffer.toString()
        }
    }
}
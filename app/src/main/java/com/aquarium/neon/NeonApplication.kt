package com.aquarium.neon

import android.app.Application
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NeonApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        AppLogger.log("=== NeonApplication onCreate ===")
        setupGlobalCrashHandler()
    }

    private fun setupGlobalCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLogger.log("CRITICAL CRASH in thread ${thread.name}: ${throwable.localizedMessage}")
            try {
                val logFile = File(cacheDir, AppLogger.LOG_FILE_NAME)
                PrintWriter(FileOutputStream(logFile, true)).use { pw ->
                    pw.println("\n--- UNCAUGHT CRASH AT ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} ---")
                    throwable.printStackTrace(pw)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        lateinit var instance: NeonApplication
            private set
    }
}

object AppLogger {
    const val LOG_FILE_NAME = "aquarium_diagnostic_log.txt"
    private val logMemoryBuffer = StringBuilder()

    @Synchronized
    fun log(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val formattedMsg = "[$timestamp] $message"
        println("NeonAquariumLog: $formattedMsg")
        logMemoryBuffer.append(formattedMsg).append("\n")

        try {
            val file = File(NeonApplication.instance.cacheDir, LOG_FILE_NAME)
            FileOutputStream(file, true).use { fos ->
                fos.write((formattedMsg + "\n").toByteArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun getFullLog(): String {
        val file = File(NeonApplication.instance.cacheDir, LOG_FILE_NAME)
        return if (file.exists()) {
            file.readText()
        } else {
            logMemoryBuffer.toString()
        }
    }
}
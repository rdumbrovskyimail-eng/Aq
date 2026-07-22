package com.aquarium.neon

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter

class MainActivity : AppCompatActivity() {

    private lateinit var aquariumView: AquariumView
    private val logFileName = "crash_log.txt"

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                saveLogToUserSelectedFolder(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupCrashHandler()
        setupImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        aquariumView = AquariumView(this)
        setContentView(aquariumView)

        Handler(Looper.getMainLooper()).postDelayed({
            promptUserForLogDirectory()
        }, 10000)
    }

    private fun setupCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            try {
                val cacheLogFile = File(cacheDir, logFileName)
                val pw = PrintWriter(FileOutputStream(cacheLogFile, true))
                pw.println("--- CRASH at ${java.util.Date()} ---")
                throwable.printStackTrace(pw)
                pw.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun promptUserForLogDirectory() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, logFileName)
        }
        createDocumentLauncher.launch(intent)
    }

    private fun saveLogToUserSelectedFolder(uri: Uri) {
        try {
            val cacheLogFile = File(cacheDir, logFileName)
            
            val logContent = if (cacheLogFile.exists()) {
                cacheLogFile.readText()
            } else {
                "Крашей не зафиксировано. Поток отрисовки работает стабильно!\n"
            }

            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(logContent.toByteArray())
            }
            Toast.makeText(this, "Лог сохранен!", Toast.LENGTH_LONG).show()
            
            if (cacheLogFile.exists()) cacheLogFile.delete()
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка сохранения лога", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }
    }

    override fun onResume() {
        super.onResume()
        aquariumView.startSimulation()
    }

    override fun onPause() {
        super.onPause()
        aquariumView.stopSimulation()
    }
}
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var aquariumView: AquariumView

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                saveLogToSelectedUri(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.log("MainActivity: onCreate started")
        
        setupImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        aquariumView = AquariumView(this)
        setContentView(aquariumView)
        AppLogger.log("MainActivity: setContentView attached AquariumView")

        // Запуск таймера на 5 секунд
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !isDestroyed) {
                AppLogger.log("MainActivity: 5-second timer triggered dialog")
                showSaveLogDialog()
            }
        }, 5000)
    }

    private fun showSaveLogDialog() {
        AlertDialog.Builder(this)
            .setTitle("Диагностика аквариума")
            .setMessage("Приложение отработало 5 секунд. Сохранить диагностический лог в файл?")
            .setPositiveButton("Сохранить лог") { _, _ ->
                promptUserForLogDirectory()
            }
            .setNegativeButton("Закрыть", null)
            .setCancelable(false)
            .show()
    }

    private fun promptUserForLogDirectory() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, AppLogger.LOG_FILE_NAME)
        }
        createDocumentLauncher.launch(intent)
    }

    private fun saveLogToSelectedUri(uri: Uri) {
        try {
            val logData = AppLogger.getFullLog()
            contentResolver.openOutputStream(uri)?.use { os ->
                os.write(logData.toByteArray())
            }
            Toast.makeText(this, "Лог сохранен!", Toast.LENGTH_LONG).show()
            AppLogger.log("MainActivity: Log successfully saved to URI: $uri")
        } catch (e: Exception) {
            AppLogger.log("MainActivity ERROR saving log: ${e.message}")
            Toast.makeText(this, "Ошибка сохранения лога: ${e.message}", Toast.LENGTH_SHORT).show()
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
        AppLogger.log("MainActivity: onResume")
        aquariumView.startSimulation()
    }

    override fun onPause() {
        super.onPause()
        AppLogger.log("MainActivity: onPause")
        aquariumView.stopSimulation()
    }
}
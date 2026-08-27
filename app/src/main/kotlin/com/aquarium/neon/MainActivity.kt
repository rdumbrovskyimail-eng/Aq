package com.aquarium.neon

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var glView: AquariumGLView
    private lateinit var root: FrameLayout
    private val audio = AquariumAudio()
    private var panel: SettingsPanel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Необработанное исключение в ${thread.name}", throwable)
            // Цепочку обработчиков рвать нельзя, иначе система не получит отчёт
            // о падении и процесс зависнет вместо корректного завершения
            previous?.uncaughtException(thread, throwable)
        }

        setupImmersive()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        root = FrameLayout(this)
        glView = AquariumGLView(this, audio)
        root.addView(
            glView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val dp = resources.displayMetrics.density
        val size = (46 * dp).roundToInt()
        val margin = (16 * dp).roundToInt()
        val gear = GearButton(this) { openPanel() }
        root.addView(gear, FrameLayout.LayoutParams(size, size).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = margin + (18 * dp).roundToInt()
            marginEnd = margin
        })

        setContentView(root)
    }

    private fun openPanel() {
        if (panel != null) return
        val p = SettingsPanel(this) { closePanel() }
        panel = p
        root.addView(
            p,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun closePanel() {
        panel?.let { root.removeView(it) }
        panel = null
        setupImmersive()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (panel != null) closePanel() else super.onBackPressed()
    }

    private fun setupImmersive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { c ->
                c.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                c.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
        glView.onResume()
        audio.start()          // поток микшера живёт только пока экран активен
    }

    override fun onPause() {
        super.onPause()
        audio.stop()
        glView.onPause()
    }
}
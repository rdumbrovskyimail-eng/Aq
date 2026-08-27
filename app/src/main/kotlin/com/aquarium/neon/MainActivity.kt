package com.aquarium.neon

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
            previous?.uncaughtException(thread, throwable)
        }

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

        // setContentView должен вызываться до настройки WindowInsets / Immersive mode
        setContentView(root)
        setupImmersive()
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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupImmersive()
        }
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
        audio.start()
    }

    override fun onPause() {
        super.onPause()
        audio.stop()
        glView.onPause()
    }
}
package com.aquarium.neon

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.MotionEvent
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import kotlin.math.abs

@SuppressLint("ViewConstructor")
class AquariumGLView(context: Context, audio: AquariumAudio) : GLSurfaceView(context) {

    private val aquariumRenderer = AquariumRenderer(audio)
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var lastActionNanos = 0L

    init {
        setEGLContextClientVersion(3)
        // MSAA обязателен: плавники, иглы крылатки и щупальца медуз — это полигоны
        // толщиной в пиксель, без сглаживания они рассыпаются в лесенку
        setEGLConfigChooser(MsaaConfigChooser())
        // Контекст переживает сворачивание: при возврате не пересобираем меши и шейдеры
        preserveEGLContextOnPause = true
        setRenderer(aquariumRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        isFocusable = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y
                downTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_UP -> {
                // Реагируем на отпускание без GestureDetector: onSingleTapConfirmed
                // ждёт таймаут двойного тапа (~300 мс), и удар по стеклу ощущается вялым
                val moved = abs(event.x - downX) + abs(event.y - downY)
                val held = System.currentTimeMillis() - downTime
                if (moved < 40f && held < 600) fire(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                // Протяжка сыплет корм или гонит волну, но не чаще 8 раз в секунду
                val now = System.nanoTime()
                if (now - lastActionNanos > 125_000_000L &&
                    abs(event.x - downX) + abs(event.y - downY) > 60f
                ) {
                    lastActionNanos = now
                    fire(event.x, event.y)
                }
            }
        }
        return true
    }

    /** Координаты снимаются в UI-потоке: MotionEvent к моменту queueEvent уже переиспользован. */
    private fun fire(x: Float, y: Float) {
        queueEvent { aquariumRenderer.onTap(x, y) }
        performClick()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

/**
 * Выбор EGL-конфигурации: пробуем 4x MSAA, затем 2x, затем без сглаживания.
 * Глубина 24 бита — при 16 битах дно и камни начинают z-fight'иться.
 */
private class MsaaConfigChooser : GLSurfaceView.EGLConfigChooser {

    override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
        for (samples in intArrayOf(4, 2, 0)) {
            val attrs = attribs(samples)
            val num = IntArray(1)
            if (!egl.eglChooseConfig(display, attrs, null, 0, num) || num[0] <= 0) continue
            val configs = arrayOfNulls<EGLConfig>(num[0])
            if (!egl.eglChooseConfig(display, attrs, configs, num[0], num)) continue
            pickBest(egl, display, configs, num[0])?.let {
                Log.i(TAG, "EGL: MSAA x$samples, глубина 24 бита")
                return it
            }
        }
        throw IllegalStateException("Нет подходящей EGL-конфигурации для OpenGL ES 3.0")
    }

    private fun attribs(samples: Int): IntArray {
        val base = intArrayOf(
            EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
            EGL10.EGL_RED_SIZE, 8,
            EGL10.EGL_GREEN_SIZE, 8,
            EGL10.EGL_BLUE_SIZE, 8,
            EGL10.EGL_ALPHA_SIZE, 0,
            EGL10.EGL_DEPTH_SIZE, 24,
            EGL10.EGL_STENCIL_SIZE, 0
        )
        return if (samples > 0)
            base + intArrayOf(EGL10.EGL_SAMPLE_BUFFERS, 1, EGL10.EGL_SAMPLES, samples, EGL10.EGL_NONE)
        else
            base + intArrayOf(EGL10.EGL_NONE)
    }

    private fun pickBest(egl: EGL10, display: EGLDisplay, configs: Array<EGLConfig?>, n: Int): EGLConfig? {
        var best: EGLConfig? = null
        var bestDepth = -1
        for (i in 0 until n) {
            val c = configs[i] ?: continue
            if (attr(egl, display, c, EGL10.EGL_RED_SIZE) != 8) continue
            if (attr(egl, display, c, EGL10.EGL_GREEN_SIZE) != 8) continue
            if (attr(egl, display, c, EGL10.EGL_BLUE_SIZE) != 8) continue
            val d = attr(egl, display, c, EGL10.EGL_DEPTH_SIZE)
            if (d < 16) continue
            if (d > bestDepth) { bestDepth = d; best = c }
        }
        return best
    }

    private fun attr(egl: EGL10, display: EGLDisplay, c: EGLConfig, what: Int): Int {
        val out = IntArray(1)
        return if (egl.eglGetConfigAttrib(display, c, what, out)) out[0] else 0
    }

    private companion object { const val EGL_OPENGL_ES3_BIT = 0x0040 }
}
package com.rdumbrovskyi.lifemap

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.concurrent.CopyOnWriteArrayList

class AquariumView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private var thread: Thread? = null
    private var running = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // Используем потокобезопасный список для предотвращения ConcurrentModificationException
    val fishes = CopyOnWriteArrayList<FishEntity>()
    val caves = listOf<Any>()
    var touchPoint: Vector2? = null

    init {
        holder.addCallback(this)
        AppLogger.log("AquariumView initialized")
    }

    fun startSimulation() {
        if (running) return
        running = true
        thread = Thread(this)
        thread?.start()
        AppLogger.log("Simulation started")
    }

    fun stopSimulation() {
        running = false
        try {
            thread?.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        AppLogger.log("Simulation stopped")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        initWorld(width, height)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopSimulation()
    }

    private fun initWorld(w: Int, h: Int) {
        fishes.clear()
        val defaultColor = Color.CYAN
        for (i in 0..50) {
            val fish = FishEntity(
                pos = Vector2((Math.random() * w).toFloat(), (Math.random() * h).toFloat()),
                velocity = Vector2(((Math.random() - 0.5) * 4).toFloat(), ((Math.random() - 0.5) * 4).toFloat()),
                config = FishConfig("Neon", defaultColor, 2f, 1f, BehaviorType.SCHOOLING),
                depth = (Math.random() * 0.5 + 0.5).toFloat()
            )
            fishes.add(fish)
        }
        AppLogger.log("World initialized with ${fishes.size} fishes")
    }

    override fun run() {
        while (running) {
            if (!holder.surface.isValid) continue
            
            updateWorld()
            drawWorld()

            try {
                Thread.sleep(16) // ~60 FPS
            } catch (e: InterruptedException) {
                break
            }
        }
    }

    private fun updateWorld() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        for (fish in fishes) {
            fish.update(w, h, fishes, caves, touchPoint)
        }
    }

    private fun drawWorld() {
        val canvas: Canvas = holder.lockCanvas() ?: return
        try {
            canvas.drawColor(Color.BLACK) // Фоновый цвет аквариума
            for (fish in fishes) {
                fish.draw(canvas, paint)
            }
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }
}

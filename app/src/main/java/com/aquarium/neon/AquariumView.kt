package com.aquarium.neon

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.*
import kotlin.random.Random

enum class LightingMode { DAY, SUNSET, NIGHT }

class AquariumView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private var renderThread: Thread? = null
    @Volatile private var isRunning = false

    private val fishes = CopyOnWriteArrayList<FishEntity>()
    private val caves = CopyOnWriteArrayList<CoralCave>()
    private val foods = CopyOnWriteArrayList<FoodParticle>()
    private val planktons = CopyOnWriteArrayList<PlanktonParticle>()
    private val plants = CopyOnWriteArrayList<PlantStem>()

    private val lightShafts = VolumetricLightShafts()
    private val caustics = SeabedCaustics()
    private val audioEngine = ProceduralAudioEngine()

    private var lightingMode = LightingMode.DAY
    private var isAudioEnabled = true

    private var tapPoint: Vector2D? = null
    private var tapShockwave = 0f
    private var screenW = 0f
    private var screenH = 0f
    private var isWorldInitialized = false

    var currentAccX = 0f
    var currentAccY = 0f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val uiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 36f; typeface = Typeface.DEFAULT_BOLD }
    private val bgPaint = Paint()

    private val bubbles = List(80) { Vector2D(0f, 0f) }

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {}

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        if (w > 0 && h > 0 && (!isWorldInitialized || screenW != w.toFloat() || screenH != h.toFloat())) {
            stopSimulation()
            screenW = w.toFloat()
            screenH = h.toFloat()
            initAquariumWorld(screenW, screenH)
            isWorldInitialized = true
            startSimulation()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopSimulation()
    }

    private fun initAquariumWorld(w: Float, h: Float) {
        fishes.clear()
        caves.clear()
        foods.clear()
        planktons.clear()
        plants.clear()

        caves.add(CoralCave(Vector2D(w * 0.18f, h - 85f), 160f, Color.parseColor("#00E5FF")))
        caves.add(CoralCave(Vector2D(w * 0.82f, h - 110f), 190f, Color.parseColor("#FF0077")))

        for (i in 0..12) {
            plants.add(PlantStem(w * (0.05f + i * 0.08f), h - 40f, color = Color.parseColor(if (i % 2 == 0) "#00E676" else "#00B0FF")))
        }

        for (b in bubbles) b.set(Random.nextFloat() * w, Random.nextFloat() * h)
        repeat(50) {
            planktons.add(PlanktonParticle(Vector2D(Random.nextFloat() * w, Random.nextFloat() * h)))
        }

        for (species in FishSpeciesRegistry.ALL_SPECIES) {
            val count = when (species.behavior) {
                BehaviorType.FLOCKING -> Random.nextInt(3, 5)
                BehaviorType.SOLITARY -> Random.nextInt(1, 2)
                BehaviorType.AGGRESSIVE -> 1
                else -> Random.nextInt(1, 3)
            }
            repeat(count) {
                fishes.add(FishEntity(species, Vector2D(Random.nextFloat() * (w - 200f) + 100f, Random.nextFloat() * (h - 200f) + 100f)))
            }
        }
        updateBackgroundGradient()
    }

    private fun updateBackgroundGradient() {
        val colors = when (lightingMode) {
            LightingMode.DAY -> intArrayOf(Color.parseColor("#01182A"), Color.parseColor("#003355"), Color.parseColor("#000B14"))
            LightingMode.SUNSET -> intArrayOf(Color.parseColor("#2A081A"), Color.parseColor("#4A1525"), Color.parseColor("#0A020C"))
            LightingMode.NIGHT -> intArrayOf(Color.parseColor("#020208"), Color.parseColor("#05051A"), Color.parseColor("#010103"))
        }
        bgPaint.shader = LinearGradient(0f, 0f, 0f, screenH, colors, null, Shader.TileMode.CLAMP)
    }

    fun startSimulation() {
        if (!isRunning && isWorldInitialized) {
            isRunning = true
            renderThread = Thread(this).apply { start() }
            if (isAudioEnabled) audioEngine.start()
        }
    }

    fun stopSimulation() {
        isRunning = false
        audioEngine.stop()
        try { renderThread?.join() } catch (e: Exception) { e.printStackTrace() }
    }

    override fun run() {
        while (isRunning) {
            val startTime = System.currentTimeMillis()
            val surf = holder.surface
            if (surf != null && surf.isValid && screenW > 0f && screenH > 0f) {
                var canvas: Canvas? = null
                try {
                    canvas = holder.lockCanvas()
                    if (canvas != null) {
                        updatePhysics()
                        drawWorld(canvas)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    if (canvas != null) {
                        try { holder.unlockCanvasAndPost(canvas) } catch (e: Exception) { e.printStackTrace() }
                    }
                }
            }

            val elapsed = System.currentTimeMillis() - startTime
            val sleepTime = (16 - elapsed).coerceAtLeast(4)
            try { Thread.sleep(sleepTime) } catch (e: Exception) {}
        }
    }

    private fun updatePhysics() {
        val timeSec = System.currentTimeMillis() * 0.001f

        if (tapPoint != null) {
            tapShockwave += 25f
            if (tapShockwave > 550f) { tapPoint = null; tapShockwave = 0f }
        }

        for (plant in plants) plant.update(timeSec, currentAccX, currentAccY, tapPoint, fishes)
        for (plankton in planktons) plankton.update(screenW, screenH, currentAccX, currentAccY)

        foods.removeAll { it.isEaten }
        for (food in foods) food.update(currentAccX, currentAccY, screenH - 50f)

        for (fish in fishes) fish.update(screenW, screenH, fishes, caves, foods, tapPoint, currentAccX, currentAccY)

        for (b in bubbles) {
            b.y -= 2.8f
            b.x += sin(b.y * 0.02f) * 1.4f + currentAccX * 0.8f
            if (b.y < -20f) { b.y = screenH + 20f; b.x = Random.nextFloat() * screenW }
        }
    }

    private fun drawWorld(canvas: Canvas) {
        val timeSec = System.currentTimeMillis() * 0.001f
        canvas.drawRect(0f, 0f, screenW, screenH, bgPaint)

        if (lightingMode != LightingMode.NIGHT) {
            lightShafts.draw(canvas, screenW, screenH, timeSec, if (lightingMode == LightingMode.DAY) 1.0f else 0.4f)
        }

        caustics.draw(canvas, screenW, screenH, timeSec, if (lightingMode == LightingMode.NIGHT) 15 else 40)

        for (plant in plants) plant.draw(canvas, fillPaint)
        for (plankton in planktons) plankton.draw(canvas, fillPaint)
        for (food in foods) food.draw(canvas, fillPaint)

        fillPaint.color = Color.argb(90, 0, 240, 255)
        for (b in bubbles) canvas.drawCircle(b.x, b.y, 3.5f, fillPaint)

        // Отрисовка с z-сортировкой рыб по глубине
        val sortedFishes = fishes.sortedBy { it.depth }
        for (fish in sortedFishes) fish.draw(canvas, lightingMode == LightingMode.NIGHT, screenH)

        tapPoint?.let { pt ->
            strokePaint.color = Color.parseColor("#00F0FF")
            strokePaint.strokeWidth = 9f
            strokePaint.alpha = ((1f - tapShockwave / 550f) * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(pt.x, pt.y, tapShockwave, strokePaint)
        }

        drawHUD(canvas)
    }

    private fun drawHUD(canvas: Canvas) {
        uiPaint.color = Color.WHITE
        uiPaint.style = Paint.Style.FILL

        canvas.drawText("🐠 Рыб: ${fishes.size}", 40f, 60f, uiPaint)

        // Кнопки интерфейса
        drawButton(canvas, "🍞 Корм", 40f, screenH - 80f, 160f, 60f, Color.parseColor("#FF9800"))
        drawButton(canvas, "🌗 Свет", 220f, screenH - 80f, 160f, 60f, Color.parseColor("#0288D1"))
        drawButton(canvas, "🧼 Очистка", 400f, screenH - 80f, 180f, 60f, Color.parseColor("#00C853"))
        drawButton(canvas, if (isAudioEnabled) "🔊 Звук" else "🔇 Звук", 600f, screenH - 80f, 160f, 60f, Color.parseColor("#7B1FA2"))
    }

    private fun drawButton(canvas: Canvas, text: String, x: Float, y: Float, w: Float, h: Float, bgColor: Int) {
        fillPaint.color = bgColor
        fillPaint.alpha = 200
        canvas.drawRoundRect(x, y, x + w, y + h, 16f, 16f, fillPaint)
        uiPaint.color = Color.WHITE
        canvas.drawText(text, x + 20f, y + 42f, uiPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val ex = event.x
            val ey = event.y

            if (ey > screenH - 100f) {
                if (ex in 40f..200f) {
                    repeat(6) {
                        foods.add(FoodParticle(Vector2D(Random.nextFloat() * (screenW - 200f) + 100f, 40f)))
                    }
                    return true
                } else if (ex in 220f..380f) {
                    lightingMode = when (lightingMode) {
                        LightingMode.DAY -> LightingMode.SUNSET
                        LightingMode.SUNSET -> LightingMode.NIGHT
                        LightingMode.NIGHT -> LightingMode.DAY
                    }
                    updateBackgroundGradient()
                    return true
                } else if (ex in 400f..580f) {
                    for (b in bubbles) b.y = screenH + Random.nextFloat() * 200f
                    return true
                } else if (ex in 600f..760f) {
                    isAudioEnabled = !isAudioEnabled
                    if (isAudioEnabled) audioEngine.start() else audioEngine.stop()
                    return true
                }
            }

            tapPoint = Vector2D(ex, ey)
            tapShockwave = 10f
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
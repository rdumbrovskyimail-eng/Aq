package com.aquarium.neon

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.*
import kotlin.random.Random

class AquariumView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private var renderThread: Thread? = null
    @Volatile private var isRunning = false

    private val fishes = mutableListOf<FishEntity>()
    private val caves = mutableListOf<CoralCave>()
    private val anemones = mutableListOf<AnemoneTentacle>()
    
    private var tapPoint: Vector2D? = null
    private var tapShockwave = 0f
    private var screenW = 0f
    private var screenH = 0f
    private var isWorldInitialized = false

    // Кисти для высокой производительности без вылетов
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bgPaint = Paint()

    private val bubbles = List(70) { Vector2D(0f, 0f) }

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {}

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        if (w > 0 && h > 0 && (!isWorldInitialized || screenW != w.toFloat() || screenH != h.toFloat())) {
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
        anemones.clear()

        caves.add(CoralCave(Vector2D(w * 0.18f, h - 85f), 160f, Color.parseColor("#00E5FF")))
        caves.add(CoralCave(Vector2D(w * 0.82f, h - 110f), 190f, Color.parseColor("#FF0077")))

        for (i in 0..4) {
            anemones.add(AnemoneTentacle(Vector2D(w * (0.25f + i * 0.13f), h - 45f)))
        }

        for (b in bubbles) {
            b.set(Random.nextFloat() * w, Random.nextFloat() * h)
        }

        for (species in FishSpeciesRegistry.ALL_SPECIES) {
            val count = when (species.behavior) {
                BehaviorType.FLOCKING -> Random.nextInt(3, 6)
                BehaviorType.SOLITARY -> Random.nextInt(1, 2)
                BehaviorType.AGGRESSIVE -> 1
                else -> Random.nextInt(1, 3)
            }
            repeat(count) {
                fishes.add(FishEntity(
                    species,
                    Vector2D(Random.nextFloat() * (w - 200f) + 100f, Random.nextFloat() * (h - 200f) + 100f)
                ))
            }
        }

        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(Color.parseColor("#010914"), Color.parseColor("#001326"), Color.parseColor("#00050B")),
            null, Shader.TileMode.CLAMP
        )
    }

    fun startSimulation() {
        if (!isRunning && isWorldInitialized) {
            isRunning = true
            renderThread = Thread(this).apply { start() }
        }
    }

    fun stopSimulation() {
        isRunning = false
        try { renderThread?.join() } catch (e: Exception) { e.printStackTrace() }
    }

    override fun run() {
        while (isRunning) {
            val startTime = System.currentTimeMillis()

            if (holder.surface.isValid && screenW > 0f && screenH > 0f) {
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
        if (tapPoint != null) {
            tapShockwave += 25f
            if (tapShockwave > 550f) { tapPoint = null; tapShockwave = 0f }
        }

        for (anemone in anemones) anemone.update(tapPoint)
        for (fish in fishes) fish.update(screenW, screenH, fishes, caves, anemones, tapPoint)

        for (b in bubbles) {
            b.y -= 2.6f
            b.x += sin(b.y * 0.02f) * 1.3f
            if (b.y < -20f) { b.y = screenH + 20f; b.x = Random.nextFloat() * screenW }
        }
    }

    private fun drawWorld(canvas: Canvas) {
        canvas.drawRect(0f, 0f, screenW, screenH, bgPaint)

        renderLightRays(canvas)
        renderCavesAndAnemones(canvas)

        fillPaint.color = Color.argb(95, 0, 240, 255)
        for (b in bubbles) canvas.drawCircle(b.x, b.y, 3.2f, fillPaint)

        for (fish in fishes) renderFishEntity(canvas, fish)

        tapPoint?.let { pt ->
            strokePaint.color = Color.parseColor("#00F0FF")
            strokePaint.strokeWidth = 9f
            strokePaint.alpha = ((1f - tapShockwave / 550f) * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(pt.x, pt.y, tapShockwave, strokePaint)
        }
    }

    private fun renderLightRays(canvas: Canvas) {
        val time = System.currentTimeMillis() * 0.001f
        strokePaint.strokeWidth = 45f
        strokePaint.color = Color.parseColor("#0A00E5FF")
        for (i in 0..7) {
            val x = (screenW / 7f) * i + sin(time + i) * 55f
            canvas.drawLine(x, 0f, x + 220f, screenH, strokePaint)
        }
    }

    private fun renderCavesAndAnemones(canvas: Canvas) {
        for (cave in caves) {
            fillPaint.color = Color.parseColor("#1C000000")
            canvas.drawCircle(cave.pos.x, cave.pos.y, cave.radius, fillPaint)
            strokePaint.color = cave.neonColor
            strokePaint.strokeWidth = 5f
            canvas.drawCircle(cave.pos.x, cave.pos.y, cave.radius * 0.85f, strokePaint)
        }

        for (anemone in anemones) {
            strokePaint.color = Color.parseColor("#FF0077")
            strokePaint.strokeWidth = 7f
            for (i in 0 until anemone.tentacleCount) {
                val angle = anemone.angles[i]
                val wave = sin(anemone.phases[i]) * 26f
                val endX = anemone.basePos.x + cos(angle) * anemone.length + wave
                val endY = anemone.basePos.y + sin(angle) * anemone.length
                canvas.drawLine(anemone.basePos.x, anemone.basePos.y, endX, endY, strokePaint)
            }
        }
    }

    private fun renderFishEntity(canvas: Canvas, fish: FishEntity) {
        val head = fish.spine[0]
        val angle = atan2(fish.velocity.y, fish.velocity.x) * (180f / PI.toFloat())

        canvas.save()
        canvas.translate(head.x, head.y)
        canvas.rotate(angle)

        val scale = fish.config.sizeScale * 14f

        // 100% Безопасный 2-х слойный неоновый ореол (Без BlurMaskFilter, без вылетов!)
        glowPaint.color = if (fish.isAttacking) Color.parseColor("#66FF0000") else (fish.config.neonGlowColor and 0x44FFFFFF)
        canvas.drawCircle(0f, 0f, scale * 2.2f, glowPaint)
        glowPaint.color = if (fish.isAttacking) Color.RED else fish.config.neonGlowColor
        canvas.drawCircle(0f, 0f, scale * 1.4f, glowPaint)

        fillPaint.color = fish.config.primaryColor
        strokePaint.color = fish.config.accentColor
        strokePaint.strokeWidth = 4f

        // Уникальная процедурная графика для всех 10 VisualForm
        when (fish.config.form) {
            VisualForm.TALL_DISC -> {
                canvas.drawOval(-scale * 1.2f, -scale * 1.7f, scale * 1.2f, scale * 1.7f, fillPaint)
                canvas.drawLine(0f, -scale * 1.7f, -scale * 0.8f, -scale * 2.8f, strokePaint)
                canvas.drawLine(0f, scale * 1.7f, -scale * 0.8f, scale * 2.8f, strokePaint)
            }
            VisualForm.EEL_SNAKE -> {
                val path = Path().apply {
                    moveTo(scale * 2.2f, 0f)
                    quadTo(0f, -scale * 0.4f, -scale * 3.2f, sin(fish.swimCycle) * 20f)
                    quadTo(0f, scale * 0.4f, scale * 2.2f, 0f)
                }
                canvas.drawPath(path, fillPaint)
            }
            VisualForm.JELLY_GLOW -> {
                canvas.drawArc(-scale * 1.5f, -scale * 1.5f, scale * 1.5f, scale * 1.5f, 180f, 180f, true, fillPaint)
                for (i in -2..2) {
                    val tx = i * scale * 0.5f
                    canvas.drawLine(tx, 0f, tx + sin(fish.swimCycle + i) * 10f, scale * 2.0f, strokePaint)
                }
            }
            VisualForm.STAR_CRAWLER -> {
                val path = Path()
                for (i in 0 until 5) {
                    val a1 = i * Math.PI * 2 / 5
                    val a2 = (i + 0.5) * Math.PI * 2 / 5
                    if (i == 0) path.moveTo(cos(a1).toFloat() * scale, sin(a1).toFloat() * scale)
                    else path.lineTo(cos(a1).toFloat() * scale, sin(a1).toFloat() * scale)
                    path.lineTo(cos(a2).toFloat() * (scale * 0.4f), sin(a2).toFloat() * (scale * 0.4f))
                }
                path.close()
                canvas.drawPath(path, fillPaint)
            }
            VisualForm.SEAHORSE -> {
                val path = Path().apply {
                    moveTo(scale * 0.5f, -scale * 1.2f)
                    quadTo(scale * 1.2f, -scale * 0.6f, scale * 0.4f, 0f)
                    quadTo(-scale * 0.8f, scale * 0.8f, -scale * 0.2f, scale * 1.8f)
                    quadTo(-scale * 0.8f, scale * 1.4f, 0f, 0f)
                }
                canvas.drawPath(path, fillPaint)
            }
            VisualForm.MANTA_RAY -> {
                val wingSwing = sin(fish.swimCycle) * scale * 0.4f
                val path = Path().apply {
                    moveTo(scale * 1.6f, 0f)
                    lineTo(-scale * 0.5f, -scale * 2.5f + wingSwing)
                    lineTo(-scale * 1.2f, 0f)
                    lineTo(-scale * 0.5f, scale * 2.5f - wingSwing)
                    close()
                }
                canvas.drawPath(path, fillPaint)
                canvas.drawLine(-scale * 1.2f, 0f, -scale * 3.2f, 0f, strokePaint)
            }
            VisualForm.LIONFISH_SPIKY -> {
                canvas.drawOval(-scale * 1.1f, -scale * 0.7f, scale * 1.1f, scale * 0.7f, fillPaint)
                for (i in 0 until 8) {
                    val ang = (i / 8f) * Math.PI * 2
                    val xEnd = cos(ang).toFloat() * scale * 2.4f
                    val yEnd = sin(ang).toFloat() * scale * 2.4f
                    canvas.drawLine(0f, 0f, xEnd, yEnd, strokePaint)
                }
            }
            VisualForm.PIRANHA_BITE -> {
                canvas.drawOval(-scale * 1.4f, -scale * 1.1f, scale * 1.4f, scale * 1.1f, fillPaint)
                if (fish.isAttacking) {
                    val mouthPath = Path().apply {
                        moveTo(scale * 1.4f, -scale * 0.3f)
                        lineTo(scale * 0.6f, 0f)
                        lineTo(scale * 1.4f, scale * 0.3f)
                    }
                    fillPaint.color = Color.BLACK
                    canvas.drawPath(mouthPath, fillPaint)
                    fillPaint.color = fish.config.primaryColor
                }
            }
            VisualForm.SHRIMP_MANTIS -> {
                canvas.drawRect(-scale * 1.5f, -scale * 0.4f, scale * 1.5f, scale * 0.4f, fillPaint)
                canvas.drawCircle(scale * 1.2f, -scale * 0.5f, scale * 0.3f, strokePaint)
                canvas.drawCircle(scale * 1.2f, scale * 0.5f, scale * 0.3f, strokePaint)
            }
            else -> { // SLENDER_NEON и другие
                val bodyPath = Path().apply {
                    moveTo(scale * 1.4f, 0f)
                    quadTo(0f, -scale * 0.6f, -scale * 1.5f, sin(fish.swimCycle) * 10f)
                    quadTo(0f, scale * 0.6f, scale * 1.4f, 0f)
                }
                canvas.drawPath(bodyPath, fillPaint)
                canvas.drawLine(scale * 0.8f, 0f, -scale * 1.2f, 0f, strokePaint)
            }
        }

        // Глаз со светящейся точкой
        fillPaint.color = if (fish.isAttacking) Color.RED else Color.WHITE
        canvas.drawCircle(scale * 0.7f, -scale * 0.25f, scale * 0.22f, fillPaint)
        fillPaint.color = Color.BLACK
        canvas.drawCircle(scale * 0.8f, -scale * 0.25f, scale * 0.10f, fillPaint)

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            tapPoint = Vector2D(event.x, event.y)
            tapShockwave = 10f
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
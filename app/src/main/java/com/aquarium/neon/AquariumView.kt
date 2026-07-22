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

    // Набор графических элементов и кистей
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bgPaint = Paint()

    private val bubbles = List(60) { Vector2D(0f, 0f) }

    init {
        holder.addCallback(this)
        isFocusable = true
        setLayerType(LAYER_TYPE_SOFTWARE, null)
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
    override fun surfaceDestroyed(holder: SurfaceHolder) { stopSimulation() }

    private fun initAquariumWorld(w: Float, h: Float) {
        fishes.clear()
        caves.clear()
        anemones.clear()

        caves.add(CoralCave(Vector2D(w * 0.2f, h - 80f), 150f, Color.parseColor("#00E5FF")))
        caves.add(CoralCave(Vector2D(w * 0.8f, h - 100f), 180f, Color.parseColor("#FF0077")))

        for (i in 0..3) {
            anemones.add(AnemoneTentacle(Vector2D(w * (0.3f + i * 0.15f), h - 40f)))
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
            if (!holder.surface.isValid || screenW <= 0f || screenH <= 0f) continue
            val canvas = holder.lockCanvas() ?: continue
            try {
                updatePhysics()
                drawWorld(canvas)
            } catch (e: Exception) { e.printStackTrace() } finally {
                holder.unlockCanvasAndPost(canvas)
            }
            try { Thread.sleep(16) } catch (e: Exception) {}
        }
    }

    private fun updatePhysics() {
        if (tapPoint != null) {
            tapShockwave += 25f
            if (tapShockwave > 500f) { tapPoint = null; tapShockwave = 0f }
        }

        for (anemone in anemones) anemone.update(tapPoint)
        for (fish in fishes) fish.update(screenW, screenH, fishes, caves, anemones, tapPoint)

        for (b in bubbles) {
            b.y -= 2.5f
            b.x += sin(b.y * 0.02f) * 1.2f
            if (b.y < -20f) { b.y = screenH + 20f; b.x = Random.nextFloat() * screenW }
        }
    }

    private fun drawWorld(canvas: Canvas) {
        canvas.drawRect(0f, 0f, screenW, screenH, bgPaint)
        renderLightRays(canvas)
        renderCavesAndAnemones(canvas)
        fillPaint.color = Color.argb(90, 0, 240, 255)
        for (b in bubbles) canvas.drawCircle(b.x, b.y, 3f, fillPaint)
        for (fish in fishes) renderFishEntity(canvas, fish)
        tapPoint?.let { pt ->
            strokePaint.color = Color.parseColor("#00F0FF")
            strokePaint.strokeWidth = 8f
            strokePaint.alpha = ((1f - tapShockwave / 500f) * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(pt.x, pt.y, tapShockwave, strokePaint)
        }
    }

    private fun renderLightRays(canvas: Canvas) {
        val time = System.currentTimeMillis() * 0.001f
        strokePaint.strokeWidth = 40f
        strokePaint.color = Color.parseColor("#0A00E5FF")
        for (i in 0..6) {
            val x = (screenW / 6f) * i + sin(time + i) * 50f
            canvas.drawLine(x, 0f, x + 200f, screenH, strokePaint)
        }
    }

    private fun renderCavesAndAnemones(canvas: Canvas) {
        for (cave in caves) {
            fillPaint.color = Color.parseColor("#1A000000")
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
                val wave = sin(anemone.phases[i]) * 25f
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
        fillPaint.color = if (fish.isAttacking) Color.RED else fish.config.neonGlowColor
        fillPaint.maskFilter = BlurMaskFilter(22f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(0f, 0f, scale * 1.5f, fillPaint)
        fillPaint.maskFilter = null
        fillPaint.color = fish.config.primaryColor
        when (fish.config.form) {
            VisualForm.TALL_DISC -> {
                canvas.drawOval(-scale * 1.2f, -scale * 1.6f, scale * 1.2f, scale * 1.6f, fillPaint)
            }
            VisualForm.EEL_SNAKE -> {
                val path = Path().apply {
                    moveTo(scale * 2.0f, 0f)
                    quadTo(0f, -scale * 0.4f, -scale * 2.8f, sin(fish.swimCycle) * 18f)
                    quadTo(0f, scale * 0.4f, scale * 2.0f, 0f)
                }
                canvas.drawPath(path, fillPaint)
            }
            VisualForm.JELLY_GLOW -> {
                canvas.drawArc(-scale * 1.5f, -scale * 1.5f, scale * 1.5f, scale * 1.5f, 180f, 180f, true, fillPaint)
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
            else -> {
                val bodyPath = Path().apply {
                    moveTo(scale * 1.3f, 0f)
                    quadTo(0f, -scale * 0.6f, -scale * 1.4f, sin(fish.swimCycle) * 10f)
                    quadTo(0f, scale * 0.6f, scale * 1.3f, 0f)
                }
                canvas.drawPath(bodyPath, fillPaint)
            }
        }
        strokePaint.color = fish.config.accentColor
        strokePaint.strokeWidth = 4f
        canvas.drawCircle(0f, 0f, scale * 0.35f, strokePaint)
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
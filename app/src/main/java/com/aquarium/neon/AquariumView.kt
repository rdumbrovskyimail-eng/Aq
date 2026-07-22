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
    private val anemones = mutableListOf<Anemone>()
    private var tapPoint: Vector2D? = null
    private var tapWave = 0f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bgPaint = Paint()

    private val bubbles = List(70) { Vector2D(Random.nextFloat() * 2500f, Random.nextFloat() * 2500f) }

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        initAquariumWorld(width.toFloat(), height.toFloat())
        startSimulation()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) { stopSimulation() }

    private fun initAquariumWorld(w: Float, h: Float) {
        fishes.clear()
        anemones.clear()

        for (i in 0..4) {
            anemones.add(Anemone(Vector2D(w * (0.15f + i * 0.18f), h - 60f)))
        }

        for (species in FishCatalog.SPECIES_LIST) {
            val count = when (species.behavior) {
                BehaviorType.FLOCKING -> Random.nextInt(4, 8)
                BehaviorType.SOLITARY -> Random.nextInt(1, 3)
                BehaviorType.AGGRESSIVE -> Random.nextInt(1, 2)
                else -> Random.nextInt(2, 4)
            }
            repeat(count) {
                fishes.add(FishEntity(
                    species,
                    Vector2D(Random.nextFloat() * (w - 300f) + 150f, Random.nextFloat() * (h - 300f) + 150f)
                ))
            }
        }

        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(Color.parseColor("#010A15"), Color.parseColor("#00152B"), Color.parseColor("#00050D")),
            null, Shader.TileMode.CLAMP
        )
    }

    fun startSimulation() {
        if (!isRunning) {
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
            if (!holder.surface.isValid) continue
            val canvas = holder.lockCanvas() ?: continue

            updatePhysics()
            renderWorld(canvas)

            holder.unlockCanvasAndPost(canvas)
            try { Thread.sleep(16) } catch (e: Exception) {}
        }
    }

    private fun updatePhysics() {
        val w = width.toFloat(); val h = height.toFloat()

        if (tapPoint != null) {
            tapWave += 28f
            if (tapWave > 500f) { tapPoint = null; tapWave = 0f }
        }

        for (anemone in anemones) anemone.update(tapPoint)
        for (fish in fishes) fish.update(w, h, fishes, anemones, tapPoint)

        for (b in bubbles) {
            b.y -= 2.5f
            b.x += sin(b.y * 0.015f) * 1.2f
            if (b.y < -20f) { b.y = h + 20f; b.x = Random.nextFloat() * w }
        }
    }

    private fun renderWorld(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        renderGodRays(canvas)

        renderAnemones(canvas)

        fillPaint.color = Color.argb(90, 0, 229, 255)
        for (b in bubbles) {
            canvas.drawCircle(b.x, b.y, 3f + sin(b.y * 0.05f) * 2f, fillPaint)
        }

        for (fish in fishes) {
            renderSkeletalFish(canvas, fish)
        }

        tapPoint?.let { pt ->
            strokePaint.color = Color.parseColor("#00F0FF")
            strokePaint.strokeWidth = 8f
            strokePaint.alpha = ((1f - tapWave / 500f) * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(pt.x, pt.y, tapWave, strokePaint)
        }
    }

    private fun renderGodRays(canvas: Canvas) {
        val time = System.currentTimeMillis() * 0.001f
        strokePaint.strokeWidth = 40f
        strokePaint.color = Color.parseColor("#0A00E5FF")
        for (i in 0..6) {
            val xStart = (width / 6f) * i + sin(time + i) * 50f
            canvas.drawLine(xStart, 0f, xStart + 200f, height.toFloat(), strokePaint)
        }
    }

    private fun renderAnemones(canvas: Canvas) {
        for (anemone in anemones) {
            fillPaint.color = Color.parseColor("#00E5FF")
            fillPaint.maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawCircle(anemone.basePos.x, anemone.basePos.y, 40f, fillPaint)
            fillPaint.maskFilter = null

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

    private fun renderSkeletalFish(canvas: Canvas, fish: FishEntity) {
        val head = fish.segments[0]
        val angle = atan2(fish.velocity.y, fish.velocity.x) * (180f / PI.toFloat())

        canvas.save()
        canvas.translate(head.x, head.y)
        canvas.rotate(angle)

        val scale = fish.config.sizeScale * 14f

        fillPaint.color = if (fish.isAttacking) Color.RED else fish.config.neonGlowColor
        fillPaint.maskFilter = BlurMaskFilter(25f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(0f, 0f, scale * 1.5f, fillPaint)
        fillPaint.maskFilter = null

        val bodyPath = Path()
        bodyPath.moveTo(scale * 1.2f, 0f)

        for (i in 1 until fish.segments.size) {
            val seg = fish.segments[i]
            val localX = -i * scale * 0.6f
            val localY = sin(fish.swimCycle + i * 0.6f) * (scale * 0.3f)
            bodyPath.lineTo(localX, localY - (scale * (1f - i.toFloat() / fish.segments.size)))
        }
        for (i in fish.segments.size - 1 downTo 1) {
            val localX = -i * scale * 0.6f
            val localY = sin(fish.swimCycle + i * 0.6f) * (scale * 0.3f)
            bodyPath.lineTo(localX, localY + (scale * (1f - i.toFloat() / fish.segments.size)))
        }
        bodyPath.close()

        fillPaint.color = fish.config.bodyColor
        canvas.drawPath(bodyPath, fillPaint)

        strokePaint.color = fish.config.accentColor
        strokePaint.strokeWidth = 4f
        canvas.drawPath(bodyPath, strokePaint)

        fillPaint.color = if (fish.isAttacking) Color.RED else Color.WHITE
        canvas.drawCircle(scale * 0.7f, -scale * 0.3f, scale * 0.25f, fillPaint)
        fillPaint.color = Color.BLACK
        canvas.drawCircle(scale * 0.8f, -scale * 0.3f, scale * 0.12f, fillPaint)

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            tapPoint = Vector2D(event.x, event.y)
            tapWave = 10f
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
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

    // Набор графических элементов и кистей
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bgPaint = Paint()

    private val bubbles = List(80) { Vector2D(Random.nextFloat() * 2500f, Random.nextFloat() * 2500f) }

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        initAquarium(width.toFloat(), height.toFloat())
        startSimulation()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) { stopSimulation() }

    private fun initAquarium(w: Float, h: Float) {
        fishes.clear()
        caves.clear()
        anemones.clear()

        // Пещеры
        caves.add(CoralCave(Vector2D(w * 0.2f, h - 90f), 170f, Color.parseColor("#00E5FF")))
        caves.add(CoralCave(Vector2D(w * 0.8f, h - 110f), 200f, Color.parseColor("#FF0077")))

        // Анемоны
        for (i in 0..3) {
            anemones.add(AnemoneTentacle(Vector2D(w * (0.35f + i * 0.15f), h - 50f)))
        }

        // Заполнение аквариума 35 видами
        for (species in FishSpeciesRegistry.ALL_SPECIES) {
            val count = when (species.behavior) {
                BehaviorType.FLOCKING -> Random.nextInt(4, 7)
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
            intArrayOf(Color.parseColor("#010914"), Color.parseColor("#001326"), Color.parseColor("#00050B")),
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

            updateScene()
            drawScene(canvas)

            holder.unlockCanvasAndPost(canvas)
            try { Thread.sleep(16) } catch (e: Exception) {}
        }
    }

    private fun updateScene() {
        val w = width.toFloat(); val h = height.toFloat()

        if (tapPoint != null) {
            tapShockwave += 30f
            if (tapShockwave > 550f) { tapPoint = null; tapShockwave = 0f }
        }

        for (anemone in anemones) anemone.update(tapPoint)
        for (fish in fishes) fish.update(w, h, fishes, caves, anemones, tapPoint)

        for (b in bubbles) {
            b.y -= 2.8f
            b.x += sin(b.y * 0.02f) * 1.5f
            if (b.y < -20f) { b.y = h + 20f; b.x = Random.nextFloat() * w }
        }
    }

    private fun drawScene(canvas: Canvas) {
        // 1. Фон глубины
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 2. Неоновые лучи света (God Rays)
        renderVolumetricRays(canvas)

        // 3. Пещеры и Анемоны
        renderCavesAndAnemones(canvas)

        // 4. Пузырьки
        fillPaint.color = Color.argb(100, 0, 240, 255)
        for (b in bubbles) canvas.drawCircle(b.x, b.y, 3.5f, fillPaint)

        // 5. Отрисовка 35 видов рыб
        for (fish in fishes) renderProceduralFish(canvas, fish)

        // 6. Ударная волна
        tapPoint?.let { pt ->
            strokePaint.color = Color.parseColor("#00F0FF")
            strokePaint.strokeWidth = 9f
            strokePaint.alpha = ((1f - tapShockwave / 550f) * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(pt.x, pt.y, tapShockwave, strokePaint)
        }
    }

    private fun renderVolumetricRays(canvas: Canvas) {
        val time = System.currentTimeMillis() * 0.001f
        strokePaint.strokeWidth = 50f
        strokePaint.color = Color.parseColor("#0C00E5FF")
        for (i in 0..7) {
            val x = (width / 7f) * i + sin(time + i) * 60f
            canvas.drawLine(x, 0f, x + 250f, height.toFloat(), strokePaint)
        }
    }

    private fun renderCavesAndAnemones(canvas: Canvas) {
        // Пещеры
        for (cave in caves) {
            fillPaint.color = Color.parseColor("#1A000000")
            canvas.drawCircle(cave.pos.x, cave.pos.y, cave.radius, fillPaint)
            strokePaint.color = cave.neonColor
            strokePaint.strokeWidth = 6f
            canvas.drawCircle(cave.pos.x, cave.pos.y, cave.radius * 0.85f, strokePaint)
        }

        // Анемоны
        for (anemone in anemones) {
            strokePaint.color = Color.parseColor("#FF0077")
            strokePaint.strokeWidth = 8f
            for (i in 0 until anemone.tentacleCount) {
                val angle = anemone.angles[i]
                val wave = sin(anemone.phases[i]) * 30f
                val endX = anemone.basePos.x + cos(angle) * anemone.length + wave
                val endY = anemone.basePos.y + sin(angle) * anemone.length
                canvas.drawLine(anemone.basePos.x, anemone.basePos.y, endX, endY, strokePaint)
            }
        }
    }

    private fun renderProceduralFish(canvas: Canvas, fish: FishEntity) {
        val head = fish.spine[0]
        val angle = atan2(fish.velocity.y, fish.velocity.x) * (180f / PI.toFloat())

        canvas.save()
        canvas.translate(head.x, head.y)
        canvas.rotate(angle)

        val scale = fish.config.sizeScale * 15f

        // 1. Неоновое свечение (Glow Filter)
        fillPaint.color = if (fish.isAttacking) Color.RED else fish.config.neonGlowColor
        fillPaint.maskFilter = BlurMaskFilter(28f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(0f, 0f, scale * 1.6f, fillPaint)
        fillPaint.maskFilter = null

        // 2. Построение формы тела по форме VisualForm
        fillPaint.color = fish.config.primaryColor
        when (fish.config.form) {
            VisualForm.TALL_DISC -> {
                canvas.drawOval(-scale * 1.3f, -scale * 1.7f, scale * 1.3f, scale * 1.7f, fillPaint)
            }
            VisualForm.EEL_SNAKE -> {
                val path = Path().apply {
                    moveTo(scale * 2.2f, 0f)
                    quadraticTo(0f, -scale * 0.4f, -scale * 3.0f, sin(fish.swimCycle) * 20f)
                    quadraticTo(0f, scale * 0.4f, scale * 2.2f, 0f)
                }
                canvas.drawPath(path, fillPaint)
            }
            VisualForm.JELLY_GLOW -> {
                canvas.drawArc(-scale * 1.6f, -scale * 1.6f, scale * 1.6f, scale * 1.6f, 180f, 180f, true, fillPaint)
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
                    moveTo(scale * 1.4f, 0f)
                    quadraticTo(0f, -scale * 0.7f, -scale * 1.5f, sin(fish.swimCycle) * 12f)
                    quadraticTo(0f, scale * 0.7f, scale * 1.4f, 0f)
                }
                canvas.drawPath(bodyPath, fillPaint)
            }
        }

        // 3. Акцентный узор
        strokePaint.color = fish.config.accentColor
        strokePaint.strokeWidth = 5f
        canvas.drawCircle(0f, 0f, scale * 0.4f, strokePaint)

        // 4. Глаз (Изменяет цвет при АТАКЕ)
        fillPaint.color = if (fish.isAttacking) Color.RED else Color.WHITE
        canvas.drawCircle(scale * 0.8f, -scale * 0.3f, scale * 0.26f, fillPaint)
        fillPaint.color = Color.BLACK
        canvas.drawCircle(scale * 0.9f, -scale * 0.3f, scale * 0.12f, fillPaint)

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            tapPoint = Vector2D(event.x, event.y)
            tapShockwave = 15f
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
package com.aquarium.neon

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.*
import kotlin.random.Random

data class Bubble(
    var x: Float, var y: Float,
    val radius: Float,
    val speedY: Float,
    val wobble: Float
)

data class ImpactParticle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var life: Float,
    val color: Int
)

data class CoralPlant(
    val x: Float, val y: Float,
    val color: Int,
    val branches: List<Triple<Float, Float, Float>>
)

class AquariumView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private var renderThread: Thread? = null
    @Volatile private var isRunning = false

    private val worldLock = Any()
    @Volatile private var tapPoint: Vector2D? = null
    private var tapShockwave = 0f

    private val fishes    = mutableListOf<FishEntity>()
    private val caves     = mutableListOf<CoralCave>()
    private val anemones  = mutableListOf<AnemoneTentacle>()
    private val bubbles   = mutableListOf<Bubble>()
    private val particles = mutableListOf<ImpactParticle>()
    private val corals    = mutableListOf<CoralPlant>()

    private var screenW = 0f
    private var screenH = 0f
    @Volatile private var isWorldInitialized = false
    private var frameTime = 0L

    private val fillPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bgPaint     = Paint()
    private val reusePath   = Path()

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {}

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        if (w > 0 && h > 0 && (!isWorldInitialized || screenW != w.toFloat() || screenH != h.toFloat())) {
            screenW = w.toFloat()
            screenH = h.toFloat()
            initWorld(screenW, screenH)
            isWorldInitialized = true
            startSimulation()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopSimulation()
    }

    private fun initWorld(w: Float, h: Float) {
        synchronized(worldLock) {
            fishes.clear(); caves.clear(); anemones.clear()
            bubbles.clear(); particles.clear(); corals.clear()
            tapPoint = null
            tapShockwave = 0f

            caves += CoralCave(Vector2D(w * 0.15f, h - 90f),  170f, Color.parseColor("#00E5FF"))
            caves += CoralCave(Vector2D(w * 0.85f, h - 105f), 195f, Color.parseColor("#FF0077"))

            for (i in 0..5) {
                anemones += AnemoneTentacle(
                    Vector2D(w * (0.18f + i * 0.13f), h - 50f),
                    tentacleCount = 14 + Random.nextInt(4),
                    length = 110f + Random.nextFloat() * 40f
                )
            }

            repeat(80) {
                val r = 1.5f + Random.nextFloat() * 4f
                bubbles += Bubble(
                    x = Random.nextFloat() * w,
                    y = Random.nextFloat() * h,
                    radius = r,
                    speedY = 1.4f + (5.5f - r) * 0.3f,
                    wobble = 0.008f + Random.nextFloat() * 0.018f
                )
            }

            val coralColors = listOf(
                Color.parseColor("#FF0077"), Color.parseColor("#FF5500"),
                Color.parseColor("#00E5FF"), Color.parseColor("#B388FF"),
                Color.parseColor("#00C853"), Color.parseColor("#FFAB40")
            )
            repeat(18) {
                val cx = w * 0.04f + Random.nextFloat() * w * 0.92f
                val cy = h - 30f
                val col = coralColors[Random.nextInt(coralColors.size)]
                val branches = (0 until 3 + Random.nextInt(5)).map {
                    val ang = -PI.toFloat() / 2f + (Random.nextFloat() - 0.5f) * 1.4f
                    val len = 40f + Random.nextFloat() * 80f
                    Triple(cx + cos(ang) * len, cy + sin(ang) * len, 2f + Random.nextFloat() * 4f)
                }
                corals += CoralPlant(cx, cy, col, branches)
            }

            for (species in FishSpeciesRegistry.ALL_SPECIES) {
                val count = when (species.behavior) {
                    BehaviorType.FLOCKING       -> Random.nextInt(4, 7)
                    BehaviorType.SOLITARY       -> 1
                    BehaviorType.AGGRESSIVE     -> 1
                    BehaviorType.PREDATOR       -> 1
                    BehaviorType.BOTTOM_DWELLER -> Random.nextInt(1, 3)
                    BehaviorType.HIDER          -> Random.nextInt(1, 3)
                }
                repeat(count) {
                    fishes += FishEntity(
                        species,
                        Vector2D(100f + Random.nextFloat() * (w - 200f), 100f + Random.nextFloat() * (h - 200f))
                    )
                }
            }

            bgPaint.shader = LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(
                    0xFF002B47.toInt(),
                    0xFF00182E.toInt(),
                    0xFF000A1A.toInt()
                ),
                null, Shader.TileMode.CLAMP
            )
        }
    }

    fun startSimulation() {
        if (!isRunning && isWorldInitialized) {
            isRunning = true
            renderThread = Thread(this, "AquariumRender").apply { start() }
        }
    }

    fun stopSimulation() {
        isRunning = false
        try { renderThread?.join(1500) } catch (e: Exception) { e.printStackTrace() }
    }

    override fun run() {
        while (isRunning) {
            val t0 = System.currentTimeMillis()
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
                        try { holder.unlockCanvasAndPost(canvas) }
                        catch (e: Exception) { e.printStackTrace() }
                    }
                }
            }
            val sleep = (16L - (System.currentTimeMillis() - t0)).coerceAtLeast(4L)
            try { Thread.sleep(sleep) } catch (_: Exception) {}
        }
    }

    private fun updatePhysics() {
        frameTime++
        var currentTap: Vector2D?

        synchronized(worldLock) {
            currentTap = tapPoint
            if (currentTap != null) {
                tapShockwave += 22f
                if (tapShockwave > 560f) { tapPoint = null; tapShockwave = 0f; currentTap = null }
            }

            val iter = particles.iterator()
            while (iter.hasNext()) {
                val p = iter.next()
                p.x += p.vx; p.y += p.vy; p.vy += 0.15f; p.life -= 0.035f
                if (p.life <= 0f) iter.remove()
            }

            for (a in anemones) a.update(currentTap)
            for (f in fishes) f.update(screenW, screenH, fishes, caves, currentTap)

            for (b in bubbles) {
                b.y -= b.speedY
                b.x += sin(b.y * b.wobble) * 1.2f
                if (b.y < -10f) { b.y = screenH + 10f; b.x = Random.nextFloat() * screenW }
            }
        }
    }

    private fun drawWorld(canvas: Canvas) {
        canvas.drawRect(0f, 0f, screenW, screenH, bgPaint)
        renderCaustics(canvas)
        renderLightRays(canvas)
        renderSandFloor(canvas)

        synchronized(worldLock) {
            renderCorals(canvas)
            renderCavesAndAnemones(canvas)
            renderBubbles(canvas)

            val sortedFishes = fishes.sortedBy { it.depth }
            for (f in sortedFishes) renderFishEntity(canvas, f)

            renderParticles(canvas)
            renderTapShockwave(canvas)
        }
    }

    private fun renderCaustics(canvas: Canvas) {
        val t = frameTime * 0.008f
        for (i in 0..11) {
            val cx = screenW * (0.08f + (i % 4) * 0.27f) + sin(t + i * 1.3f) * 35f
            val cy = screenH * 0.15f + (i / 4) * screenH * 0.22f + cos(t * 0.7f + i) * 20f
            val r  = 60f + sin(t * 1.1f + i * 0.8f) * 20f
            fillPaint.color = Color.argb(14, 0, 220, 255)
            canvas.drawCircle(cx, cy, r, fillPaint)
        }
    }

    private fun renderLightRays(canvas: Canvas) {
        val t = frameTime * 0.007f
        strokePaint.strokeWidth = 48f
        strokePaint.color = Color.argb(9, 0, 229, 255)
        for (i in 0..8) {
            val x = (screenW / 8f) * i + sin(t + i * 0.7f) * 50f
            canvas.drawLine(x, 0f, x + 180f, screenH, strokePaint)
        }
    }

    private fun renderSandFloor(canvas: Canvas) {
        val floorTop = screenH - 55f
        fillPaint.shader = LinearGradient(
            0f, floorTop, 0f, screenH,
            intArrayOf(Color.parseColor("#CC0A0702"), Color.parseColor("#FF0C0904")),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, floorTop, screenW, screenH, fillPaint)
        fillPaint.shader = null
    }

    private fun renderCorals(canvas: Canvas) {
        for (c in corals) {
            val wave = sin(frameTime * 0.04f + c.x * 0.01f) * 5f
            for ((ex, ey, thick) in c.branches) {
                strokePaint.color = c.color; strokePaint.strokeWidth = thick; strokePaint.alpha = 220
                canvas.drawLine(c.x, c.y, ex + wave, ey, strokePaint)
            }
            strokePaint.alpha = 255
        }
    }

    private fun renderCavesAndAnemones(canvas: Canvas) {
        for (cave in caves) {
            fillPaint.color = Color.argb(40, 0, 0, 0)
            canvas.drawCircle(cave.pos.x, cave.pos.y, cave.radius, fillPaint)
            strokePaint.color = cave.neonColor; strokePaint.strokeWidth = 4f; strokePaint.alpha = 180
            canvas.drawCircle(cave.pos.x, cave.pos.y, cave.radius * 0.82f, strokePaint)
            strokePaint.alpha = 255
        }
        for (anemone in anemones) {
            strokePaint.color = Color.parseColor("#FF0077"); strokePaint.strokeWidth = 6f
            for (i in 0 until anemone.tentacleCount) {
                val angle = anemone.angles[i]
                val wave  = sin(anemone.phases[i]) * 28f
                val endX  = anemone.basePos.x + cos(angle) * anemone.length + wave
                val endY  = anemone.basePos.y + sin(angle) * anemone.length
                canvas.drawLine(anemone.basePos.x, anemone.basePos.y, endX, endY, strokePaint)
            }
        }
    }

    private fun renderBubbles(canvas: Canvas) {
        for (b in bubbles) {
            fillPaint.color = Color.argb(80, 0, 240, 255)
            canvas.drawCircle(b.x, b.y, b.radius, fillPaint)
        }
    }

    private fun renderParticles(canvas: Canvas) {
        for (p in particles) {
            val a = (p.life * 255f).toInt().coerceIn(0, 255)
            fillPaint.color = (p.color and 0x00FFFFFF) or (a shl 24)
            canvas.drawCircle(p.x, p.y, p.life * 5f + 2f, fillPaint)
        }
    }

    private fun renderTapShockwave(canvas: Canvas) {
        val tap = tapPoint ?: return
        val progress = tapShockwave / 560f
        strokePaint.color = Color.parseColor("#00F0FF"); strokePaint.strokeWidth = 8f
        strokePaint.alpha = ((1f - progress) * 220).toInt().coerceIn(0, 255)
        canvas.drawCircle(tap.x, tap.y, tapShockwave, strokePaint)
        strokePaint.alpha = 255
    }

    private fun renderFishEntity(canvas: Canvas, fish: FishEntity) {
        val head  = fish.spine[0]
        val angle = atan2(fish.velocity.y, fish.velocity.x) * (180f / PI.toFloat())
        val scale = fish.config.sizeScale * 14f
        val depthAlpha = (130 + fish.depth * 125).toInt()
        val depthScale = 0.7f + fish.depth * 0.3f

        canvas.save()
        canvas.translate(head.x, head.y)
        canvas.rotate(angle)
        canvas.scale(depthScale, depthScale)

        val gc = fish.config.neonGlowColor
        glowPaint.color = Color.argb(if (fish.isAttacking) 90 else 40, Color.red(gc), Color.green(gc), Color.blue(gc))
        canvas.drawCircle(0f, 0f, scale * 2.2f, glowPaint)

        fillPaint.color = (fish.config.primaryColor and 0x00FFFFFF) or (depthAlpha shl 24)
        strokePaint.color = fish.config.accentColor; strokePaint.strokeWidth = 3.5f

        when (fish.config.form) {
            VisualForm.TALL_DISC -> {
                canvas.drawOval(-scale * 1.2f, -scale * 1.7f, scale * 1.2f, scale * 1.7f, fillPaint)
                canvas.drawLine(0f, -scale * 1.7f, -scale * 0.9f, -scale * 2.9f, strokePaint)
                canvas.drawLine(0f,  scale * 1.7f, -scale * 0.9f,  scale * 2.9f, strokePaint)
            }
            VisualForm.MANTA_RAY -> {
                val ws = sin(fish.swimCycle * 0.5f) * scale * 0.5f
                reusePath.reset()
                reusePath.moveTo(scale * 1.7f, 0f)
                reusePath.cubicTo(scale * 0.5f, -scale, -scale * 0.3f, -scale * 2f, -scale * 0.5f, -scale * 2.6f + ws)
                reusePath.lineTo(-scale * 1.3f, 0f)
                reusePath.cubicTo(-scale * 0.3f, scale * 2f, scale * 0.5f, scale, scale * 1.7f, 0f)
                reusePath.close()
                canvas.drawPath(reusePath, fillPaint)
            }
            else -> {
                reusePath.reset()
                reusePath.moveTo(scale * 1.4f, 0f)
                reusePath.quadTo(0f, -scale * 0.6f, -scale * 1.5f, sin(fish.swimCycle) * 12f)
                reusePath.quadTo(0f,  scale * 0.6f,  scale * 1.4f, 0f)
                canvas.drawPath(reusePath, fillPaint)
            }
        }

        fillPaint.color = if (fish.isAttacking) Color.RED else Color.WHITE
        canvas.drawCircle(scale * 0.72f, -scale * 0.28f, scale * 0.23f, fillPaint)
        fillPaint.color = Color.BLACK
        canvas.drawCircle(scale * 0.82f, -scale * 0.28f, scale * 0.11f, fillPaint)

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                synchronized(worldLock) {
                    tapPoint = Vector2D(event.x, event.y)
                    tapShockwave = 8f
                    spawnImpactParticlesLocked(event.x, event.y)
                }
                performClick()
            }
        }
        return true
    }

    private fun spawnImpactParticlesLocked(x: Float, y: Float) {
        val colors = listOf(Color.parseColor("#00F0FF"), Color.parseColor("#FF0077"), Color.WHITE)
        repeat(10) {
            val ang = Random.nextFloat() * PI.toFloat() * 2f
            val spd = 3f + Random.nextFloat() * 5f
            particles += ImpactParticle(
                x, y,
                cos(ang) * spd, sin(ang) * spd - 2f,
                0.8f + Random.nextFloat() * 0.2f,
                colors[Random.nextInt(colors.size)]
            )
        }
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
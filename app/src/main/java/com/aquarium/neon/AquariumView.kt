package com.aquarium.neon

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.*
import kotlin.random.Random

class AquariumView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var tapPoint: Vector2D? = null
    private var tapShockwave = 0f

    private val fishes    = CopyOnWriteArrayList<FishEntity>()
    private val caves     = CopyOnWriteArrayList<CoralCave>()
    private val anemones  = CopyOnWriteArrayList<AnemoneTentacle>()
    private val bubbles   = mutableListOf<Bubble>()
    private val particles = mutableListOf<ImpactParticle>()
    private val corals    = mutableListOf<CoralPlant>()

    private var screenW = 0f
    private var screenH = 0f
    @Volatile private var isWorldInitialized = false
    @Volatile private var isSimulating = false
    private var frameTime = 0L

    private val fillPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        style = Paint.Style.FILL 
        color = Color.argb(40, 0, 0, 0)
    }
    private val bgPaint   = Paint()
    private val reusePath = Path()

    init {
        isFocusable = true
        isClickable = true
        AppLogger.log("AquariumView initialized")
    }

    fun startSimulation() {
        if (!isSimulating) {
            isSimulating = true
            AppLogger.log("AquariumView: Simulation started")
            postInvalidateOnAnimation()
        }
    }

    fun stopSimulation() {
        isSimulating = false
        AppLogger.log("AquariumView: Simulation stopped")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        AppLogger.log("AquariumView onSizeChanged: w=$w, h=$h")
        if (w > 0 && h > 0) {
            screenW = w.toFloat()
            screenH = h.toFloat()
            initWorld(screenW, screenH)
            isWorldInitialized = true
            startSimulation()
        }
    }

    private fun initWorld(w: Float, h: Float) {
        AppLogger.log("AquariumView: initWorld start (${w}x${h})")
        try {
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
                    BehaviorType.FLOCKING       -> Random.nextInt(2, 4)
                    BehaviorType.SOLITARY       -> 1
                    BehaviorType.AGGRESSIVE     -> 1
                    BehaviorType.PREDATOR       -> 1
                    BehaviorType.BOTTOM_DWELLER -> Random.nextInt(1, 2)
                    BehaviorType.HIDER          -> Random.nextInt(1, 2)
                }
                repeat(count) {
                    val spawnW = (w - 200f).coerceAtLeast(100f)
                    val spawnH = (h - 200f).coerceAtLeast(100f)
                    fishes += FishEntity(
                        species,
                        Vector2D(100f + Random.nextFloat() * (spawnW - 50f), 100f + Random.nextFloat() * (spawnH - 50f))
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
            AppLogger.log("AquariumView: initWorld success. Total fishes: ${fishes.size}")
        } catch (e: Exception) {
            AppLogger.log("AquariumView initWorld ERROR: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isWorldInitialized || screenW <= 0f || screenH <= 0f) {
            if (isSimulating) postInvalidateOnAnimation()
            return
        }

        try {
            if (isSimulating) {
                updatePhysics()
            }
            drawWorld(canvas)
        } catch (e: Exception) {
            AppLogger.log("AquariumView onDraw ERROR: ${e.message}")
        } finally {
            if (isSimulating) {
                postInvalidateOnAnimation()
            }
        }
    }

    private fun updatePhysics() {
        frameTime++
        var currentTap = tapPoint
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

    private fun drawWorld(canvas: Canvas) {
        canvas.drawRect(0f, 0f, screenW, screenH, bgPaint)
        renderCaustics(canvas)
        renderLightRays(canvas)
        renderSandFloor(canvas)

        renderCorals(canvas)
        renderCavesAndAnemones(canvas)
        renderBubbles(canvas)

        val sortedFishes = fishes.sortedBy { it.depth }
        for (f in sortedFishes) renderFishEntity(canvas, f)

        renderParticles(canvas)
        renderTapShockwave(canvas)
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
        val time = System.currentTimeMillis() * 0.0005f
        strokePaint.strokeWidth = 60f
        
        for (i in 0..8) {
            val wave = sin(time + i) * 30f + cos(time * 0.7f - i) * 20f + sin(time * 1.3f) * 10f
            val x = (screenW / 8f) * i + wave
            
            strokePaint.color = Color.argb((15 + sin(time + i * 2) * 5).toInt(), 0, 220, 255)
            canvas.drawLine(x, -50f, x + 150f + wave * 2, screenH + 50f, strokePaint)
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
        val depthScale = 0.7f + fish.depth * 0.3f

        canvas.save()
        val shadowOffsetX = 30f * depthScale
        val shadowOffsetY = 60f * depthScale
        canvas.translate(head.x + shadowOffsetX, head.y + shadowOffsetY)
        canvas.rotate(angle)
        canvas.scale(depthScale * 0.9f, depthScale * 0.9f)
        canvas.drawOval(-scale * 1.5f, -scale * 0.6f, scale * 1.5f, scale * 0.6f, shadowPaint)
        canvas.restore()

        canvas.save()
        canvas.translate(head.x, head.y)
        canvas.rotate(angle)
        canvas.scale(depthScale, depthScale)

        val gc = fish.config.neonGlowColor
        glowPaint.color = Color.argb(if (fish.isAttacking) 90 else 40, Color.red(gc), Color.green(gc), Color.blue(gc))
        canvas.drawCircle(0f, 0f, scale * 2.2f, glowPaint)

        val fogColor = Color.parseColor("#001326")
        fillPaint.color = blendColors(fogColor, fish.config.primaryColor, fish.depth)
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
                tapPoint = Vector2D(event.x, event.y)
                tapShockwave = 8f
                spawnImpactParticles(event.x, event.y)
                invalidate()
                performClick()
            }
        }
        return true
    }

    private fun spawnImpactParticles(x: Float, y: Float) {
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

    private fun blendColors(color1: Int, color2: Int, ratio: Float): Int {
        val rRatio = ratio.coerceIn(0f, 1f)
        val inverseRatio = 1f - rRatio
        val r = (Color.red(color1) * inverseRatio + Color.red(color2) * rRatio).toInt().coerceIn(0, 255)
        val g = (Color.green(color1) * inverseRatio + Color.green(color2) * rRatio).toInt().coerceIn(0, 255)
        val b = (Color.blue(color1) * inverseRatio + Color.blue(color2) * rRatio).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}
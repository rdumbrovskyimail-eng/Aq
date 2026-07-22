package com.aquarium.neon

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.TextureView
import kotlin.math.*
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────
// 1. Векторная математика 2D
// ─────────────────────────────────────────────────────────────
data class Vector2D(var x: Float = 0f, var y: Float = 0f) {
    fun add(v: Vector2D): Vector2D { x += v.x; y += v.y; return this }
    fun sub(v: Vector2D): Vector2D { x -= v.x; y -= v.y; return this }
    fun mult(n: Float): Vector2D { x *= n; y *= n; return this }
    fun div(n: Float): Vector2D { if (n != 0f) { x /= n; y /= n }; return this }
    fun mag(): Float = sqrt(x * x + y * y)
    fun magSq(): Float = x * x + y * y
    fun normalize(): Vector2D { val m = mag(); if (m > 0.0001f) div(m); return this }
    fun limit(max: Float): Vector2D { if (magSq() > max * max) { normalize(); mult(max) }; return this }
    fun dist(v: Vector2D): Float = sqrt((x - v.x).pow(2) + (y - v.y).pow(2))
    fun set(nx: Float, ny: Float) { x = nx; y = ny }
}

// ─────────────────────────────────────────────────────────────
// 2. Энумы поведений и форм
// ─────────────────────────────────────────────────────────────
enum class BehaviorType { FLOCKING, SOLITARY, BOTTOM_DWELLER, HIDER, AGGRESSIVE, PREDATOR }
enum class VisualForm {
    SLENDER_NEON, TALL_DISC, EEL_SNAKE, JELLY_GLOW, STAR_CRAWLER,
    SEAHORSE, MANTA_RAY, LIONFISH_SPIKY, PIRANHA_BITE, SHRIMP_MANTIS
}

// ─────────────────────────────────────────────────────────────
// 3. Конфигурация видов рыб
// ─────────────────────────────────────────────────────────────
data class SpeciesConfig(
    val id: Int,
    val name: String,
    val primaryColor: Int,
    val neonGlowColor: Int,
    val accentColor: Int,
    val form: VisualForm,
    val maxSpeed: Float,
    val behavior: BehaviorType,
    val sizeScale: Float,
    val segmentCount: Int = 8,
    val finFrequency: Float = 0.15f
)

object FishSpeciesRegistry {
    val ALL_SPECIES = listOf(
        SpeciesConfig(1,  "Neon Tetra Electric",       Color.parseColor("#00E5FF"), Color.parseColor("#00F0FF"), Color.RED,     VisualForm.SLENDER_NEON,  5.5f, BehaviorType.FLOCKING,       0.9f),
        SpeciesConfig(2,  "Cardinal Flare Tetra",      Color.parseColor("#FF0055"), Color.parseColor("#FF0077"), Color.CYAN,    VisualForm.SLENDER_NEON,  5.2f, BehaviorType.FLOCKING,       0.95f),
        SpeciesConfig(3,  "Lime Cyber Glowfish",       Color.parseColor("#39FF14"), Color.parseColor("#70FF40"), Color.YELLOW,  VisualForm.SLENDER_NEON,  5.8f, BehaviorType.FLOCKING,       1.0f),
        SpeciesConfig(4,  "Cosmic Cyan Danio",         Color.parseColor("#0044FF"), Color.parseColor("#33AACC"), Color.WHITE,   VisualForm.SLENDER_NEON,  6.2f, BehaviorType.FLOCKING,       0.85f),
        SpeciesConfig(5,  "Sunburst Imperial Angel",   Color.parseColor("#FFAA00"), Color.parseColor("#FFEE00"), Color.RED,     VisualForm.TALL_DISC,     2.6f, BehaviorType.SOLITARY,       2.4f),
        SpeciesConfig(6,  "Midnight Cyber Angel",      Color.parseColor("#151515"), Color.parseColor("#00FFFF"), Color.MAGENTA, VisualForm.TALL_DISC,     2.3f, BehaviorType.SOLITARY,       2.6f),
        SpeciesConfig(7,  "Nemo Anemone Clown",        Color.parseColor("#FF5500"), Color.parseColor("#FF8800"), Color.WHITE,   VisualForm.SLENDER_NEON,  3.4f, BehaviorType.HIDER,         1.3f),
        SpeciesConfig(8,  "Royal Indigo Tang",         Color.parseColor("#0D47A1"), Color.parseColor("#29B6F6"), Color.YELLOW,  VisualForm.SLENDER_NEON,  4.2f, BehaviorType.SOLITARY,       1.7f),
        SpeciesConfig(9,  "Turquoise Discus Prime",    Color.parseColor("#00E676"), Color.parseColor("#B9F6CA"), Color.MAGENTA, VisualForm.TALL_DISC,     2.0f, BehaviorType.SOLITARY,       2.9f),
        SpeciesConfig(10, "Crimson Killer Piranha",    Color.parseColor("#D50000"), Color.parseColor("#FF1744"), Color.YELLOW,  VisualForm.PIRANHA_BITE,  7.5f, BehaviorType.AGGRESSIVE,    2.1f),
        SpeciesConfig(11, "Golden Dragon Betta",       Color.parseColor("#FFD700"), Color.parseColor("#FFFF80"), Color.RED,     VisualForm.TALL_DISC,     3.0f, BehaviorType.AGGRESSIVE,    2.2f),
        SpeciesConfig(12, "Spiky Volitan Lionfish",    Color.parseColor("#FF3D00"), Color.parseColor("#FFAB91"), Color.WHITE,   VisualForm.LIONFISH_SPIKY,2.2f, BehaviorType.AGGRESSIVE,    3.2f),
        SpeciesConfig(13, "Mandarin Psychedelic",      Color.parseColor("#AA00FF"), Color.parseColor("#EA80FC"), Color.GREEN,   VisualForm.SLENDER_NEON,  2.5f, BehaviorType.HIDER,         1.2f),
        SpeciesConfig(14, "Electric Ribbon Eel",       Color.parseColor("#0288D1"), Color.parseColor("#80D8FF"), Color.YELLOW,  VisualForm.EEL_SNAKE,     4.0f, BehaviorType.HIDER,         3.5f, 14),
        SpeciesConfig(15, "Pink Biolum Jelly",         Color.parseColor("#E91E63"), Color.parseColor("#FF80AB"), Color.CYAN,    VisualForm.JELLY_GLOW,    1.3f, BehaviorType.SOLITARY,       2.3f),
        SpeciesConfig(16, "Abyssal Viper Dragon",      Color.parseColor("#311B92"), Color.parseColor("#B388FF"), Color.CYAN,    VisualForm.EEL_SNAKE,     5.8f, BehaviorType.PREDATOR,       2.8f, 12),
        SpeciesConfig(17, "Crown Pygmy Seahorse",      Color.parseColor("#FF4081"), Color.parseColor("#FF80AB"), Color.WHITE,   VisualForm.SEAHORSE,      1.4f, BehaviorType.HIDER,         1.2f),
        SpeciesConfig(18, "Ruby Starfish Crawler",     Color.parseColor("#FF1744"), Color.parseColor("#FF5252"), Color.YELLOW,  VisualForm.STAR_CRAWLER,  0.4f, BehaviorType.BOTTOM_DWELLER, 1.9f),
        SpeciesConfig(19, "Linckia Cyan Star",         Color.parseColor("#2979FF"), Color.parseColor("#82B1FF"), Color.WHITE,   VisualForm.STAR_CRAWLER,  0.35f,BehaviorType.BOTTOM_DWELLER, 2.0f),
        SpeciesConfig(20, "Flame Dwarf Angel",         Color.parseColor("#FF3D00"), Color.parseColor("#FF9100"), Color.BLACK,   VisualForm.TALL_DISC,     3.6f, BehaviorType.SOLITARY,       1.6f),
        SpeciesConfig(21, "Powder Blue Surgeon",       Color.parseColor("#00B0FF"), Color.parseColor("#E0F7FA"), Color.BLACK,   VisualForm.SLENDER_NEON,  4.5f, BehaviorType.FLOCKING,       1.7f),
        SpeciesConfig(22, "Fire Goby Dart",            Color.parseColor("#FF1744"), Color.parseColor("#FF9100"), Color.WHITE,   VisualForm.SLENDER_NEON,  6.5f, BehaviorType.HIDER,         1.3f),
        SpeciesConfig(23, "Neon Manta Ray",            Color.parseColor("#1A237E"), Color.parseColor("#00E5FF"), Color.WHITE,   VisualForm.MANTA_RAY,     2.8f, BehaviorType.SOLITARY,       4.2f),
        SpeciesConfig(24, "Transparent Glass Catfish", Color.parseColor("#88EEFFFF"), Color.parseColor("#00E5FF"), Color.MAGENTA, VisualForm.SLENDER_NEON, 4.0f, BehaviorType.FLOCKING,      1.6f),
        SpeciesConfig(25, "Royal Gramma Bicolor",      Color.parseColor("#651FFF"), Color.parseColor("#B388FF"), Color.YELLOW,  VisualForm.SLENDER_NEON,  3.4f, BehaviorType.HIDER,         1.4f),
        SpeciesConfig(26, "Emperor Circle Angel",      Color.parseColor("#3D5AFE"), Color.parseColor("#8C9EFF"), Color.YELLOW,  VisualForm.TALL_DISC,     2.5f, BehaviorType.SOLITARY,       2.8f),
        SpeciesConfig(27, "Beaked Butterfly Fish",     Color.parseColor("#FF9100"), Color.parseColor("#FFE0B2"), Color.WHITE,   VisualForm.TALL_DISC,     2.9f, BehaviorType.SOLITARY,       2.2f),
        SpeciesConfig(28, "Leafy Seadragon",           Color.parseColor("#64DD17"), Color.parseColor("#CCFF90"), Color.YELLOW,  VisualForm.SEAHORSE,      1.3f, BehaviorType.HIDER,         2.3f),
        SpeciesConfig(29, "Black Diamond Piranha",     Color.parseColor("#121212"), Color.parseColor("#FF0000"), Color.RED,     VisualForm.PIRANHA_BITE,  8.0f, BehaviorType.AGGRESSIVE,    2.3f),
        SpeciesConfig(30, "Neon Pink Zebra Danio",     Color.parseColor("#FF007F"), Color.parseColor("#FF80BF"), Color.WHITE,   VisualForm.SLENDER_NEON,  6.0f, BehaviorType.FLOCKING,       1.0f),
        SpeciesConfig(31, "Peacock Mantis Strike",     Color.parseColor("#00C853"), Color.parseColor("#B2FF59"), Color.RED,     VisualForm.SHRIMP_MANTIS, 4.8f, BehaviorType.AGGRESSIVE,    1.7f),
        SpeciesConfig(32, "Electric Cyan Cichlid",     Color.parseColor("#00E5FF"), Color.parseColor("#80D8FF"), Color.BLUE,    VisualForm.SLENDER_NEON,  4.8f, BehaviorType.AGGRESSIVE,    1.9f),
        SpeciesConfig(33, "Comb Rainbow Jelly",        Color.parseColor("#18FFFF"), Color.parseColor("#FF4081"), Color.GREEN,   VisualForm.JELLY_GLOW,    1.0f, BehaviorType.SOLITARY,       2.6f),
        SpeciesConfig(34, "Harlequin Glowing Shrimp",  Color.parseColor("#FFCCEE"), Color.parseColor("#FF80AB"), Color.CYAN,   VisualForm.SHRIMP_MANTIS, 1.8f, BehaviorType.BOTTOM_DWELLER, 1.2f),
        SpeciesConfig(35, "Neon Yellow Watchman",      Color.parseColor("#FFD600"), Color.parseColor("#FFFF80"), Color.BLUE,    VisualForm.SLENDER_NEON,  2.4f, BehaviorType.HIDER,         1.4f)
    )
}

// ─────────────────────────────────────────────────────────────
// 4. Компоненты аквариума
// ─────────────────────────────────────────────────────────────
data class CoralCave(val pos: Vector2D, val radius: Float, val neonColor: Int)

class AnemoneTentacle(
    val basePos: Vector2D,
    val tentacleCount: Int = 16,
    val length: Float = 130f
) {
    val angles = FloatArray(tentacleCount) { (it.toFloat() / tentacleCount) * PI.toFloat() - PI.toFloat() / 2 }
    val phases = FloatArray(tentacleCount) { Random.nextFloat() * 20f }

    fun update(tapPoint: Vector2D?) {
        val nearTap = tapPoint != null && tapPoint.dist(basePos) < 320f
        for (i in 0 until tentacleCount) {
            phases[i] += if (nearTap) 0.35f else 0.05f
        }
    }
}

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

// ─────────────────────────────────────────────────────────────
// 5. Рыба (FishEntity)
// ─────────────────────────────────────────────────────────────
class FishEntity(
    val config: SpeciesConfig,
    var position: Vector2D,
    var velocity: Vector2D = Vector2D(
        (Random.nextFloat() * 2 - 1) * config.maxSpeed * 0.5f,
        (Random.nextFloat() * 2 - 1) * config.maxSpeed * 0.5f
    )
) {
    var acceleration = Vector2D()
    val spine = Array(config.segmentCount) { Vector2D() }
    var swimCycle = Random.nextFloat() * 100f
    var isAttacking = false
    var attackCooldown = 0
    val depth = Random.nextFloat()

    init {
        val segLen = 15f * config.sizeScale
        val angle = if (velocity.mag() > 0.01f) atan2(velocity.y, velocity.x) else 0f
        val cosA = cos(angle)
        val sinA = sin(angle)
        for (i in 0 until spine.size) {
            spine[i] = Vector2D(
                position.x - cosA * segLen * i,
                position.y - sinA * segLen * i
            )
        }
    }

    fun update(
        w: Float, h: Float,
        fishes: List<FishEntity>,
        caves: List<CoralCave>,
        tapPoint: Vector2D?
    ) {
        swimCycle += config.finFrequency

        if (tapPoint != null) {
            val distToTap = position.dist(tapPoint)
            if (distToTap < 550f) {
                if (config.behavior == BehaviorType.AGGRESSIVE || config.behavior == BehaviorType.PREDATOR) {
                    val f = Vector2D(tapPoint.x - position.x, tapPoint.y - position.y)
                        .normalize().mult(config.maxSpeed * 3.0f)
                    acceleration.add(f)
                    isAttacking = true
                    attackCooldown = 50
                } else {
                    val scale = (config.maxSpeed * 3.8f) / (distToTap / 100f).coerceAtLeast(0.3f)
                    val f = Vector2D(position.x - tapPoint.x, position.y - tapPoint.y)
                        .normalize().mult(scale)
                    acceleration.add(f)
                }
            }
        }

        if (attackCooldown > 0) attackCooldown-- else isAttacking = false

        if (config.behavior == BehaviorType.FLOCKING && !isAttacking) {
            applyBoidsFlocking(fishes)
        }

        if (config.behavior == BehaviorType.HIDER) {
            caves.minByOrNull { it.pos.dist(position) }?.let { cave ->
                if (cave.pos.dist(position) < 350f) {
                    acceleration.add(
                        Vector2D(cave.pos.x - position.x, cave.pos.y - position.y)
                            .normalize().mult(config.maxSpeed * 0.7f)
                    )
                }
            }
        }

        acceleration.add(Vector2D((Random.nextFloat() - 0.5f) * 0.5f, (Random.nextFloat() - 0.5f) * 0.5f))
        velocity.add(acceleration)
        velocity.limit(if (isAttacking) config.maxSpeed * 3.0f else config.maxSpeed)
        position.add(velocity)
        acceleration.mult(0f)

        val margin = 80f
        if (position.x < margin)     velocity.x += 1.2f
        if (position.x > w - margin) velocity.x -= 1.2f
        if (position.y < margin)     velocity.y += 1.2f
        if (position.y > h - margin && config.behavior != BehaviorType.BOTTOM_DWELLER)
            velocity.y -= 1.2f

        if (config.behavior == BehaviorType.BOTTOM_DWELLER) {
            position.y = position.y * 0.85f + (h - 80f) * 0.15f
            velocity.y *= 0.6f
        }

        updateSpineIK()
    }

    private fun updateSpineIK() {
        spine[0].set(position.x, position.y)
        val segLen = 15f * config.sizeScale
        for (i in 1 until spine.size) {
            val prev = spine[i - 1]
            val curr = spine[i]
            val dx = curr.x - prev.x
            val dy = curr.y - prev.y
            val dist = sqrt(dx * dx + dy * dy)
            if (dist > 0.001f) {
                curr.x = prev.x + (dx / dist) * segLen
                curr.y = prev.y + (dy / dist) * segLen
            } else {
                curr.x = prev.x - segLen
                curr.y = prev.y
            }
        }
    }

    private fun applyBoidsFlocking(fishes: List<FishEntity>) {
        var sep = Vector2D(); var align = Vector2D(); var center = Vector2D(); var count = 0
        for (other in fishes) {
            if (other.config.id == config.id && other !== this) {
                val d = position.dist(other.position)
                if (d in 0.1f..170f) {
                    sep.add(
                        Vector2D(position.x - other.position.x, position.y - other.position.y)
                            .normalize().div(d.coerceAtLeast(1f))
                    )
                    align.add(other.velocity)
                    center.add(other.position)
                    count++
                }
            }
        }
        if (count > 0) {
            val cf = count.toFloat()
            sep.div(cf).normalize().mult(config.maxSpeed * 1.6f)
            align.div(cf).normalize().mult(config.maxSpeed * 0.9f)
            center.div(cf).sub(position).normalize().mult(config.maxSpeed * 0.5f)
            acceleration.add(sep).add(align).add(center)
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 6. TextureView аквариума (100% стабильность на S23 Ultra)
// ─────────────────────────────────────────────────────────────
class AquariumView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener, Runnable {

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
        surfaceTextureListener = this
        isFocusable = true
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (width > 0 && height > 0) {
            screenW = width.toFloat()
            screenH = height.toFloat()
            initWorld(screenW, screenH)
            isWorldInitialized = true
            startSimulation()
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        if (width > 0 && height > 0) {
            screenW = width.toFloat()
            screenH = height.toFloat()
            initWorld(screenW, screenH)
        }
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        stopSimulation()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

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
                    BehaviorType.FLOCKING       -> Random.nextInt(3, 6)
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
            if (isAvailable && screenW > 0f && screenH > 0f) {
                var canvas: Canvas? = null
                try {
                    canvas = lockCanvas()
                    if (canvas != null) {
                        updatePhysics()
                        drawWorld(canvas)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    if (canvas != null) {
                        try { unlockCanvasAndPost(canvas) }
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
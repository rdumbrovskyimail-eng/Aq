package com.aquarium.neon

import android.graphics.Color
import kotlin.math.*
import kotlin.random.Random

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

enum class BehaviorType { FLOCKING, SOLITARY, BOTTOM_DWELLER, HIDER, AGGRESSIVE, PREDATOR }

enum class VisualForm {
    SLENDER_NEON, TALL_DISC, EEL_SNAKE, JELLY_GLOW, STAR_CRAWLER,
    SEAHORSE, MANTA_RAY, LIONFISH_SPIKY, PIRANHA_BITE, SHRIMP_MANTIS
}

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
        for (i in spine.indices) {
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
        val waterFriction = 0.98f
        val currentMaxSpeed = if (isAttacking) config.maxSpeed * 3.0f else config.maxSpeed
        velocity.add(acceleration)
        velocity.mult(waterFriction)
        if (velocity.mag() > currentMaxSpeed) {
            velocity.normalize().mult(currentMaxSpeed)
        }
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
        val sep = Vector2D(); val align = Vector2D(); val center = Vector2D(); var count = 0
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
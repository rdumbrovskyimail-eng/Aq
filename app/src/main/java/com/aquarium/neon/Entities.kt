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
    fun copy(): Vector2D = Vector2D(x, y)
    fun set(nx: Float, ny: Float) { x = nx; y = ny }
}

enum class BehaviorType { FLOCKING, SOLITARY, BOTTOM_DWELLER, HIDER, AGGRESSIVE, PREDATOR }
enum class VisualForm { SLENDER_NEON, TALL_DISC, EEL_SNAKE, JELLY_GLOW, STAR_CRAWLER, SEAHORSE, MANTA_RAY, LIONFISH_SPIKY, PIRANHA_BITE, SHRIMP_MANTIS }

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
        SpeciesConfig(4,  "Sunburst Imperial Angel",   Color.parseColor("#FFAA00"), Color.parseColor("#FFEE00"), Color.RED,     VisualForm.TALL_DISC,     2.6f, BehaviorType.SOLITARY,       2.4f),
        SpeciesConfig(5,  "Crimson Killer Piranha",    Color.parseColor("#D50000"), Color.parseColor("#FF1744"), Color.YELLOW,  VisualForm.PIRANHA_BITE,  7.5f, BehaviorType.AGGRESSIVE,    2.1f),
        SpeciesConfig(6,  "Neon Manta Ray",            Color.parseColor("#1A237E"), Color.parseColor("#00E5FF"), Color.WHITE,   VisualForm.MANTA_RAY,     2.8f, BehaviorType.SOLITARY,       4.2f),
        SpeciesConfig(7,  "Transparent Glass Catfish", Color.parseColor("#88EEFFFF"), Color.parseColor("#00E5FF"), Color.MAGENTA, VisualForm.SLENDER_NEON, 4.0f, BehaviorType.FLOCKING,      1.6f)
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
                val fleeForce = Vector2D(position.x - tapPoint.x, position.y - tapPoint.y)
                    .normalize().mult(config.maxSpeed * 3.0f)
                acceleration.add(fleeForce)
            }
        }

        acceleration.add(Vector2D((Random.nextFloat() - 0.5f) * 0.5f, (Random.nextFloat() - 0.5f) * 0.5f))
        velocity.add(acceleration)
        velocity.limit(config.maxSpeed)
        position.add(velocity)
        acceleration.mult(0f)

        val margin = 80f
        if (position.x < margin)     velocity.x += 1.2f
        if (position.x > w - margin) velocity.x -= 1.2f
        if (position.y < margin)     velocity.y += 1.2f
        if (position.y > h - margin) velocity.y -= 1.2f

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
}
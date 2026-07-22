package com.aquarium.neon

import android.graphics.Color
import kotlin.math.*
import kotlin.random.Random

data class Vector2D(var x: Float = 0f, var y: Float = 0f) {
    fun add(v: Vector2D) { x += v.x; y += v.y }
    fun sub(v: Vector2D) { x -= v.x; y -= v.y }
    fun mult(n: Float) { x *= n; y *= n }
    fun div(n: Float) { if (n != 0f) { x /= n; y /= n } }
    fun mag(): Float = sqrt(x * x + y * y)
    fun normalize() { val m = mag(); if (m != 0f) div(m) }
    fun limit(max: Float) { if (mag() > max) { normalize(); mult(max) } }
    fun dist(v: Vector2D): Float = sqrt((x - v.x).pow(2) + (y - v.y).pow(2))
    fun copy(): Vector2D = Vector2D(x, y)
}

enum class BehaviorType {
    FLOCKING, SOLITARY, BOTTOM_DWELLER, HIDER, AGGRESSIVE, PREDATOR
}

enum class BodyType {
    SLENDER_NEON, TALL_DISC, EEL_SNAKE, JELLY_GLOW, STAR_CRAWLER, SEAHORSE, MANTA_RAY
}

data class SpeciesConfig(
    val id: Int,
    val name: String,
    val bodyColor: Int,
    val neonGlowColor: Int,
    val accentColor: Int,
    val bodyType: BodyType,
    val maxSpeed: Float,
    val behavior: BehaviorType,
    val sizeScale: Float,
    val segmentCount: Int = 6
)

object FishCatalog {
    val SPECIES_LIST = listOf(
        SpeciesConfig(1, "Neon Tetra Ultra", Color.parseColor("#00E5FF"), Color.parseColor("#00F0FF"), Color.RED, BodyType.SLENDER_NEON, 5.2f, BehaviorType.FLOCKING, 0.9f),
        SpeciesConfig(2, "Cardinal Glow", Color.parseColor("#FF0055"), Color.parseColor("#FF0077"), Color.CYAN, BodyType.SLENDER_NEON, 5.0f, BehaviorType.FLOCKING, 0.95f),
        SpeciesConfig(3, "Electric Lime Glowfish", Color.parseColor("#39FF14"), Color.parseColor("#70FF40"), Color.YELLOW, BodyType.SLENDER_NEON, 5.5f, BehaviorType.FLOCKING, 1.0f),
        SpeciesConfig(4, "Cosmic Cyan Danio", Color.parseColor("#0044FF"), Color.parseColor("#33AACC"), Color.WHITE, BodyType.SLENDER_NEON, 6.0f, BehaviorType.FLOCKING, 0.85f),
        SpeciesConfig(5, "Sunburst Angelfish", Color.parseColor("#FFAA00"), Color.parseColor("#FFEE00"), Color.RED, BodyType.TALL_DISC, 2.5f, BehaviorType.SOLITARY, 2.3f),
        SpeciesConfig(6, "Midnight Lace Angel", Color.parseColor("#1A1A1A"), Color.parseColor("#00FFFF"), Color.MAGENTA, BodyType.TALL_DISC, 2.2f, BehaviorType.SOLITARY, 2.5f),
        SpeciesConfig(7, "Clownfish Anemone", Color.parseColor("#FF5500"), Color.parseColor("#FF8800"), Color.WHITE, BodyType.SLENDER_NEON, 3.2f, BehaviorType.HIDER, 1.3f),
        SpeciesConfig(8, "Royal Blue Tang", Color.parseColor("#0D47A1"), Color.parseColor("#29B6F6"), Color.YELLOW, BodyType.SLENDER_NEON, 4.0f, BehaviorType.SOLITARY, 1.7f),
        SpeciesConfig(9, "Turquoise Discus", Color.parseColor("#00E676"), Color.parseColor("#B9F6CA"), Color.MAGENTA, BodyType.TALL_DISC, 1.9f, BehaviorType.SOLITARY, 2.8f),
        SpeciesConfig(10, "Crimson Piranha", Color.parseColor("#D50000"), Color.parseColor("#FF1744"), Color.YELLOW, BodyType.SLENDER_NEON, 7.0f, BehaviorType.AGGRESSIVE, 2.0f),
        SpeciesConfig(11, "Golden Dragon Betta", Color.parseColor("#FFD700"), Color.parseColor("#FFFF80"), Color.RED, BodyType.TALL_DISC, 2.8f, BehaviorType.AGGRESSIVE, 2.0f),
        SpeciesConfig(12, "Volitan Lionfish", Color.parseColor("#FF3D00"), Color.parseColor("#FFAB91"), Color.WHITE, BodyType.TALL_DISC, 2.1f, BehaviorType.AGGRESSIVE, 3.0f),
        SpeciesConfig(13, "Mandarin Psychedelic", Color.parseColor("#AA00FF"), Color.parseColor("#EA80FC"), Color.GREEN, BodyType.SLENDER_NEON, 2.4f, BehaviorType.HIDER, 1.2f),
        SpeciesConfig(14, "Electric Blue Ribbon Eel", Color.parseColor("#0288D1"), Color.parseColor("#80D8FF"), Color.YELLOW, BodyType.EEL_SNAKE, 3.8f, BehaviorType.HIDER, 3.2f, 12),
        SpeciesConfig(15, "Pink Bioluminescent Jelly", Color.parseColor("#E91E63"), Color.parseColor("#FF80AB"), Color.CYAN, BodyType.JELLY_GLOW, 1.2f, BehaviorType.SOLITARY, 2.2f),
        SpeciesConfig(16, "Deepsea Viper Dragonfish", Color.parseColor("#311B92"), Color.parseColor("#B388FF"), Color.CYAN, BodyType.EEL_SNAKE, 5.5f, BehaviorType.PREDATOR, 2.6f, 10),
        SpeciesConfig(17, "Crown Pygmy Seahorse", Color.parseColor("#FF4081"), Color.parseColor("#FF80AB"), Color.WHITE, BodyType.SEAHORSE, 1.3f, BehaviorType.HIDER, 1.1f),
        SpeciesConfig(18, "Ruby Sea Star", Color.parseColor("#FF1744"), Color.parseColor("#FF5252"), Color.YELLOW, BodyType.STAR_CRAWLER, 0.4f, BehaviorType.BOTTOM_DWELLER, 1.8f),
        SpeciesConfig(19, "Linckia Blue Starfish", Color.parseColor("#2979FF"), Color.parseColor("#82B1FF"), Color.WHITE, BodyType.STAR_CRAWLER, 0.3f, BehaviorType.BOTTOM_DWELLER, 1.9f),
        SpeciesConfig(20, "Flame Dwarf Angel", Color.parseColor("#FF3D00"), Color.parseColor("#FF9100"), Color.BLACK, BodyType.TALL_DISC, 3.5f, BehaviorType.SOLITARY, 1.5f),
        SpeciesConfig(21, "Powder Blue Surgeon", Color.parseColor("#00B0FF"), Color.parseColor("#E0F7FA"), Color.BLACK, BodyType.SLENDER_NEON, 4.2f, BehaviorType.FLOCKING, 1.6f),
        SpeciesConfig(22, "Fire Goby Dart", Color.parseColor("#FF1744"), Color.parseColor("#FF9100"), Color.WHITE, BodyType.SLENDER_NEON, 6.2f, BehaviorType.HIDER, 1.2f),
        SpeciesConfig(23, "Bioluminescent Manta Ray", Color.parseColor("#1A237E"), Color.parseColor("#00E5FF"), Color.WHITE, BodyType.MANTA_RAY, 2.6f, BehaviorType.SOLITARY, 4.0f),
        SpeciesConfig(24, "Transparent Glass Catfish", Color.parseColor("#40FFFFFF"), Color.parseColor("#00E5FF"), Color.MAGENTA, BodyType.SLENDER_NEON, 3.8f, BehaviorType.FLOCKING, 1.5f),
        SpeciesConfig(25, "Royal Gramma Bicolor", Color.parseColor("#651FFF"), Color.parseColor("#B388FF"), Color.YELLOW, BodyType.SLENDER_NEON, 3.2f, BehaviorType.HIDER, 1.3f),
        SpeciesConfig(26, "Emperor Circle Angel", Color.parseColor("#3D5AFE"), Color.parseColor("#8C9EFF"), Color.YELLOW, BodyType.TALL_DISC, 2.3f, BehaviorType.SOLITARY, 2.7f),
        SpeciesConfig(27, "Copperband Beak Butterfly", Color.parseColor("#FF9100"), Color.parseColor("#FFE0B2"), Color.WHITE, BodyType.TALL_DISC, 2.7f, BehaviorType.SOLITARY, 2.1f),
        SpeciesConfig(28, "Leafy Camouflage Dragon", Color.parseColor("#64DD17"), Color.parseColor("#CCFF90"), Color.YELLOW, BodyType.SEAHORSE, 1.2f, BehaviorType.HIDER, 2.2f),
        SpeciesConfig(29, "Black Diamond Piranha", Color.parseColor("#121212"), Color.parseColor("#FF0000"), Color.RED, BodyType.SLENDER_NEON, 7.5f, BehaviorType.AGGRESSIVE, 2.2f),
        SpeciesConfig(30, "Neon Pink Zebra Danio", Color.parseColor("#FF007F"), Color.parseColor("#FF80BF"), Color.WHITE, BodyType.SLENDER_NEON, 5.8f, BehaviorType.FLOCKING, 0.9f),
        SpeciesConfig(31, "Peacock Mantis Strike", Color.parseColor("#00C853"), Color.parseColor("#B2FF59"), Color.RED, BodyType.SLENDER_NEON, 4.5f, BehaviorType.AGGRESSIVE, 1.6f),
        SpeciesConfig(32, "Electric Cyan Cichlid", Color.parseColor("#00E5FF"), Color.parseColor("#80D8FF"), Color.BLUE, BodyType.SLENDER_NEON, 4.5f, BehaviorType.AGGRESSIVE, 1.8f),
        SpeciesConfig(33, "Rainbow Comb Jelly", Color.parseColor("#18FFFF"), Color.parseColor("#FF4081"), Color.GREEN, BodyType.JELLY_GLOW, 0.9f, BehaviorType.SOLITARY, 2.5f),
        SpeciesConfig(34, "Harlequin Glowing Shrimp", Color.parseColor("#FFFFFF"), Color.parseColor("#FF80AB"), Color.CYAN, BodyType.SLENDER_NEON, 1.6f, BehaviorType.BOTTOM_DWELLER, 1.1f),
        SpeciesConfig(35, "Neon Yellow Watchman", Color.parseColor("#FFD600"), Color.parseColor("#FFFF80"), Color.BLUE, BodyType.SLENDER_NEON, 2.2f, BehaviorType.HIDER, 1.3f)
    )
}

class Anemone(val basePos: Vector2D, val tentacleCount: Int = 14, val length: Float = 110f) {
    val angles = FloatArray(tentacleCount) { (it.toFloat() / tentacleCount) * PI.toFloat() - PI.toFloat() / 2 }
    val phases = FloatArray(tentacleCount) { Random.nextFloat() * 10f }

    fun update(tapPoint: Vector2D?) {
        for (i in 0 until tentacleCount) {
            phases[i] += 0.04f
            if (tapPoint != null && tapPoint.dist(basePos) < 250f) {
                phases[i] += 0.2f
            }
        }
    }
}

class FishEntity(
    val config: SpeciesConfig,
    var position: Vector2D,
    var velocity: Vector2D = Vector2D(Random.nextFloat() * 2 - 1, Random.nextFloat() * 2 - 1)
) {
    var acceleration = Vector2D()
    val segments = Array(config.segmentCount) { position.copy() }
    var swimCycle = Random.nextFloat() * 100f
    var isAttacking = false
    var attackTimer = 0

    fun update(w: Float, h: Float, fishes: List<FishEntity>, anemones: List<Anemone>, tapPoint: Vector2D?) {
        swimCycle += 0.15f

        if (tapPoint != null) {
            val distToTap = position.dist(tapPoint)
            if (distToTap < 500f) {
                if (config.behavior == BehaviorType.AGGRESSIVE || config.behavior == BehaviorType.PREDATOR) {
                    val attackForce = Vector2D(tapPoint.x - position.x, tapPoint.y - position.y)
                    attackForce.normalize()
                    attackForce.mult(config.maxSpeed * 2.8f)
                    acceleration.add(attackForce)
                    isAttacking = true
                    attackTimer = 40
                } else {
                    val fleeForce = Vector2D(position.x - tapPoint.x, position.y - tapPoint.y)
                    fleeForce.normalize()
                    fleeForce.mult(config.maxSpeed * 3.5f / (distToTap / 100f).coerceAtLeast(0.4f))
                    acceleration.add(fleeForce)
                }
            }
        }

        if (attackTimer > 0) attackTimer-- else isAttacking = false

        if (config.behavior == BehaviorType.FLOCKING && !isAttacking) {
            applyFlocking(fishes)
        }

        if (config.behavior == BehaviorType.HIDER) {
            anemones.minByOrNull { it.basePos.dist(position) }?.let { near ->
                if (near.basePos.dist(position) < 300f) {
                    val hideForce = Vector2D(near.basePos.x - position.x, near.basePos.y - position.y)
                    hideForce.normalize()
                    hideForce.mult(config.maxSpeed * 0.5f)
                    acceleration.add(hideForce)
                }
            }
        }

        acceleration.add(Vector2D((Random.nextFloat() - 0.5f) * 0.4f, (Random.nextFloat() - 0.5f) * 0.4f))

        velocity.add(acceleration)
        velocity.limit(if (isAttacking) config.maxSpeed * 2.8f else config.maxSpeed)
        position.add(velocity)
        acceleration.mult(0f)

        val margin = 90f
        if (position.x < margin) velocity.x += 0.8f
        if (position.x > w - margin) velocity.x -= 0.8f
        if (position.y < margin) velocity.y += 0.8f
        if (position.y > h - margin && config.behavior != BehaviorType.BOTTOM_DWELLER) velocity.y -= 0.8f

        if (config.behavior == BehaviorType.BOTTOM_DWELLER) {
            position.y = position.y * 0.92f + (h - 80f) * 0.08f
        }

        updateSegments()
    }

    private fun updateSegments() {
        segments[0] = position.copy()
        val segDist = 18f * config.sizeScale
        for (i in 1 until segments.size) {
            val prev = segments[i - 1]
            val current = segments[i]
            val dir = Vector2D(current.x - prev.x, current.y - prev.y)
            dir.normalize()
            dir.mult(segDist)
            segments[i] = Vector2D(prev.x + dir.x, prev.y + dir.y)
        }
    }

    private fun applyFlocking(fishes: List<FishEntity>) {
        var sep = Vector2D(); var align = Vector2D(); var avgPos = Vector2D(); var count = 0
        for (other in fishes) {
            if (other.config.id == config.id && other != this) {
                val d = position.dist(other.position)
                if (d in 0.1f..160f) {
                    val diff = Vector2D(position.x - other.position.x, position.y - other.position.y)
                    diff.normalize()
                    sep.add(diff)
                    align.add(other.velocity)
                    avgPos.add(other.position)
                    count++
                }
            }
        }
        if (count > 0) {
            sep.div(count.toFloat()); sep.mult(config.maxSpeed)
            align.div(count.toFloat()); align.mult(config.maxSpeed)
            avgPos.div(count.toFloat()); avgPos.sub(position); avgPos.mult(config.maxSpeed)
            acceleration.add(sep); acceleration.add(align); acceleration.add(avgPos)
        }
    }
}
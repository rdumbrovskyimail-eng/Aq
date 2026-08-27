package com.aquarium.neon

import kotlin.math.*
import kotlin.random.Random

enum class FoodType { FLAKES, RAW_MEAT }

/** Кусок корма. Хлопья парят, мясо тонет и сочится кровью. */
class FoodEntity(@JvmField val pos: Vector3D, @JvmField val type: FoodType, seed: Int) {
    @JvmField val vel = Vector3D()
    @JvmField var active = true
    @JvmField var bites = if (type == FoodType.RAW_MEAT) 8 else 3
    @JvmField var spin = 0f
    @JvmField var wobble: Float
    @JvmField var age = 0f
    private val rnd = Random(seed)

    init { wobble = rnd.nextFloat() * 10f }

    fun update(dt: Float, floorY: Float, halfD: Float, particles: ParticlePool) {
        if (!active) return
        age += dt
        wobble += dt * 3.0f
        spin += dt * if (type == FoodType.RAW_MEAT) 40f else 150f

        // Хлопья почти нейтральной плавучести — падают кружась, мясо идёт ко дну
        val sink = if (type == FoodType.RAW_MEAT) -1.15f else -0.42f
        vel.y = sink + sin(wobble) * 0.16f
        vel.x = cos(wobble * 0.7f) * if (type == FoodType.RAW_MEAT) 0.14f else 0.34f
        vel.z = sin(wobble * 0.5f) * if (type == FoodType.RAW_MEAT) 0.14f else 0.30f
        pos.addScaled(vel, dt)

        if (type == FoodType.RAW_MEAT && rnd.nextFloat() < dt * 22f) {
            particles.emitBlood(pos.x, pos.y, pos.z, 2)
        }

        val ground = floorY + Geometry.floorHeight(pos.x, pos.z, halfD) + 0.25f
        if (pos.y <= ground) { pos.y = ground; vel.zero() }

        // Несъеденный корм со временем растворяется, а не копится вечно
        if (age > 90f) active = false
    }
}

/** Акустическая волна от стука по стеклу. */
class Shockwave(@JvmField val pos: Vector3D, @JvmField val maxRadius: Float = 15f) {
    @JvmField var currentRadius = 0.2f
    @JvmField var intensity = 1f
    @JvmField var active = true

    fun update(dt: Float) {
        if (!active) return
        currentRadius += dt * 20f
        intensity = (1f - currentRadius / maxRadius).coerceIn(0f, 1f)
        if (currentRadius >= maxRadius) active = false
    }
}

/** Пузырёк воздуха. Скорость всплытия подчиняется закону Стокса: v ~ r². */
class Bubble(@JvmField val pos: Vector3D, seed: Int) {
    @JvmField var radius = 0f
    @JvmField var rise = 0f
    @JvmField var wobblePhase = 0f
    @JvmField var wobbleRate = 0f
    @JvmField var alive = true
    private val rnd = Random(seed)

    init { roll() }

    private fun roll() {
        radius = 0.055f + rnd.nextFloat().pow(2.2f) * 0.26f
        rise = 0.80f + radius * radius * 46f + rnd.nextFloat() * 0.3f
        wobblePhase = rnd.nextFloat() * 12f
        wobbleRate = 1.4f + rnd.nextFloat() * 2.6f
    }

    /** @return true, если пузырь лопнул на поверхности в этом кадре. */
    fun update(dt: Float, time: Float, b: Bounds): Boolean {
        pos.y += rise * dt
        val a = time * wobbleRate + wobblePhase
        pos.x += cos(a) * dt * 0.40f
        pos.z += sin(a * 0.83f) * dt * 0.30f
        radius *= 1f + dt * 0.04f          // расширяется по мере падения давления столба воды

        if (pos.y > b.halfY - 0.15f) {
            roll()
            pos.y = b.floorY + rnd.nextFloat() * 0.5f
            pos.x = (rnd.nextFloat() - 0.5f) * b.halfX * 1.9f
            pos.z = (rnd.nextFloat() - 0.5f) * b.halfZ * 1.9f
            return true
        }
        return false
    }
}

class Plant(
    @JvmField val pos: Vector3D, @JvmField val height: Float, @JvmField val yawDeg: Float,
    @JvmField val phase: Float, @JvmField val stiffness: Float,
    @JvmField val color: FloatArray, @JvmField val tipColor: FloatArray
)

class Rock(
    @JvmField val pos: Vector3D, @JvmField val scale: Vector3D, @JvmField val yawDeg: Float,
    @JvmField val meshIndex: Int, @JvmField val base: FloatArray, @JvmField val moss: FloatArray
)

/**
 * Морской снег: детрит, медленно оседающий в толще воды.
 * Один из самых узнаваемых признаков настоящей подводной съёмки.
 */
class MarineSnow(private val count: Int, b: Bounds, seed: Int) {
    private val x = FloatArray(count)
    private val y = FloatArray(count)
    private val z = FloatArray(count)
    private val fall = FloatArray(count)
    private val phase = FloatArray(count)
    private val size = FloatArray(count)
    private val bounds = b

    init {
        val rnd = Random(seed)
        for (i in 0 until count) {
            x[i] = (rnd.nextFloat() - 0.5f) * b.halfX * 2.2f
            y[i] = b.floorY + rnd.nextFloat() * (b.halfY - b.floorY)
            z[i] = (rnd.nextFloat() - 0.5f) * b.halfZ * 2f
            fall[i] = 0.06f + rnd.nextFloat() * 0.16f
            phase[i] = rnd.nextFloat() * 12f
            size[i] = 0.05f + rnd.nextFloat() * 0.10f
        }
    }

    fun update(dt: Float, time: Float, current: Float) {
        for (i in 0 until count) {
            y[i] -= fall[i] * dt
            x[i] += sin(time * 0.4f + phase[i]) * dt * 0.12f * current
            z[i] += cos(time * 0.3f + phase[i] * 1.7f) * dt * 0.08f * current
            if (y[i] < bounds.floorY) {
                y[i] = bounds.halfY
                x[i] = (Random.nextFloat() - 0.5f) * bounds.halfX * 2.2f
                z[i] = (Random.nextFloat() - 0.5f) * bounds.halfZ * 2f
            }
        }
    }

    fun emit(cloud: PointCloud) {
        for (i in 0 until count) {
            cloud.push(x[i], y[i], z[i], 0.72f, 0.80f, 0.86f, 0.30f, size[i], 1f)
        }
    }
}

/**
 * Пул частиц на плоских массивах. Объекты не создаются и не удаляются в рантайме,
 * поэтому сборщик мусора не просыпается во время отрисовки.
 * Частицы делятся на два класса: кровь и снег рисуются альфа-смешением
 * (они поглощают свет), кавитация — аддитивно (она светится).
 */
class ParticlePool(private val capacity: Int = 900) {
    private val x = FloatArray(capacity); private val y = FloatArray(capacity); private val z = FloatArray(capacity)
    private val vx = FloatArray(capacity); private val vy = FloatArray(capacity); private val vz = FloatArray(capacity)
    private val r = FloatArray(capacity); private val g = FloatArray(capacity); private val bl = FloatArray(capacity)
    private val a = FloatArray(capacity); private val size = FloatArray(capacity)
    private val life = FloatArray(capacity); private val maxLife = FloatArray(capacity)
    private val additive = BooleanArray(capacity)
    private val buoyancy = FloatArray(capacity)
    private var cursor = 0
    private val rnd = Random(90210)

    private fun next(): Int { val i = cursor; cursor = (cursor + 1) % capacity; return i }

    /** Медленно расплывающийся шлейф крови от куска мяса. */
    fun emitBlood(cx: Float, cy: Float, cz: Float, count: Int) {
        repeat(count) {
            val i = next()
            x[i] = cx + (rnd.nextFloat() - 0.5f) * 0.25f
            y[i] = cy + (rnd.nextFloat() - 0.5f) * 0.25f
            z[i] = cz + (rnd.nextFloat() - 0.5f) * 0.25f
            vx[i] = (rnd.nextFloat() - 0.5f) * 0.35f
            vy[i] = (rnd.nextFloat() - 0.5f) * 0.22f
            vz[i] = (rnd.nextFloat() - 0.5f) * 0.35f
            r[i] = 0.48f + rnd.nextFloat() * 0.18f; g[i] = 0.015f; bl[i] = 0.03f
            a[i] = 0.42f
            maxLife[i] = 4.5f + rnd.nextFloat() * 3.5f
            life[i] = maxLife[i]
            size[i] = 0.30f + rnd.nextFloat() * 0.45f
            additive[i] = false
            buoyancy[i] = 0.03f
        }
    }

    /** Резкий выброс крови в момент укуса. */
    fun burstBlood(cx: Float, cy: Float, cz: Float, count: Int) {
        repeat(count) {
            val i = next()
            x[i] = cx; y[i] = cy; z[i] = cz
            val u = rnd.nextFloat() * 2f - 1f
            val th = rnd.nextFloat() * 2f * PI.toFloat()
            val s = sqrt(1f - u * u)
            val sp = 0.9f + rnd.nextFloat() * 2.8f
            vx[i] = cos(th) * s * sp; vy[i] = u * sp * 0.7f; vz[i] = sin(th) * s * sp
            r[i] = 0.62f; g[i] = 0.0f; bl[i] = 0.025f
            a[i] = 0.60f
            maxLife[i] = 3.0f + rnd.nextFloat() * 2.5f
            life[i] = maxLife[i]
            size[i] = 0.35f + rnd.nextFloat() * 0.55f
            additive[i] = false
            buoyancy[i] = 0.05f
        }
    }

    /** Кавитационные пузырьки от акустического удара — светящееся кольцо. */
    fun burstCavitation(cx: Float, cy: Float, cz: Float, count: Int) {
        repeat(count) {
            val i = next()
            x[i] = cx; y[i] = cy; z[i] = cz
            val th = rnd.nextFloat() * 2f * PI.toFloat()
            val sp = 3.5f + rnd.nextFloat() * 7.5f
            vx[i] = cos(th) * sp
            vy[i] = (rnd.nextFloat() - 0.35f) * sp * 0.55f
            vz[i] = sin(th) * sp * 0.5f
            r[i] = 0.55f; g[i] = 0.90f; bl[i] = 1.0f
            a[i] = 0.85f
            maxLife[i] = 0.55f + rnd.nextFloat() * 0.45f
            life[i] = maxLife[i]
            size[i] = 0.22f + rnd.nextFloat() * 0.40f
            additive[i] = true
            buoyancy[i] = 1.8f
        }
    }

    /** Облачко песка, поднятое рыбой у самого дна. */
    fun puffSand(cx: Float, cy: Float, cz: Float, count: Int) {
        repeat(count) {
            val i = next()
            x[i] = cx + (rnd.nextFloat() - 0.5f) * 0.5f
            y[i] = cy; z[i] = cz + (rnd.nextFloat() - 0.5f) * 0.5f
            vx[i] = (rnd.nextFloat() - 0.5f) * 0.9f
            vy[i] = rnd.nextFloat() * 0.5f
            vz[i] = (rnd.nextFloat() - 0.5f) * 0.9f
            r[i] = 0.42f; g[i] = 0.36f; bl[i] = 0.26f
            a[i] = 0.30f
            maxLife[i] = 1.6f + rnd.nextFloat() * 1.4f
            life[i] = maxLife[i]
            size[i] = 0.30f + rnd.nextFloat() * 0.5f
            additive[i] = false
            buoyancy[i] = 0.02f
        }
    }

    fun update(dt: Float) {
        // Демпфирование через exp(): результат не зависит от частоты кадров,
        // в отличие от покадрового умножения на константу
        val damp = exp(-2.4f * dt)
        for (i in 0 until capacity) {
            if (life[i] <= 0f) continue
            life[i] -= dt
            x[i] += vx[i] * dt; y[i] += vy[i] * dt; z[i] += vz[i] * dt
            vx[i] *= damp; vz[i] *= damp
            vy[i] = vy[i] * damp + buoyancy[i] * dt
        }
    }

    fun emit(alphaCloud: PointCloud, addCloud: PointCloud) {
        for (i in 0 until capacity) {
            val l = life[i]
            if (l <= 0f) continue
            val t = (l / maxLife[i]).coerceIn(0f, 1f)
            // Кровь по мере диффузии расплывается и бледнеет
            val sz = size[i] * (1.0f + (1f - t) * 0.9f)
            val target = if (additive[i]) addCloud else alphaCloud
            target.push(x[i], y[i], z[i], r[i], g[i], bl[i], a[i] * t * t, sz, t)
        }
    }
}
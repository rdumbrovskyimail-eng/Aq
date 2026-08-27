package com.aquarium.neon

import android.graphics.Color
import kotlin.math.*
import kotlin.random.Random

enum class FishForm(val id: Int) {
    FUSIFORM(0), DISC(1), EEL(2), MANTA(3), JELLYFISH(4), SHARK(5), LIONFISH(6)
}

enum class Diet { HERBIVORE, OMNIVORE, CARNIVORE }

/** Рисунок окраски — обрабатывается во фрагментном шейдере. */
enum class Pattern(val id: Int) { STRIPES(0), SPOTS(1), MESH(2), SOLID(3), GRADIENT(4) }

class Species(
    @JvmField val name: String,
    @JvmField val form: FishForm,
    primaryHex: String,
    glowHex: String,
    accentHex: String,
    @JvmField val scale: Float,
    @JvmField val cruise: Float,
    @JvmField val tailBeat: Float,
    @JvmField val waveAmp: Float,
    @JvmField val waveFreq: Float,
    @JvmField val stripeFreq: Float,
    @JvmField val schooling: Float,
    @JvmField val pattern: Pattern = Pattern.STRIPES,
    @JvmField val stripeSharp: Float = 0.18f,
    @JvmField val metallic: Float = 0.35f,
    @JvmField val diet: Diet = Diet.OMNIVORE,
    @JvmField val predator: Boolean = false,
    @JvmField val bottomHugger: Boolean = false,
    @JvmField val opacity: Float = 1f,
    @JvmField val preferredY: Float = 0f,
    @JvmField val baseCount: Int = 4
) {
    @JvmField val primary: FloatArray = rgb(primaryHex)
    @JvmField val glow: FloatArray = rgb(glowHex)
    @JvmField val accent: FloatArray = rgb(accentHex)
    /** Условная масса: решает, кто кого может съесть. */
    @JvmField val mass: Float = scale * scale * scale

    private companion object {
        fun rgb(hex: String): FloatArray {
            val c = Color.parseColor(hex)
            // sRGB -> линейное пространство. Без этого шага освещение считается
            // в гамма-пространстве и яркие цвета выжигаются в белый.
            fun lin(v: Int): Float {
                val s = v / 255f
                return if (s <= 0.04045f) s / 12.92f else ((s + 0.055f) / 1.055f).pow(2.4f)
            }
            return floatArrayOf(lin(Color.red(c)), lin(Color.green(c)), lin(Color.blue(c)))
        }
    }
}

object FishCatalog {
    val SPECIES: List<Species> = listOf(
        Species("Рыба-клоун оцеллярис", FishForm.FUSIFORM, "#FF6A00", "#FFB347", "#FFFFFF",
            0.44f, 3.0f, 15.5f, 0.21f, 3.6f, 3.2f, 0.55f,
            Pattern.STRIPES, 0.10f, 0.30f, Diet.OMNIVORE, preferredY = -1.5f, baseCount = 6),

        Species("Голубой хирург", FishForm.FUSIFORM, "#0B3FE0", "#3FA9FF", "#FFD400",
            0.66f, 3.4f, 13.0f, 0.19f, 3.3f, 1.6f, 0.60f,
            Pattern.GRADIENT, 0.20f, 0.45f, Diet.HERBIVORE, preferredY = 1.0f, baseCount = 5),

        Species("Жёлтый зебрасома", FishForm.DISC, "#FFD400", "#FFF07A", "#FFA300",
            0.62f, 2.8f, 11.0f, 0.16f, 2.9f, 2.0f, 0.55f,
            Pattern.SOLID, 0.18f, 0.40f, Diet.HERBIVORE, preferredY = 0.5f, baseCount = 5),

        Species("Голубой пудровый хирург", FishForm.FUSIFORM, "#3FC6F5", "#D8F6FF", "#111820",
            0.72f, 3.1f, 12.0f, 0.18f, 3.1f, 2.2f, 0.50f,
            Pattern.GRADIENT, 0.20f, 0.45f, Diet.HERBIVORE, preferredY = 1.2f, baseCount = 4),

        Species("Зелёная хромис", FishForm.FUSIFORM, "#2BE8B0", "#9BFFE0", "#0A5A6E",
            0.34f, 3.8f, 18.0f, 0.22f, 3.8f, 4.0f, 0.95f,
            Pattern.SOLID, 0.15f, 0.55f, Diet.OMNIVORE, preferredY = 2.0f, baseCount = 12),

        Species("Кардинал Банггай", FishForm.DISC, "#D8DEE9", "#FFFFFF", "#101318",
            0.46f, 2.2f, 9.5f, 0.14f, 2.7f, 3.0f, 0.75f,
            Pattern.STRIPES, 0.06f, 0.30f, Diet.CARNIVORE, preferredY = -0.5f, baseCount = 7),

        Species("Медянополосая бабочка", FishForm.DISC, "#FAFAF0", "#FFE7A8", "#E07A00",
            0.70f, 2.4f, 10.0f, 0.15f, 2.8f, 5.0f, 0.35f,
            Pattern.STRIPES, 0.09f, 0.35f, Diet.CARNIVORE, preferredY = 0.0f, baseCount = 4),

        Species("Императорский ангел", FishForm.DISC, "#0E2E8A", "#3FD8FF", "#FFD400",
            0.98f, 2.0f, 8.0f, 0.13f, 2.4f, 9.0f, 0.15f,
            Pattern.STRIPES, 0.14f, 0.55f, Diet.OMNIVORE, preferredY = -0.5f, baseCount = 2),

        Species("Мавританский идол", FishForm.DISC, "#FFF6D8", "#FFFBF0", "#161616",
            0.84f, 2.5f, 9.5f, 0.15f, 2.6f, 2.6f, 0.40f,
            Pattern.STRIPES, 0.07f, 0.30f, Diet.OMNIVORE, preferredY = 1.0f, baseCount = 4),

        Species("Королевская грамма", FishForm.FUSIFORM, "#8B2BE2", "#C77DFF", "#FFE500",
            0.38f, 2.8f, 15.0f, 0.20f, 3.4f, 1.4f, 0.30f,
            Pattern.GRADIENT, 0.30f, 0.40f, Diet.CARNIVORE, preferredY = -3.0f, baseCount = 5),

        Species("Огненный бычок", FishForm.FUSIFORM, "#FFF2D0", "#FF7A2F", "#E01B00",
            0.36f, 3.6f, 17.0f, 0.21f, 3.6f, 1.2f, 0.35f,
            Pattern.GRADIENT, 0.25f, 0.35f, Diet.CARNIVORE, preferredY = -2.0f, baseCount = 5),

        Species("Мандаринка", FishForm.FUSIFORM, "#0B7A5E", "#25E0B0", "#FF7A00",
            0.40f, 1.5f, 9.0f, 0.15f, 3.0f, 6.0f, 0.05f,
            Pattern.MESH, 0.20f, 0.30f, Diet.CARNIVORE, bottomHugger = true, preferredY = -5.0f, baseCount = 3),

        Species("Желтохвостый неон", FishForm.FUSIFORM, "#1B4FE8", "#7FB3FF", "#FFE04A",
            0.32f, 3.9f, 18.5f, 0.22f, 3.9f, 1.5f, 0.90f,
            Pattern.GRADIENT, 0.28f, 0.50f, Diet.OMNIVORE, preferredY = 2.5f, baseCount = 10),

        Species("Пятнистый спинорог", FishForm.DISC, "#1A1F2B", "#5FE0FF", "#F0F4FF",
            0.78f, 2.3f, 9.0f, 0.14f, 2.6f, 4.0f, 0.10f,
            Pattern.SPOTS, 0.20f, 0.45f, Diet.OMNIVORE, preferredY = -1.0f, baseCount = 2),

        Species("Красная крылатка", FishForm.LIONFISH, "#8E1B10", "#FF5A3C", "#FFF3E0",
            1.05f, 1.5f, 5.5f, 0.14f, 2.2f, 8.0f, 0.02f,
            Pattern.STRIPES, 0.08f, 0.25f, Diet.CARNIVORE, predator = true, preferredY = -2.5f, baseCount = 2),

        Species("Черноперая рифовая акула", FishForm.SHARK, "#5A6472", "#8FA0B4", "#12161C",
            1.85f, 4.4f, 7.5f, 0.16f, 2.7f, 1.0f, 0.08f,
            Pattern.GRADIENT, 0.25f, 0.30f, Diet.CARNIVORE, predator = true, preferredY = 1.5f, baseCount = 1),

        Species("Зелёная гигантская мурена", FishForm.EEL, "#22402C", "#4E9E52", "#B9D8A8",
            1.30f, 1.9f, 6.5f, 0.30f, 5.8f, 6.0f, 0.00f,
            Pattern.MESH, 0.20f, 0.20f, Diet.CARNIVORE, predator = true, bottomHugger = true, preferredY = -5.5f, baseCount = 1),

        Species("Зебровая мурена", FishForm.EEL, "#2B1810", "#C9A227", "#F5E6C8",
            0.95f, 2.1f, 7.5f, 0.30f, 6.2f, 14.0f, 0.00f,
            Pattern.STRIPES, 0.06f, 0.20f, Diet.CARNIVORE, bottomHugger = true, preferredY = -5.0f, baseCount = 1),

        Species("Океаническая манта", FishForm.MANTA, "#141B2E", "#3FC6F5", "#EAF6FF",
            2.05f, 1.7f, 2.8f, 0.32f, 1.8f, 2.0f, 0.12f,
            Pattern.SPOTS, 0.20f, 0.30f, Diet.HERBIVORE, preferredY = 2.5f, baseCount = 1),

        Species("Пятнистый орляк", FishForm.MANTA, "#1B2436", "#7FE3FF", "#FFFFFF",
            1.35f, 2.1f, 3.4f, 0.30f, 1.9f, 2.0f, 0.20f,
            Pattern.SPOTS, 0.20f, 0.30f, Diet.HERBIVORE, preferredY = 0.5f, baseCount = 2),

        Species("Ушастая медуза", FishForm.JELLYFISH, "#D8B4FE", "#B07CFF", "#7B2CBF",
            0.95f, 0.70f, 1.9f, 0.10f, 1.5f, 3.0f, 0.00f,
            Pattern.SOLID, 0.20f, 0.10f, Diet.HERBIVORE, opacity = 0.52f, preferredY = 3.5f, baseCount = 3),

        Species("Тихоокеанская крапива", FishForm.JELLYFISH, "#FF9E4A", "#FFD9A8", "#B33C00",
            1.15f, 0.58f, 1.6f, 0.10f, 1.4f, 3.0f, 0.00f,
            Pattern.SOLID, 0.20f, 0.10f, Diet.CARNIVORE, opacity = 0.48f, preferredY = 4.2f, baseCount = 2)
    )
}

enum class HuntState { WANDERING, STALKING, LUNGING }

/**
 * Одна рыба. Вся физика в единицах «мир в секунду» и умножается на dt,
 * поэтому поведение одинаково на 60, 90 и 120 Гц.
 */
class FishEntity(
    @JvmField val species: Species,
    @JvmField val position: Vector3D,
    private val seed: Int
) {
    @JvmField val velocity = Vector3D()
    @JvmField val acceleration = Vector3D()

    @JvmField var swimClock: Float
    @JvmField var yaw = 0f
    @JvmField var pitch = 0f
    @JvmField var roll = 0f
    @JvmField var panic = 0f
    @JvmField var bloodTint = 0f
    @JvmField var finTension = 0f
    @JvmField var health = 100f
    @JvmField var hunger = 0.35f
    @JvmField var huntState = HuntState.WANDERING
    @JvmField var isAlive = true
    @JvmField var respawnTimer = 0f
    @JvmField var biteCooldown = 0f

    private var wanderClock = seed * 1.13f
    private val wanderSeed = seed * 0.7391f
    private var effortBeat = 1f

    private val sep = Vector3D()
    private val ali = Vector3D()
    private val coh = Vector3D()
    private val tmp = Vector3D()
    private val closest = Vector3D()

    init {
        val rnd = Random(seed)
        swimClock = rnd.nextFloat() * 100f
        velocity.set(
            (rnd.nextFloat() - 0.5f) * species.cruise, 0f,
            (rnd.nextFloat() - 0.5f) * species.cruise
        )
    }

    fun update(
        dt: Float, all: List<FishEntity>, foods: MutableList<FoodEntity>,
        shocks: List<Shockwave>, particles: ParticlePool, b: Bounds, events: EcosystemEvents
    ) {
        // ── Мёртвая рыба: отсчёт до возрождения ──────────────────────────────
        // В версии 4.0 съеденная рыба исчезала навсегда, и через несколько минут
        // аквариум пустел. Здесь популяция самовосстанавливается.
        if (!isAlive) {
            respawnTimer -= dt
            if (respawnTimer <= 0f) respawn(b)
            return
        }

        acceleration.zero()
        hunger = (hunger + dt * 0.010f).coerceAtMost(1f)
        bloodTint = (bloodTint - dt * 0.35f).coerceAtLeast(0f)
        panic = (panic - dt * 0.60f).coerceAtLeast(0f)
        biteCooldown = (biteCooldown - dt).coerceAtLeast(0f)
        if (health < 100f) health = (health + dt * 3.5f).coerceAtMost(100f)

        // ── 1. Ударная волна от стука по стеклу ─────────────────────────────
        for (sw in shocks) {
            if (!sw.active) continue
            val d = position.dist(sw.pos)
            // Импульс получают те, кого фронт волны уже накрыл, но не успел уйти далеко
            if (d < sw.currentRadius + 3.5f && d > sw.currentRadius - 5.5f) {
                tmp.setFrom(position).sub(sw.pos).normalize()
                tmp.y += 0.30f
                tmp.normalize()
                val falloff = (1f - d / sw.maxRadius).coerceIn(0f, 1f)
                acceleration.addScaled(tmp, falloff * species.cruise * 26f)
                panic = max(panic, falloff)
            }
        }

        // ── 2. Поиск корма ──────────────────────────────────────────────────
        var target: FoodEntity? = null
        var targetDist = Float.MAX_VALUE
        for (f in foods) {
            if (!f.active) continue
            val wantsMeat = species.predator || species.diet == Diet.CARNIVORE
            val prefers = if (f.type == FoodType.RAW_MEAT) wantsMeat else species.diet != Diet.CARNIVORE || !species.predator
            if (!prefers) continue
            val d = position.dist(f.pos)
            // Кровь распространяется по всему объёму — хищник чует мясо издалека
            val sense = if (f.type == FoodType.RAW_MEAT) 26f else 13f
            if (d < sense && d < targetDist) { targetDist = d; target = f }
        }

        if (target != null && hunger > 0.15f) {
            tmp.setFrom(target.pos).sub(position).normalize()
            val rush = if (target.type == FoodType.RAW_MEAT && species.predator) 2.4f else 1.4f
            acceleration.addScaled(tmp, species.cruise * rush)
            val reach = species.scale * 1.3f + 0.3f
            if (targetDist < reach && biteCooldown <= 0f) {
                target.bites--
                biteCooldown = 0.35f
                hunger = (hunger - 0.30f).coerceAtLeast(0f)
                if (target.type == FoodType.RAW_MEAT) {
                    particles.burstBlood(position.x, position.y, position.z, 14)
                    events.onBite(position, true)
                } else {
                    events.onBite(position, false)
                }
                if (target.bites <= 0) target.active = false
            }
        } else {
            ecosystem(all, particles, dt, events)
        }

        // ── 3. Блуждание на несинхронных синусах (белый шум даёт дрожание) ──
        wanderClock += dt
        val w = wanderClock
        acceleration.add(
            (sin(w * 0.53f + wanderSeed) + sin(w * 0.19f + wanderSeed * 3.1f)) * species.cruise * 0.50f,
            sin(w * 0.37f + wanderSeed * 2.3f) * species.cruise * 0.28f,
            (cos(w * 0.61f + wanderSeed * 1.7f) + cos(w * 0.23f + wanderSeed)) * species.cruise * 0.50f
        )

        // ── 4. Предпочитаемый горизонт обитания ─────────────────────────────
        acceleration.y += (species.preferredY - position.y) * 0.28f
        if (species.bottomHugger) acceleration.y += (b.floorY + 1.7f - position.y) * 0.55f

        // ── 5. Мягкие стенки ────────────────────────────────────────────────
        wall(position.x, -b.halfX, b.halfX) { acceleration.x += it }
        wall(position.y, b.floorY + 1.0f, b.halfY) { acceleration.y += it }
        wall(position.z, -b.halfZ, b.halfZ) { acceleration.z += it }

        // ── 6. Интеграция + гидродинамическое сопротивление ────────────────
        velocity.addScaled(acceleration, dt)
        velocity.mult(1f - (1.35f * dt).coerceAtMost(0.4f))
        val burst = if (huntState == HuntState.LUNGING) 2.4f else 1f
        velocity.limit(species.cruise * burst * (1f + panic * 2.4f))
        position.addScaled(velocity, dt)

        if (!position.isFinite()) position.set(0f, 0f, 0f)
        position.x = position.x.coerceIn(-b.halfX - 1f, b.halfX + 1f)
        position.y = position.y.coerceIn(b.floorY + 0.4f, b.halfY + 1f)
        position.z = position.z.coerceIn(-b.halfZ - 1f, b.halfZ + 1f)

        // ── 7. Ориентация ───────────────────────────────────────────────────
        val spd = velocity.mag()
        if (spd > 0.02f) {
            // Голова меша смотрит в +Z, поэтому atan2(vx, vz) — точный курс без сдвига
            val targetYaw = atan2(velocity.x, velocity.z) * RAD
            var dYaw = targetYaw - yaw
            while (dYaw > 180f) dYaw -= 360f
            while (dYaw < -180f) dYaw += 360f
            yaw += dYaw * (1f - exp(-5.0f * dt))

            val targetPitch = asin((-velocity.y / spd).coerceIn(-1f, 1f)) * RAD
            pitch += (targetPitch - pitch) * (1f - exp(-4.5f * dt))
            // Крен в вираже: рыба закладывает поворот всем корпусом
            val targetRoll = (dYaw * 0.55f).coerceIn(-30f, 30f)
            roll += (targetRoll - roll) * (1f - exp(-5f * dt))
        }

        // Частота взмаха растёт с усилием — спокойная рыба машет медленно
        val effort = (spd / species.cruise).coerceIn(0.25f, 3.4f)
        effortBeat += (effort - effortBeat) * (1f - exp(-4f * dt))
        swimClock += dt * effortBeat

        // Испуг растопыривает плавники
        val wantTension = max(panic, if (huntState == HuntState.LUNGING) 0.55f else 0f)
        finTension += (wantTension - finTension) * (1f - exp(-8f * dt))
    }

    private fun respawn(b: Bounds) {
        val rnd = Random(seed * 31 + (respawnTimer * 1000).toInt())
        isAlive = true
        health = 100f
        hunger = 0.3f
        panic = 0f
        bloodTint = 0f
        huntState = HuntState.WANDERING
        // Новая особь появляется у дальней стенки — как будто выплыла из грота
        position.set(
            (rnd.nextFloat() - 0.5f) * b.halfX * 1.6f,
            species.preferredY.coerceIn(b.floorY + 2f, b.halfY - 2f),
            -b.halfZ + rnd.nextFloat() * 2f
        )
        velocity.set(0f, 0f, species.cruise * 0.5f)
    }

    private fun ecosystem(all: List<FishEntity>, particles: ParticlePool, dt: Float, events: EcosystemEvents) {
        sep.zero(); ali.zero(); coh.zero()
        var mates = 0
        var prey: FishEntity? = null
        var preyDist = Float.MAX_VALUE
        val huntingAllowed = Settings.predatorsEnabled

        for (other in all) {
            if (other === this || !other.isAlive) continue
            val d2 = position.distSq(other.position)
            if (d2 > NEIGHBOR_R2) continue
            val d = sqrt(d2).coerceAtLeast(0.1f)

            if (huntingAllowed && species.predator && !other.species.predator && hunger > 0.40f) {
                // Оценка добычи по массе: мелочь атакуется охотно,
                // сопоставимую по размеру рыбу — только при сильном голоде
                val ratio = species.mass / max(other.species.mass, 0.001f)
                val worth = ratio > 3.0f || (hunger > 0.80f && ratio > 1.4f)
                if (worth && d < preyDist) { preyDist = d; prey = other }
            } else if (huntingAllowed && !species.predator && other.species.predator && d < 8.0f) {
                tmp.setFrom(position).sub(other.position).normalize()
                    .mult((8.0f - d) * species.cruise * 1.5f)
                acceleration.add(tmp)
                panic = max(panic, (8.0f - d) / 8.0f * 0.85f)
            } else if (other.species === species && species.schooling > 0.01f) {
                tmp.setFrom(position).sub(other.position).div(d * d)
                sep.add(tmp); ali.add(other.velocity); coh.add(other.position)
                mates++
            } else if (d < 1.8f) {
                // Личная дистанция между разными видами
                tmp.setFrom(position).sub(other.position).normalize().mult((1.8f - d) * 1.6f)
                sep.add(tmp)
            }
        }

        val victim = prey
        if (victim != null) {
            if (preyDist > 3.2f) {
                huntState = HuntState.STALKING
                tmp.setFrom(victim.position).sub(position).normalize().mult(species.cruise * 1.25f)
                acceleration.add(tmp)
            } else {
                huntState = HuntState.LUNGING
                tmp.setFrom(victim.position).sub(position).normalize().mult(species.cruise * 3.2f)
                acceleration.add(tmp)
                if (preyDist < species.scale * 1.0f && biteCooldown <= 0f) {
                    biteCooldown = 1.2f
                    val dmg = 38f * (species.mass / max(victim.species.mass, 0.001f)).coerceIn(0.5f, 3f)
                    victim.health -= dmg
                    victim.bloodTint = 1f
                    victim.panic = 1f
                    particles.burstBlood(victim.position.x, victim.position.y, victim.position.z, 26)
                    events.onStrike(victim.position)
                    if (victim.health <= 0f) {
                        victim.isAlive = false
                        victim.respawnTimer = 22f + Random.nextFloat() * 14f
                        hunger = (hunger - 0.65f).coerceAtLeast(0f)
                        events.onKill(victim.position, victim.species.scale)
                    }
                    huntState = HuntState.WANDERING
                }
            }
        } else {
            huntState = HuntState.WANDERING
        }

        if (mates > 0) {
            val f = mates.toFloat(); val s = species.schooling
            // Веса модели Рейнольдса: разделение сильнее выравнивания, оно — сильнее сплочения.
            // При равных весах стая либо схлопывается в точку, либо разлетается.
            sep.div(f).normalize().mult(species.cruise * 2.7f * s)
            ali.div(f).normalize().mult(species.cruise * 1.5f * s)
            coh.div(f).sub(position).normalize().mult(species.cruise * 0.9f * s)
            acceleration.add(sep).add(ali).add(coh)
        }
    }

    private inline fun wall(v: Float, lo: Float, hi: Float, push: (Float) -> Unit) {
        val margin = 3.2f; val force = 27f
        if (v < lo + margin) push(((lo + margin - v) / margin).pow(2f) * force)
        if (v > hi - margin) push(-((v - (hi - margin)) / margin).pow(2f) * force)
    }

    private companion object {
        const val RAD = 57.29578f
        const val NEIGHBOR_R2 = 49f      // радиус восприятия соседей 7.0
    }
}

/** Обратные вызовы из симуляции наружу: звук, всплески, порождение падали. */
interface EcosystemEvents {
    fun onBite(pos: Vector3D, meat: Boolean)
    fun onStrike(pos: Vector3D)
    fun onKill(pos: Vector3D, size: Float)
}

class Bounds(
    @JvmField val halfX: Float, @JvmField val halfY: Float,
    @JvmField val halfZ: Float, @JvmField val floorY: Float
)
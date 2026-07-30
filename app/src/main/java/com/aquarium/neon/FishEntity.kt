package com.aquarium.neon

import android.graphics.*
import kotlin.math.*
import kotlin.random.Random

enum class FishState { WANDER, SCHOOL, FEED, FLEE, HUNT }

class FishEntity(
    val config: SpeciesConfig,
    var position: Vector2D,
    var velocity: Vector2D = Vector2D(
        (Random.nextFloat() * 2 - 1) * config.maxSpeed * 0.5f,
        (Random.nextFloat() * 2 - 1) * config.maxSpeed * 0.5f
    )
) {
    var acceleration = Vector2D()
    val spine = Array(config.segmentCount) { position.copy() }
    var state = FishState.WANDER

    var swimCycle = Random.nextFloat() * 100f
    var hunger = Random.nextFloat() * 50f
    val depth = Random.nextFloat() // 0.0 = дальний план, 1.0 = ближний план

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bodyPath = Path()

    // Профиль толщины рыбы вдоль скелета (Envelope Radii)
    private val bodyRadii = FloatArray(config.segmentCount) { i ->
        val t = i.toFloat() / (config.segmentCount - 1)
        val baseRadius = config.sizeScale * 14f
        when {
            t < 0.15f -> baseRadius * (0.5f + t / 0.15f * 0.5f) // Голова
            t < 0.5f -> baseRadius * (1.0f - (t - 0.15f) / 0.35f * 0.3f) // Туловище
            else -> baseRadius * (0.7f - (t - 0.5f) / 0.5f * 0.55f) // Хвост
        }
    }

    init {
        updateSpineIK(true)
    }

    fun update(
        w: Float,
        h: Float,
        allFishes: List<FishEntity>,
        caves: List<CoralCave>,
        foods: MutableList<FoodParticle>,
        tapPoint: Vector2D?,
        accX: Float,
        accY: Float
    ) {
        swimCycle += config.finFrequency * (velocity.mag() / config.maxSpeed).coerceAtLeast(0.35f)
        hunger = (hunger + 0.025f).coerceAtMost(100f)

        acceleration.add(accX * 0.08f, accY * 0.08f)

        // 1. Реакция на Тап по экрану
        if (tapPoint != null) {
            val dist = position.dist(tapPoint)
            if (dist < 450f) {
                if (config.behavior == BehaviorType.AGGRESSIVE || config.behavior == BehaviorType.PREDATOR) {
                    val force = Vector2D(tapPoint.x - position.x, tapPoint.y - position.y).normalize().mult(config.maxSpeed * 2.2f)
                    acceleration.add(force)
                    state = FishState.HUNT
                } else {
                    val flee = Vector2D(position.x - tapPoint.x, position.y - tapPoint.y).normalize().mult(config.maxSpeed * 3.2f)
                    acceleration.add(flee)
                    state = FishState.FLEE
                }
            }
        }

        // 2. Логика Поиска Пищи / Поведения
        if (state != FishState.FLEE) {
            if (hunger > 20f && foods.isNotEmpty()) {
                val nearestFood = foods.filter { !it.isEaten }.minByOrNull { it.position.dist(position) }
                if (nearestFood != null && nearestFood.position.dist(position) < 400f) {
                    state = FishState.FEED
                    val seek = Vector2D(nearestFood.position.x - position.x, nearestFood.position.y - position.y).normalize().mult(config.maxSpeed * 1.4f)
                    acceleration.add(seek)

                    if (nearestFood.position.dist(position) < (bodyRadii[0] + nearestFood.radius + 6f)) {
                        nearestFood.isEaten = true
                        hunger = (hunger - 40f).coerceAtLeast(0f)
                    }
                } else {
                    state = if (config.behavior == BehaviorType.FLOCKING) FishState.SCHOOL else FishState.WANDER
                }
            } else {
                state = if (config.behavior == BehaviorType.FLOCKING) FishState.SCHOOL else FishState.WANDER
            }

            if (state == FishState.SCHOOL) {
                applyAdvancedFlocking(allFishes)
            } else if (state == FishState.WANDER) {
                // Органический импульсный взмах
                val burst = sin(swimCycle * 0.7f)
                if (burst > 0.88f) {
                    val forward = Vector2D.fromAngle(velocity.heading(), config.maxSpeed * 0.3f)
                    acceleration.add(forward)
                }
            }
        }

        // Общее отталкивание от ВСЕХ рыб (Решение проблемы непересечения)
        applySoftSpatialAvoidance(allFishes)

        val curMaxSpeed = when (state) {
            FishState.FLEE -> config.maxSpeed * 2.5f
            FishState.FEED -> config.maxSpeed * 1.5f
            FishState.HUNT -> config.maxSpeed * 2.0f
            else -> config.maxSpeed
        }

        velocity.add(acceleration)
        velocity.limit(curMaxSpeed)
        position.add(velocity)
        acceleration.mult(0f)
        velocity.mult(0.985f)

        // Границы экрана
        val margin = 80f
        if (position.x < margin) velocity.x += 0.9f
        if (position.x > w - margin) velocity.x -= 0.9f
        if (position.y < margin + 40f) velocity.y += 0.9f
        if (position.y > h - margin) {
            if (config.behavior == BehaviorType.BOTTOM_DWELLER) {
                position.y = position.y * 0.88f + (h - 70f) * 0.12f
                velocity.y *= 0.5f
            } else {
                velocity.y -= 0.9f
            }
        }

        updateSpineIK(false)
    }

    /**
     * НЕПРОБИВАЕМЫЙ ИИ BOIDS:
     * Гарантирует, что рыбы никогда не сбиваются в одну точку.
     */
    private fun applyAdvancedFlocking(allFishes: List<FishEntity>) {
        val sep = Vector2D()
        val align = Vector2D()
        val center = Vector2D()
        var flockCount = 0
        var cohesionCount = 0

        val myPersonalRadius = config.sizeScale * 35f

        for (other in allFishes) {
            if (other === this) continue

            val d = position.dist(other.position)

            // Стайное поведение только для своего вида
            if (other.config.id == config.id) {
                if (d > 0.1f && d < 220f) {
                    align.add(other.velocity)
                    flockCount++

                    // Сплочение (Cohesion) действует только вне персональной зоны!
                    if (d > myPersonalRadius * 1.8f) {
                        center.add(other.position)
                        cohesionCount++
                    }
                }
            }
        }

        if (flockCount > 0) {
            align.div(flockCount.toFloat()).normalize().mult(config.maxSpeed * 0.9f)
            acceleration.add(align)
        }
        if (cohesionCount > 0) {
            center.div(cohesionCount.toFloat()).sub(position).normalize().mult(config.maxSpeed * 0.5f)
            acceleration.add(center)
        }
    }

    /**
     * Физическая персональная зона отталкивания для ВСЕХ сущностей (Квадратичное отталкивание)
     */
    private fun applySoftSpatialAvoidance(allFishes: List<FishEntity>) {
        val sep = Vector2D()
        for (other in allFishes) {
            if (other === this) continue
            val d = position.dist(other.position)
            val minAllowedDist = (bodyRadii[0] + other.bodyRadii[0]) * 2.2f

            if (d < minAllowedDist && d > 0.001f) {
                val overlap = (minAllowedDist - d) / minAllowedDist
                val pushForce = Vector2D(position.x - other.position.x, position.y - other.position.y)
                    .normalize()
                    .mult(config.maxSpeed * overlap * overlap * 4.0f) // Квадратичный рост силы!
                sep.add(pushForce)
            }

            // Избегание хищников крупными и мелкими рыбами
            if (other.config.behavior == BehaviorType.AGGRESSIVE || other.config.behavior == BehaviorType.PREDATOR) {
                if (config.behavior != BehaviorType.AGGRESSIVE && config.behavior != BehaviorType.PREDATOR) {
                    if (d < 280f && d > 0.1f) {
                        val fleePredator = Vector2D(position.x - other.position.x, position.y - other.position.y)
                            .normalize()
                            .mult(config.maxSpeed * 2.5f * (1.0f - d / 280f))
                        sep.add(fleePredator)
                    }
                }
            }
        }
        acceleration.add(sep)
    }

    private fun updateSpineIK(reset: Boolean) {
        spine[0].set(position.x, position.y)
        val segLen = 12f * config.sizeScale

        for (i in 1 until spine.size) {
            val prev = spine[i - 1]
            val curr = spine[i]
            if (reset) {
                curr.set(prev.x - segLen, prev.y)
            } else {
                val dx = curr.x - prev.x
                val dy = curr.y - prev.y
                val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(0.001f)
                curr.x = prev.x + (dx / dist) * segLen
                curr.y = prev.y + (dy / dist) * segLen
            }
        }
    }

    /**
     * РЕНДЕРИНГ МАКСИМАЛЬНОГО КАЧЕСТВА:
     * Построение органического Mesh-контура поверх IK-скелета
     */
    fun draw(canvas: Canvas, isNightMode: Boolean, screenHeight: Float) {
        val depthScale = 0.75f + depth * 0.25f
        val depthAlpha = (140 + depth * 115).toInt()

        // 1. Динамическая Тень на дне аквариума
        drawSeabedShadow(canvas, screenHeight, depthScale)

        // 2. Свечение и Ореол (Bioluminescent Glow)
        if (isNightMode || config.neonGlowColor != 0) {
            val glowColor = if (isNightMode) config.neonGlowColor else (config.neonGlowColor and 0x55FFFFFF)
            glowPaint.color = glowColor
            canvas.drawCircle(spine[0].x, spine[0].y, bodyRadii[0] * 2.8f * depthScale, glowPaint)
        }

        // 3. Отрисовка Плавников (Fins)
        drawFins(canvas, depthScale)

        // 4. Отрисовка Органического Тела (Spine Contour Mesh)
        drawOrganicBodyMesh(canvas, depthAlpha)

        // 5. Глаза и Детали
        drawAnatomy(canvas, depthScale)
    }

    private fun drawSeabedShadow(canvas: Canvas, screenHeight: Float, depthScale: Float) {
        val shadowY = screenHeight - 45f
        val shadowX = spine[0].x
        val shadowWidth = bodyRadii[0] * 2.5f * depthScale
        val shadowAlpha = ((1.0f - (shadowY - spine[0].y).coerceAtLeast(0f) / screenHeight) * 90).toInt().coerceIn(0, 90)

        shadowPaint.color = Color.argb(shadowAlpha, 0, 0, 0)
        canvas.drawOval(shadowX - shadowWidth, shadowY - 8f, shadowX + shadowWidth, shadowY + 8f, shadowPaint)
    }

    private fun drawOrganicBodyMesh(canvas: Canvas, depthAlpha: Int) {
        val count = spine.size
        val leftPts = Array(count) { Vector2D() }
        val rightPts = Array(count) { Vector2D() }

        for (i in 0 until count) {
            val pPrev = if (i > 0) spine[i - 1] else spine[0]
            val pNext = if (i < count - 1) spine[i + 1] else spine[count - 1]

            val dx = pNext.x - pPrev.x
            val dy = pNext.y - pPrev.y
            val len = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(0.001f)

            // Нормаль к сегменту
            val nx = -dy / len
            val ny = dx / len

            val r = bodyRadii[i]
            leftPts[i].set(spine[i].x + nx * r, spine[i].y + ny * r)
            rightPts[i].set(spine[i].x - nx * r, spine[i].y - ny * r)
        }

        bodyPath.reset()
        bodyPath.moveTo(spine[0].x, spine[0].y)

        // Левая сторона контура
        for (i in 0 until count - 1) {
            val midX = (leftPts[i].x + leftPts[i + 1].x) / 2f
            val midY = (leftPts[i].y + leftPts[i + 1].y) / 2f
            bodyPath.quadTo(leftPts[i].x, leftPts[i].y, midX, midY)
        }
        bodyPath.lineTo(leftPts.last().x, leftPts.last().y)

        // Кончик хвоста
        bodyPath.lineTo(spine.last().x, spine.last().y)
        bodyPath.lineTo(rightPts.last().x, rightPts.last().y)

        // Правая сторона контура
        for (i in count - 1 downTo 1) {
            val midX = (rightPts[i].x + rightPts[i - 1].x) / 2f
            val midY = (rightPts[i].y + rightPts[i - 1].y) / 2f
            bodyPath.quadTo(rightPts[i].x, rightPts[i].y, midX, midY)
        }
        bodyPath.close()

        fillPaint.shader = LinearGradient(
            spine[0].x, spine[0].y, spine.last().x, spine.last().y,
            intArrayOf(config.primaryColor, config.accentColor, Color.BLACK),
            floatArrayOf(0f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )
        fillPaint.alpha = depthAlpha
        canvas.drawPath(bodyPath, fillPaint)
        fillPaint.shader = null
    }

    private fun drawFins(canvas: Canvas, depthScale: Float) {
        strokePaint.color = config.accentColor
        strokePaint.strokeWidth = 3f * depthScale
        fillPaint.color = Color.argb(160, Color.red(config.accentColor), Color.green(config.accentColor), Color.blue(config.accentColor))

        // Хвостовой плавник
        val tailNode = spine.last()
        val prevTail = spine[spine.size - 2]
        val tailAngle = atan2((tailNode.y - prevTail.y).toDouble(), (tailNode.x - prevTail.x).toDouble()).toFloat()

        val tailSway = sin(swimCycle * 1.4f) * 16f * config.sizeScale
        val finLen = 30f * config.sizeScale * depthScale

        bodyPath.reset()
        bodyPath.moveTo(tailNode.x, tailNode.y)
        bodyPath.lineTo(
            tailNode.x - cos(tailAngle - 0.4f) * finLen,
            tailNode.y - sin(tailAngle - 0.4f) * finLen + tailSway
        )
        bodyPath.lineTo(
            tailNode.x - cos(tailAngle + 0.4f) * finLen,
            tailNode.y - sin(tailAngle + 0.4f) * finLen + tailSway
        )
        bodyPath.close()
        canvas.drawPath(bodyPath, fillPaint)
    }

    private fun drawAnatomy(canvas: Canvas, depthScale: Float) {
        val head = spine[0]
        val neck = spine[1]
        val dirAngle = atan2((head.y - neck.y).toDouble(), (head.x - neck.x).toDouble()).toFloat()

        val eyeDist = bodyRadii[0] * 0.65f
        val eyeX = head.x + cos(dirAngle + 0.5f) * eyeDist
        val eyeY = head.y + sin(dirAngle + 0.5f) * eyeDist
        val eyeR = 3.5f * config.sizeScale * depthScale

        fillPaint.color = Color.WHITE
        canvas.drawCircle(eyeX, eyeY, eyeR, fillPaint)

        fillPaint.color = if (state == FishState.HUNT || state == FishState.FLEE) Color.RED else Color.BLACK
        canvas.drawCircle(eyeX, eyeY, eyeR * 0.5f, fillPaint)

        fillPaint.color = Color.WHITE
        canvas.drawCircle(eyeX - eyeR * 0.2f, eyeY - eyeR * 0.2f, eyeR * 0.2f, fillPaint)
    }
}
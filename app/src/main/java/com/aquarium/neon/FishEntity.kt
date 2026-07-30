package com.aquarium.neon

import android.graphics.*
import kotlin.math.*
import kotlin.random.Random

enum class FishState { WANDER, SCHOOL, FEED, FLEE, HUNT, REST }

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
    var gillPulse = 0f
    var pitchAngle = 0f
    var rollAngle = 0f

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()

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
        currentX: Float,
        currentY: Float
    ) {
        swimCycle += config.finFrequency * (velocity.mag() / config.maxSpeed).coerceAtLeast(0.4f)
        gillPulse += 0.08f
        hunger = (hunger + 0.03f).coerceAtMost(100f)

        acceleration.add(currentX * 0.1f, currentY * 0.1f)

        if (tapPoint != null) {
            val dist = position.dist(tapPoint)
            if (dist < 500f) {
                if (config.behavior == BehaviorType.AGGRESSIVE || config.behavior == BehaviorType.PREDATOR) {
                    val force = Vector2D(tapPoint.x - position.x, tapPoint.y - position.y).normalize().mult(config.maxSpeed * 2.5f)
                    acceleration.add(force)
                    state = FishState.HUNT
                } else {
                    val flee = Vector2D(position.x - tapPoint.x, position.y - tapPoint.y).normalize().mult(config.maxSpeed * 3.5f)
                    acceleration.add(flee)
                    state = FishState.FLEE
                }
            }
        }

        if (state != FishState.FLEE) {
            if (hunger > 25f && foods.isNotEmpty()) {
                val nearestFood = foods.filter { !it.isEaten }.minByOrNull { it.position.dist(position) }
                if (nearestFood != null && nearestFood.position.dist(position) < 450f) {
                    state = FishState.FEED
                    val seekForce = Vector2D(nearestFood.position.x - position.x, nearestFood.position.y - position.y).normalize().mult(config.maxSpeed * 1.5f)
                    acceleration.add(seekForce)

                    if (nearestFood.position.dist(position) < (16f * config.sizeScale + nearestFood.radius)) {
                        nearestFood.isEaten = true
                        hunger = (hunger - 45f).coerceAtLeast(0f)
                    }
                } else {
                    state = if (config.behavior == BehaviorType.FLOCKING) FishState.SCHOOL else FishState.WANDER
                }
            } else {
                state = if (config.behavior == BehaviorType.FLOCKING) FishState.SCHOOL else FishState.WANDER
            }

            if (state == FishState.SCHOOL) {
                applyFlocking(allFishes)
            } else if (state == FishState.WANDER) {
                val burst = sin(swimCycle * 0.8f)
                if (burst > 0.85f) {
                    val forward = Vector2D.fromAngle(velocity.heading(), config.maxSpeed * 0.35f)
                    acceleration.add(forward)
                }
            }
        }

        val curMaxSpeed = when (state) {
            FishState.FLEE -> config.maxSpeed * 2.8f
            FishState.FEED -> config.maxSpeed * 1.6f
            FishState.HUNT -> config.maxSpeed * 2.2f
            else -> config.maxSpeed
        }

        velocity.add(acceleration)
        velocity.limit(curMaxSpeed)
        position.add(velocity)
        acceleration.mult(0f)
        velocity.mult(0.985f)

        val margin = 90f
        if (position.x < margin) velocity.x += 0.8f
        if (position.x > w - margin) velocity.x -= 0.8f
        if (position.y < margin + 40f) velocity.y += 0.8f
        if (position.y > h - margin) {
            if (config.behavior == BehaviorType.BOTTOM_DWELLER) {
                position.y = position.y * 0.9f + (h - 75f) * 0.1f
                velocity.y *= 0.5f
            } else {
                velocity.y -= 0.8f
            }
        }

        val headingAngle = velocity.heading()
        val targetPitch = (velocity.y / curMaxSpeed).coerceIn(-1f, 1f) * 0.5f
        pitchAngle = pitchAngle * 0.85f + targetPitch * 0.15f

        val targetRoll = (acceleration.x * sin(headingAngle) - acceleration.y * cos(headingAngle)) * 0.3f
        rollAngle = rollAngle * 0.8f + targetRoll * 0.2f

        updateSpineIK(false)
    }

    private fun applyFlocking(allFishes: List<FishEntity>) {
        val sep = Vector2D(); val align = Vector2D(); val center = Vector2D(); var count = 0
        for (other in allFishes) {
            if (other.config.id == config.id && other !== this) {
                val d = position.dist(other.position)
                if (d in 0.1f..180f) {
                    sep.add(Vector2D(position.x - other.position.x, position.y - other.position.y).normalize().div(d))
                    align.add(other.velocity)
                    center.add(other.position)
                    count++
                }
            }
        }
        if (count > 0) {
            val cf = count.toFloat()
            sep.div(cf).normalize().mult(config.maxSpeed)
            align.div(cf).normalize().mult(config.maxSpeed)
            center.div(cf).sub(position).normalize().mult(config.maxSpeed)

            acceleration.add(sep.mult(1.5f)).add(align.mult(1.0f)).add(center.mult(1.0f))
        }
    }

    private fun updateSpineIK(reset: Boolean) {
        spine[0].set(position.x, position.y)
        val segLen = 14f * config.sizeScale
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

    fun draw(canvas: Canvas, isNightMode: Boolean) {
        val head = spine[0]
        val angleDeg = Math.toDegrees(velocity.heading().toDouble()).toFloat()
        val scale = config.sizeScale * 14f

        canvas.save()
        canvas.translate(head.x, head.y)
        canvas.rotate(angleDeg)
        canvas.scale(1.0f, cos(pitchAngle.toDouble()).toFloat().coerceAtLeast(0.4f))

        if (isNightMode || config.neonGlowColor != 0) {
            val glowColor = if (isNightMode) config.neonGlowColor else (config.neonGlowColor and 0x55FFFFFF)
            glowPaint.color = glowColor
            canvas.drawCircle(0f, 0f, scale * 2.4f, glowPaint)
        }

        drawFins(canvas, scale)
        drawBody(canvas, scale)
        drawAnatomy(canvas, scale)

        canvas.restore()
    }

    private fun drawFins(canvas: Canvas, scale: Float) {
        strokePaint.color = config.accentColor
        strokePaint.strokeWidth = 3f
        fillPaint.color = Color.argb(180, Color.red(config.accentColor), Color.green(config.accentColor), Color.blue(config.accentColor))

        val tailSway = sin(swimCycle * 1.5f) * scale * 0.8f
        val tailX = -spine.size * 10f * config.sizeScale

        path.reset()
        path.moveTo(tailX, 0f)
        path.lineTo(tailX - scale * 1.8f, -scale * 1.2f + tailSway)
        path.lineTo(tailX - scale * 1.2f, tailSway * 0.5f)
        path.lineTo(tailX - scale * 1.8f, scale * 1.2f + tailSway)
        path.close()
        canvas.drawPath(path, fillPaint)

        val dorsalWave = sin(swimCycle * 0.8f) * scale * 0.2f
        path.reset()
        path.moveTo(-scale * 0.2f, -scale * 0.8f)
        path.quadTo(-scale * 1.0f, -scale * 2.2f + dorsalWave, -scale * 2.0f, -scale * 0.6f)
        path.close()
        canvas.drawPath(path, fillPaint)

        val pectoralSway = cos(swimCycle * 1.2f) * scale * 0.4f
        canvas.drawLine(scale * 0.2f, scale * 0.4f, scale * 0.6f + pectoralSway, scale * 1.5f, strokePaint)
        canvas.drawLine(scale * 0.2f, -scale * 0.4f, scale * 0.6f + pectoralSway, -scale * 1.5f, strokePaint)
    }

    private fun drawBody(canvas: Canvas, scale: Float) {
        bodyPaint.shader = LinearGradient(
            scale * 1.5f, -scale, -scale * 2f, scale,
            intArrayOf(config.primaryColor, config.accentColor, Color.BLACK),
            floatArrayOf(0f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )

        when (config.form) {
            VisualForm.TALL_DISC -> {
                canvas.drawOval(-scale * 1.6f, -scale * 2.0f, scale * 1.6f, scale * 2.0f, bodyPaint)
            }
            VisualForm.EEL_SNAKE -> {
                path.reset()
                path.moveTo(scale * 2.5f, 0f)
                for (i in 1 until spine.size) {
                    val segmentOffset = sin(swimCycle + i * 0.5f) * (i * 2.5f)
                    val x = -i * 12f * config.sizeScale
                    path.lineTo(x, segmentOffset)
                }
                strokePaint.color = config.primaryColor
                strokePaint.strokeWidth = scale * 1.2f
                canvas.drawPath(path, strokePaint)
            }
            VisualForm.JELLY_GLOW -> {
                val bellPulse = sin(swimCycle * 0.8f) * scale * 0.2f
                canvas.drawArc(-scale * 1.8f, -scale * 1.8f + bellPulse, scale * 1.8f, scale * 1.8f + bellPulse, 180f, 180f, true, bodyPaint)
            }
            else -> {
                path.reset()
                path.moveTo(scale * 1.8f, 0f)
                path.quadTo(0f, -scale * 0.9f, -scale * 2.2f, sin(swimCycle) * 6f)
                path.quadTo(0f, scale * 0.9f, scale * 1.8f, 0f)
                canvas.drawPath(path, bodyPaint)
            }
        }
        bodyPaint.shader = null
    }

    private fun drawAnatomy(canvas: Canvas, scale: Float) {
        val eyeX = scale * 0.9f
        val eyeY = -scale * 0.35f
        bodyPaint.color = Color.WHITE
        canvas.drawCircle(eyeX, eyeY, scale * 0.28f, bodyPaint)

        bodyPaint.color = if (state == FishState.HUNT || state == FishState.FLEE) Color.RED else Color.BLACK
        canvas.drawCircle(eyeX + scale * 0.05f, eyeY, scale * 0.14f, bodyPaint)

        bodyPaint.color = Color.WHITE
        canvas.drawCircle(eyeX + scale * 0.08f, eyeY - scale * 0.05f, scale * 0.05f, bodyPaint)

        val gillAlpha = ((sin(gillPulse) * 0.4f + 0.5f) * 200).toInt().coerceIn(0, 255)
        strokePaint.color = Color.argb(gillAlpha, 255, 255, 255)
        strokePaint.strokeWidth = 2f
        canvas.drawArc(scale * 0.2f, -scale * 0.4f, scale * 0.6f, scale * 0.4f, 120f, 120f, false, strokePaint)
    }
}
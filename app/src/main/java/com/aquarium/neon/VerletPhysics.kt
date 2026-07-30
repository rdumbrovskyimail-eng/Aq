package com.aquarium.neon

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.sin

class VerletNode(
    var x: Float,
    var y: Float,
    var oldX: Float = x,
    var oldY: Float = y,
    var isPinned: Boolean = false,
    val width: Float = 4f
) {
    fun update(gravityX: Float, gravityY: Float, drag: Float = 0.95f) {
        if (isPinned) return
        val vx = (x - oldX) * drag + gravityX
        val vy = (y - oldY) * drag + gravityY
        oldX = x
        oldY = y
        x += vx
        y += vy
    }
}

class VerletLink(
    val p1: VerletNode,
    val p2: VerletNode,
    val length: Float,
    val stiffness: Float = 0.85f
) {
    fun solve() {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(0.001f)
        val delta = (dist - length) / dist

        if (!p1.isPinned) {
            p1.x += dx * 0.5f * delta * stiffness
            p1.y += dy * 0.5f * delta * stiffness
        }
        if (!p2.isPinned) {
            p2.x -= dx * 0.5f * delta * stiffness
            p2.y -= dy * 0.5f * delta * stiffness
        }
    }
}

class PlantStem(
    val rootX: Float,
    val rootY: Float,
    val segmentCount: Int = 14,
    val segmentLength: Float = 16f,
    val color: Int,
    val baseWidth: Float = 16f
) {
    val nodes = ArrayList<VerletNode>()
    val links = ArrayList<VerletLink>()
    private val path = Path()

    init {
        for (i in 0 until segmentCount) {
            val w = baseWidth * (1f - (i.toFloat() / segmentCount) * 0.75f)
            val node = VerletNode(rootX, rootY - i * segmentLength, isPinned = (i == 0), width = w)
            nodes.add(node)
        }
        for (i in 0 until segmentCount - 1) {
            links.add(VerletLink(nodes[i], nodes[i + 1], segmentLength))
        }
    }

    fun update(time: Float, currentX: Float, currentY: Float, touchPoint: Vector2D?) {
        val waveFactor = sin(time * 2.2f + rootX * 0.04f) * 0.45f
        val buoyX = currentX + waveFactor
        val buoyY = -0.18f + currentY

        for (node in nodes) {
            node.update(buoyX, buoyY)
            if (touchPoint != null) {
                val d = Math.hypot((node.x - touchPoint.x).toDouble(), (node.y - touchPoint.y).toDouble()).toFloat()
                if (d < 200f && d > 1f) {
                    val push = (200f - d) / 200f * 5.0f
                    node.x += (node.x - touchPoint.x) / d * push
                    node.y += (node.y - touchPoint.y) / d * push
                }
            }
        }

        repeat(5) {
            for (link in links) link.solve()
        }
    }

    fun draw(canvas: Canvas, paint: Paint) {
        if (nodes.isEmpty()) return
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND

        path.reset()
        path.moveTo(nodes[0].x, nodes[0].y)
        for (i in 1 until nodes.size) {
            val prev = nodes[i - 1]
            val curr = nodes[i]
            val midX = (prev.x + curr.x) / 2f
            val midY = (prev.y + curr.y) / 2f
            path.quadTo(prev.x, prev.y, midX, midY)
        }
        path.lineTo(nodes.last().x, nodes.last().y)

        paint.strokeWidth = baseWidth
        canvas.drawPath(path, paint)
    }
}
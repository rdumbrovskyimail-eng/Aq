package com.aquarium.neon

import kotlin.math.*

data class Vector2D(var x: Float = 0f, var y: Float = 0f) {
    fun set(nx: Float, ny: Float): Vector2D { x = nx; y = ny; return this }
    fun set(v: Vector2D): Vector2D { x = v.x; y = v.y; return this }
    fun add(v: Vector2D): Vector2D { x += v.x; y += v.y; return this }
    fun add(dx: Float, dy: Float): Vector2D { x += dx; y += dy; return this }
    fun sub(v: Vector2D): Vector2D { x -= v.x; y -= v.y; return this }
    fun mult(n: Float): Vector2D { x *= n; y *= n; return this }
    fun div(n: Float): Vector2D { if (n != 0f) { x /= n; y /= n }; return this }
    fun mag(): Float = sqrt(x * x + y * y)
    fun magSq(): Float = x * x + y * y
    fun normalize(): Vector2D { val m = mag(); if (m > 0.0001f) div(m); return this }
    fun limit(max: Float): Vector2D { if (magSq() > max * max) { normalize(); mult(max) }; return this }
    fun dist(v: Vector2D): Float = sqrt((x - v.x).pow(2) + (y - v.y).pow(2))
    fun distSq(v: Vector2D): Float = (x - v.x).pow(2) + (y - v.y).pow(2)
    fun heading(): Float = atan2(y, x)
    fun copy(): Vector2D = Vector2D(x, y)

    companion object {
        fun fromAngle(angleRad: Float, length: Float = 1f): Vector2D {
            return Vector2D(cos(angleRad) * length, sin(angleRad) * length)
        }
    }
}
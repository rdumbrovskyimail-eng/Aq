package com.aquarium.neon

import kotlin.math.*

const val TAG = "Aquarium"

/** Мутабельный 3D-вектор: операции на месте, ноль аллокаций в игровом цикле. */
class Vector3D(@JvmField var x: Float = 0f, @JvmField var y: Float = 0f, @JvmField var z: Float = 0f) {
    fun set(nx: Float, ny: Float, nz: Float): Vector3D { x = nx; y = ny; z = nz; return this }
    fun setFrom(v: Vector3D): Vector3D { x = v.x; y = v.y; z = v.z; return this }
    fun zero(): Vector3D { x = 0f; y = 0f; z = 0f; return this }

    fun add(v: Vector3D): Vector3D { x += v.x; y += v.y; z += v.z; return this }
    fun addScaled(v: Vector3D, s: Float): Vector3D { x += v.x * s; y += v.y * s; z += v.z * s; return this }
    fun add(ax: Float, ay: Float, az: Float): Vector3D { x += ax; y += ay; z += az; return this }
    fun sub(v: Vector3D): Vector3D { x -= v.x; y -= v.y; z -= v.z; return this }
    fun mult(n: Float): Vector3D { x *= n; y *= n; z *= n; return this }
    fun div(n: Float): Vector3D { if (abs(n) > 1e-6f) { x /= n; y /= n; z /= n }; return this }

    fun mag(): Float = sqrt(x * x + y * y + z * z)
    fun magSq(): Float = x * x + y * y + z * z

    fun normalize(): Vector3D {
        val m = mag()
        if (m > 1e-5f) { x /= m; y /= m; z /= m } else { x = 0f; y = 0f; z = 0f }
        return this
    }

    fun limit(max: Float): Vector3D {
        val sq = magSq()
        if (sq > max * max && sq > 1e-10f) { val s = max / sqrt(sq); x *= s; y *= s; z *= s }
        return this
    }

    fun distSq(v: Vector3D): Float {
        val dx = x - v.x; val dy = y - v.y; val dz = z - v.z
        return dx * dx + dy * dy + dz * dz
    }

    fun dist(v: Vector3D): Float = sqrt(distSq(v))
    fun dot(v: Vector3D): Float = x * v.x + y * v.y + z * v.z
    fun copy(): Vector3D = Vector3D(x, y, z)
    fun isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()
}

class Ray3D(@JvmField val origin: Vector3D, @JvmField val direction: Vector3D) {
    fun closestPoint(point: Vector3D, out: Vector3D): Vector3D {
        val vx = point.x - origin.x; val vy = point.y - origin.y; val vz = point.z - origin.z
        val t = (vx * direction.x + vy * direction.y + vz * direction.z).coerceAtLeast(0f)
        out.set(origin.x + direction.x * t, origin.y + direction.y * t, origin.z + direction.z * t)
        return out
    }

    fun intersectPlaneZ(planeZ: Float, out: Vector3D): Boolean {
        if (abs(direction.z) < 1e-5f) return false
        val t = (planeZ - origin.z) / direction.z
        if (t < 0f) return false
        out.set(origin.x + direction.x * t, origin.y + direction.y * t, planeZ)
        return true
    }
}

object Noise {
    private fun hash(x: Int, y: Int): Float {
        var h = x * 374761393 + y * 668265263
        h = (h xor (h shr 13)) * 1274126177
        return ((h xor (h shr 16)) and 0x7fffffff) / 2147483647f
    }
    private fun smooth(t: Float) = t * t * (3f - 2f * t)

    fun value2D(px: Float, py: Float): Float {
        val xi = floor(px).toInt(); val yi = floor(py).toInt()
        val xf = smooth(px - xi); val yf = smooth(py - yi)
        val a = hash(xi, yi); val b = hash(xi + 1, yi)
        val c = hash(xi, yi + 1); val d = hash(xi + 1, yi + 1)
        return (a + (b - a) * xf) * (1f - yf) + (c + (d - c) * xf) * yf
    }

    fun fbm(px: Float, py: Float, octaves: Int = 4): Float {
        var sum = 0f; var amp = 0.5f; var freq = 1f; var norm = 0f
        repeat(octaves) {
            sum += value2D(px * freq, py * freq) * amp
            norm += amp; amp *= 0.5f; freq *= 2f
        }
        return sum / norm
    }
}

/** Линейная интерполяция цветовых троек — для суточного цикла освещения. */
fun lerpColor(a: FloatArray, b: FloatArray, t: Float, out: FloatArray): FloatArray {
    out[0] = a[0] + (b[0] - a[0]) * t
    out[1] = a[1] + (b[1] - a[1]) * t
    out[2] = a[2] + (b[2] - a[2]) * t
    return out
}

fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
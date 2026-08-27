package com.aquarium.neon

import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/** Сырые данные меша. Stride = 8 float: [px,py,pz, nx,ny,nz, u,v]. */
class MeshData(@JvmField val vertices: FloatArray, @JvmField val indices: ShortArray)

/** Меш в видеопамяти. VAO хранит всё состояние атрибутов — чужие буферы не протекают. */
class GpuMesh private constructor(
    @JvmField val vao: Int, @JvmField val vbo: Int,
    @JvmField val ibo: Int, @JvmField val indexCount: Int
) {
    fun draw() {
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, 0)
    }

    fun release() {
        GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
        GLES30.glDeleteBuffers(2, intArrayOf(vbo, ibo), 0)
    }

    companion object {
        const val STRIDE = 8 * 4

        fun upload(data: MeshData): GpuMesh {
            val vb: FloatBuffer = ByteBuffer.allocateDirect(data.vertices.size * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(data.vertices); position(0) }
            val ib: ShortBuffer = ByteBuffer.allocateDirect(data.indices.size * 2)
                .order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(data.indices); position(0) }

            val ids = IntArray(2); val vao = IntArray(1)
            GLES30.glGenBuffers(2, ids, 0)
            GLES30.glGenVertexArrays(1, vao, 0)

            GLES30.glBindVertexArray(vao[0])
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ids[0])
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.vertices.size * 4, vb, GLES30.GL_STATIC_DRAW)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ids[1])
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, data.indices.size * 2, ib, GLES30.GL_STATIC_DRAW)

            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, STRIDE, 0)
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, STRIDE, 3 * 4)
            GLES30.glEnableVertexAttribArray(2)
            GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, STRIDE, 6 * 4)

            GLES30.glBindVertexArray(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
            return GpuMesh(vao[0], ids[0], ids[1], data.indices.size)
        }
    }
}

/** Программа с кэшем локаций юниформов: glGetUniformLocation вызывается один раз на имя. */
class GlProgram(vertexSrc: String, fragmentSrc: String, label: String) {
    @JvmField val id: Int = link(vertexSrc, fragmentSrc, label)
    private val cache = HashMap<String, Int>(48)
    val isValid: Boolean get() = id != 0

    fun use() = GLES30.glUseProgram(id)
    fun loc(name: String): Int = cache.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }
    fun f(name: String, v: Float) = GLES30.glUniform1f(loc(name), v)
    fun i(name: String, v: Int) = GLES30.glUniform1i(loc(name), v)
    fun v3(name: String, a: Float, b: Float, c: Float) = GLES30.glUniform3f(loc(name), a, b, c)
    fun v3(name: String, c: FloatArray) = GLES30.glUniform3f(loc(name), c[0], c[1], c[2])
    fun v4(name: String, a: Float, b: Float, c: Float, d: Float) = GLES30.glUniform4f(loc(name), a, b, c, d)
    fun mat4(name: String, m: FloatArray) = GLES30.glUniformMatrix4fv(loc(name), 1, false, m, 0)
    fun release() { if (id != 0) GLES30.glDeleteProgram(id) }

    private companion object {
        fun link(vs: String, fs: String, label: String): Int {
            val v = compile(GLES30.GL_VERTEX_SHADER, vs, "$label.vert")
            val f = compile(GLES30.GL_FRAGMENT_SHADER, fs, "$label.frag")
            if (v == 0 || f == 0) return 0
            val p = GLES30.glCreateProgram()
            GLES30.glAttachShader(p, v); GLES30.glAttachShader(p, f)
            GLES30.glLinkProgram(p)
            val st = IntArray(1)
            GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, st, 0)
            GLES30.glDeleteShader(v); GLES30.glDeleteShader(f)
            if (st[0] != GLES30.GL_TRUE) {
                Log.e(TAG, "Ошибка линковки [$label]: " + GLES30.glGetProgramInfoLog(p))
                GLES30.glDeleteProgram(p); return 0
            }
            return p
        }

        fun compile(type: Int, src: String, label: String): Int {
            val s = GLES30.glCreateShader(type)
            GLES30.glShaderSource(s, src); GLES30.glCompileShader(s)
            val st = IntArray(1)
            GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, st, 0)
            if (st[0] == 0) {
                Log.e(TAG, "Ошибка компиляции [$label]: " + GLES30.glGetShaderInfoLog(s))
                GLES30.glDeleteShader(s); return 0
            }
            return s
        }
    }
}

/**
 * Динамическое облако точек. Один VBO с GL_STREAM_DRAW и переиспользуемый
 * FloatBuffer — за кадр не создаётся ни одного объекта.
 */
class PointCloud(private val maxPoints: Int) {
    private val stride = 9   // x,y,z, r,g,b,a, size, life
    private val buffer: FloatBuffer = ByteBuffer.allocateDirect(maxPoints * stride * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val vao = IntArray(1)
    private val vbo = IntArray(1)
    private var count = 0

    init {
        GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glGenBuffers(1, vbo, 0)
        GLES30.glBindVertexArray(vao[0])
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, maxPoints * stride * 4, null, GLES30.GL_STREAM_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride * 4, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, stride * 4, 3 * 4)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, stride * 4, 7 * 4)
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun begin() { buffer.position(0); count = 0 }

    fun push(x: Float, y: Float, z: Float, r: Float, g: Float, b: Float, a: Float, size: Float, life: Float) {
        if (count >= maxPoints) return
        buffer.put(x); buffer.put(y); buffer.put(z)
        buffer.put(r); buffer.put(g); buffer.put(b); buffer.put(a)
        buffer.put(size); buffer.put(life)
        count++
    }

    fun flush() {
        if (count == 0) return
        buffer.position(0)
        GLES30.glBindVertexArray(vao[0])
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, count * stride * 4, buffer)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, count)
        GLES30.glBindVertexArray(0)
    }

    fun release() {
        GLES30.glDeleteVertexArrays(1, vao, 0)
        GLES30.glDeleteBuffers(1, vbo, 0)
    }
}
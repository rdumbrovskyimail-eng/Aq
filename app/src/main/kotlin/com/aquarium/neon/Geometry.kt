package com.aquarium.neon

import kotlin.math.*
import kotlin.random.Random

/**
 * Сборщик мешей. Нормали вычисляются накоплением нормалей граней по вершинам —
 * это единственный способ получить корректное освещение на процедурной геометрии.
 */
class MeshBuilder(estimateVerts: Int = 512) {
    private val px = ArrayList<Float>(estimateVerts * 3)
    private val uv = ArrayList<Float>(estimateVerts * 2)
    private val idx = ArrayList<Int>(estimateVerts * 6)
    val vertexCount: Int get() = px.size / 3

    fun v(x: Float, y: Float, z: Float, u: Float, w: Float): Int {
        px.add(x); px.add(y); px.add(z); uv.add(u); uv.add(w)
        return px.size / 3 - 1
    }

    fun tri(a: Int, b: Int, c: Int) { idx.add(a); idx.add(b); idx.add(c) }
    fun quad(a: Int, b: Int, c: Int, d: Int) { tri(a, b, c); tri(a, c, d) }

    fun build(): MeshData {
        val n = px.size / 3
        require(n in 1..65535) { "Недопустимое число вершин: $n (лимит GL_UNSIGNED_SHORT)" }

        val nrm = FloatArray(n * 3)
        var i = 0
        while (i < idx.size) {
            val a = idx[i] * 3; val b = idx[i + 1] * 3; val c = idx[i + 2] * 3
            val ux = px[b] - px[a]; val uy = px[b + 1] - px[a + 1]; val uz = px[b + 2] - px[a + 2]
            val vx = px[c] - px[a]; val vy = px[c + 1] - px[a + 1]; val vz = px[c + 2] - px[a + 2]
            val nx = uy * vz - uz * vy
            val ny = uz * vx - ux * vz
            val nz = ux * vy - uy * vx
            nrm[a] += nx; nrm[a + 1] += ny; nrm[a + 2] += nz
            nrm[b] += nx; nrm[b + 1] += ny; nrm[b + 2] += nz
            nrm[c] += nx; nrm[c + 1] += ny; nrm[c + 2] += nz
            i += 3
        }

        val out = FloatArray(n * 8)
        for (k in 0 until n) {
            val o = k * 8
            out[o] = px[k * 3]; out[o + 1] = px[k * 3 + 1]; out[o + 2] = px[k * 3 + 2]
            var nx = nrm[k * 3]; var ny = nrm[k * 3 + 1]; var nz = nrm[k * 3 + 2]
            val len = sqrt(nx * nx + ny * ny + nz * nz)
            if (len > 1e-6f) { nx /= len; ny /= len; nz /= len } else { nx = 0f; ny = 1f; nz = 0f }
            out[o + 3] = nx; out[o + 4] = ny; out[o + 5] = nz
            out[o + 6] = uv[k * 2]; out[o + 7] = uv[k * 2 + 1]
        }
        return MeshData(out, ShortArray(idx.size) { idx[it].toShort() })
    }
}

object Geometry {

    /** Z-координата носа для каждой формы — нужна шейдеру, чтобы считать фазу волны. */
    fun headZ(form: Int): Float = when (form) {
        1 -> 1.00f; 2 -> 1.30f; 3 -> 0.92f; 4 -> 0.92f; 5 -> 1.80f; 6 -> 1.00f; else -> 1.10f
    }

    fun bodyLength(form: Int): Float = when (form) {
        1 -> 2.20f; 2 -> 4.80f; 3 -> 2.40f; 4 -> 3.60f; 5 -> 4.90f; 6 -> 2.40f; else -> 2.60f
    }

    /** Позиция и радиус глаза в локальных координатах: (x, y, z, r). r = 0 — глаза нет. */
    fun eye(form: Int): FloatArray = when (form) {
        0 -> floatArrayOf(0.175f, 0.115f, 0.760f, 0.105f)   // обычная рыба
        1 -> floatArrayOf(0.120f, 0.280f, 0.640f, 0.110f)   // дисковидная
        2 -> floatArrayOf(0.105f, 0.075f, 1.000f, 0.070f)   // угорь
        3 -> floatArrayOf(0.330f, 0.055f, 0.560f, 0.075f)   // скат — глаза по бокам головы
        4 -> floatArrayOf(0f, 0f, 0f, 0f)                   // медузы глаз не имеют
        5 -> floatArrayOf(0.280f, 0.140f, 1.310f, 0.100f)   // акула
        6 -> floatArrayOf(0.215f, 0.170f, 0.700f, 0.115f)   // крылатка
        else -> floatArrayOf(0f, 0f, 0f, 0f)
    }

    fun fish(form: Int): MeshData = when (form) {
        1 -> discFish(); 2 -> eel(); 3 -> manta(); 4 -> jellyfish()
        5 -> shark(); 6 -> lionfish(); else -> fusiform()
    }

    // ── Вспомогательные построители ──────────────────────────────────────────

    private fun revolve(
        b: MeshBuilder, rings: Int, seg: Int, zH: Float, zT: Float,
        rx: (Float) -> Float, ry: (Float) -> Float
    ) {
        val base = b.vertexCount
        for (i in 0..rings) {
            val u = i.toFloat() / rings
            val z = zH + (zT - zH) * u
            val a = rx(u); val c = ry(u)
            for (j in 0..seg) {
                val vv = j.toFloat() / seg
                val th = vv * 2f * PI.toFloat()
                b.v(cos(th) * a, sin(th) * c, z, vv, u)
            }
        }
        for (i in 0 until rings) for (j in 0 until seg) {
            val r0 = base + i * (seg + 1) + j
            val r1 = r0 + seg + 1
            b.tri(r0, r1, r0 + 1); b.tri(r0 + 1, r1, r1 + 1)
        }
    }

    /** Плоский плавник в плоскости X = const, полигон задаётся парами (y, z). */
    private fun finYZ(b: MeshBuilder, x: Float, pts: Array<FloatArray>) {
        val ids = IntArray(pts.size)
        for (k in pts.indices) ids[k] = b.v(x, pts[k][0], pts[k][1], k.toFloat() / (pts.size - 1), 1f)
        for (k in 1 until pts.size - 1) b.tri(ids[0], ids[k], ids[k + 1])
    }

    /** Плоский плавник в плоскости Y = const, полигон задаётся парами (x, z). */
    private fun finXZ(b: MeshBuilder, y: Float, pts: Array<FloatArray>) {
        val ids = IntArray(pts.size)
        for (k in pts.indices) ids[k] = b.v(pts[k][0], y, pts[k][1], k.toFloat() / (pts.size - 1), 1f)
        for (k in 1 until pts.size - 1) b.tri(ids[0], ids[k], ids[k + 1])
    }

    /** Лента переменной ширины вдоль оси Z — для щупалец и хвостов-хлыстов. */
    private fun ribbonZ(
        b: MeshBuilder, segs: Int, z0: Float, z1: Float,
        ox: Float, oy: Float, dirX: Float, dirY: Float,
        w0: Float, taper: Float, shrink: Float
    ) {
        val e0 = IntArray(segs + 1); val e1 = IntArray(segs + 1)
        for (k in 0..segs) {
            val t = k.toFloat() / segs
            val z = z0 + (z1 - z0) * t
            val w = w0 * (1f - t).pow(taper)
            val s = 1f - t * shrink
            e0[k] = b.v(ox * s - dirX * w, oy * s - dirY * w, z, 0f, 1f - t)
            e1[k] = b.v(ox * s + dirX * w, oy * s + dirY * w, z, 1f, 1f - t)
        }
        for (k in 0 until segs) b.quad(e0[k], e1[k], e1[k + 1], e0[k + 1])
    }

    // ── Формы рыб ────────────────────────────────────────────────────────────

    /** Обтекаемое веретеновидное тело: тетры, хирурги, клоуны, кардиналы. */
    private fun fusiform(): MeshData {
        val b = MeshBuilder(800)
        val prof = { u: Float -> sin(u.pow(0.82f) * PI.toFloat()).pow(0.85f) }
        revolve(b, 22, 16, 1.10f, -1.50f, { prof(it) * 0.33f }, { prof(it) * 0.50f })

        // Раздвоенный (гомоцеркальный) хвост
        finYZ(b, 0f, arrayOf(
            floatArrayOf(0.00f, -1.46f), floatArrayOf(0.62f, -2.24f), floatArrayOf(0.40f, -2.42f),
            floatArrayOf(0.00f, -1.86f), floatArrayOf(-0.40f, -2.42f), floatArrayOf(-0.62f, -2.24f)
        ))
        finYZ(b, 0f, arrayOf(   // спинной
            floatArrayOf(0.36f, 0.42f), floatArrayOf(0.86f, -0.02f),
            floatArrayOf(0.74f, -0.55f), floatArrayOf(0.30f, -0.72f)
        ))
        finYZ(b, 0f, arrayOf(   // анальный
            floatArrayOf(-0.32f, -0.30f), floatArrayOf(-0.68f, -0.70f),
            floatArrayOf(-0.58f, -1.05f), floatArrayOf(-0.26f, -1.02f)
        ))
        finXZ(b, -0.04f, arrayOf(   // грудные
            floatArrayOf(0.26f, 0.34f), floatArrayOf(0.86f, -0.10f),
            floatArrayOf(0.70f, -0.44f), floatArrayOf(0.24f, -0.20f)
        ))
        finXZ(b, -0.04f, arrayOf(
            floatArrayOf(-0.24f, -0.20f), floatArrayOf(-0.70f, -0.44f),
            floatArrayOf(-0.86f, -0.10f), floatArrayOf(-0.26f, 0.34f)
        ))
        return b.build()
    }

    /** Высокое дисковидное тело: ангелы, бабочки, мавританский идол. */
    private fun discFish(): MeshData {
        val b = MeshBuilder(800)
        val prof = { u: Float -> sin(u.pow(0.9f) * PI.toFloat()).pow(0.7f) }
        revolve(b, 20, 16, 1.00f, -1.20f, { prof(it) * 0.20f }, { prof(it) * 1.08f })

        finYZ(b, 0f, arrayOf(   // парусный спинной с вытянутой нитью
            floatArrayOf(0.80f, 0.55f), floatArrayOf(1.95f, 0.05f), floatArrayOf(2.55f, -0.75f),
            floatArrayOf(1.30f, -1.05f), floatArrayOf(0.62f, -1.14f)
        ))
        finYZ(b, 0f, arrayOf(   // анальный
            floatArrayOf(-0.78f, 0.42f), floatArrayOf(-1.85f, -0.10f), floatArrayOf(-2.20f, -1.05f),
            floatArrayOf(-1.20f, -1.10f), floatArrayOf(-0.60f, -1.16f)
        ))
        finYZ(b, 0f, arrayOf(   // вуалевый хвост
            floatArrayOf(0.00f, -1.18f), floatArrayOf(0.85f, -2.05f), floatArrayOf(0.30f, -2.35f),
            floatArrayOf(-0.30f, -2.35f), floatArrayOf(-0.85f, -2.05f)
        ))
        finXZ(b, -0.02f, arrayOf(   // нитевидные брюшные лучи
            floatArrayOf(0.05f, -0.15f), floatArrayOf(0.17f, -0.22f),
            floatArrayOf(0.10f, -2.30f), floatArrayOf(0.00f, -2.28f)
        ))
        finXZ(b, 0.02f, arrayOf(
            floatArrayOf(-0.05f, -0.15f), floatArrayOf(-0.10f, -2.28f),
            floatArrayOf(-0.17f, -2.30f), floatArrayOf(-0.12f, -0.22f)
        ))
        return b.build()
    }

    /** Змеевидное тело со сплошной спинной лентой: мурены, угри. */
    private fun eel(): MeshData {
        val b = MeshBuilder(1000)
        val zH = 1.30f; val zT = -3.50f
        val prof = { u: Float -> (1f - exp(-u * 9f)) * (1f - u).pow(0.55f) }
        revolve(b, 30, 12, zH, zT, { prof(it) * 0.155f }, { prof(it) * 0.235f })

        // Спинная лента идёт от затылка до кончика хвоста — без неё мурена похожа на червя
        val n = 20
        val top = IntArray(n + 1); val edge = IntArray(n + 1)
        for (k in 0..n) {
            val u = k.toFloat() / n
            val z = zH + (zT - zH) * u
            val h = prof(u)
            top[k] = b.v(0f, h * 0.235f, z, 0f, u)
            edge[k] = b.v(0f, h * 0.235f + 0.15f + h * 0.20f, z, 1f, u)
        }
        for (k in 0 until n) b.quad(top[k], edge[k], edge[k + 1], top[k + 1])

        // Нижняя кромка ближе к хвосту
        val m = 12
        val bot = IntArray(m + 1); val bEdge = IntArray(m + 1)
        for (k in 0..m) {
            val u = 0.45f + 0.55f * k.toFloat() / m
            val z = zH + (zT - zH) * u
            val h = prof(u)
            bot[k] = b.v(0f, -h * 0.235f, z, 0f, u)
            bEdge[k] = b.v(0f, -h * 0.235f - 0.10f - h * 0.12f, z, 1f, u)
        }
        for (k in 0 until m) b.quad(bot[k], bot[k + 1], bEdge[k + 1], bEdge[k])
        return b.build()
    }

    /** Скат: крыло-дельта с профилем по хорде, замкнутое по задней кромке. */
    private fun manta(): MeshData {
        val b = MeshBuilder(1500)
        val span = 22; val chord = 14
        val top = Array(span + 1) { IntArray(chord + 1) }
        val bot = Array(span + 1) { IntArray(chord + 1) }

        for (i in 0..span) {
            val sx = i.toFloat() / span * 2f - 1f
            val ax = abs(sx)
            val front = 0.92f - 1.28f * ax        // передняя кромка уходит назад
            val back = -0.78f - 0.42f * ax
            val x = sx * 1.85f
            for (j in 0..chord) {
                val t = j.toFloat() / chord
                val z = front + (back - front) * t
                val camber = sin(t.pow(0.62f) * PI.toFloat())
                val th = 0.135f * camber * (1f - ax * 0.82f).coerceAtLeast(0.06f)
                top[i][j] = b.v(x, th, z, sx * 0.5f + 0.5f, t)
                bot[i][j] = b.v(x, -th * 0.72f, z, sx * 0.5f + 0.5f, t)
            }
        }
        for (i in 0 until span) for (j in 0 until chord) {
            b.quad(top[i][j], top[i + 1][j], top[i + 1][j + 1], top[i][j + 1])
            b.quad(bot[i][j], bot[i][j + 1], bot[i + 1][j + 1], bot[i + 1][j])
        }
        // Замыкаем заднюю кромку — иначе крыло просвечивает изнутри
        for (i in 0 until span) b.quad(top[i][chord], top[i + 1][chord], bot[i + 1][chord], bot[i][chord])

        // Головные (цефальные) лопасти
        finXZ(b, 0.05f, arrayOf(
            floatArrayOf(0.14f, 0.90f), floatArrayOf(0.30f, 1.42f),
            floatArrayOf(0.20f, 1.46f), floatArrayOf(0.06f, 0.92f)
        ))
        finXZ(b, 0.05f, arrayOf(
            floatArrayOf(-0.06f, 0.92f), floatArrayOf(-0.20f, 1.46f),
            floatArrayOf(-0.30f, 1.42f), floatArrayOf(-0.14f, 0.90f)
        ))
        // Хвост-хлыст
        ribbonZ(b, 12, -1.15f, -3.65f, 0f, 0f, 1f, 0f, 0.075f, 1.4f, 0f)
        return b.build()
    }

    /** Медуза: купол, оральные лопасти и восемь щупалец-лент. */
    private fun jellyfish(): MeshData {
        val b = MeshBuilder(1000)
        val rings = 18; val seg = 20
        val prof = { u: Float ->
            if (u < 0.72f) sin(u / 0.72f * (PI.toFloat() / 2f)).pow(0.78f)
            else { val k = (u - 0.72f) / 0.28f; (1f - k * 0.35f) * (1f + sin(k * PI.toFloat()) * 0.10f) }
        }
        val zAt = { u: Float ->
            if (u < 0.72f) cos(u / 0.72f * (PI.toFloat() / 2f)) * 0.92f
            else -((u - 0.72f) / 0.28f) * 0.42f
        }

        val base = b.vertexCount
        for (i in 0..rings) {
            val u = i.toFloat() / rings
            val r = prof(u) * 0.90f; val z = zAt(u)
            for (j in 0..seg) {
                val vv = j.toFloat() / seg
                val th = vv * 2f * PI.toFloat()
                b.v(cos(th) * r, sin(th) * r, z, vv, u)
            }
        }
        for (i in 0 until rings) for (j in 0 until seg) {
            val r0 = base + i * (seg + 1) + j
            val r1 = r0 + seg + 1
            b.tri(r0, r1, r0 + 1); b.tri(r0 + 1, r1, r1 + 1)
        }

        // Щупальца: без них шейдерная анимация «шлейфа» анимировала пустоту
        for (t in 0 until 8) {
            val ang = t / 8f * 2f * PI.toFloat()
            ribbonZ(
                b, 14, -0.40f, -2.95f,
                cos(ang) * 0.68f, sin(ang) * 0.68f,
                -sin(ang), cos(ang), 0.085f, 0.7f, 0.35f
            )
        }
        // Четыре широкие оральные лопасти в центре
        for (t in 0 until 4) {
            val ang = t / 4f * 2f * PI.toFloat() + 0.4f
            ribbonZ(
                b, 10, -0.30f, -1.90f,
                cos(ang) * 0.22f, sin(ang) * 0.22f,
                -sin(ang), cos(ang), 0.20f, 1.1f, 0.15f
            )
        }
        return b.build()
    }

    /** Акула: торпедовидное тело, гетероцеркальный хвост, серповидные грудные. */
    private fun shark(): MeshData {
        val b = MeshBuilder(1100)
        val prof = { u: Float ->
            // Пик толщины сдвинут к голове — характерный силуэт хищника
            sin((u * 0.86f + 0.07f).pow(0.72f) * PI.toFloat()).pow(0.9f)
        }
        revolve(b, 26, 16, 1.80f, -2.00f, { prof(it) * 0.42f }, { prof(it) * 0.52f })

        finYZ(b, 0f, arrayOf(   // первый спинной
            floatArrayOf(0.46f, 0.30f), floatArrayOf(1.42f, -0.34f),
            floatArrayOf(1.28f, -0.72f), floatArrayOf(0.34f, -0.62f)
        ))
        finYZ(b, 0f, arrayOf(   // второй спинной
            floatArrayOf(0.28f, -1.35f), floatArrayOf(0.72f, -1.62f),
            floatArrayOf(0.64f, -1.82f), floatArrayOf(0.22f, -1.72f)
        ))
        finYZ(b, 0f, arrayOf(   // анальный
            floatArrayOf(-0.26f, -1.40f), floatArrayOf(-0.62f, -1.70f),
            floatArrayOf(-0.54f, -1.90f), floatArrayOf(-0.20f, -1.78f)
        ))
        finYZ(b, 0f, arrayOf(   // гетероцеркальный хвост: верхняя лопасть длиннее
            floatArrayOf(0.00f, -1.95f), floatArrayOf(1.15f, -3.05f), floatArrayOf(0.78f, -3.28f),
            floatArrayOf(0.00f, -2.32f), floatArrayOf(-0.62f, -2.92f), floatArrayOf(-0.40f, -2.58f)
        ))
        finXZ(b, -0.14f, arrayOf(   // серповидные грудные
            floatArrayOf(0.36f, 0.62f), floatArrayOf(1.58f, -0.18f),
            floatArrayOf(1.30f, -0.62f), floatArrayOf(0.30f, -0.12f)
        ))
        finXZ(b, -0.14f, arrayOf(
            floatArrayOf(-0.30f, -0.12f), floatArrayOf(-1.30f, -0.62f),
            floatArrayOf(-1.58f, -0.18f), floatArrayOf(-0.36f, 0.62f)
        ))
        return b.build()
    }

    /** Крылатка: веер ядовитых лучей и огромные грудные плавники. */
    private fun lionfish(): MeshData {
        val b = MeshBuilder(1400)
        val prof = { u: Float -> sin(u.pow(0.85f) * PI.toFloat()).pow(0.8f) }
        revolve(b, 20, 14, 1.00f, -1.30f, { prof(it) * 0.38f }, { prof(it) * 0.55f })

        // Спинные ядовитые иглы — каждая отдельным лучом с перепонкой
        for (i in 0 until 9) {
            val z = 0.55f - i * 0.19f
            val len = 1.55f - abs(i - 3) * 0.10f
            finYZ(b, 0f, arrayOf(
                floatArrayOf(0.42f, z), floatArrayOf(len, z - 0.30f),
                floatArrayOf(len * 0.88f, z - 0.42f), floatArrayOf(0.36f, z - 0.16f)
            ))
        }
        // Анальные иглы
        for (i in 0 until 4) {
            val z = -0.55f - i * 0.18f
            finYZ(b, 0f, arrayOf(
                floatArrayOf(-0.38f, z), floatArrayOf(-1.05f, z - 0.26f),
                floatArrayOf(-0.94f, z - 0.38f), floatArrayOf(-0.32f, z - 0.14f)
            ))
        }
        // Веерные грудные плавники
        finXZ(b, 0.02f, arrayOf(
            floatArrayOf(0.30f, 0.42f), floatArrayOf(1.55f, 0.52f), floatArrayOf(1.82f, -0.30f),
            floatArrayOf(1.20f, -0.86f), floatArrayOf(0.30f, -0.40f)
        ))
        finXZ(b, 0.02f, arrayOf(
            floatArrayOf(-0.30f, -0.40f), floatArrayOf(-1.20f, -0.86f), floatArrayOf(-1.82f, -0.30f),
            floatArrayOf(-1.55f, 0.52f), floatArrayOf(-0.30f, 0.42f)
        ))
        finYZ(b, 0f, arrayOf(   // округлый хвост
            floatArrayOf(0.00f, -1.28f), floatArrayOf(0.68f, -1.95f),
            floatArrayOf(0.00f, -2.15f), floatArrayOf(-0.68f, -1.95f)
        ))
        return b.build()
    }

    // ── Окружение ────────────────────────────────────────────────────────────

    fun meatChunk(): MeshData {
        val b = MeshBuilder(120)
        val s = 0.35f
        val p = intArrayOf(
            b.v(-s, -s * 0.70f, -s, 0f, 0f),
            b.v(s * 1.20f, -s * 0.60f, -s * 0.80f, 1f, 0f),
            b.v(s * 0.90f, s * 0.80f, -s * 0.90f, 1f, 1f),
            b.v(-s * 0.80f, s * 1.10f, -s, 0f, 1f),
            b.v(-s * 1.10f, -s * 0.80f, s * 1.20f, 0f, 0f),
            b.v(s, -s * 0.70f, s, 1f, 0f),
            b.v(s * 1.10f, s * 0.90f, s * 0.80f, 1f, 1f),
            b.v(-s * 0.90f, s * 0.70f, s * 1.10f, 0f, 1f)
        )
        b.quad(p[3], p[2], p[1], p[0]); b.quad(p[4], p[5], p[6], p[7])
        b.quad(p[7], p[3], p[0], p[4]); b.quad(p[2], p[6], p[5], p[1])
        b.quad(p[7], p[6], p[2], p[3]); b.quad(p[0], p[1], p[5], p[4])
        return b.build()
    }

    fun flake(): MeshData {
        val b = MeshBuilder(24)
        // Тонкая изогнутая пластинка — хлопья корма именно так и выглядят
        val ids = Array(3) { IntArray(3) }
        for (i in 0..2) for (j in 0..2) {
            val x = (i - 1) * 0.28f
            val z = (j - 1) * 0.28f
            ids[i][j] = b.v(x, (x * x + z * z) * 0.5f, z, i * 0.5f, j * 0.5f)
        }
        for (i in 0 until 2) for (j in 0 until 2) b.quad(ids[i][j], ids[i][j + 1], ids[i + 1][j + 1], ids[i + 1][j])
        return b.build()
    }

    fun sandFloor(halfW: Float, halfD: Float, y: Float, cells: Int = 72): MeshData {
        val b = MeshBuilder((cells + 1) * (cells + 1))
        val ids = Array(cells + 1) { IntArray(cells + 1) }
        for (i in 0..cells) for (j in 0..cells) {
            val fx = i.toFloat() / cells; val fz = j.toFloat() / cells
            val x = -halfW + fx * halfW * 2f
            val z = -halfD + fz * halfD * 2f
            ids[i][j] = b.v(x, y + floorHeight(x, z, halfD), z, fx * 8f, fz * 8f)
        }
        for (i in 0 until cells) for (j in 0 until cells) {
            b.quad(ids[i][j], ids[i][j + 1], ids[i + 1][j + 1], ids[i + 1][j])
        }
        return b.build()
    }

    /** Публичная формула рельефа: рендерер сажает камни и растения ровно на грунт. */
    fun floorHeight(x: Float, z: Float, halfD: Float): Float {
        val h = Noise.fbm(x * 0.08f + 11f, z * 0.08f + 7f, 4) * 1.35f +
                Noise.fbm(x * 0.42f, z * 0.42f, 2) * 0.28f
        val slope = ((-z + halfD) / (halfD * 2f)).pow(1.6f) * 1.5f
        return h + slope
    }

    fun rock(seed: Int, lat: Int = 16, lon: Int = 20): MeshData {
        val b = MeshBuilder(lat * lon)
        val rnd = Random(seed)
        val o1 = rnd.nextFloat() * 20f; val o2 = rnd.nextFloat() * 20f
        val sx = 0.85f + rnd.nextFloat() * 0.5f
        val sy = 0.60f + rnd.nextFloat() * 0.45f
        val sz = 0.85f + rnd.nextFloat() * 0.5f

        val ids = Array(lat + 1) { IntArray(lon + 1) }
        for (i in 0..lat) {
            val th = i.toFloat() / lat * PI.toFloat()
            for (j in 0..lon) {
                val ph = j.toFloat() / lon * 2f * PI.toFloat()
                val nx = sin(th) * cos(ph); val ny = cos(th); val nz = sin(th) * sin(ph)
                var r = 1f
                r += (Noise.value2D(nx * 2.4f + o1, nz * 2.4f + o2) - 0.5f) * 0.55f
                r += (Noise.value2D(nx * 6.5f + o2, ny * 6.5f + o1) - 0.5f) * 0.22f
                r += (Noise.value2D(ny * 15f + o1, nz * 15f + o2) - 0.5f) * 0.09f
                ids[i][j] = b.v(nx * r * sx, ny * r * sy, nz * r * sz, j.toFloat() / lon, i.toFloat() / lat)
            }
        }
        for (i in 0 until lat) for (j in 0 until lon) {
            b.quad(ids[i][j], ids[i][j + 1], ids[i + 1][j + 1], ids[i + 1][j])
        }
        return b.build()
    }

    fun seaweed(segments: Int = 22, twist: Float = 2.4f): MeshData {
        val b = MeshBuilder(segments * 2 + 4)
        val l = IntArray(segments + 1); val r = IntArray(segments + 1)
        for (i in 0..segments) {
            val t = i.toFloat() / segments
            val w = (1f - t * 0.62f) * 0.16f * (0.35f + sin(t * PI.toFloat()) * 0.85f)
            val a = t * twist
            val cx = cos(a) * w; val cz = sin(a) * w
            l[i] = b.v(-cx, t, -cz, 0f, t)
            r[i] = b.v(cx, t, cz, 1f, t)
        }
        for (i in 0 until segments) b.quad(l[i], r[i], r[i + 1], l[i + 1])
        return b.build()
    }

    fun sphere(lat: Int = 14, lon: Int = 18): MeshData {
        val b = MeshBuilder(lat * lon)
        val ids = Array(lat + 1) { IntArray(lon + 1) }
        for (i in 0..lat) {
            val th = i.toFloat() / lat * PI.toFloat()
            for (j in 0..lon) {
                val ph = j.toFloat() / lon * 2f * PI.toFloat()
                ids[i][j] = b.v(sin(th) * cos(ph), cos(th), sin(th) * sin(ph),
                    j.toFloat() / lon, i.toFloat() / lat)
            }
        }
        for (i in 0 until lat) for (j in 0 until lon) {
            b.quad(ids[i][j], ids[i][j + 1], ids[i + 1][j + 1], ids[i + 1][j])
        }
        return b.build()
    }

    fun screenQuad(): MeshData {
        val b = MeshBuilder(4)
        val a = b.v(-1f, -1f, 0f, 0f, 0f); val c = b.v(1f, -1f, 0f, 1f, 0f)
        val d = b.v(1f, 1f, 0f, 1f, 1f);   val e = b.v(-1f, 1f, 0f, 0f, 1f)
        b.quad(a, c, d, e)
        return b.build()
    }

    fun waterPlane(halfW: Float, halfD: Float, cells: Int = 44): MeshData {
        val b = MeshBuilder((cells + 1) * (cells + 1))
        val ids = Array(cells + 1) { IntArray(cells + 1) }
        for (i in 0..cells) for (j in 0..cells) {
            val fx = i.toFloat() / cells; val fz = j.toFloat() / cells
            ids[i][j] = b.v(-halfW + fx * halfW * 2f, 0f, -halfD + fz * halfD * 2f, fx, fz)
        }
        for (i in 0 until cells) for (j in 0 until cells) {
            b.quad(ids[i][j], ids[i][j + 1], ids[i + 1][j + 1], ids[i + 1][j])
        }
        return b.build()
    }
}
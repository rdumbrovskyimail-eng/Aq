package com.aquarium.neon

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import kotlin.math.*
import kotlin.random.Random
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class AquariumRenderer(private val audio: AquariumAudio) : GLSurfaceView.Renderer, EcosystemEvents {

    // ── Матрицы ───────────────────────────────────────────────────────────────
    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProj = FloatArray(16)
    private val invViewProj = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private val nearPt = FloatArray(4)
    private val farPt = FloatArray(4)
    private val nearRes = FloatArray(4)
    private val farRes = FloatArray(4)

    // ── Программы ─────────────────────────────────────────────────────────────
    private var pFish: GlProgram? = null
    private var pFloor: GlProgram? = null
    private var pRock: GlProgram? = null
    private var pPlant: GlProgram? = null
    private var pFood: GlProgram? = null
    private var pBubble: GlProgram? = null
    private var pSurface: GlProgram? = null
    private var pBg: GlProgram? = null
    private var pGodray: GlProgram? = null
    private var pParticle: GlProgram? = null

    // ── Меши ──────────────────────────────────────────────────────────────────
    private val fishMeshes = HashMap<FishForm, GpuMesh>()
    private var mFloor: GpuMesh? = null
    private val mRocks = ArrayList<GpuMesh>(4)
    private var mPlant: GpuMesh? = null
    private var mBubble: GpuMesh? = null
    private var mMeat: GpuMesh? = null
    private var mFlake: GpuMesh? = null
    private var mSurface: GpuMesh? = null
    private var mQuad: GpuMesh? = null
    private var cloudAlpha: PointCloud? = null
    private var cloudAdd: PointCloud? = null

    // ── Сцена ─────────────────────────────────────────────────────────────────
    private val bounds = Bounds(halfX = 16f, halfY = 8.5f, halfZ = 9.5f, floorY = -8.5f)
    private val fishes = ArrayList<FishEntity>(160)
    private val bubbles = ArrayList<Bubble>(180)
    private val plants = ArrayList<Plant>(70)
    private val rocks = ArrayList<Rock>(16)
    private val foods = ArrayList<FoodEntity>(48)
    private val shocks = ArrayList<Shockwave>(8)
    private val pool = ParticlePool(900)
    private var snow: MarineSnow? = null
    private val rnd = Random(20260827)
    private var worldBuilt = false

    // ── Кадр ──────────────────────────────────────────────────────────────────
    private var time = 0f
    private var lastNanos = 0L
    private var accumulator = 0f
    private var vpW = 1f
    private var vpH = 1f
    private var aspect = 1.77f
    private var cameraZ = 26f
    private val cameraPos = Vector3D(0f, 1f, 26f)
    private var driftPhase = 0f
    private var blipTimer = 0f

    private var sortIdx = IntArray(256)
    private var sortKey = FloatArray(256)
    private val hitPoint = Vector3D()

    // ── Палитра освещения по времени суток ────────────────────────────────────
    private val lightDir = floatArrayOf(0.30f, 1f, 0.45f)
    private val lightColor = FloatArray(3)
    private val ambientColor = FloatArray(3)
    private val fogColor = FloatArray(3)
    private val deepColor = FloatArray(3)
    private val shallowColor = FloatArray(3)
    private val rayColor = FloatArray(3)
    private val sandColor = FloatArray(3)
    private var fogDensity = 0.030f

    private companion object {
        const val FIXED_STEP = 1f / 90f

        // Ночь / рассвет / полдень — между ними идёт интерполяция
        val NIGHT_LIGHT = floatArrayOf(0.16f, 0.26f, 0.48f)
        val DAWN_LIGHT = floatArrayOf(0.95f, 0.62f, 0.42f)
        val NOON_LIGHT = floatArrayOf(1.00f, 0.97f, 0.90f)

        val NIGHT_AMB = floatArrayOf(0.020f, 0.045f, 0.085f)
        val DAWN_AMB = floatArrayOf(0.075f, 0.070f, 0.090f)
        val NOON_AMB = floatArrayOf(0.085f, 0.135f, 0.200f)

        val NIGHT_FOG = floatArrayOf(0.004f, 0.016f, 0.038f)
        val DAWN_FOG = floatArrayOf(0.035f, 0.045f, 0.080f)
        val NOON_FOG = floatArrayOf(0.014f, 0.062f, 0.110f)

        val NIGHT_DEEP = floatArrayOf(0.002f, 0.007f, 0.020f)
        val DAWN_DEEP = floatArrayOf(0.014f, 0.018f, 0.040f)
        val NOON_DEEP = floatArrayOf(0.005f, 0.026f, 0.062f)

        val NIGHT_SHAL = floatArrayOf(0.010f, 0.038f, 0.090f)
        val DAWN_SHAL = floatArrayOf(0.120f, 0.090f, 0.130f)
        val NOON_SHAL = floatArrayOf(0.040f, 0.190f, 0.330f)
    }

    // ═══════════════════════════ ЖИЗНЕННЫЙ ЦИКЛ ═══════════════════════════

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // При потере EGL-контекста метод вызывается повторно. Старые хэндлы
        // недействительны, поэтому пересоздаём ресурсы — но состояние экосистемы
        // сохраняем, чтобы рыбы не «перезапускались» после сворачивания.
        releaseGl()

        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)
        GLES30.glFrontFace(GLES30.GL_CCW)
        GLES30.glClearColor(0.004f, 0.02f, 0.05f, 1f)

        pFish     = GlProgram(Shaders.FISH_VS,    Shaders.FISH_FS,     "fish")
        pFloor    = GlProgram(Shaders.WORLD_VS,   Shaders.FLOOR_FS,    "floor")
        pRock     = GlProgram(Shaders.WORLD_VS,   Shaders.ROCK_FS,     "rock")
        pPlant    = GlProgram(Shaders.PLANT_VS,   Shaders.PLANT_FS,    "plant")
        pFood     = GlProgram(Shaders.WORLD_VS,   Shaders.FOOD_FS,     "food")
        pBubble   = GlProgram(Shaders.BUBBLE_VS,  Shaders.BUBBLE_FS,   "bubble")
        pSurface  = GlProgram(Shaders.SURFACE_VS, Shaders.SURFACE_FS,  "surface")
        pBg       = GlProgram(Shaders.BG_VS,      Shaders.BG_FS,       "bg")
        pGodray   = GlProgram(Shaders.BG_VS,      Shaders.GODRAY_FS,   "godray")
        pParticle = GlProgram(Shaders.PARTICLE_VS, Shaders.PARTICLE_FS, "particle")

        for (form in FishForm.values()) fishMeshes[form] = GpuMesh.upload(Geometry.fish(form.id))
        mFloor = GpuMesh.upload(Geometry.sandFloor(bounds.halfX + 8f, bounds.halfZ + 6f, bounds.floorY, 72))
        repeat(4) { mRocks.add(GpuMesh.upload(Geometry.rock(1000 + it * 37))) }
        mPlant = GpuMesh.upload(Geometry.seaweed())
        mBubble = GpuMesh.upload(Geometry.sphere())
        mMeat = GpuMesh.upload(Geometry.meatChunk())
        mFlake = GpuMesh.upload(Geometry.flake())
        mSurface = GpuMesh.upload(Geometry.waterPlane(bounds.halfX + 8f, bounds.halfZ + 6f, 44))
        mQuad = GpuMesh.upload(Geometry.screenQuad())
        cloudAlpha = PointCloud(1400)
        cloudAdd = PointCloud(600)

        if (!worldBuilt) { buildWorld(); worldBuilt = true }
        lastNanos = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        GLES30.glViewport(0, 0, width, height)
        vpW = width.toFloat(); vpH = height.toFloat()
        aspect = vpW / vpH
        updateCamera()
        lastNanos = System.nanoTime()
    }

    /**
     * Камера отъезжает ровно настолько, чтобы аквариум влезал целиком.
     * В версии 4.0 дистанция была захардкожена (z = 27), и в портретной
     * ориентации рыбы уплывали далеко за края экрана.
     */
    private fun updateCamera() {
        val fovY = 52f
        val tanHalf = tan(fovY * 0.5f * PI.toFloat() / 180f)
        val needW = (bounds.halfX + 1.5f) / aspect / tanHalf
        val needH = (bounds.halfY + 1.5f) / tanHalf
        cameraZ = max(needW, needH).coerceIn(18f, 54f)

        val drift = if (Settings.cameraDrift) driftPhase else 0f
        cameraPos.set(
            sin(drift * 0.11f) * 1.6f,
            1.0f + sin(drift * 0.07f) * 0.5f,
            cameraZ + sin(drift * 0.05f) * 0.9f
        )

        Matrix.perspectiveM(proj, 0, fovY, aspect, 0.8f, cameraZ + bounds.halfZ + 34f)
        Matrix.setLookAtM(
            view, 0, cameraPos.x, cameraPos.y, cameraPos.z,
            cameraPos.x * 0.25f, -0.8f, 0f, 0f, 1f, 0f
        )
        Matrix.multiplyMM(viewProj, 0, proj, 0, view, 0)
        Matrix.invertM(invViewProj, 0, viewProj, 0)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        var dt = (now - lastNanos) / 1_000_000_000f
        lastNanos = now
        if (!dt.isFinite() || dt < 0f) dt = 0f
        dt = dt.coerceAtMost(0.10f)      // спайк после сворачивания не должен телепортировать рыб
        time += dt
        driftPhase += dt

        if (Settings.resetRequested) { Settings.resetRequested = false; buildWorld() }
        if (Settings.rebuildRequested) { Settings.rebuildRequested = false; rebuildPopulation() }

        updatePalette()
        if (Settings.cameraDrift) updateCamera()

        accumulator += dt
        var steps = 0
        while (accumulator >= FIXED_STEP && steps < 4) {
            simulate(FIXED_STEP)
            accumulator -= FIXED_STEP
            steps++
        }
        if (steps == 4) accumulator = 0f

        render()
    }

    // ═══════════════════════════ ПАЛИТРА ═══════════════════════════

    private fun updatePalette() {
        // 0 — глухая ночь, 0.5 — полдень, 1 — снова ночь
        val t = Settings.timeOfDay.coerceIn(0f, 1f)
        val day = 1f - abs(t - 0.5f) * 2f          // 0 ночью, 1 в полдень
        if (day < 0.5f) {
            val k = day * 2f
            lerpColor(NIGHT_LIGHT, DAWN_LIGHT, k, lightColor)
            lerpColor(NIGHT_AMB, DAWN_AMB, k, ambientColor)
            lerpColor(NIGHT_FOG, DAWN_FOG, k, fogColor)
            lerpColor(NIGHT_DEEP, DAWN_DEEP, k, deepColor)
            lerpColor(NIGHT_SHAL, DAWN_SHAL, k, shallowColor)
        } else {
            val k = (day - 0.5f) * 2f
            lerpColor(DAWN_LIGHT, NOON_LIGHT, k, lightColor)
            lerpColor(DAWN_AMB, NOON_AMB, k, ambientColor)
            lerpColor(DAWN_FOG, NOON_FOG, k, fogColor)
            lerpColor(DAWN_DEEP, NOON_DEEP, k, deepColor)
            lerpColor(DAWN_SHAL, NOON_SHAL, k, shallowColor)
        }
        // Ночью вода кажется прозрачнее — рассеивать нечего
        fogDensity = lerp(0.021f, 0.033f, day)
        rayColor[0] = lightColor[0] * 0.45f
        rayColor[1] = lightColor[1] * 0.80f
        rayColor[2] = lightColor[2] * 1.05f
        sandColor[0] = lerp(0.10f, 0.235f, day)
        sandColor[1] = lerp(0.11f, 0.205f, day)
        sandColor[2] = lerp(0.15f, 0.150f, day)
        // Солнце ходит по небу
        lightDir[0] = sin((t - 0.5f) * PI.toFloat()) * 0.8f
        lightDir[1] = 0.35f + day * 0.85f
        lightDir[2] = 0.45f
    }

    // ═══════════════════════════ СИМУЛЯЦИЯ ═══════════════════════════

    private fun simulate(dt: Float) {
        for (i in shocks.indices.reversed()) {
            shocks[i].update(dt)
            if (!shocks[i].active) shocks.removeAt(i)
        }
        for (i in foods.indices.reversed()) {
            foods[i].update(dt, bounds.floorY, bounds.halfZ + 6f, pool)
            if (!foods[i].active) foods.removeAt(i)
        }
        for (i in fishes.indices) {
            fishes[i].update(dt, fishes, foods, shocks, pool, bounds, this)
        }

        val active = activeBubbles()
        for (i in 0 until active) {
            if (bubbles[i].update(dt, time, bounds)) {
                // Пузырь лопнул на поверхности — озвучиваем с панорамой по X
                audio.bubble(bubbles[i].radius, (bubbles[i].pos.x / bounds.halfX).coerceIn(-1f, 1f))
            }
        }

        // Редкие одиночные «цоки» в толще воды
        blipTimer -= dt
        if (blipTimer <= 0f) {
            blipTimer = 0.4f + rnd.nextFloat() * 2.2f
            audio.blip((rnd.nextFloat() * 2f - 1f) * 0.8f)
        }

        snow?.update(dt, time, Settings.currentStrength)
        pool.update(dt)
    }

    private fun activeBubbles(): Int =
        (bubbles.size * Settings.bubbleDensity.coerceIn(0f, 1f)).toInt().coerceIn(0, bubbles.size)

    // ═══════════════════════════ ПОСТРОЕНИЕ МИРА ═══════════════════════════

    private fun buildWorld() {
        fishes.clear(); bubbles.clear(); plants.clear(); rocks.clear()
        foods.clear(); shocks.clear()

        rebuildPopulation()

        repeat(180) { i ->
            bubbles.add(
                Bubble(
                    Vector3D(
                        (rnd.nextFloat() - 0.5f) * bounds.halfX * 1.9f,
                        bounds.floorY + rnd.nextFloat() * (bounds.halfY - bounds.floorY),
                        (rnd.nextFloat() - 0.5f) * bounds.halfZ * 1.9f
                    ), 5000 + i
                )
            )
        }

        val palettes = arrayOf(
            floatArrayOf(0.030f, 0.40f, 0.20f) to floatArrayOf(0.30f, 1.00f, 0.55f),
            floatArrayOf(0.050f, 0.30f, 0.34f) to floatArrayOf(0.25f, 0.95f, 0.90f),
            floatArrayOf(0.300f, 0.05f, 0.24f) to floatArrayOf(1.00f, 0.40f, 0.75f),
            floatArrayOf(0.280f, 0.16f, 0.03f) to floatArrayOf(1.00f, 0.72f, 0.22f),
            floatArrayOf(0.100f, 0.08f, 0.36f) to floatArrayOf(0.58f, 0.48f, 1.00f),
            floatArrayOf(0.060f, 0.22f, 0.10f) to floatArrayOf(0.85f, 1.00f, 0.40f)
        )
        val halfD = bounds.halfZ + 6f
        repeat(62) {
            val (col, tip) = palettes[rnd.nextInt(palettes.size)]
            val x = (rnd.nextFloat() - 0.5f) * (bounds.halfX + 6f) * 2f
            val z = -bounds.halfZ - 3f + rnd.nextFloat() * (bounds.halfZ * 2f + 5f)
            plants.add(
                Plant(
                    Vector3D(x, bounds.floorY + Geometry.floorHeight(x, z, halfD) - 0.15f, z),
                    3.0f + rnd.nextFloat() * 6.0f,
                    rnd.nextFloat() * 360f, rnd.nextFloat() * 12f, rnd.nextFloat() * 0.6f,
                    col, tip
                )
            )
        }

        val rockPal = arrayOf(
            floatArrayOf(0.075f, 0.085f, 0.105f) to floatArrayOf(0.10f, 0.80f, 0.55f),
            floatArrayOf(0.095f, 0.080f, 0.070f) to floatArrayOf(0.85f, 0.25f, 0.60f),
            floatArrayOf(0.060f, 0.075f, 0.095f) to floatArrayOf(0.20f, 0.60f, 1.00f),
            floatArrayOf(0.100f, 0.092f, 0.078f) to floatArrayOf(1.00f, 0.62f, 0.18f)
        )
        repeat(14) {
            val (base, moss) = rockPal[rnd.nextInt(rockPal.size)]
            val x = (rnd.nextFloat() - 0.5f) * (bounds.halfX + 5f) * 2f
            val z = -bounds.halfZ - 2f + rnd.nextFloat() * (bounds.halfZ * 2f + 3f)
            val s = 0.9f + rnd.nextFloat() * 2.5f
            rocks.add(
                Rock(
                    Vector3D(x, bounds.floorY + Geometry.floorHeight(x, z, halfD) - s * 0.35f, z),
                    Vector3D(s, s * (0.55f + rnd.nextFloat() * 0.5f), s),
                    rnd.nextFloat() * 360f, rnd.nextInt(4), base, moss
                )
            )
        }

        snow = MarineSnow(420, bounds, 777)
        ensureSortCapacity()
    }

    /** Пересобирает только рыб — вызывается при смене плотности популяции. */
    private fun rebuildPopulation() {
        fishes.clear()
        var seed = 1
        val density = Settings.fishDensity.coerceIn(0.25f, 2.2f)
        for (sp in FishCatalog.SPECIES) {
            val n = max(1, (sp.baseCount * density).roundToInt())
            repeat(n) {
                fishes.add(
                    FishEntity(
                        sp,
                        Vector3D(
                            (rnd.nextFloat() - 0.5f) * bounds.halfX * 1.7f,
                            (sp.preferredY + (rnd.nextFloat() - 0.5f) * 3f)
                                .coerceIn(bounds.floorY + 1.5f, bounds.halfY - 1.5f),
                            (rnd.nextFloat() - 0.5f) * bounds.halfZ * 1.6f
                        ),
                        seed++
                    )
                )
            }
        }
        // Соседние рыбы одного вида делят юниформы — меньше вызовов в GL
        fishes.sortBy { FishCatalog.SPECIES.indexOf(it.species) }
        ensureSortCapacity()
    }

    private fun ensureSortCapacity() {
        val need = max(max(bubbles.size, fishes.size), 256)
        if (sortIdx.size < need) { sortIdx = IntArray(need); sortKey = FloatArray(need) }
    }

    // ═══════════════════════════ СОБЫТИЯ ЭКОСИСТЕМЫ ═══════════════════════════

    override fun onBite(pos: Vector3D, meat: Boolean) {
        audio.blip(panOf(pos))
    }

    override fun onStrike(pos: Vector3D) {
        audio.knock(0.35f, panOf(pos))
        pool.burstCavitation(pos.x, pos.y, pos.z, 8)
    }

    override fun onKill(pos: Vector3D, size: Float) {
        // От погибшей рыбы остаётся падаль: её доедают остальные хищники
        if (foods.size < 40) {
            foods.add(FoodEntity(pos.copy(), FoodType.RAW_MEAT, rnd.nextInt()))
        }
        pool.burstBlood(pos.x, pos.y, pos.z, 40)
        audio.splash(0.4f, panOf(pos))
    }

    private fun panOf(p: Vector3D) = (p.x / bounds.halfX).coerceIn(-1f, 1f)

    // ═══════════════════════════ ВВОД ═══════════════════════════

    /** Экранная точка -> луч в мире -> точка на переднем стекле. */
    fun onTap(screenX: Float, screenY: Float) {
        if (vpW <= 1f || vpH <= 1f) return
        val ndcX = 2f * screenX / vpW - 1f
        val ndcY = 1f - 2f * screenY / vpH

        nearPt[0] = ndcX; nearPt[1] = ndcY; nearPt[2] = -1f; nearPt[3] = 1f
        farPt[0] = ndcX; farPt[1] = ndcY; farPt[2] = 1f; farPt[3] = 1f
        Matrix.multiplyMV(nearRes, 0, invViewProj, 0, nearPt, 0)
        Matrix.multiplyMV(farRes, 0, invViewProj, 0, farPt, 0)
        if (abs(nearRes[3]) < 1e-6f || abs(farRes[3]) < 1e-6f) return

        val o = Vector3D(nearRes[0] / nearRes[3], nearRes[1] / nearRes[3], nearRes[2] / nearRes[3])
        val d = Vector3D(farRes[0] / farRes[3], farRes[1] / farRes[3], farRes[2] / farRes[3])
            .sub(o).normalize()
        val ray = Ray3D(o, d)
        if (!ray.intersectPlaneZ(bounds.halfZ - 1.5f, hitPoint)) return

        hitPoint.x = hitPoint.x.coerceIn(-bounds.halfX, bounds.halfX)
        hitPoint.y = hitPoint.y.coerceIn(bounds.floorY + 1f, bounds.halfY - 0.5f)

        when (Settings.tapMode) {
            Settings.TapMode.KNOCK -> {
                shocks.add(Shockwave(hitPoint.copy()))
                pool.burstCavitation(hitPoint.x, hitPoint.y, hitPoint.z, 46)
                audio.knock(1f, panOf(hitPoint))
            }
            Settings.TapMode.FLAKES -> {
                // Хлопья сыплются с поверхности россыпью
                repeat(7) {
                    if (foods.size >= 44) return@repeat
                    foods.add(
                        FoodEntity(
                            Vector3D(
                                hitPoint.x + (rnd.nextFloat() - 0.5f) * 2.4f,
                                bounds.halfY - 0.4f,
                                hitPoint.z - rnd.nextFloat() * 4f
                            ), FoodType.FLAKES, rnd.nextInt()
                        )
                    )
                }
                audio.splash(0.35f, panOf(hitPoint))
            }
            Settings.TapMode.MEAT -> {
                if (foods.size < 44) {
                    foods.add(
                        FoodEntity(
                            Vector3D(hitPoint.x, bounds.halfY - 0.5f, hitPoint.z - 2f),
                            FoodType.RAW_MEAT, rnd.nextInt()
                        )
                    )
                }
                pool.burstBlood(hitPoint.x, bounds.halfY - 0.6f, hitPoint.z - 2f, 18)
                audio.splash(0.85f, panOf(hitPoint))
            }
        }
    }

    // ═══════════════════════════ ОТРИСОВКА ═══════════════════════════

    private fun render() {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        drawBackground()

        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)

        drawFloor()
        drawRocks()
        GLES30.glDisable(GLES30.GL_CULL_FACE)   // листья, плавники и щупальца двусторонние
        drawPlants()
        drawFood()
        drawFishes(transparent = false)

        // Прозрачные объекты: сортировка от дальних к ближним, без записи глубины
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)

        drawFishes(transparent = true)
        drawBubbles()
        drawWaterSurface()
        drawAlphaParticles()

        // Аддитивные эффекты
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        drawAdditiveParticles()
        drawGodRays()

        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glBindVertexArray(0)
    }

    private fun common(p: GlProgram) {
        p.f("uTime", time)
        p.v3("uCameraPos", cameraPos.x, cameraPos.y, cameraPos.z)
        p.v3("uLightDir", lightDir)
        p.v3("uLightColor", lightColor)
        p.v3("uAmbientColor", ambientColor)
        p.v3("uFogColor", fogColor)
        p.f("uFogDensity", fogDensity)
    }

    private fun drawBackground() {
        val p = pBg ?: return; val q = mQuad ?: return
        if (!p.isValid) return
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)
        p.use()
        p.f("uTime", time)
        p.v3("uDeepColor", deepColor)
        p.v3("uShallowColor", shallowColor)
        q.draw()
    }

    private fun drawFloor() {
        val p = pFloor ?: return; val m = mFloor ?: return
        if (!p.isValid) return
        p.use()
        Matrix.setIdentityM(model, 0)
        Matrix.multiplyMM(mvp, 0, viewProj, 0, model, 0)
        p.mat4("uMVP", mvp); p.mat4("uModel", model)
        common(p)
        p.v3("uSandColor", sandColor)
        m.draw()
    }

    private fun drawRocks() {
        val p = pRock ?: return
        if (!p.isValid || mRocks.isEmpty()) return
        p.use(); common(p)
        for (r in rocks) {
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, r.pos.x, r.pos.y, r.pos.z)
            Matrix.rotateM(model, 0, r.yawDeg, 0f, 1f, 0f)
            Matrix.scaleM(model, 0, r.scale.x, r.scale.y, r.scale.z)
            Matrix.multiplyMM(mvp, 0, viewProj, 0, model, 0)
            p.mat4("uMVP", mvp); p.mat4("uModel", model)
            p.v3("uBaseColor", r.base); p.v3("uMossColor", r.moss)
            mRocks[r.meshIndex % mRocks.size].draw()
        }
    }

    private fun drawPlants() {
        val p = pPlant ?: return; val m = mPlant ?: return
        if (!p.isValid) return
        p.use(); common(p)
        p.f("uCurrent", Settings.currentStrength.coerceIn(0f, 1.5f))
        for (pl in plants) {
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, pl.pos.x, pl.pos.y, pl.pos.z)
            Matrix.rotateM(model, 0, pl.yawDeg, 0f, 1f, 0f)
            Matrix.scaleM(model, 0, 1f, pl.height, 1f)
            Matrix.multiplyMM(mvp, 0, viewProj, 0, model, 0)
            p.mat4("uMVP", mvp); p.mat4("uModel", model)
            p.f("uPhase", pl.phase); p.f("uStiffness", pl.stiffness)
            p.v3("uColor", pl.color); p.v3("uTipColor", pl.tipColor)
            m.draw()
        }
    }

    private fun drawFood() {
        val p = pFood ?: return
        if (!p.isValid) return
        p.use(); common(p)
        for (f in foods) {
            if (!f.active) continue
            val meat = f.type == FoodType.RAW_MEAT
            val mesh = (if (meat) mMeat else mFlake) ?: continue
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, f.pos.x, f.pos.y, f.pos.z)
            Matrix.rotateM(model, 0, f.spin, 0.4f, 1f, 0.3f)
            val s = if (meat) 0.85f else 0.30f
            Matrix.scaleM(model, 0, s, s, s)
            Matrix.multiplyMM(mvp, 0, viewProj, 0, model, 0)
            p.mat4("uMVP", mvp); p.mat4("uModel", model)
            p.i("uIsMeat", if (meat) 1 else 0)
            if (meat) p.v3("uBaseColor", 0.55f, 0.045f, 0.055f)
            else p.v3("uBaseColor", 0.42f, 0.30f, 0.08f)
            mesh.draw()
        }
    }

    private fun drawFishes(transparent: Boolean) {
        val p = pFish ?: return
        if (!p.isValid) return
        p.use(); common(p)

        if (!transparent) {
            var last: Species? = null
            for (f in fishes) {
                if (!f.isAlive || f.species.opacity < 0.999f) continue
                if (f.species !== last) { applySpecies(p, f.species); last = f.species }
                drawFish(p, f)
            }
        } else {
            var n = 0
            for (i in fishes.indices) {
                val f = fishes[i]
                if (!f.isAlive || f.species.opacity >= 0.999f) continue
                sortIdx[n] = i; sortKey[n] = f.position.z; n++
            }
            sortAscending(n)
            var last: Species? = null
            for (k in 0 until n) {
                val f = fishes[sortIdx[k]]
                if (f.species !== last) { applySpecies(p, f.species); last = f.species }
                drawFish(p, f)
            }
        }
    }

    private fun applySpecies(p: GlProgram, sp: Species) {
        p.v3("uPrimary", sp.primary)
        p.v3("uGlow", sp.glow)
        p.v3("uAccent", sp.accent)
        p.f("uStripeFreq", sp.stripeFreq)
        p.f("uStripeSharp", sp.stripeSharp)
        p.f("uMetallic", sp.metallic)
        p.f("uOpacity", sp.opacity)
        p.f("uWaveAmp", sp.waveAmp)
        p.f("uWaveFreq", sp.waveFreq)
        p.f("uBodyLen", Geometry.bodyLength(sp.form.id))
        p.f("uHeadZ", Geometry.headZ(sp.form.id))
        p.f("uSwimSpeed", sp.tailBeat)
        p.i("uPattern", sp.pattern.id)
        p.i("uForm", sp.form.id)
        val e = Geometry.eye(sp.form.id)
        p.v4("uEye", e[0], e[1], e[2], e[3])
    }

    private fun drawFish(p: GlProgram, f: FishEntity) {
        val mesh = fishMeshes[f.species.form] ?: return
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, f.position.x, f.position.y, f.position.z)
        Matrix.rotateM(model, 0, f.yaw, 0f, 1f, 0f)
        Matrix.rotateM(model, 0, f.pitch, 1f, 0f, 0f)
        Matrix.rotateM(model, 0, f.roll, 0f, 0f, 1f)
        Matrix.scaleM(model, 0, f.species.scale, f.species.scale, f.species.scale)
        Matrix.multiplyMM(mvp, 0, viewProj, 0, model, 0)
        p.mat4("uMVP", mvp); p.mat4("uModel", model)
        // Фаза волны у каждой рыбы своя, иначе вся стая машет хвостом как метроном
        p.f("uTime", f.swimClock)
        p.f("uPanic", f.panic)
        p.f("uBloodTint", f.bloodTint)
        p.f("uFinTension", f.finTension)
        mesh.draw()
    }

    private fun drawBubbles() {
        val p = pBubble ?: return; val m = mBubble ?: return
        if (!p.isValid) return
        p.use(); common(p)
        val n = activeBubbles()
        for (i in 0 until n) { sortIdx[i] = i; sortKey[i] = bubbles[i].pos.z }
        sortAscending(n)
        for (k in 0 until n) {
            val b = bubbles[sortIdx[k]]
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, b.pos.x, b.pos.y, b.pos.z)
            Matrix.scaleM(model, 0, b.radius, b.radius, b.radius)
            Matrix.multiplyMM(mvp, 0, viewProj, 0, model, 0)
            p.mat4("uMVP", mvp); p.mat4("uModel", model)
            p.f("uWobble", b.wobblePhase)
            m.draw()
        }
    }

    private fun drawWaterSurface() {
        val p = pSurface ?: return; val m = mSurface ?: return
        if (!p.isValid) return
        p.use(); common(p)
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, 0f, bounds.halfY + 0.6f, 0f)
        Matrix.multiplyMM(mvp, 0, viewProj, 0, model, 0)
        p.mat4("uMVP", mvp); p.mat4("uModel", model)
        m.draw()
    }

    private fun drawAlphaParticles() {
        val p = pParticle ?: return; val c = cloudAlpha ?: return
        if (!p.isValid) return
        p.use()
        p.mat4("uMVP", viewProj)
        p.f("uViewportH", vpH * 0.42f)
        p.i("uSoftCore", 0)
        val add = cloudAdd ?: return
        // Один проход по пулу наполняет оба буфера: альфа-облако (кровь, песок,
        // морской снег) и аддитивное (кавитация). Второй проход был бы вдвое дороже.
        c.begin()
        add.begin()
        pool.emit(c, add)
        if (Settings.marineSnow) snow?.emit(c)
        c.flush()
    }

    private fun drawAdditiveParticles() {
        val p = pParticle ?: return; val c = cloudAdd ?: return
        if (!p.isValid) return
        p.use()
        p.mat4("uMVP", viewProj)
        p.f("uViewportH", vpH * 0.42f)
        p.i("uSoftCore", 1)
        c.flush()      // буфер наполнен в drawAlphaParticles — здесь только выводим
    }

    private fun drawGodRays() {
        val p = pGodray ?: return; val q = mQuad ?: return
        if (!p.isValid) return
        val intensity = Settings.godRayIntensity
        if (intensity <= 0.005f) return
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        p.use()
        p.f("uTime", time)
        p.v3("uRayColor", rayColor)
        p.f("uIntensity", intensity)
        q.draw()
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    /** Сортировка вставками: между кадрами массив почти упорядочен, поэтому ~O(n). */
    private fun sortAscending(n: Int) {
        for (i in 1 until n) {
            val ki = sortKey[i]; val vi = sortIdx[i]
            var j = i - 1
            while (j >= 0 && sortKey[j] > ki) {
                sortKey[j + 1] = sortKey[j]; sortIdx[j + 1] = sortIdx[j]; j--
            }
            sortKey[j + 1] = ki; sortIdx[j + 1] = vi
        }
    }

    // ═══════════════════════════ РЕСУРСЫ ═══════════════════════════

    private fun releaseGl() {
        listOf(pFish, pFloor, pRock, pPlant, pFood, pBubble, pSurface, pBg, pGodray, pParticle)
            .forEach { it?.release() }
        pFish = null; pFloor = null; pRock = null; pPlant = null; pFood = null
        pBubble = null; pSurface = null; pBg = null; pGodray = null; pParticle = null

        fishMeshes.values.forEach { it.release() }; fishMeshes.clear()
        mRocks.forEach { it.release() }; mRocks.clear()
        listOf(mFloor, mPlant, mBubble, mMeat, mFlake, mSurface, mQuad).forEach { it?.release() }
        mFloor = null; mPlant = null; mBubble = null; mMeat = null
        mFlake = null; mSurface = null; mQuad = null
        cloudAlpha?.release(); cloudAdd?.release()
        cloudAlpha = null; cloudAdd = null
    }
}
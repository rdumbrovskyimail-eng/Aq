package com.aquarium.neon

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlin.math.*
import kotlin.random.Random

/**
 * Процедурный звуковой движок. Ни одного файла с ресурсами — весь звук
 * синтезируется в реальном времени, поэтому APK не растёт и сборка через
 * GitHub Actions не требует бинарных ассетов.
 *
 * Один стерео AudioTrack в режиме STREAM. Отдельный поток смешивает
 * фоновую подложку и голоса из пула фиксированного размера. AudioTrack.write
 * блокируется, когда буфер заполнен, — это и задаёт темп без таймеров.
 *
 * Модель звука пузыря — резонанс Миннарта: пузырь радиуса r звучит на частоте
 * f0 ≈ 3.26 / d (Гц·м) с быстрым затуханием и повышением тона по мере схлопывания
 * оболочки. Именно поэтому мелкие пузырьки «цокают» высоко, а крупные — «булькают».
 */
class AquariumAudio {

    private companion object {
        const val SR = 44100
        const val CHUNK = 512          // кадров за одну итерацию микшера
        const val MAX_VOICES = 28
        const val TWO_PI = 6.283185307f
    }

    private enum class Kind { BUBBLE, KNOCK, SPLASH, POP }

    private class Voice {
        @JvmField var active = false
        @JvmField var kind = Kind.BUBBLE
        @JvmField var t = 0f          // время от старта голоса, секунды
        @JvmField var dur = 0f
        @JvmField var phase = 0f
        @JvmField var phase2 = 0f
        @JvmField var f0 = 0f
        @JvmField var chirp = 0f
        @JvmField var decay = 0f
        @JvmField var amp = 0f
        @JvmField var panL = 0.7f
        @JvmField var panR = 0.7f
        @JvmField var noiseState = 0f
    }

    private val voices = Array(MAX_VOICES) { Voice() }
    private val lock = Any()
    private val rnd = Random(4711)

    private var track: AudioTrack? = null
    private var mixThread: Thread? = null
    @Volatile private var running = false

    // Состояние фоновой подложки
    private var brown = 0f
    private var lp1 = 0f
    private var lp2 = 0f
    private var ambPhase = 0f
    private var swellPhase = 0f
    private var hissLp = 0f

    private val mixL = FloatArray(CHUNK)
    private val mixR = FloatArray(CHUNK)
    private val pcm = ShortArray(CHUNK * 2)

    // ─────────────────────────── Жизненный цикл ───────────────────────────

    fun start() {
        if (running) return
        val minBuf = AudioTrack.getMinBufferSize(
            SR, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) { Log.w(TAG, "AudioTrack недоступен на этом устройстве"); return }
        val bufBytes = max(minBuf, CHUNK * 2 * 2 * 4)

        val t = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SR)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_MUSIC, SR, AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT, bufBytes, AudioTrack.MODE_STREAM
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось создать AudioTrack", e); return
        }

        if (t.state != AudioTrack.STATE_INITIALIZED) { t.release(); return }

        track = t
        running = true
        t.play()
        mixThread = Thread({ mixLoop(t) }, "AquariumAudio").apply {
            priority = Thread.NORM_PRIORITY + 1
            start()
        }
    }

    fun stop() {
        running = false
        mixThread?.let { try { it.join(500) } catch (_: InterruptedException) {} }
        mixThread = null
        track?.let {
            try { it.pause(); it.flush(); it.stop() } catch (_: IllegalStateException) {}
            it.release()
        }
        track = null
        synchronized(lock) { for (v in voices) v.active = false }
    }

    // ─────────────────────────── Триггеры ───────────────────────────

    /**
     * Лопнувший на поверхности пузырь.
     * @param radius радиус в мировых единицах (0.05..0.35)
     * @param pan    -1 слева .. +1 справа
     */
    fun bubble(radius: Float, pan: Float) {
        if (!Settings.soundEnabled) return
        // Мировые единицы -> миллиметры реального пузыря
        val dMm = (radius * 8f).coerceIn(0.35f, 3.0f) * 2f
        val f0 = (3260f / dMm).coerceIn(420f, 3400f)
        acquire()?.let { v ->
            synchronized(lock) {
                v.kind = Kind.BUBBLE
                v.f0 = f0
                v.chirp = 1.6f + rnd.nextFloat() * 1.2f     // повышение тона при схлопывании
                v.decay = 42f + rnd.nextFloat() * 34f
                v.dur = 0.09f
                v.amp = (0.10f + radius * 0.55f) * (0.6f + rnd.nextFloat() * 0.5f)
                v.t = 0f; v.phase = 0f; v.phase2 = 0f
                setPan(v, pan)
                v.active = true
            }
        }
    }

    /** Тихий «цок» одиночного пузырька в толще воды. */
    fun blip(pan: Float) {
        if (!Settings.soundEnabled) return
        acquire()?.let { v ->
            synchronized(lock) {
                v.kind = Kind.POP
                v.f0 = 900f + rnd.nextFloat() * 2400f
                v.chirp = 2.4f
                v.decay = 90f + rnd.nextFloat() * 60f
                v.dur = 0.05f
                v.amp = 0.05f + rnd.nextFloat() * 0.07f
                v.t = 0f; v.phase = 0f; v.phase2 = 0f
                setPan(v, pan)
                v.active = true
            }
        }
    }

    /** Стук костяшкой по стеклу: две резонансные моды плюс шумовой транзиент. */
    fun knock(strength: Float, pan: Float) {
        if (!Settings.soundEnabled) return
        acquire()?.let { v ->
            synchronized(lock) {
                v.kind = Kind.KNOCK
                v.f0 = 132f + rnd.nextFloat() * 34f
                v.chirp = 0f
                v.decay = 11f
                v.dur = 0.55f
                v.amp = (0.30f + strength * 0.45f)
                v.t = 0f; v.phase = 0f; v.phase2 = 0f
                v.noiseState = 0f
                setPan(v, pan)
                v.active = true
            }
        }
    }

    /** Всплеск от корма, упавшего на поверхность. */
    fun splash(strength: Float, pan: Float) {
        if (!Settings.soundEnabled) return
        acquire()?.let { v ->
            synchronized(lock) {
                v.kind = Kind.SPLASH
                v.f0 = 0f
                v.decay = 13f
                v.dur = 0.42f
                v.amp = 0.16f + strength * 0.22f
                v.t = 0f; v.phase = 0f; v.noiseState = 0f
                setPan(v, pan)
                v.active = true
            }
        }
    }

    private fun setPan(v: Voice, pan: Float) {
        val p = pan.coerceIn(-1f, 1f)
        // Равномощное панорамирование: суммарная энергия не проседает по центру
        val a = (p + 1f) * 0.25f * PI.toFloat()
        v.panL = cos(a); v.panR = sin(a)
    }

    private fun acquire(): Voice? {
        synchronized(lock) {
            for (v in voices) if (!v.active) return v
            // Пул занят — вытесняем самый старый голос, чтобы не терять свежие события
            var oldest = voices[0]
            for (v in voices) if (v.t > oldest.t) oldest = v
            return oldest
        }
    }

    // ─────────────────────────── Микшер ───────────────────────────

    private fun mixLoop(t: AudioTrack) {
        val dt = 1f / SR
        while (running) {
            java.util.Arrays.fill(mixL, 0f)
            java.util.Arrays.fill(mixR, 0f)

            renderAmbience(dt)
            renderVoices(dt)

            val g = if (Settings.soundEnabled) Settings.soundVolume.coerceIn(0f, 1f) else 0f
            for (i in 0 until CHUNK) {
                // Мягкое ограничение вместо жёсткого клиппинга
                val l = softClip(mixL[i] * g)
                val r = softClip(mixR[i] * g)
                pcm[i * 2] = (l * 32000f).toInt().toShort()
                pcm[i * 2 + 1] = (r * 32000f).toInt().toShort()
            }

            try {
                if (t.write(pcm, 0, pcm.size) < 0) break
            } catch (e: IllegalStateException) {
                break
            }
        }
    }

    private fun softClip(x: Float): Float = x / (1f + abs(x) * 0.7f)

    /**
     * Подложка: коричневый шум через два полюса нижних частот даёт глухой
     * подводный гул, поверх — медленная «дышащая» модуляция и лёгкое шипение
     * компрессора, как в реальном аквариуме с фильтром.
     */
    private fun renderAmbience(dt: Float) {
        val depth = 0.055f + (1f - Settings.timeOfDay) * 0.012f
        for (i in 0 until CHUNK) {
            val white = rnd.nextFloat() * 2f - 1f
            brown = (brown + white * 0.035f) * 0.996f      // интегратор с утечкой
            lp1 += (brown - lp1) * 0.055f
            lp2 += (lp1 - lp2) * 0.055f

            swellPhase += dt * 0.11f
            val swell = 0.72f + 0.28f * sin(swellPhase * TWO_PI)

            ambPhase += dt * 47f
            if (ambPhase > TWO_PI) ambPhase -= TWO_PI
            val rumble = sin(ambPhase) * 0.02f

            hissLp += ((rnd.nextFloat() * 2f - 1f) - hissLp) * 0.30f
            val hiss = hissLp * 0.011f

            val s = (lp2 * 5.5f + rumble + hiss) * depth * swell
            // Слабая декорреляция каналов создаёт ощущение объёма воды
            mixL[i] += s
            mixR[i] += s * 0.94f + lp1 * depth * 0.35f
        }
    }

    private fun renderVoices(dt: Float) {
        synchronized(lock) {
            for (v in voices) {
                if (!v.active) continue
                for (i in 0 until CHUNK) {
                    if (v.t >= v.dur) { v.active = false; break }
                    val env: Float
                    val s: Float

                    when (v.kind) {
                        Kind.BUBBLE, Kind.POP -> {
                            val f = v.f0 * (1f + v.chirp * v.t * 10f)
                            v.phase += TWO_PI * f * dt
                            if (v.phase > TWO_PI) v.phase -= TWO_PI
                            env = exp(-v.decay * v.t)
                            // Небольшая вторая гармоника даёт «водянистость» тону
                            s = (sin(v.phase) * 0.85f + sin(v.phase * 2f) * 0.15f) * env * v.amp
                        }
                        Kind.KNOCK -> {
                            v.phase += TWO_PI * v.f0 * dt
                            v.phase2 += TWO_PI * (v.f0 * 4.7f) * dt
                            if (v.phase > TWO_PI) v.phase -= TWO_PI
                            if (v.phase2 > TWO_PI) v.phase2 -= TWO_PI
                            env = exp(-v.decay * v.t)
                            val attack = exp(-260f * v.t)            // щелчок в момент удара
                            v.noiseState += ((rnd.nextFloat() * 2f - 1f) - v.noiseState) * 0.5f
                            s = (sin(v.phase) * 0.72f + sin(v.phase2) * 0.20f * exp(-38f * v.t) +
                                 v.noiseState * attack * 0.55f) * env * v.amp
                        }
                        Kind.SPLASH -> {
                            v.noiseState += ((rnd.nextFloat() * 2f - 1f) - v.noiseState) * 0.22f
                            env = exp(-v.decay * v.t) * (1f - exp(-160f * v.t))
                            s = v.noiseState * env * v.amp
                        }
                    }

                    mixL[i] += s * v.panL
                    mixR[i] += s * v.panR
                    v.t += dt
                }
            }
        }
    }
}
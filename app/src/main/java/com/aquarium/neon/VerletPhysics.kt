package com.aquarium.neon

import android.graphics.*
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.*
import kotlin.random.Random

class FoodParticle(
    var position: Vector2D,
    var velocity: Vector2D = Vector2D((Random.nextFloat() - 0.5f) * 0.5f, Random.nextFloat() * 0.7f + 0.5f),
    val color: Int = if (Random.nextBoolean()) Color.parseColor("#FF9800") else Color.parseColor("#E91E63"),
    val radius: Float = Random.nextFloat() * 3f + 4f
) {
    var isEaten = false

    fun update(accX: Float, accY: Float, bottomY: Float) {
        if (position.y < bottomY - radius) {
            velocity.x = velocity.x * 0.96f + (accX + sin(position.y * 0.04f) * 0.3f) * 0.04f
            velocity.y = (velocity.y * 0.98f + 0.45f + accY * 0.15f).coerceAtMost(2.2f)
            position.add(velocity)
        } else {
            velocity.set(0f, 0f)
            position.y = bottomY - radius
        }
    }

    fun draw(canvas: Canvas, paint: Paint) {
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawCircle(position.x, position.y, radius, paint)
        paint.color = Color.argb(160, 255, 255, 255)
        canvas.drawCircle(position.x - radius * 0.3f, position.y - radius * 0.3f, radius * 0.3f, paint)
    }
}

class PlanktonParticle(
    var position: Vector2D,
    val radius: Float = Random.nextFloat() * 2.2f + 1f,
    val depth: Float = Random.nextFloat()
) {
    private var phase = Random.nextFloat() * 10f

    fun update(w: Float, h: Float, accX: Float, accY: Float) {
        phase += 0.02f
        val depthSpeed = (0.3f + depth * 0.7f)
        position.x += sin(phase) * 0.5f * depthSpeed + accX * 0.3f
        position.y -= 0.3f * depthSpeed - accY * 0.1f

        if (position.y < -10f) position.y = h + 10f
        if (position.x < -10f) position.x = w + 10f
        if (position.x > w + 10f) position.x = -10f
    }

    fun draw(canvas: Canvas, paint: Paint) {
        paint.style = Paint.Style.FILL
        val alpha = ((sin(phase) * 0.35f + 0.55f) * (100 + depth * 155)).toInt().coerceIn(0, 255)
        paint.color = Color.argb(alpha, 0, 229, 255)
        canvas.drawCircle(position.x, position.y, radius * (0.6f + depth * 0.4f), paint)
    }
}

class VolumetricLightShafts {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()

    fun draw(canvas: Canvas, w: Float, h: Float, timeSec: Float, intensityMultiplier: Float = 1.0f) {
        val shaftCount = 8
        for (i in 0 until shaftCount) {
            val baseStartX = (w / (shaftCount - 1)) * i
            val shift = sin(timeSec * 0.6f + i * 1.4f) * 70f
            val topX1 = baseStartX + shift - 40f
            val topX2 = baseStartX + shift + 80f
            val bottomX1 = topX1 + 180f
            val bottomX2 = topX2 + 250f

            val alpha = ((sin(timeSec * 1.1f + i * 1.9f) * 0.035f + 0.065f) * intensityMultiplier * 255).toInt().coerceIn(0, 80)

            path.reset()
            path.moveTo(topX1, 0f)
            path.lineTo(topX2, 0f)
            path.lineTo(bottomX2, h)
            path.lineTo(bottomX1, h)
            path.close()

            val lg = LinearGradient(
                (topX1 + topX2) / 2f, 0f,
                (bottomX1 + bottomX2) / 2f, h,
                intArrayOf(Color.argb(alpha, 180, 245, 255), Color.argb(0, 0, 80, 180)),
                null, Shader.TileMode.CLAMP
            )
            paint.shader = lg
            canvas.drawPath(path, paint)
        }
        paint.shader = null
    }
}

class SeabedCaustics {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()

    fun draw(canvas: Canvas, w: Float, h: Float, timeSec: Float, colorAlpha: Int = 30) {
        paint.color = Color.argb(colorAlpha, 0, 230, 255)
        paint.strokeWidth = 3f

        val seabedY = h - 85f
        val cols = 16
        val colWidth = w / cols

        path.reset()
        for (i in 0..cols) {
            val x = i * colWidth
            val wave1 = sin(timeSec * 2.0f + i * 0.6f) * 20f
            val wave2 = cos(timeSec * 1.4f + i * 1.1f) * 15f
            val y1 = seabedY + wave1
            val y2 = seabedY + wave2 + 30f

            if (i == 0) {
                path.moveTo(x, y1)
            } else {
                val prevX = (i - 1) * colWidth
                path.quadTo((prevX + x) / 2f, y2, x, y1)
            }
        }
        canvas.drawPath(path, paint)
    }
}

class ProceduralAudioEngine {
    private var audioTrack: AudioTrack? = null
    @Volatile private var isPlaying = false
    private var synthThread: Thread? = null

    fun start() {
        if (isPlaying) return
        try {
            val sampleRate = 22050
            val minBufSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufSize.coerceAtLeast(4096))
                .build()

            audioTrack?.play()
            isPlaying = true

            synthThread = Thread {
                val buffer = ShortArray(1024)
                var phase = 0.0
                val sampleRateF = sampleRate.toDouble()

                while (isPlaying) {
                    for (i in buffer.indices) {
                        phase += 2.0 * Math.PI * 40.0 / sampleRateF
                        if (phase > 2.0 * Math.PI) phase -= 2.0 * Math.PI
                        val sineSample = sin(phase) * 450.0
                        val noiseSample = (Random.nextDouble() - 0.5) * 300.0
                        buffer[i] = (sineSample + noiseSample).toInt().coerceIn(-32768, 32767).toShort()
                    }
                    audioTrack?.write(buffer, 0, buffer.size)
                }
            }.apply { start() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        isPlaying = false
        try {
            synthThread?.join(250)
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
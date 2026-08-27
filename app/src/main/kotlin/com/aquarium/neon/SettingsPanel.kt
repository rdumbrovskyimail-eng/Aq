package com.aquarium.neon

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import kotlin.math.roundToInt

/**
 * Панель настроек, собранная кодом без XML-ресурсов: проект остаётся
 * однофайловым по ресурсам и собирается в CI без бинарных ассетов.
 */
@SuppressLint("ViewConstructor")
class SettingsPanel(context: Context, private val onClose: () -> Unit) : FrameLayout(context) {

    private val dp = context.resources.displayMetrics.density
    private fun d(v: Float) = (v * dp).roundToInt()

    private companion object {
        const val ACCENT = 0xFF3FD8FF.toInt()
        const val TEXT = 0xFFE8F4FA.toInt()
        const val DIM = 0xFF8FA8B8.toInt()
        const val PANEL = 0xF00A1520.toInt()
        const val CHIP_OFF = 0x2233566B
        const val CHIP_ON = 0x553FD8FF
    }

    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val modeChips = ArrayList<TextView>(3)

    init {
        // Затемнение позади панели, закрывает по тапу мимо
        setBackgroundColor(0xA0000814.toInt())
        isClickable = true
        setOnClickListener { onClose() }

        val card = ScrollView(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = d(22f).toFloat()
                setColor(PANEL)
                setStroke(d(1f), 0x553FD8FF)
            }
            isClickable = true
            setOnClickListener { }        // перехватываем тап, чтобы не закрыть панель
            addView(content)
        }
        content.setPadding(d(20f), d(18f), d(20f), d(22f))

        val lp = LayoutParams(
            resources.displayMetrics.widthPixels.coerceAtMost(d(420f)),
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
            marginStart = d(14f); marginEnd = d(14f)
            topMargin = d(28f); bottomMargin = d(28f)
        }
        addView(card, lp)

        buildContent()
    }

    private fun buildContent() {
        header("Аквариум")

        content.addView(tapModeRow())
        hint("Одиночный тап по экрану выполняет выбранное действие")

        section("Живность")
        slider("Плотность популяции", Settings.fishDensity, 0.25f, 2.0f, { v ->
            Settings.fishDensity = v
            "${(v * 100).roundToInt()} %"
        }, onRelease = { Settings.requestRebuild() })

        toggle("Хищники охотятся", Settings.predatorsEnabled) { on ->
            Settings.predatorsEnabled = on
        }
        hint("Выключите, если хотите спокойный аквариум без охоты и крови")

        section("Вода")
        slider("Пузырьки", Settings.bubbleDensity, 0f, 1f) { v ->
            Settings.bubbleDensity = v
            "${(v * 100).roundToInt()} %"
        }
        slider("Донное течение", Settings.currentStrength, 0f, 1.4f) { v ->
            Settings.currentStrength = v
            "${(v * 100 / 1.4f).roundToInt()} %"
        }
        toggle("Морской снег", Settings.marineSnow) { Settings.marineSnow = it }

        section("Свет")
        slider("Время суток", Settings.timeOfDay, 0f, 1f) { v ->
            Settings.timeOfDay = v
            when {
                v < 0.16f || v > 0.84f -> "Ночь"
                v < 0.32f -> "Рассвет"
                v < 0.68f -> "День"
                else -> "Закат"
            }
        }
        slider("Лучи света", Settings.godRayIntensity, 0f, 1f) { v ->
            Settings.godRayIntensity = v
            "${(v * 100).roundToInt()} %"
        }
        toggle("Плавное движение камеры", Settings.cameraDrift) { Settings.cameraDrift = it }

        section("Звук")
        toggle("Звук включён", Settings.soundEnabled) { Settings.soundEnabled = it }
        slider("Громкость", Settings.soundVolume, 0f, 1f) { v ->
            Settings.soundVolume = v
            "${(v * 100).roundToInt()} %"
        }
        hint("Бульканье, стук по стеклу и подводный гул синтезируются на лету")

        content.addView(View(context), LinearLayout.LayoutParams(1, d(12f)))
        content.addView(bigButton("Пересоздать аквариум") {
            Settings.requestReset(); onClose()
        })
        content.addView(View(context), LinearLayout.LayoutParams(1, d(8f)))
        content.addView(bigButton("Закрыть") { onClose() })
    }

    // ── Кирпичики интерфейса ──────────────────────────────────────────────────

    private fun header(text: String) {
        content.addView(TextView(context).apply {
            this.text = text
            setTextColor(TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
            setPadding(0, 0, 0, d(4f))
        })
    }

    private fun section(title: String) {
        content.addView(TextView(context).apply {
            text = title.uppercase()
            setTextColor(ACCENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            letterSpacing = 0.14f
            setPadding(0, d(18f), 0, d(6f))
        })
    }

    private fun hint(text: String) {
        content.addView(TextView(context).apply {
            this.text = text
            setTextColor(DIM)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setPadding(0, d(2f), 0, d(4f))
        })
    }

    private fun toggle(label: String, initial: Boolean, onChange: (Boolean) -> Unit) {
        content.addView(SwitchCompat(context).apply {
            text = label
            isChecked = initial
            setTextColor(TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            setPadding(0, d(9f), 0, d(9f))
            setOnCheckedChangeListener { _, v -> onChange(v) }
        })
    }

    /**
     * @param format вызывается при каждом изменении, применяет значение
     *               и возвращает подпись для показа справа.
     */
    private fun slider(
        label: String, initial: Float, min: Float, max: Float,
        format: (Float) -> String, onRelease: (() -> Unit)? = null
    ) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, d(10f), 0, 0)
        }
        val name = TextView(context).apply {
            text = label
            setTextColor(TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
        }
        val value = TextView(context).apply {
            setTextColor(ACCENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.END
            text = format(initial)
        }
        row.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(value, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(row)

        content.addView(SeekBar(context).apply {
            this.max = 1000
            progress = (((initial - min) / (max - min)) * 1000f).roundToInt().coerceIn(0, 1000)
            progressDrawable?.setTint(ACCENT)
            thumb?.setTint(ACCENT)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    value.text = format(min + (max - min) * p / 1000f)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) { onRelease?.invoke() }
            })
        })
    }

    private fun tapModeRow(): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, d(12f), 0, d(2f))
        }
        val modes = listOf(
            Settings.TapMode.KNOCK to "Стук",
            Settings.TapMode.FLAKES to "Хлопья",
            Settings.TapMode.MEAT to "Мясо"
        )
        for ((mode, title) in modes) {
            val chip = TextView(context).apply {
                text = title
                gravity = Gravity.CENTER
                setTextColor(TEXT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, d(11f), 0, d(11f))
                setOnClickListener {
                    Settings.tapMode = mode
                    refreshChips()
                }
            }
            modeChips.add(chip)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = if (modeChips.size == 1) 0 else d(8f)
            row.addView(chip, lp)
        }
        refreshChips()
        return row
    }

    private fun refreshChips() {
        val order = listOf(Settings.TapMode.KNOCK, Settings.TapMode.FLAKES, Settings.TapMode.MEAT)
        for (i in modeChips.indices) {
            val on = Settings.tapMode == order[i]
            modeChips[i].background = GradientDrawable().apply {
                cornerRadius = d(13f).toFloat()
                setColor(if (on) CHIP_ON else CHIP_OFF)
                setStroke(d(1f), if (on) ACCENT else 0x33FFFFFF)
            }
            modeChips[i].setTextColor(if (on) ACCENT else DIM)
        }
    }

    private fun bigButton(title: String, action: () -> Unit): Button {
        return Button(context).apply {
            text = title
            isAllCaps = false
            setTextColor(TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            background = GradientDrawable().apply {
                cornerRadius = d(14f).toFloat()
                setColor(0x333FD8FF)
                setStroke(d(1f), 0x663FD8FF)
            }
            setOnClickListener { action() }
        }
    }
}

/** Круглая кнопка-шестерёнка в углу экрана. */
@SuppressLint("ViewConstructor")
class GearButton(context: Context, onClick: () -> Unit) : TextView(context) {
    init {
        text = "\u2699"                    // символ шестерни, шрифт не нужен
        setTextColor(0xCCE8F4FA.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x66081420)
            setStroke(2, 0x553FD8FF)
        }
        setOnClickListener { onClick() }
        alpha = 0.55f
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        // Кнопка подсвечивается при касании — иначе на тёмном фоне непонятно, нажалась ли она
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> alpha = 1f
            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL -> alpha = 0.55f
        }
        return super.onTouchEvent(event)
    }
}
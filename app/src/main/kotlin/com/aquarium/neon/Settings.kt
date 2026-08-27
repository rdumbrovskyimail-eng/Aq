package com.aquarium.neon

/**
 * Настройки, которые пользователь меняет на лету.
 * Поля читаются GL-потоком и пишутся UI-потоком, поэтому все @Volatile.
 * Тяжёлые изменения (число рыб) идут через флаг rebuildRequested — сама
 * пересборка происходит на GL-потоке в безопасной точке кадра.
 */
object Settings {

    enum class TapMode { KNOCK, FLAKES, MEAT }

    @Volatile var soundEnabled = true
    @Volatile var soundVolume = 0.65f

    @Volatile var bubbleDensity = 0.75f      // 0..1 -> 0..170 пузырей
    @Volatile var marineSnow = true
    @Volatile var godRayIntensity = 0.45f
    @Volatile var currentStrength = 0.6f     // сила донного течения
    @Volatile var timeOfDay = 0.30f          // 0 = ночь, 0.5 = полдень, 1 = ночь

    @Volatile var predatorsEnabled = true
    @Volatile var fishDensity = 1.0f         // множитель популяции 0.3..2.0
    @Volatile var cameraDrift = true

    @Volatile var tapMode = TapMode.KNOCK

    @Volatile var rebuildRequested = false
    @Volatile var resetRequested = false

    fun requestRebuild() { rebuildRequested = true }
    fun requestReset() { resetRequested = true }
}
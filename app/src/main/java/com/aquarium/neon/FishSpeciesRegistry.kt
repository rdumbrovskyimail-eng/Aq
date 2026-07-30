package com.aquarium.neon

import android.graphics.Color

enum class BehaviorType { FLOCKING, SOLITARY, BOTTOM_DWELLER, HIDER, AGGRESSIVE, PREDATOR }
enum class VisualForm { SLENDER_NEON, TALL_DISC, EEL_SNAKE, JELLY_GLOW, STAR_CRAWLER, SEAHORSE, MANTA_RAY, LIONFISH_SPIKY, PIRANHA_BITE, SHRIMP_MANTIS }

data class SpeciesConfig(
    val id: Int,
    val name: String,
    val primaryColor: Int,
    val neonGlowColor: Int,
    val accentColor: Int,
    val form: VisualForm,
    val maxSpeed: Float,
    val behavior: BehaviorType,
    val sizeScale: Float,
    val segmentCount: Int = 8,
    val finFrequency: Float = 0.16f
)

object FishSpeciesRegistry {
    val ALL_SPECIES = listOf(
        SpeciesConfig(1,  "Electric Neon Tetra",       Color.parseColor("#00E5FF"), Color.parseColor("#00F0FF"), Color.RED,     VisualForm.SLENDER_NEON,  5.6f, BehaviorType.FLOCKING,       0.9f),
        SpeciesConfig(2,  "Cardinal Flare Tetra",      Color.parseColor("#FF0055"), Color.parseColor("#FF0077"), Color.CYAN,    VisualForm.SLENDER_NEON,  5.3f, BehaviorType.FLOCKING,       0.95f),
        SpeciesConfig(3,  "Lime Cyber Glowfish",       Color.parseColor("#39FF14"), Color.parseColor("#70FF40"), Color.YELLOW,  VisualForm.SLENDER_NEON,  5.8f, BehaviorType.FLOCKING,       1.0f),
        SpeciesConfig(4,  "Sunburst Imperial Angel",   Color.parseColor("#FFAA00"), Color.parseColor("#FFEE00"), Color.RED,     VisualForm.TALL_DISC,     2.7f, BehaviorType.SOLITARY,       2.4f),
        SpeciesConfig(5,  "Midnight Cyber Angel",      Color.parseColor("#151515"), Color.parseColor("#00FFFF"), Color.MAGENTA, VisualForm.TALL_DISC,     2.4f, BehaviorType.SOLITARY,       2.6f),
        SpeciesConfig(6,  "Nemo Anemone Clown",        Color.parseColor("#FF5500"), Color.parseColor("#FF8800"), Color.WHITE,   VisualForm.SLENDER_NEON,  3.5f, BehaviorType.HIDER,         1.3f),
        SpeciesConfig(7,  "Royal Indigo Tang",         Color.parseColor("#0D47A1"), Color.parseColor("#29B6F6"), Color.YELLOW,  VisualForm.SLENDER_NEON,  4.3f, BehaviorType.SOLITARY,       1.7f),
        SpeciesConfig(8,  "Turquoise Discus Prime",    Color.parseColor("#00E676"), Color.parseColor("#B9F6CA"), Color.MAGENTA, VisualForm.TALL_DISC,     2.1f, BehaviorType.SOLITARY,       2.9f),
        SpeciesConfig(9,  "Crimson Killer Piranha",    Color.parseColor("#D50000"), Color.parseColor("#FF1744"), Color.YELLOW,  VisualForm.PIRANHA_BITE,  7.6f, BehaviorType.AGGRESSIVE,    2.1f),
        SpeciesConfig(10, "Spiky Volitan Lionfish",    Color.parseColor("#FF3D00"), Color.parseColor("#FFAB91"), Color.WHITE,   VisualForm.LIONFISH_SPIKY,2.3f, BehaviorType.AGGRESSIVE,    3.2f),
        SpeciesConfig(11, "Electric Ribbon Eel",       Color.parseColor("#0288D1"), Color.parseColor("#80D8FF"), Color.YELLOW,  VisualForm.EEL_SNAKE,     4.1f, BehaviorType.HIDER,         3.5f, 14),
        SpeciesConfig(12, "Pink Biolum Jelly",         Color.parseColor("#E91E63"), Color.parseColor("#FF80AB"), Color.CYAN,    VisualForm.JELLY_GLOW,    1.4f, BehaviorType.SOLITARY,       2.3f),
        SpeciesConfig(13, "Abyssal Viper Dragon",      Color.parseColor("#311B92"), Color.parseColor("#B388FF"), Color.CYAN,    VisualForm.EEL_SNAKE,     5.9f, BehaviorType.PREDATOR,       2.8f, 12),
        SpeciesConfig(14, "Ruby Starfish Crawler",     Color.parseColor("#FF1744"), Color.parseColor("#FF5252"), Color.YELLOW,  VisualForm.STAR_CRAWLER,  0.45f,BehaviorType.BOTTOM_DWELLER, 1.9f),
        SpeciesConfig(15, "Neon Manta Ray",            Color.parseColor("#1A237E"), Color.parseColor("#00E5FF"), Color.WHITE,   VisualForm.MANTA_RAY,     2.9f, BehaviorType.SOLITARY,       4.2f),
        SpeciesConfig(16, "Peacock Mantis Strike",     Color.parseColor("#00C853"), Color.parseColor("#B2FF59"), Color.RED,     VisualForm.SHRIMP_MANTIS, 4.9f, BehaviorType.AGGRESSIVE,    1.7f)
    )
}
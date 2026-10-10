package io.github.sammynashed.emran.ui.theme

enum class EmranTheme(val presetName: String) {
    EXPRESSIVE("EXPRESSIVE"),
    COSMIC("COSMIC"),
    TEAL("TEAL"),
    ROSE("ROSE"),
    GOLDEN("GOLDEN"),
    EMERALD("EMERALD"),
    OCEAN("OCEAN"),
    AMYTHIST("AMYTHIST"),
    SUNFIRE("SUNFIRE"),
    SAPPHIRE("SAPPHIRE"),
    LAVENDER("LAVENDER"),
    MINT("MINT"),
    FOREST("FOREST"),
    BLOOD_MOON("BLOOD_MOON"),
    MONOCHROME("MONOCHROME"),
    SAKURA("SAKURA"),
    NEBULA("NEBULA"),
    DESERT("DESERT"),
    AURORA("AURORA"),
    BLACK_STONE("BLACK_STONE");

    companion object {
        fun fromPresetName(name: String?): EmranTheme {
            return entries.find { it.presetName == name } ?: EXPRESSIVE
        }
    }
}

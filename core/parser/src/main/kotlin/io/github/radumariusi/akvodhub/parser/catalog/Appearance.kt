package io.github.radumariusi.akvodhub.parser.catalog

enum class PosterSize(val label: String, val widthDp: Int, val imageSize: String) {
    SMALL("Mic", 92, "w185"),
    MEDIUM("Mediu", 112, "w342"),
    LARGE("Mare", 144, "w342"),
    ;

    val heightDp: Int get() = widthDp * 3 / 2

    companion object {
        fun decode(value: String?): PosterSize = entries.firstOrNull { it.name == value } ?: MEDIUM
    }
}

enum class AccentColor(val label: String, val argb: Long, val onArgb: Long = 0xFFFFFFFF) {
    RED("Roșu", 0xFFE50914),
    BLUE("Albastru", 0xFF1565C0),
    GREEN("Verde", 0xFF2E7D32),
    PURPLE("Violet", 0xFF7B1FA2),
    GOLD("Auriu", 0xFFFFB300, 0xFF101010),
    ;

    companion object {
        fun decode(value: String?): AccentColor = entries.firstOrNull { it.name == value } ?: RED
    }
}

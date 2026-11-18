package io.github.radumariusi.akvodhub.parser.player

/** Limita în octeți oprește încărcarea înainte de plafonul temporal pe surse cu bitrate mare. */
enum class BufferPreset(
    val label: String,
    val minMs: Int,
    val maxMs: Int,
    val playMs: Int,
    val rebufferMs: Int,
    val bytes: Int,
) {
    SMALL("Mic", 5_000, 15_000, 1_000, 2_000, 12 * 1024 * 1024),
    NORMAL("Normal", 24_000, 50_000, 4_000, 8_000, 48 * 1024 * 1024),
    LARGE("Mare · mai multă memorie", 40_000, 120_000, 6_000, 10_000, 64 * 1024 * 1024),
    ;

    companion object {
        fun decode(value: String?): BufferPreset = entries.firstOrNull { it.name == value } ?: NORMAL
    }
}

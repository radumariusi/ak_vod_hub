package io.github.radumariusi.akvodhub.parser.player

object PlaybackDefaults {
    val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    fun speed(value: Float): Float = value.takeIf { it in speeds } ?: 1f
    fun offset(value: Long): Long = value.coerceIn(-120_000L, 120_000L)
}

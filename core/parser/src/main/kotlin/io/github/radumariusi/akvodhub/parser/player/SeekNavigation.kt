package io.github.radumariusi.akvodhub.parser.player

object SeekNavigation {
    fun increment(heldMs: Long): Long = when {
        heldMs < 1_000 -> 10_000
        heldMs < 3_000 -> 30_000
        heldMs < 6_000 -> 60_000
        else -> 300_000
    }

    fun target(positionMs: Long, deltaMs: Long, durationMs: Long): Long? {
        if (durationMs <= 0) return null
        val end = (durationMs - 1_000).coerceAtLeast(0)
        val position = positionMs.coerceIn(0, end)
        return if (deltaMs > 0) position + deltaMs.coerceAtMost(end - position)
        else position + deltaMs.coerceAtLeast(-position)
    }

    fun minute(text: String, durationMs: Long): Long? {
        val minutes = text.toLongOrNull()?.takeIf { it >= 0 && it <= Long.MAX_VALUE / 60_000 } ?: return null
        val value = minutes * 60_000
        return value.takeIf { durationMs > 0 && it < durationMs }
    }
}

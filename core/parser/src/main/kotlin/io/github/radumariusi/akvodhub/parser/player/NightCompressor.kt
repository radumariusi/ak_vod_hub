package io.github.radumariusi.akvodhub.parser.player

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

/** Compresor cu anvelopă comună tuturor canalelor; nu schimbă balansul stereo. */
class NightCompressor(sampleRate: Int) {
    private val attack = exp(-1.0 / (sampleRate.coerceAtLeast(1) * 0.003))
    private val release = exp(-1.0 / (sampleRate.coerceAtLeast(1) * 0.25))
    private var envelope = 0.0

    fun gain(peak: Double): Double {
        val level = abs(peak).coerceIn(0.0, 1.0)
        val coefficient = if (level > envelope) attack else release
        envelope = coefficient * envelope + (1 - coefficient) * level
        val compression = if (envelope > 0.18) (0.18 / envelope).pow(0.75) else 1.0
        // Limitarea vârfului curent previne clipping-ul în timpul atacului.
        return (compression * 1.6).coerceAtMost(0.95 / max(level, 0.00001))
    }
}

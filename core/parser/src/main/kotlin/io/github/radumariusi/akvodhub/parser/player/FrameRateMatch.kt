package io.github.radumariusi.akvodhub.parser.player

import kotlin.math.abs
import kotlin.math.round

/** Alege cel mai mic multiplu întreg, fără schimbarea rezoluției; null = fără mod compatibil. */
object FrameRateMatch {
    fun choose(frameRate: Float, rates: List<Float>): Int? {
        if (!frameRate.isFinite() || frameRate < 10f || frameRate > 120f) return null
        return rates.indices.filter { i ->
            val multiple = rates[i] / frameRate
            multiple >= 0.99f && abs(multiple - round(multiple)) < 0.005f
        }.minByOrNull { rates[it] }
    }
}

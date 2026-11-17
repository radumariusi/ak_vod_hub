package io.github.radumariusi.akvodhub.parser.player

import java.security.MessageDigest
import java.util.Locale

object SeriesPlayback {
    const val MAX_INTRO_MS = 600_000L

    fun key(tmdbId: Long, name: String, year: Int?): String =
        if (tmdbId > 0) {
            "tmdb_$tmdbId"
        } else {
            val identity = "${name.trim().lowercase(Locale.ROOT)}|$year"
            "local_" +
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(identity.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
        }

    fun validIntro(positionMs: Long, durationMs: Long): Boolean =
        positionMs in 1_000..MAX_INTRO_MS && durationMs > positionMs + 1_000

    fun canSkip(positionMs: Long, durationMs: Long, endMs: Long): Boolean =
        validIntro(endMs, durationMs) && positionMs in 0 until endMs

    /** Alegerea disponibilă în episod; null lasă algoritmul global existent să decidă. */
    fun subtitleChoice(remembered: String?, embedded: Set<String>, addons: Set<String>): String? {
        if (remembered == "off") return "off"
        val mode = remembered?.substringBefore(':') ?: return null
        val language = remembered.substringAfter(':', "")
        if (mode == "embedded" && language in embedded) return "embedded:$language"
        if (language in addons) return "addon:$language"
        if (language in embedded) return "embedded:$language"
        return null
    }

    fun audioLanguages(remembered: String?, global: List<String>): List<String> =
        (listOfNotNull(remembered) + global).distinct()

    fun validSetting(key: String, value: String): Boolean {
        val match =
            Regex("series[.](tmdb_[1-9][0-9]*|local_[0-9a-f]{64})[.](intro|audio|text)").matchEntire(key)
                ?: return false
        return when (match.groupValues[2]) {
            "intro" -> value.toLongOrNull()?.let { it in 1_000..MAX_INTRO_MS } == true
            "audio" -> value.matches(Regex("[a-z]{2}"))
            "text" -> value == "off" || value.matches(Regex("(embedded|addon):[a-z]{2}"))
            else -> false
        }
    }
}

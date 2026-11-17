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
            "local_" + MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }

    fun validIntro(positionMs: Long, durationMs: Long): Boolean =
        positionMs in 1_000..MAX_INTRO_MS && durationMs > positionMs + 1_000

    fun canSkip(positionMs: Long, durationMs: Long, endMs: Long): Boolean =
        validIntro(endMs, durationMs) && positionMs in 0 until endMs

    fun validSetting(key: String, value: String): Boolean =
        key.matches(Regex("series\.(tmdb_[1-9][0-9]*|local_[0-9a-f]{64})\.intro")) &&
            value.toLongOrNull()?.let { it in 1_000..MAX_INTRO_MS } == true
}

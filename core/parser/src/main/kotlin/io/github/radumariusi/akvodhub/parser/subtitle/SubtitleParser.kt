package io.github.radumariusi.akvodhub.parser.subtitle

/**
 * Citește subtitrările descărcate din addon-uri: SRT (cel mai des), WebVTT și ASS/SSA. Etichetele de stil
 * (`<i>`, `{\an8}`, culori) se scot: playerul afișează textul simplu, cu stilul ales în Setări.
 */
object SubtitleParser {
    private val timing =
        Regex("""^\s*([\d:.,]+)\s*-->\s*([\d:.,]+)""")
    private val htmlTag = Regex("""</?[a-zA-Z][^>]*>""")
    private val assTag = Regex("""\{[^}]*\}""")

    fun parse(text: String): SubtitleDocument {
        val clean = text.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        val head = clean.trimStart().take(64)
        return when {
            head.startsWith("WEBVTT") -> parseTimed(clean, vtt = true)
            clean.contains("[Events]") || clean.lineSequence().any { it.startsWith("Dialogue:") } -> parseAss(clean)
            else -> parseTimed(clean, vtt = false)
        }
    }

    /** SRT și WebVTT: blocuri despărțite de rânduri goale, cu un rând „start --> sfârșit”. */
    private fun parseTimed(
        text: String,
        vtt: Boolean,
    ): SubtitleDocument {
        val cues = mutableListOf<SubtitleCue>()
        val lines = text.split('\n')
        var i = 0
        while (i < lines.size) {
            val match = timing.find(lines[i])
            if (match == null) {
                i++
                continue
            }
            val start = parseTime(match.groupValues[1])
            val end = parseTime(match.groupValues[2])
            i++
            val body = StringBuilder()
            while (i < lines.size && lines[i].isNotBlank()) {
                // Un rând de timp fără rând gol înainte: începe replica următoare.
                if (timing.containsMatchIn(lines[i])) break
                if (body.isNotEmpty()) body.append('\n')
                body.append(lines[i])
                i++
            }
            if (start != null && end != null) {
                val cleaned = cleanText(body.toString(), vtt)
                if (cleaned.isNotEmpty()) cues += SubtitleCue(start, end, cleaned)
            }
        }
        return SubtitleDocument(cues)
    }

    /** ASS/SSA: rândurile „Dialogue:”, cu ordinea câmpurilor din rândul „Format:” al secțiunii [Events]. */
    private fun parseAss(text: String): SubtitleDocument {
        var startIndex = 1
        var endIndex = 2
        var textIndex = 9
        var fields = 10
        var inEvents = false
        val cues = mutableListOf<SubtitleCue>()
        for (raw in text.split('\n')) {
            val line = raw.trim()
            if (line.startsWith("[")) {
                inEvents = line.equals("[Events]", ignoreCase = true)
                continue
            }
            if (inEvents && line.startsWith("Format:", ignoreCase = true)) {
                val names = line.substringAfter(':').split(',').map { it.trim().lowercase() }
                startIndex = names.indexOf("start").takeIf { it >= 0 } ?: startIndex
                endIndex = names.indexOf("end").takeIf { it >= 0 } ?: endIndex
                textIndex = names.indexOf("text").takeIf { it >= 0 } ?: (names.size - 1)
                fields = names.size
                continue
            }
            if (!line.startsWith("Dialogue:", ignoreCase = true)) continue
            val parts = line.substringAfter(':').split(',', limit = fields)
            if (parts.size < fields) continue
            val start = parseTime(parts[startIndex].trim()) ?: continue
            val end = parseTime(parts[endIndex].trim()) ?: continue
            val body =
                parts[textIndex]
                    .replace("\\N", "\n")
                    .replace("\\n", "\n")
                    .replace("\\h", " ")
            val cleaned = cleanText(body, vtt = false)
            if (cleaned.isNotEmpty()) cues += SubtitleCue(start, end, cleaned)
        }
        return SubtitleDocument(cues)
    }

    private fun cleanText(
        text: String,
        vtt: Boolean,
    ): String {
        var t = text.replace(assTag, "").replace(htmlTag, "")
        if (vtt || t.contains('&')) {
            t =
                t
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&nbsp;", " ")
                    .replace("&amp;", "&")
        }
        return t
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    /**
     * Timp în milisecunde din „01:02:03,456”, „01:02:03.456”, „02:03.456” (WebVTT) sau „1:02:03.45” (ASS,
     * sutimi). null dacă nu se poate citi.
     */
    fun parseTime(value: String): Long? {
        val v = value.trim()
        val sep = v.lastIndexOfAny(charArrayOf(',', '.'))
        val main = if (sep >= 0) v.substring(0, sep) else v
        val fraction = if (sep >= 0) v.substring(sep + 1) else ""
        val parts = main.split(':')
        if (parts.size !in 2..3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) return null
        if (fraction.isNotEmpty() && !fraction.all(Char::isDigit)) return null
        val nums = parts.map { it.toLong() }
        val (h, m, s) = if (nums.size == 3) Triple(nums[0], nums[1], nums[2]) else Triple(0L, nums[0], nums[1])
        val ms =
            when {
                fraction.isEmpty() -> 0L
                fraction.length >= 3 -> fraction.take(3).toLong()
                else -> fraction.padEnd(3, '0').toLong()
            }
        return ((h * 60 + m) * 60 + s) * 1000 + ms
    }
}

package io.github.radumariusi.akvodhub.parser.match

import java.text.Normalizer

/**
 * Compară titluri ca un om: fără diacritice, majuscule, punctuație sau articolul „The” de la început,
 * cu cifre romane transformate (`Part II` = `Part 2`) și `&` = `and`.
 */
object TitleSimilarity {
    private val marks = Regex("""\p{M}+""")
    private val nonWord = Regex("""[^\p{L}\p{N}]+""")
    private val leadingArticle = Regex("""^(the|a|an) """)
    private val roman =
        mapOf(
            "ii" to "2",
            "iii" to "3",
            "iv" to "4",
            "v" to "5",
            "vi" to "6",
            "vii" to "7",
            "viii" to "8",
            "ix" to "9",
            "x" to "10",
        )

    /** Forma canonică a unui titlu, folosită la comparare și drept cheie de cache. */
    fun normalize(title: String): String {
        val plain = marks.replace(Normalizer.normalize(title, Normalizer.Form.NFD), "")
        val words =
            nonWord
                .replace(plain.lowercase().replace("&", " and ").replace("'", ""), " ")
                .trim()
                .split(' ')
                .filter { it.isNotEmpty() }
                .mapIndexed { i, w -> if (i > 0) roman[w] ?: w else w }
        return leadingArticle.replace(words.joinToString(" "), "")
    }

    /**
     * Cât de asemănătoare sunt două titluri deja normalizate, între 0 și 1.
     * Un titlu care e începutul celuilalt (subtitlu omis: „Avatar” / „Avatar The Way of Water”) primește 0,85.
     */
    fun similarity(
        a: String,
        b: String,
    ): Double {
        if (a == b) return if (a.isEmpty()) 0.0 else 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val (short, long) = if (a.length <= b.length) a to b else b to a
        val prefix = if (long.startsWith("$short ")) PREFIX_SCORE else 0.0
        val lev = 1.0 - levenshtein(a, b).toDouble() / long.length
        return maxOf(prefix, lev)
    }

    private fun levenshtein(
        a: String,
        b: String,
    ): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            val t = prev
            prev = cur
            cur = t
        }
        return prev[b.length]
    }

    private const val PREFIX_SCORE = 0.85
}

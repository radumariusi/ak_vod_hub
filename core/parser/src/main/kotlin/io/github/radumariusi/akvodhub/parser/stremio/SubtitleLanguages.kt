package io.github.radumariusi.akvodhub.parser.stremio

import java.util.Locale

/**
 * Codurile de limbă din addon-uri sunt amestecate: ISO 639-2 („rum”, „ron”, „eng”, „pob”), ISO 639-1 („ro”)
 * sau nume („Romanian”). Le aducem la ISO 639-1, ca să le comparăm cu limbile preferate.
 */
object SubtitleLanguages {
    /** Codurile ISO 639-2/B și variantele OpenSubtitles care nu se văd din `Locale`. */
    private val special =
        mapOf(
            "rum" to "ro",
            "mol" to "ro",
            "fre" to "fr",
            "ger" to "de",
            "dut" to "nl",
            "gre" to "el",
            "chi" to "zh",
            "zht" to "zh",
            "zhe" to "zh",
            "cze" to "cs",
            "slo" to "sk",
            "per" to "fa",
            "arm" to "hy",
            "geo" to "ka",
            "ice" to "is",
            "mac" to "mk",
            "may" to "ms",
            "alb" to "sq",
            "baq" to "eu",
            "bur" to "my",
            "wel" to "cy",
            "tib" to "bo",
            "pob" to "pt",
            "pb" to "pt",
            "ptbr" to "pt",
            "pt-br" to "pt",
            "por" to "pt",
            "scc" to "sr",
            "scr" to "hr",
            "spn" to "es",
            "spa" to "es",
            "ea" to "es",
            "iw" to "he",
            "in" to "id",
            "ze" to "zh",
        )

    private val iso3: Map<String, String> by lazy {
        val map = HashMap<String, String>()
        for (code in Locale.getISOLanguages()) {
            val locale = Locale.forLanguageTag(code)
            runCatching { locale.isO3Language }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { map[it] = code }
            locale
                .getDisplayLanguage(Locale.ENGLISH)
                .lowercase()
                .takeIf { it.isNotEmpty() && it != code }
                ?.let { map.putIfAbsent(it, code) }
        }
        map
    }

    /** ISO 639-1 (ex. `ro`) sau null dacă nu recunoaștem codul. */
    fun toIso1(raw: String?): String? {
        val code =
            raw
                ?.trim()
                ?.lowercase()
                ?.replace('_', '-')
                ?.takeIf { it.isNotEmpty() } ?: return null
        special[code]?.let { return it }
        val base = code.substringBefore('-')
        if (base.length == 2 && base.all { it in 'a'..'z' }) return special[base] ?: base
        special[base]?.let { return it }
        return iso3[base] ?: iso3[code]
    }

    /** Portugheza braziliană apare separat în meniu. */
    fun isBrazilian(raw: String?): Boolean =
        raw?.trim()?.lowercase()?.replace('_', '-') in setOf("pob", "pb", "ptbr", "pt-br")

    /** Numele limbii în română, ex. „Română”, „Engleză”, „Portugheză (BR)”; codul brut dacă nu-l știm. */
    fun displayName(raw: String?): String {
        val iso = toIso1(raw) ?: return raw?.trim()?.takeIf { it.isNotEmpty() }?.uppercase() ?: "Necunoscută"
        val ro = Locale.forLanguageTag("ro")
        val name = Locale.forLanguageTag(iso).getDisplayLanguage(ro)
        val pretty =
            if (name.isNullOrBlank() || name == iso) iso.uppercase() else name.replaceFirstChar { it.titlecase(ro) }
        return if (isBrazilian(raw)) "$pretty (BR)" else pretty
    }
}

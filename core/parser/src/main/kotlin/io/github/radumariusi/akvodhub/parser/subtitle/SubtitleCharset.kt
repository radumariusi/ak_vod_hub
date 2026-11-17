package io.github.radumariusi.akvodhub.parser.subtitle

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Subtitrările vin des în codificări vechi (ex. Windows-1250 la cele românești, cu „º” în loc de „ș”).
 * Încercăm UTF-8 (și BOM-urile UTF-16), apoi codificarea Windows obișnuită pentru limba subtitrării.
 */
object SubtitleCharset {
    private val central = setOf("ro", "pl", "cs", "sk", "hu", "hr", "sl", "bs", "sq", "sr")
    private val cyrillic = setOf("ru", "bg", "uk", "mk", "be")

    fun decode(
        bytes: ByteArray,
        language: String?,
    ): String {
        val text = decodeRaw(bytes, language)
        return if (language == "ro") fixRomanian(text) else text
    }

    /**
     * Subtitrări românești convertite greșit din Windows-1250 în Windows-1252 (cu „º”, „þ”, „ã”):
     * literele se pun la loc.
     */
    fun fixRomanian(text: String): String {
        if (text.none { it in "ºªþÞãÃ" }) return text
        return buildString(text.length) {
            for (c in text) {
                append(
                    when (c) {
                        'º' -> 'ș'
                        'ª' -> 'Ș'
                        'þ' -> 'ț'
                        'Þ' -> 'Ț'
                        'ã' -> 'ă'
                        'Ã' -> 'Ă'
                        else -> c
                    },
                )
            }
        }
    }

    private fun decodeRaw(
        bytes: ByteArray,
        language: String?,
    ): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        strictUtf8(bytes)?.let { return it }
        return String(bytes, legacyCharset(language))
    }

    fun legacyCharset(language: String?): Charset {
        val name =
            when (language) {
                in central -> "windows-1250"
                in cyrillic -> "windows-1251"
                "el" -> "windows-1253"
                "tr" -> "windows-1254"
                "he" -> "windows-1255"
                "ar", "fa" -> "windows-1256"
                else -> "windows-1252"
            }
        return runCatching { Charset.forName(name) }.getOrDefault(Charsets.ISO_8859_1)
    }

    private fun strictUtf8(bytes: ByteArray): String? =
        try {
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            null
        }
}

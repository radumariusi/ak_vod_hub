package io.github.radumariusi.akvodhub.parser.playlist

import java.io.Reader

class JsonObjectStreamException(
    val reason: String,
) : IllegalArgumentException(reason)

/** Frames a single JSON object or an array of objects. The caller parses and validates each object. */
object JsonObjectStream {
    fun objects(reader: Reader): Sequence<String> =
        sequence {
            var next = reader.read()
            if (next == BOM) next = reader.read()
            when (reader.skipWhitespace(next)) {
                '{'.code -> {
                    yield(reader.readObject())
                }

                '['.code -> {
                    var start = reader.skipWhitespace()
                    if (start != ']'.code) {
                        while (true) {
                            if (start != '{'.code) invalid("obiect așteptat")
                            yield(reader.readObject())
                            when (reader.skipWhitespace()) {
                                ']'.code -> break
                                ','.code -> start = reader.skipWhitespace()
                                else -> invalid("separator sau final de listă așteptat")
                            }
                        }
                    }
                }

                else -> {
                    invalid("obiect sau listă așteptată")
                }
            }
            if (reader.skipWhitespace() != -1) invalid("date după finalul răspunsului")
        }

    private fun Reader.readObject(): String {
        val objectText = StringBuilder().append('{')
        var depth = 1
        var quoted = false
        var escaped = false
        while (depth > 0) {
            val next = read()
            if (next == -1) invalid("obiect trunchiat")
            val char = next.toChar()
            objectText.append(char)
            if (objectText.length > MAX_OBJECT_CHARS) invalid("obiect prea mare")
            when {
                escaped -> escaped = false
                quoted && char == '\\' -> escaped = true
                char == '"' -> quoted = !quoted
                !quoted && char == '{' -> depth++
                !quoted && char == '}' -> depth--
            }
        }
        return objectText.toString()
    }

    private fun Reader.skipWhitespace(first: Int = read()): Int {
        var next = first
        while (next == ' '.code || next == '\n'.code || next == '\r'.code || next == '\t'.code) next = read()
        return next
    }

    private fun invalid(reason: String): Nothing = throw JsonObjectStreamException(reason)

    private const val BOM = 0xFEFF
    private const val MAX_OBJECT_CHARS = 4 * 1024 * 1024
}

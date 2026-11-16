package io.github.radumariusi.akvodhub.parser.playlist

import java.io.BufferedReader

/** O intrare dintr-un fișier M3U: linia `#EXTINF` plus URL-ul de după ea. */
data class M3uEntry(
    val name: String,
    val url: String,
    val groupTitle: String?,
    val logo: String?,
    val attributes: Map<String, String>,
)

/**
 * Parser M3U în flux: citește linie cu linie și nu ține fișierul în memorie,
 * deci merge și pentru playlist-uri cu sute de mii de intrări.
 *
 * Înțelege `#EXTINF` cu atribute (`tvg-name`, `tvg-logo`, `group-title` etc.) și `#EXTGRP`.
 * Alte directive (`#EXTVLCOPT`, `#KODIPROP`) sunt ignorate.
 */
object M3uParser {
    /** Intrările din [reader], citite leneș. Cititorul se închide de către apelant. */
    fun entries(reader: BufferedReader): Sequence<M3uEntry> =
        sequence {
            var info: ExtInf? = null
            var extGroup: String? = null
            var first = true
            while (true) {
                var line = reader.readLine() ?: break
                if (first) {
                    line = line.removePrefix("﻿")
                    first = false
                }
                line = line.trim()
                when {
                    line.isEmpty() -> {
                        continue
                    }

                    line.startsWith("#EXTINF", ignoreCase = true) -> {
                        info = parseExtInf(line)
                        extGroup = null
                    }

                    line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                        extGroup = line.substring(8).trim().ifEmpty { null }
                    }

                    line.startsWith("#") -> {
                        continue
                    }

                    else -> {
                        val current = info
                        val attributes = current?.attributes.orEmpty()
                        val name =
                            current?.title?.ifBlank { null }
                                ?: attributes["tvg-name"]?.ifBlank { null }
                                ?: nameFromUrl(line)
                        yield(
                            M3uEntry(
                                name = name,
                                url = line,
                                groupTitle = attributes["group-title"]?.trim()?.ifEmpty { null } ?: extGroup,
                                logo = attributes["tvg-logo"]?.trim()?.ifEmpty { null },
                                attributes = attributes,
                            ),
                        )
                        info = null
                        extGroup = null
                    }
                }
            }
        }

    private class ExtInf(
        val title: String,
        val attributes: Map<String, String>,
    )

    /** `#EXTINF:-1 tvg-name="X" group-title="Filme",Titlu afișat` */
    private fun parseExtInf(line: String): ExtInf {
        val colon = line.indexOf(':')
        if (colon < 0) return ExtInf("", emptyMap())
        val attributes = HashMap<String, String>(8)
        var i = colon + 1
        val n = line.length
        // Durata (ex. -1 sau 0) până la primul spațiu sau virgulă.
        while (i < n && line[i] != ' ' && line[i] != ',') i++
        while (i < n) {
            val c = line[i]
            when {
                c == ',' -> {
                    return ExtInf(line.substring(i + 1).trim(), attributes)
                }

                c == ' ' || c == '\t' -> {
                    i++
                }

                else -> {
                    val keyStart = i
                    while (i < n && line[i] != '=' && line[i] != ' ' && line[i] != ',') i++
                    val key = line.substring(keyStart, i).lowercase()
                    if (i < n && line[i] == '=') {
                        i++
                        val value: String
                        if (i < n && (line[i] == '"' || line[i] == '\'')) {
                            val quote = line[i]
                            val end = line.indexOf(quote, i + 1).let { if (it < 0) n else it }
                            value = line.substring(i + 1, end)
                            i = (end + 1).coerceAtMost(n)
                        } else {
                            val valueStart = i
                            while (i < n && line[i] != ' ' && line[i] != ',') i++
                            value = line.substring(valueStart, i)
                        }
                        if (key.isNotEmpty()) attributes[key] = value
                    }
                }
            }
        }
        return ExtInf("", attributes)
    }

    private fun nameFromUrl(url: String): String {
        val path = url.substringBefore('?').substringBefore('#').trimEnd('/')
        return path.substringAfterLast('/').ifEmpty { url }
    }
}

package io.github.radumariusi.akvodhub.parser.remote

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder

/** O cerere HTTP primită de serverul local de configurare (pagina deschisă de pe telefon). */
data class HttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
    val body: String,
)

/** Cererea nu e HTTP valid sau e prea mare. */
class BadRequestException(
    message: String,
) : IOException(message)

/**
 * Un cititor HTTP/1.1 minimal: linia de cerere, antetele și corpul (după `Content-Length`).
 * Ajunge pentru pagina de configurare: cereri mici, fără `chunked`, câte una pe conexiune.
 */
object HttpMessage {
    const val MAX_HEADER_BYTES = 16 * 1024
    const val MAX_BODY_BYTES = 1024 * 1024

    fun read(input: InputStream): HttpRequest {
        val requestLine = readLine(input) ?: throw BadRequestException("Conexiune închisă.")
        val parts = requestLine.split(' ')
        if (parts.size != 3 || !parts[2].startsWith("HTTP/")) throw BadRequestException("Cerere invalidă.")
        val headers = LinkedHashMap<String, String>()
        var headerBytes = requestLine.length
        while (true) {
            val line = readLine(input) ?: throw BadRequestException("Antete incomplete.")
            if (line.isEmpty()) break
            headerBytes += line.length
            if (headerBytes > MAX_HEADER_BYTES) throw BadRequestException("Antete prea mari.")
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length < 0 || length > MAX_BODY_BYTES) throw BadRequestException("Corp prea mare.")
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) throw BadRequestException("Corp incomplet.")
            read += n
        }
        val target = parts[1]
        val path = target.substringBefore('?')
        val query = if ('?' in target) parseForm(target.substringAfter('?')) else emptyMap()
        return HttpRequest(parts[0].uppercase(), decode(path), query, headers, String(body, Charsets.UTF_8))
    }

    /** `a=1&b=x%20y` → mapă (ultima valoare câștigă). */
    fun parseForm(text: String): Map<String, String> =
        text
            .split('&')
            .filter { it.isNotEmpty() }
            .associate { pair ->
                val key = pair.substringBefore('=')
                val value = if ('=' in pair) pair.substringAfter('=') else ""
                decode(key) to decode(value)
            }

    /** Răspunsul complet (antete + corp), cu conexiunea închisă după el. */
    fun response(
        status: Int,
        contentType: String,
        body: ByteArray,
        extraHeaders: Map<String, String> = emptyMap(),
    ): ByteArray {
        val head =
            buildString {
                append("HTTP/1.1 ")
                    .append(status)
                    .append(' ')
                    .append(reason(status))
                    .append("\r\n")
                append("Content-Type: ").append(contentType).append("\r\n")
                append("Content-Length: ").append(body.size).append("\r\n")
                append("Cache-Control: no-store\r\n")
                append("Connection: close\r\n")
                extraHeaders.forEach { (k, v) -> append(k).append(": ").append(v).append("\r\n") }
                append("\r\n")
            }
        return head.toByteArray(Charsets.UTF_8) + body
    }

    fun reason(status: Int): String =
        when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            413 -> "Payload Too Large"
            500 -> "Internal Server Error"
            else -> "Error"
        }

    private fun decode(s: String): String = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

    /** O linie terminată cu CRLF (sau LF); null la sfârșitul fluxului fără nimic citit. */
    private fun readLine(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (out.size() == 0) null else out.toString(Charsets.UTF_8.name())
            if (b == '\n'.code) break
            if (out.size() > MAX_HEADER_BYTES) throw BadRequestException("Linie prea lungă.")
            out.write(b)
        }
        return out.toString(Charsets.UTF_8.name()).removeSuffix("\r")
    }
}

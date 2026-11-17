package io.github.radumariusi.akvodhub.parser.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpMessageTest {
    private fun read(raw: String) = HttpMessage.read(raw.byteInputStream(Charsets.UTF_8))

    @Test
    fun readsGetWithQuery() {
        val r = read("GET /api/backup?t=ab12&x=a%20b HTTP/1.1\r\nHost: 192.168.1.5:8686\r\n\r\n")
        assertEquals("GET", r.method)
        assertEquals("/api/backup", r.path)
        assertEquals(mapOf("t" to "ab12", "x" to "a b"), r.query)
        assertEquals("192.168.1.5:8686", r.headers["host"])
        assertEquals("", r.body)
    }

    @Test
    fun readsPostBodyWithUtf8() {
        val body = """{"name":"Filme româneşti"}"""
        val bytes = body.toByteArray(Charsets.UTF_8).size
        val head = "POST /api/m3u?t=x HTTP/1.1\r\nContent-Type: application/json\r\nContent-Length: $bytes\r\n\r\n"
        val r = read(head + body)
        assertEquals("POST", r.method)
        assertEquals(body, r.body)
    }

    @Test
    fun acceptsBareLineFeeds() {
        val r = read("GET / HTTP/1.0\nHost: x\n\n")
        assertEquals("/", r.path)
        assertTrue(r.query.isEmpty())
    }

    @Test(expected = BadRequestException::class)
    fun rejectsGarbage() {
        read("hello\r\n\r\n")
    }

    @Test(expected = BadRequestException::class)
    fun rejectsHugeBody() {
        read("POST / HTTP/1.1\r\nContent-Length: ${HttpMessage.MAX_BODY_BYTES + 1}\r\n\r\n")
    }

    @Test
    fun buildsResponse() {
        val text = String(HttpMessage.response(200, "text/plain", "ok".toByteArray()), Charsets.UTF_8)
        assertTrue(text.startsWith("HTTP/1.1 200 OK\r\n"))
        assertTrue("Content-Length: 2\r\n" in text)
        assertTrue(text.endsWith("\r\n\r\nok"))
    }
}

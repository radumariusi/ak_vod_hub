package io.github.radumariusi.akvodhub.parser.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.StringReader

class JsonObjectStreamTest {
    @Test
    fun readsBomAndQuotedBraces() {
        val first = """{"name":"a } \" {","info":{"year":2026}}"""
        val second = """{"id":"2"}"""
        val response = "\uFEFF [$first,\n$second] \r\n"
        assertEquals(listOf(first, second), JsonObjectStream.objects(StringReader(response)).toList())
        assertEquals(listOf(first), JsonObjectStream.objects(StringReader(first)).toList())
        assertEquals(emptyList<String>(), JsonObjectStream.objects(StringReader("[ ]")).toList())
    }

    @Test
    fun rejectsIncompleteOrUnexpectedResponses() {
        listOf("", "[{}", "[{},", "[{},]", "[{},null]", "[{}]false", "[{").forEach { input ->
            assertThrows(input, JsonObjectStreamException::class.java) {
                JsonObjectStream.objects(StringReader(input)).count()
            }
        }
    }

    @Test
    fun readsCatalogSizedArray() {
        val record = """{"id":"42","optional":null}"""
        val response =
            buildString {
                append('[')
                repeat(111_000) { index ->
                    if (index > 0) append(',')
                    append(record)
                }
                append(']')
            }
        assertEquals(111_000, JsonObjectStream.objects(StringReader(response)).count())
    }
}

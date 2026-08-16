package com.spmisha134.skillops.insights.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class InsightJsonlParserTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `empty file parses successfully`() {
        val file = temporaryFile("empty.jsonl", "")

        val result = InsightJsonlParser().parse(file)

        assertEquals(file.toAbsolutePath().normalize(), result.filePath)
        assertTrue(result.events.isEmpty())
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `parser extracts timestamp and type from top level event`() {
        val file = temporaryFile(
            "session.jsonl",
            """{"timestamp":"2026-07-04T12:12:00Z","type":"response.completed","payload":{"id":"abc"}}""" + "\n",
        )

        val result = InsightJsonlParser().parse(file)

        assertTrue(result.warnings.isEmpty())
        assertEquals(1, result.events.size)
        assertEquals(1, result.events.single().lineNumber)
        assertEquals("2026-07-04T12:12:00Z", result.events.single().timestamp)
        assertEquals("response.completed", result.events.single().type)
        assertEquals("abc", result.events.single().payload?.getAsJsonObject("payload")?.get("id")?.asString)
        assertNull(result.events.single().parseError)
    }

    @Test
    fun `malformed line becomes event parse warning without failing entire file`() {
        val file = temporaryFile(
            "malformed.jsonl",
            """{"timestamp":"2026-07-04T12:12:00Z","type":"ok"}""" + "\n" +
                """{"timestamp":""" + "\n" +
                """{"type":"after"}""" + "\n",
        )

        val result = InsightJsonlParser().parse(file)

        assertEquals(3, result.events.size)
        assertEquals(listOf("ok", null, "after"), result.events.map { it.type })
        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings.single().contains("Line 2"))
        assertNotNull(result.events[1].parseError)
        assertNull(result.events[1].payload)
    }

    @Test
    fun `unknown event shape is preserved in payload`() {
        val file = temporaryFile(
            "unknown.jsonl",
            """{"custom":"value","nested":{"number":3},"message":{"type":"message.kind","body":"hello"}}""" + "\n",
        )

        val result = InsightJsonlParser().parse(file)

        val event = result.events.single()
        assertEquals("message.kind", event.type)
        assertEquals("value", event.payload?.get("custom")?.asString)
        assertEquals(3, event.payload?.getAsJsonObject("nested")?.get("number")?.asInt)
        assertEquals("hello", event.payload?.getAsJsonObject("message")?.get("body")?.asString)
    }

    @Test
    fun `parser returns warning with line number for non object json line`() {
        val file = temporaryFile("array.jsonl", """["not","an","event"]""" + "\n")

        val result = InsightJsonlParser().parse(file)

        assertEquals(1, result.events.size)
        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings.single().contains("Line 1"))
        assertTrue(result.warnings.single().contains("not an object"))
        assertEquals(1, result.events.single().lineNumber)
        assertNotNull(result.events.single().parseError)
    }

    private fun temporaryFile(fileName: String, content: String): Path {
        val file = temporaryFolder.root.toPath().resolve(fileName)
        Files.writeString(file, content)
        return file
    }
}

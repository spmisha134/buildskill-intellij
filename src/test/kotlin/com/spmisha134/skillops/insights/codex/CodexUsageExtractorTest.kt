package com.spmisha134.skillops.insights.codex

import com.spmisha134.skillops.insights.parser.InsightJsonlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class CodexUsageExtractorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `extracts total tokens from token count event`() {
        val events = parseEvents(
            """{"timestamp":"2026-07-04T12:12:00Z","type":"token_count","payload":{"total_tokens":84220}}""",
        )

        val usage = CodexUsageExtractor().extract(events)

        assertEquals(84_220L, usage?.totalTokens)
        assertNull(usage?.inputTokens)
        assertNull(usage?.outputTokens)
    }

    @Test
    fun `extracts input output cached and reasoning tokens when present`() {
        val events = parseEvents(
            """{"type":"token_count","payload":{"usage":{"input_tokens":71400,"output_tokens":8900,"cached_input_tokens":3920,"reasoning_output_tokens":120}}}""",
        )

        val usage = CodexUsageExtractor().extract(events)

        assertEquals(71_400L, usage?.inputTokens)
        assertEquals(8_900L, usage?.outputTokens)
        assertEquals(3_920L, usage?.cachedInputTokens)
        assertEquals(120L, usage?.reasoningOutputTokens)
        assertEquals(80_300L, usage?.totalTokens)
    }

    @Test
    fun `uses latest token count like event in session`() {
        val events = parseEvents(
            """{"type":"token_count","payload":{"total_tokens":100}}""",
            """{"type":"message","payload":{"usage":{"totalTokens":200}}}""",
        )

        val usage = CodexUsageExtractor().extract(events)

        assertEquals(200L, usage?.totalTokens)
    }

    @Test
    fun `handles missing token count event`() {
        val events = parseEvents(
            """{"type":"message","payload":{"body":"hello"}}""",
        )

        val usage = CodexUsageExtractor().extract(events)

        assertNull(usage)
    }

    @Test
    fun `preserves raw evidence`() {
        val events = parseEvents(
            """{"timestamp":"2026-07-04T12:12:00Z","type":"token_count","payload":{"usage":{"total_tokens":42},"extra":"keep-me"},"custom":true}""",
        )

        val usage = CodexUsageExtractor().extract(events)

        assertEquals(42L, usage?.totalTokens)
        assertEquals(1, usage?.rawEvidence?.get("lineNumber"))
        assertEquals("token_count", usage?.rawEvidence?.get("type"))
        assertTrue(usage?.rawEvidence?.containsKey("payload") == true)

        @Suppress("UNCHECKED_CAST")
        val payload = usage?.rawEvidence?.get("payload") as Map<String, Any?>
        assertEquals(true, payload["custom"])

        @Suppress("UNCHECKED_CAST")
        val nestedPayload = payload["payload"] as Map<String, Any?>
        assertEquals("keep-me", nestedPayload["extra"])
    }

    @Test
    fun `extracts rate limit fields only when evidence exists`() {
        val events = parseEvents(
            """{"type":"token_count","payload":{"total_tokens":50,"rate_limits":{"used_percent":72.5,"reset_at":"2026-07-04T12:30:00Z"}}}""",
        )

        val usage = CodexUsageExtractor().extract(events)

        assertEquals(72.5, usage?.rateLimitUsedPercent)
        assertEquals("2026-07-04T12:30:00Z", usage?.rateLimitResetAt)
    }

    @Test
    fun `extracts current codex event message token schema`() {
        val events = parseEvents(
            """{"type":"event_msg","payload":{"type":"token_count","info":{"total_token_usage":{"input_tokens":217197,"cached_input_tokens":192256,"output_tokens":2021,"reasoning_output_tokens":637,"total_tokens":219218},"last_token_usage":{"input_tokens":29865,"cached_input_tokens":29440,"output_tokens":189,"reasoning_output_tokens":53,"total_tokens":30054}},"rate_limits":{"primary":{"used_percent":4.0,"resets_at":1784974038}}}}""",
        )

        val usage = CodexUsageExtractor().extract(events)

        assertEquals(217_197L, usage?.inputTokens)
        assertEquals(192_256L, usage?.cachedInputTokens)
        assertEquals(2_021L, usage?.outputTokens)
        assertEquals(637L, usage?.reasoningOutputTokens)
        assertEquals(219_218L, usage?.totalTokens)
        assertEquals(4.0, usage?.rateLimitUsedPercent)
    }

    private fun parseEvents(vararg lines: String) =
        InsightJsonlParser().parse(temporaryFile(lines.joinToString(separator = "\n", postfix = "\n"))).events

    private fun temporaryFile(content: String): Path {
        val file = temporaryFolder.newFile("session.jsonl").toPath()
        Files.writeString(file, content)
        return file
    }
}

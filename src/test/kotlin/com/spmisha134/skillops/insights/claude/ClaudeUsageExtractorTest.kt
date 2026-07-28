package com.spmisha134.skillops.insights.claude

import com.spmisha134.skillops.insights.parser.InsightJsonlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class ClaudeUsageExtractorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `sums distinct assistant calls and all claude token categories`() {
        val file = temporaryFolder.newFile("session.jsonl").toPath()
        Files.writeString(
            file,
            """
            {"type":"assistant","message":{"id":"msg-1","usage":{"input_tokens":100,"output_tokens":10,"cache_read_input_tokens":50,"cache_creation_input_tokens":20}}}
            {"type":"assistant","message":{"id":"msg-1","usage":{"input_tokens":100,"output_tokens":10,"cache_read_input_tokens":50,"cache_creation_input_tokens":20}}}
            {"type":"assistant","message":{"id":"msg-2","usage":{"input_tokens":40,"output_tokens":5,"cache_read_input_tokens":10,"cache_creation_input_tokens":0}}}
            """.trimIndent(),
        )

        val usage = ClaudeUsageExtractor().extract(InsightJsonlParser().parse(file).events)

        assertEquals(140L, usage?.inputTokens)
        assertEquals(15L, usage?.outputTokens)
        assertEquals(60L, usage?.cachedInputTokens)
        assertEquals(20L, usage?.cacheCreationInputTokens)
        assertEquals(235L, usage?.totalTokens)
        assertNull(usage?.reasoningOutputTokens)
        assertNull(usage?.rateLimitUsedPercent)
    }

    @Test
    fun `returns null without assistant usage`() {
        val file = temporaryFolder.newFile("empty-session.jsonl").toPath()
        Files.writeString(file, """{"type":"user","message":{"content":"hello"}}""")

        assertNull(ClaudeUsageExtractor().extract(InsightJsonlParser().parse(file).events))
    }
}

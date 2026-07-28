package com.spmisha134.skillops.insights.gemini

import com.spmisha134.skillops.insights.parser.InsightJsonlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class GeminiUsageExtractorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `sums distinct gemini responses and all recorded token categories`() {
        val file = temporaryFolder.newFile("gemini.jsonl").toPath()
        Files.writeString(
            file,
            """
            {"type":"gemini","id":"g1","tokens":{"input":100,"output":20,"cached":40,"thoughts":5,"tool":3,"total":168}}
            {"type":"gemini","id":"g1","tokens":{"input":100,"output":20,"cached":40,"thoughts":5,"tool":3,"total":168}}
            {"type":"gemini","id":"g2","tokens":{"input":50,"output":10,"cached":0,"thoughts":2,"tool":1,"total":63}}
            """.trimIndent(),
        )

        val usage = GeminiUsageExtractor().extract(InsightJsonlParser().parse(file).events)

        assertEquals(150L, usage?.inputTokens)
        assertEquals(30L, usage?.outputTokens)
        assertEquals(40L, usage?.cachedInputTokens)
        assertEquals(7L, usage?.reasoningOutputTokens)
        assertEquals(4L, usage?.toolTokens)
        assertEquals(231L, usage?.totalTokens)
        assertNull(usage?.rateLimitUsedPercent)
    }
}

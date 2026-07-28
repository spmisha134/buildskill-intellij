package com.spmisha134.skillops.insights.codex

import com.spmisha134.skillops.insights.parser.InsightJsonlParser
import com.spmisha134.skillops.insights.settings.SkillOpsInsightsSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class CodexEfficiencySummaryCalculatorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `calculates ratios and search warnings`() {
        val events = parseEvents(
            """{"type":"tool_call","payload":{"search_query":[{"q":"kafka"}]}}""",
            """{"type":"tool_call","payload":{"cmd":"rg Skill"}}""",
            """{"type":"token_count","payload":{"usage":{"input_tokens":1000,"output_tokens":250,"cached_input_tokens":400,"reasoning_output_tokens":50}}}""",
        )
        val usage = CodexUsageExtractor().extract(events)

        val summary = CodexEfficiencySummaryCalculator().calculate(
            events = events,
            tokenUsage = usage,
            sizeBytes = 20_000,
            settings = SkillOpsInsightsSettings(
                largeOutputWarningBytes = 10_000,
                highOutputWarningBytes = 100_000,
                manySearchesThreshold = 2,
            ),
        )

        assertEquals(0.25, summary.outputInputRatio)
        assertEquals(40.0, summary.cachedInputPercent)
        assertEquals(20.0, summary.reasoningOutputPercent)
        assertEquals(2, summary.searchCount)
        assertTrue(summary.warnings.any { it.contains("Session log is large") })
        assertTrue(summary.warnings.any { it.contains("High repository/search activity") })
    }

    private fun parseEvents(vararg lines: String) =
        InsightJsonlParser().parse(
            temporaryFolder.newFile("session.jsonl").toPath().also {
                Files.writeString(it, lines.joinToString(separator = "\n", postfix = "\n"))
            }
        ).events
}

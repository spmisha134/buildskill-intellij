package com.spmisha134.skillops.insights.presentation

import com.spmisha134.skillops.insights.run.SkillOpsRunInsightsReport
import com.spmisha134.skillops.insights.run.EfficiencySummary
import com.spmisha134.skillops.insights.run.SkillRunInsight
import com.spmisha134.skillops.insights.usage.TokenUsage
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class RunInsightsReportFormatterTest {
    @Test
    fun `formats latest token usage and efficiency summary`() {
        val report = SkillOpsRunInsightsReport(
            insights = listOf(
                SkillRunInsight(
                    sessionPath = Path.of("/tmp/session.jsonl"),
                    sessionFileName = "session.jsonl",
                    lastModifiedMs = 1_000,
                    sizeBytes = 300,
                    matchedSkillName = "kafka-review",
                    tokenUsage = TokenUsage(
                        inputTokens = 100,
                        outputTokens = 25,
                        cachedInputTokens = 50,
                        reasoningOutputTokens = 5,
                        totalTokens = 125,
                        rateLimitUsedPercent = null,
                        rateLimitResetAt = null,
                        rawEvidence = emptyMap(),
                    ),
                    efficiencySummary = EfficiencySummary(
                        outputInputRatio = 0.25,
                        cachedInputPercent = 50.0,
                        reasoningOutputPercent = 20.0,
                        searchCount = 2,
                        warnings = emptyList(),
                    ),
                    warnings = emptyList(),
                )
            ),
            warnings = emptyList(),
        )

        val text = RunInsightsReportFormatter().format(report)

        assertTrue(text.contains("Skill: kafka-review"))
        assertTrue(text.contains("- Total: 125"))
        assertTrue(text.contains("- Output/input ratio: 0.25"))
        assertTrue(text.contains("- Cached input: 50.0%"))
        assertTrue(!text.contains("Session: session.jsonl"))
        assertTrue(!text.contains("Setup warnings:"))
    }

    @Test
    fun `groups sessions without skills under no skill`() {
        val insight = SkillRunInsight(
            sessionPath = Path.of("/tmp/session.jsonl"),
            sessionFileName = "session.jsonl",
            lastModifiedMs = 1_000,
            sizeBytes = 0,
            matchedSkillName = null,
            tokenUsage = null,
            efficiencySummary = EfficiencySummary(null, null, null, 0, emptyList()),
            warnings = emptyList(),
        )

        val label = RunInsightsReportFormatter().selectionLabel(insight)
        val text = RunInsightsReportFormatter().format(insight)

        assertTrue(label == "No skill")
        assertTrue(text.contains("Skill: No skill"))
        assertTrue(!text.contains("session.jsonl"))
    }

    @Test
    fun `uses one no skill dropdown option for separate ordinary runs`() {
        val formatter = RunInsightsReportFormatter()
        val insights = listOf(1_000L, 2_000L).map { modified ->
            SkillRunInsight(
                sessionPath = Path.of("/tmp/session-$modified.jsonl"),
                sessionFileName = "session-$modified.jsonl",
                lastModifiedMs = modified,
                sizeBytes = 0,
                matchedSkillName = null,
                tokenUsage = null,
                efficiencySummary = EfficiencySummary(null, null, null, 0, emptyList()),
                warnings = emptyList(),
            )
        }

        val dropdownOptions = insights.flatMap(formatter::skillNames).distinctBy(String::lowercase)

        assertTrue(dropdownOptions == listOf("No skill"))
        assertTrue(insights.map(formatter::runLabel).distinct().size == 2)
    }

    @Test
    fun `uses recorded skill name when repository match is unavailable`() {
        val insight = SkillRunInsight(
            sessionPath = Path.of("/tmp/session.jsonl"),
            sessionFileName = "session.jsonl",
            lastModifiedMs = 1_000,
            sizeBytes = 0,
            matchedSkillName = null,
            tokenUsage = null,
            efficiencySummary = EfficiencySummary(null, null, null, 0, emptyList()),
            warnings = emptyList(),
            recordedSkillNames = listOf("external-helper"),
        )

        val formatter = RunInsightsReportFormatter()

        assertTrue(formatter.selectionLabel(insight) == "external-helper")
        assertTrue(formatter.format(insight)
            .contains("Skill: external-helper"))
    }

    @Test
    fun `formats warning as note with improvement advice`() {
        val insight = SkillRunInsight(
            sessionPath = Path.of("/tmp/session.jsonl"),
            sessionFileName = "session.jsonl",
            lastModifiedMs = 1_000,
            sizeBytes = 127_805,
            matchedSkillName = "update-deps",
            tokenUsage = null,
            efficiencySummary = EfficiencySummary(null, null, null, 1, emptyList()),
            warnings = listOf("Session log is large (127805 bytes)."),
        )

        val text = RunInsightsReportFormatter().format(insight)

        assertTrue(text.contains("Notes"))
        assertTrue(text.contains("How to improve: Start a new Codex session"))
        assertTrue(!text.contains("Ignored 83 sessions"))
    }

    @Test
    fun `uses provider name in improvement advice`() {
        val insight = SkillRunInsight(
            sessionPath = Path.of("/tmp/session.jsonl"),
            sessionFileName = "session.jsonl",
            lastModifiedMs = 1_000,
            sizeBytes = 60_506,
            matchedSkillName = null,
            tokenUsage = null,
            efficiencySummary = EfficiencySummary(null, null, null, 0, emptyList()),
            warnings = listOf("Session log is large (60506 bytes)."),
        )

        val text = RunInsightsReportFormatter().format(insight, platformName = "Claude")

        assertTrue(text.contains("How to improve: Start a new Claude session"))
        assertTrue(!text.contains("Codex session"))
    }

    @Test
    fun `formats invocation command and run history label`() {
        val insight = SkillRunInsight(
            sessionPath = Path.of("/tmp/session.jsonl"),
            sessionFileName = "session.jsonl",
            lastModifiedMs = 1_000,
            sizeBytes = 0,
            matchedSkillName = "update-deps",
            tokenUsage = null,
            efficiencySummary = EfficiencySummary(null, null, null, 0, emptyList()),
            warnings = emptyList(),
            invocationCommand = "${'$'}update-deps run the skill",
        )
        val formatter = RunInsightsReportFormatter()

        val text = formatter.format(insight)

        assertTrue(text.contains("Command: ${'$'}update-deps run the skill"))
        assertTrue(formatter.runLabel(insight).isNotBlank())
    }
}

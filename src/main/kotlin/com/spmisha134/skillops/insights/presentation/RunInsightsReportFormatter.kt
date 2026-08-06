package com.spmisha134.skillops.insights.presentation

import com.spmisha134.skillops.insights.run.SkillOpsRunInsightsReport
import com.spmisha134.skillops.insights.run.SkillRunInsight
import com.spmisha134.skillops.insights.usage.TokenUsage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class RunInsightsReportFormatter {
    fun format(report: SkillOpsRunInsightsReport): String {
        val selectedInsight = report.latestInsight
        if (selectedInsight != null) {
            return format(selectedInsight, report.platformName)
        }

        val lines = mutableListOf<String>()
        lines += "SkillOps ${report.platformName} Run Insights"
        lines += ""

        if (report.warnings.isNotEmpty()) {
            lines += "Setup warnings:"
            report.warnings.forEach { warning -> lines += "- $warning" }
            lines += ""
        }

        lines += "No ${report.platformName} sessions were found for this project."
        return lines.joinToString("\n")
    }

    fun format(insight: SkillRunInsight, platformName: String = "Codex"): String {
        val lines = mutableListOf<String>()
        lines += "Skill: ${skillNames(insight).joinToString()}"
        lines += "Updated: ${formatInstant(insight.lastModifiedMs)}"
        insight.resumeTarget?.let { lines += "Session: ${it.sessionId}" }
        insight.invocationCommand?.let { lines += "Command: $it" }
        lines += ""
        lines += "Tokens"
        lines += insight.tokenUsage.formatTokenLines()
        lines += ""
        lines += "Efficiency"
        lines += insight.formatEfficiencyLines()

        if (insight.warnings.isNotEmpty()) {
            lines += ""
            lines += "Notes"
            insight.warnings.forEach { warning ->
                lines += "- $warning"
                lines += "  How to improve: ${warning.improvementAdvice(platformName)}"
            }
        }

        return lines.joinToString("\n")
    }

    fun selectionLabel(insight: SkillRunInsight): String {
        return skillNames(insight).joinToString()
    }

    fun skillNames(insight: SkillRunInsight): List<String> =
        insight.displaySkillNames().ifEmpty { listOf(NO_SKILL_LABEL) }

    fun runLabel(insight: SkillRunInsight): String = formatInstant(insight.lastModifiedMs)

    private fun SkillRunInsight.displaySkillNames(): List<String> =
        (matchedSkillNames + recordedSkillNames).distinctBy(String::lowercase)

    private fun SkillRunInsight.formatEfficiencyLines(): List<String> =
        listOfNotNull(
            efficiencySummary.outputInputRatio?.let { "- Output/input ratio: ${formatDecimal(it)}" },
            efficiencySummary.cachedInputPercent?.let { "- Cached input: ${formatPercent(it)}" },
            efficiencySummary.reasoningOutputPercent?.let { "- Reasoning output: ${formatPercent(it)}" },
            "- Searches: ${efficiencySummary.searchCount}",
            "- Tool calls: ${efficiencySummary.toolCallCount}",
        )

    private fun TokenUsage?.formatTokenLines(): List<String> {
        if (this == null) {
            return listOf("- Not available")
        }

        return listOfNotNull(
            totalTokens?.let { "- Total: ${formatLong(it)}" },
            inputTokens?.let { "- Input: ${formatLong(it)}" },
            outputTokens?.let { "- Output: ${formatLong(it)}" },
            cachedInputTokens?.let { "- Cached: ${formatLong(it)}" },
            cacheCreationInputTokens?.let { "- Cache creation: ${formatLong(it)}" },
            toolTokens?.let { "- Tool: ${formatLong(it)}" },
            reasoningOutputTokens?.let { "- Reasoning: ${formatLong(it)}" },
            rateLimitUsedPercent?.let { "- Rate limit: ${formatPercent(it)}" },
        )
            .ifEmpty { listOf("- Token event found, but totals were unavailable") }
    }

    private fun String.improvementAdvice(platformName: String): String = when {
        startsWith("Session log is very large") || startsWith("Session log is large") ->
            "Start a new $platformName session for a separate task and avoid returning unnecessarily large command output."
        startsWith("High repository/search activity") ->
            "Narrow the request and point $platformName to the relevant files or package when they are known."
        startsWith("Rate limit usage is high") ->
            "Wait for the rate-limit window to reset or reduce repeated large-context requests."
        startsWith("No token usage event") ->
            "Let the assistant turn finish, then reopen Run Insights so its token usage can be read."
        else -> "Review the session scope and run a smaller, focused $platformName task."
    }

    private fun formatLong(value: Long): String =
        "%,d".format(value)

    private fun formatDecimal(value: Double): String =
        "%.2f".format(value)

    private fun formatPercent(value: Double): String =
        "%.1f%%".format(value)

    private fun formatInstant(epochMs: Long): String =
        DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
            Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
        )

    companion object {
        const val NO_SKILL_LABEL = "No skill"
    }

}

package com.spmisha134.skillops.insights.codex

import com.spmisha134.skillops.insights.parser.RawInsightEvent
import com.spmisha134.skillops.insights.run.EfficiencySummary
import com.spmisha134.skillops.insights.settings.SkillOpsInsightsSettings
import com.spmisha134.skillops.insights.usage.TokenUsage

class CodexEfficiencySummaryCalculator {
    fun calculate(
        events: List<RawInsightEvent>,
        tokenUsage: TokenUsage?,
        sizeBytes: Long,
        settings: SkillOpsInsightsSettings,
    ): EfficiencySummary {
        val searchCount = events.count(::isSearchEvent)
        val toolCallCount = events.sumOf(::toolCallCount)
        val warnings = mutableListOf<String>()

        if (tokenUsage == null) {
            warnings += "No token usage event found in this session."
        }
        if (sizeBytes >= settings.highOutputWarningBytes) {
            warnings += "Session log is very large (${sizeBytes} bytes)."
        } else if (sizeBytes >= settings.largeOutputWarningBytes) {
            warnings += "Session log is large (${sizeBytes} bytes)."
        }
        if (searchCount >= settings.manySearchesThreshold) {
            warnings += "High repository/search activity detected ($searchCount searches)."
        }
        tokenUsage?.rateLimitUsedPercent?.let { usedPercent ->
            if (usedPercent >= RATE_LIMIT_WARNING_PERCENT) {
                warnings += "Rate limit usage is high (${formatPercent(usedPercent)})."
            }
        }

        return EfficiencySummary(
            outputInputRatio = ratio(tokenUsage?.outputTokens, tokenUsage?.inputTokens),
            cachedInputPercent = percent(tokenUsage?.cachedInputTokens, tokenUsage?.inputTokens),
            reasoningOutputPercent = percent(tokenUsage?.reasoningOutputTokens, tokenUsage?.outputTokens),
            searchCount = searchCount,
            warnings = warnings,
            toolCallCount = toolCallCount,
        )
    }

    private fun isSearchEvent(event: RawInsightEvent): Boolean {
        val text = event.rawText.lowercase()
        return text.contains("\"search_query\"") ||
            text.contains("\"find\"") ||
            text.contains("\"cmd\":\"rg") ||
            text.contains("\"cmd\":\"grep") ||
            text.contains(" rg ") ||
            text.contains(" grep ")
    }

    private fun toolCallCount(event: RawInsightEvent): Int =
        TOOL_USE_PATTERN.findAll(event.rawText).count()

    private fun ratio(numerator: Long?, denominator: Long?): Double? =
        if (numerator != null && denominator != null && denominator > 0) {
            numerator.toDouble() / denominator.toDouble()
        } else {
            null
        }

    private fun percent(numerator: Long?, denominator: Long?): Double? =
        ratio(numerator, denominator)?.times(100.0)

    private fun formatPercent(value: Double): String =
        "%.1f%%".format(value)

    companion object {
        private const val RATE_LIMIT_WARNING_PERCENT = 80.0
        private val TOOL_USE_PATTERN = Regex("\"type\"\\s*:\\s*\"tool_use\"")
    }
}

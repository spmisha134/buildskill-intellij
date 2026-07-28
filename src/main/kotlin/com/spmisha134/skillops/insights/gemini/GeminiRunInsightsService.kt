package com.spmisha134.skillops.insights.gemini

import com.spmisha134.skillops.insights.parser.InsightJsonlParser
import com.spmisha134.skillops.insights.parser.RawInsightEvent
import com.spmisha134.skillops.insights.parser.stringAt
import com.spmisha134.skillops.insights.run.EfficiencySummary
import com.spmisha134.skillops.insights.run.RunInsightsService
import com.spmisha134.skillops.insights.run.SkillCatalog
import com.spmisha134.skillops.insights.run.SkillOpsRunInsightsReport
import com.spmisha134.skillops.insights.run.SkillRunInsight
import com.spmisha134.skillops.insights.settings.SkillOpsInsightsSettings
import com.spmisha134.skillops.insights.usage.TokenUsage
import java.nio.file.Path

class GeminiRunInsightsService(
    private val scanner: GeminiSessionFileScanner = GeminiSessionFileScanner(),
    private val parser: InsightJsonlParser = InsightJsonlParser(),
    private val usageExtractor: GeminiUsageExtractor = GeminiUsageExtractor(),
    private val skillCatalog: SkillCatalog = SkillCatalog(),
    private val skillMatcher: GeminiSkillUsageMatcher = GeminiSkillUsageMatcher(),
) : RunInsightsService {
    override fun buildReport(
        projectRoot: Path,
        settings: SkillOpsInsightsSettings,
    ): SkillOpsRunInsightsReport {
        val normalized = settings.normalized()
        val scan = scanner.scan(normalized)
        val skills = skillCatalog.discoverGemini(projectRoot)
        val warnings = scan.warnings.toMutableList()
        if (skills.isEmpty()) warnings += "No SkillOps skills found under .gemini/skills/."

        val normalizedProject = projectRoot.toAbsolutePath().normalize()
        val projectFiles = scan.files.filter { file ->
            file.projectRoot == normalizedProject
        }
        val skipped = scan.files.size - projectFiles.size
        if (skipped > 0) warnings += "Ignored $skipped Gemini session(s) belonging to other projects."

        val parsedProjectFiles = projectFiles.map { file -> file to parser.parse(file.path) }
        val completedOrStartedRuns = parsedProjectFiles.filter { (_, parsed) ->
            parsed.events.any { event ->
                val type = event.payload?.get("type")
                type?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                    ?.asString in MEANINGFUL_RECORD_TYPES
            }
        }
        val incomplete = parsedProjectFiles.size - completedOrStartedRuns.size
        if (incomplete > 0) {
            warnings += "Ignored $incomplete empty or authentication-aborted Gemini session(s)."
        }

        val insights = completedOrStartedRuns.map { (file, parsed) ->
            val usage = usageExtractor.extract(parsed.events)
            val matched = skillMatcher.matchSkills(parsed.events, skills)
            val recorded = skillMatcher.recordedSkillNames(parsed.events)
            val efficiency = efficiency(parsed.events, usage, file.sizeBytes, normalized)
            SkillRunInsight(
                sessionPath = file.path,
                sessionFileName = file.fileName,
                lastModifiedMs = file.lastModifiedMs,
                sizeBytes = file.sizeBytes,
                matchedSkillName = matched.firstOrNull(),
                matchedSkillNames = matched,
                recordedSkillNames = recorded.ifEmpty { matched },
                invocationCommand = skillMatcher.invocationCommand(parsed.events),
                tokenUsage = usage,
                efficiencySummary = efficiency,
                warnings = parsed.warnings + efficiency.warnings,
            )
        }
        return SkillOpsRunInsightsReport(insights, warnings, "Gemini")
    }

    private fun efficiency(
        events: List<RawInsightEvent>,
        usage: TokenUsage?,
        sizeBytes: Long,
        settings: SkillOpsInsightsSettings,
    ): EfficiencySummary {
        val toolNames = events.flatMap(::toolNames)
        val searches = toolNames.count { it in SEARCH_TOOLS }
        val notes = mutableListOf<String>()
        if (usage == null) notes += "No token usage event found in this session."
        if (sizeBytes >= settings.highOutputWarningBytes) {
            notes += "Session log is very large ($sizeBytes bytes)."
        } else if (sizeBytes >= settings.largeOutputWarningBytes) {
            notes += "Session log is large ($sizeBytes bytes)."
        }
        if (searches >= settings.manySearchesThreshold) {
            notes += "High repository/search activity detected ($searches searches)."
        }
        return EfficiencySummary(
            outputInputRatio = ratio(usage?.outputTokens, usage?.inputTokens),
            cachedInputPercent = percent(usage?.cachedInputTokens, usage?.inputTokens),
            reasoningOutputPercent = percent(usage?.reasoningOutputTokens, usage?.outputTokens),
            searchCount = searches,
            warnings = notes,
            toolCallCount = toolNames.size,
        )
    }

    private fun toolNames(event: RawInsightEvent): List<String> =
        event.payload?.getAsJsonArray("toolCalls")
            ?.mapNotNull { element ->
                element.takeIf { it.isJsonObject }?.asJsonObject?.stringAt("name")
            }.orEmpty()

    private fun ratio(numerator: Long?, denominator: Long?): Double? =
        if (numerator != null && denominator != null && denominator > 0) {
            numerator.toDouble() / denominator
        } else {
            null
        }

    private fun percent(numerator: Long?, denominator: Long?): Double? =
        ratio(numerator, denominator)?.times(100)

    companion object {
        private val SEARCH_TOOLS = setOf("grep_search", "google_web_search", "search_file_content")
        private val MEANINGFUL_RECORD_TYPES = setOf("user", "gemini", "error", "warning")
    }
}

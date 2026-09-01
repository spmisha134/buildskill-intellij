package com.spmisha134.skillops.insights.gemini

import com.spmisha134.skillops.insights.parser.InsightJsonlParser
import com.spmisha134.skillops.insights.parser.RawInsightEvent
import com.spmisha134.skillops.insights.parser.stringAt
import com.spmisha134.skillops.insights.run.EfficiencySummary
import com.spmisha134.skillops.insights.run.RunInsightsService
import com.spmisha134.skillops.insights.run.SkillCatalog
import com.spmisha134.skillops.insights.run.SkillOpsRunInsightsReport
import com.spmisha134.skillops.insights.run.SkillRunInsight
import com.spmisha134.skillops.insights.run.RunInsightsProgress
import com.spmisha134.skillops.insights.settings.SkillOpsInsightsSettings
import com.spmisha134.skillops.insights.usage.TokenUsage
import java.nio.file.Path

class GeminiRunInsightsService(
    private val scanner: GeminiSessionFileScanner = GeminiSessionFileScanner(),
    private val parser: InsightJsonlParser = InsightJsonlParser(),
    private val skillCatalog: SkillCatalog = SkillCatalog(),
) : RunInsightsService {
    override fun buildReport(
        projectRoot: Path,
        settings: SkillOpsInsightsSettings,
        progress: RunInsightsProgress,
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

        val insights = mutableListOf<SkillRunInsight>()
        var incomplete = 0
        projectFiles.forEachIndexed { index, file ->
            progress.checkCanceled()
            progress.update("Parsing Gemini session ${index + 1} of ${projectFiles.size}", index, projectFiles.size)
            val accumulator = GeminiStreamingAccumulator(skills)
            val parseWarnings = parser.stream(
                file.path,
                progress.withPrefix("Gemini session ${index + 1} of ${projectFiles.size}"),
                accumulator::accept,
            )
            if (!accumulator.hasMeaningfulRecord()) {
                incomplete++
                return@forEachIndexed
            }

            val usage = accumulator.tokenUsage()
            val matched = accumulator.matchedSkills()
            val recorded = accumulator.recordedSkills()
            val (searches, toolCalls) = accumulator.counts()
            val efficiency = efficiency(searches, toolCalls, usage, file.sizeBytes, normalized)
            insights += SkillRunInsight(
                sessionPath = file.path,
                sessionFileName = file.fileName,
                lastModifiedMs = file.lastModifiedMs,
                sizeBytes = file.sizeBytes,
                matchedSkillName = matched.firstOrNull(),
                matchedSkillNames = matched,
                recordedSkillNames = recorded.ifEmpty { matched },
                invocationCommand = accumulator.invocationCommand(),
                tokenUsage = usage,
                efficiencySummary = efficiency,
                warnings = parseWarnings + efficiency.warnings,
            )
        }
        if (incomplete > 0) {
            warnings += "Ignored $incomplete empty or authentication-aborted Gemini session(s)."
        }
        return SkillOpsRunInsightsReport(insights, warnings, "Gemini")
    }

    fun buildReport(projectRoot: Path, settings: SkillOpsInsightsSettings): SkillOpsRunInsightsReport =
        buildReport(projectRoot, settings, RunInsightsProgress.NONE)

    private fun efficiency(
        searchCount: Int,
        toolCallCount: Int,
        usage: TokenUsage?,
        sizeBytes: Long,
        settings: SkillOpsInsightsSettings,
    ): EfficiencySummary {
        val searches = searchCount
        val notes = mutableListOf<String>()
        if (usage == null) notes += "No token usage event found in this session."
        if (sizeBytes >= settings.highOutputWarningBytes) {
            notes += "Transcript size warning: $sizeBytes bytes may significantly slow scanning and increase context overhead."
        } else if (sizeBytes >= settings.largeOutputWarningBytes) {
            notes += "Transcript size warning: $sizeBytes bytes may slow scanning and increase context overhead."
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
            toolCallCount = toolCallCount,
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

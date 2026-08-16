package com.spmisha134.skillops.insights.claude

import com.spmisha134.skillops.insights.parser.InsightJsonlParser
import com.spmisha134.skillops.insights.run.EfficiencySummary
import com.spmisha134.skillops.insights.run.RunInsightsService
import com.spmisha134.skillops.insights.run.SkillCatalog
import com.spmisha134.skillops.insights.run.SkillOpsRunInsightsReport
import com.spmisha134.skillops.insights.run.SkillRunInsight
import com.spmisha134.skillops.insights.run.RunInsightsProgress
import com.spmisha134.skillops.insights.settings.SkillOpsInsightsSettings
import com.spmisha134.skillops.insights.usage.TokenUsage
import java.nio.file.Files
import java.nio.file.Path

class ClaudeRunInsightsService(
    private val scanner: ClaudeSessionFileScanner = ClaudeSessionFileScanner(),
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
        val skills = skillCatalog.discoverClaude(projectRoot)
        val warnings = scan.warnings.toMutableList()
        if (skills.isEmpty()) warnings += "No SkillOps skills found under .claude/skills/."

        // Parse one main transcript (and its subagents) at a time. Keeping every
        // Claude transcript's Gson trees and raw JSON lines caused heap usage to
        // grow with the complete scan history.
        val insights = mutableListOf<SkillRunInsight>()
        var skipped = 0
        var incomplete = 0
        scan.files.forEachIndexed { index, file ->
            progress.checkCanceled()
            progress.update("Parsing Claude session ${index + 1} of ${scan.files.size}", index, scan.files.size)
            val paths = listOf(file.path) + scanner.subagentFiles(file.path)
            val accumulator = ClaudeStreamingAccumulator(projectRoot, skills)
            val sessionWarnings = paths.flatMap {
                parser.stream(
                    it,
                    progress.withPrefix("Claude session ${index + 1} of ${scan.files.size}"),
                    accumulator::accept,
                )
            }
            if (accumulator.belongsToProject() != true) {
                skipped++
                return@forEachIndexed
            }

            val usage = accumulator.tokenUsage()
            if (!usage.hasPositiveUsage()) {
                incomplete++
                return@forEachIndexed
            }

            val matched = accumulator.matchedSkills()
            val recorded = accumulator.recordedSkills()
            val (searches, toolCalls) = accumulator.counts()
            val totalSize = paths.sumOf(::safeSize)
            val efficiency = efficiency(searches, toolCalls, usage, totalSize, normalized)
            insights += SkillRunInsight(
                sessionPath = file.path,
                sessionFileName = file.fileName,
                lastModifiedMs = file.lastModifiedMs,
                sizeBytes = totalSize,
                matchedSkillName = matched.firstOrNull(),
                matchedSkillNames = matched,
                recordedSkillNames = recorded.ifEmpty { matched },
                invocationCommand = accumulator.invocationCommand(),
                tokenUsage = usage,
                efficiencySummary = efficiency,
                warnings = sessionWarnings + efficiency.warnings,
                resumeTarget = accumulator.resumeTarget(file.fileName),
            )
        }
        if (skipped > 0) warnings += "Ignored $skipped Claude session(s) belonging to other projects."
        if (incomplete > 0) {
            warnings += "Ignored $incomplete Claude session(s) without completed assistant usage."
        }
        return SkillOpsRunInsightsReport(
            insights = insights,
            warnings = warnings,
            platformName = "Claude",
        )
    }

    fun buildReport(projectRoot: Path, settings: SkillOpsInsightsSettings): SkillOpsRunInsightsReport =
        buildReport(projectRoot, settings, RunInsightsProgress.NONE)

    private fun efficiency(
        searches: Int,
        toolCalls: Int,
        usage: TokenUsage?,
        sizeBytes: Long,
        settings: SkillOpsInsightsSettings,
    ): EfficiencySummary {
        val effectiveInput = listOfNotNull(
            usage?.inputTokens,
            usage?.cachedInputTokens,
            usage?.cacheCreationInputTokens,
        ).sum()
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
            outputInputRatio = ratio(usage?.outputTokens, effectiveInput),
            cachedInputPercent = percent(usage?.cachedInputTokens, effectiveInput),
            reasoningOutputPercent = null,
            searchCount = searches,
            warnings = notes,
            toolCallCount = toolCalls,
        )
    }

    private fun ratio(numerator: Long?, denominator: Long): Double? =
        numerator?.takeIf { denominator > 0 }?.toDouble()?.div(denominator)

    private fun percent(numerator: Long?, denominator: Long): Double? =
        ratio(numerator, denominator)?.times(100)

    private fun safeSize(path: Path): Long =
        runCatching { Files.size(path) }.getOrDefault(0)

    private fun TokenUsage?.hasPositiveUsage(): Boolean =
        this != null && listOfNotNull(
            inputTokens,
            outputTokens,
            cachedInputTokens,
            cacheCreationInputTokens,
            totalTokens,
        ).any { it > 0 }

    companion object {
        private val TOOL_USE = Regex("\"type\"\\s*:\\s*\"tool_use\"")
        private val SEARCH_TOOL = Regex(
            "\"name\"\\s*:\\s*\"(?:Grep|Glob|WebSearch|WebFetch)\"",
            RegexOption.IGNORE_CASE,
        )
    }
}

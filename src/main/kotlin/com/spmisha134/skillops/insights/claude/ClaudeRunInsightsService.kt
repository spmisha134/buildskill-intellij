package com.spmisha134.skillops.insights.claude

import com.spmisha134.skillops.insights.parser.InsightJsonlParser
import com.spmisha134.skillops.insights.run.EfficiencySummary
import com.spmisha134.skillops.insights.run.RunInsightsService
import com.spmisha134.skillops.insights.run.SkillCatalog
import com.spmisha134.skillops.insights.run.SkillOpsRunInsightsReport
import com.spmisha134.skillops.insights.run.SkillRunInsight
import com.spmisha134.skillops.insights.settings.SkillOpsInsightsSettings
import com.spmisha134.skillops.insights.usage.TokenUsage
import com.spmisha134.skillops.sessions.model.SessionResumeTarget
import java.nio.file.Files
import java.nio.file.Path

class ClaudeRunInsightsService(
    private val scanner: ClaudeSessionFileScanner = ClaudeSessionFileScanner(),
    private val parser: InsightJsonlParser = InsightJsonlParser(),
    private val usageExtractor: ClaudeUsageExtractor = ClaudeUsageExtractor(),
    private val skillCatalog: SkillCatalog = SkillCatalog(),
    private val skillMatcher: ClaudeSkillUsageMatcher = ClaudeSkillUsageMatcher(),
    private val sessionMatcher: ClaudeSessionMatcher = ClaudeSessionMatcher(),
) : RunInsightsService {
    override fun buildReport(
        projectRoot: Path,
        settings: SkillOpsInsightsSettings,
    ): SkillOpsRunInsightsReport {
        val normalized = settings.normalized()
        val scan = scanner.scan(normalized)
        val skills = skillCatalog.discoverClaude(projectRoot)
        val warnings = scan.warnings.toMutableList()
        if (skills.isEmpty()) warnings += "No SkillOps skills found under .claude/skills/."

        val parsed = scan.files.map { file ->
            val paths = listOf(file.path) + scanner.subagentFiles(file.path)
            val results = paths.map(parser::parse)
            ParsedClaudeSession(
                file = file,
                events = results.flatMap { it.events },
                warnings = results.flatMap { it.warnings },
                totalSize = paths.sumOf(::safeSize),
            )
        }
        val projectSessions = parsed.filter {
            sessionMatcher.belongsToProject(it.events, projectRoot) == true
        }
        val skipped = parsed.size - projectSessions.size
        if (skipped > 0) warnings += "Ignored $skipped Claude session(s) belonging to other projects."

        val completedSessions = projectSessions.mapNotNull { session ->
            val usage = usageExtractor.extract(session.events)
            if (!usage.hasPositiveUsage()) return@mapNotNull null
            session to usage
        }
        val incomplete = projectSessions.size - completedSessions.size
        if (incomplete > 0) {
            warnings += "Ignored $incomplete Claude session(s) without completed assistant usage."
        }

        val insights = completedSessions.map { (session, usage) ->
            val matched = skillMatcher.matchSkills(session.events, skills)
            val recorded = skillMatcher.recordedSkillNames(session.events)
            val efficiency = efficiency(session.events.map { it.rawText }, usage, session.totalSize, normalized)
            SkillRunInsight(
                sessionPath = session.file.path,
                sessionFileName = session.file.fileName,
                lastModifiedMs = session.file.lastModifiedMs,
                sizeBytes = session.totalSize,
                matchedSkillName = matched.firstOrNull(),
                matchedSkillNames = matched,
                recordedSkillNames = recorded.ifEmpty { matched },
                invocationCommand = skillMatcher.invocationCommand(session.events),
                tokenUsage = usage,
                efficiencySummary = efficiency,
                warnings = session.warnings + efficiency.warnings,
                resumeTarget = session.resumeTarget(),
            )
        }
        return SkillOpsRunInsightsReport(
            insights = insights,
            warnings = warnings,
            platformName = "Claude",
        )
    }

    private fun efficiency(
        rawEvents: List<String>,
        usage: TokenUsage?,
        sizeBytes: Long,
        settings: SkillOpsInsightsSettings,
    ): EfficiencySummary {
        val toolCalls = rawEvents.sumOf { TOOL_USE.findAll(it).count() }
        val searches = rawEvents.sumOf { SEARCH_TOOL.findAll(it).count() }
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

    private fun ParsedClaudeSession.resumeTarget(): SessionResumeTarget? {
        val sessionId = events.firstNotNullOfOrNull { it.payload?.get("sessionId")?.asString }
            ?: file.fileName.removeSuffix(".jsonl").takeIf(String::isNotBlank)
        val cwd = events.firstNotNullOfOrNull {
            it.payload?.get("cwd")?.takeIf { value -> value.isJsonPrimitive }?.asString
        }
        return sessionId?.let { SessionResumeTarget(it, cwd?.let(Path::of)) }
    }

    private fun TokenUsage?.hasPositiveUsage(): Boolean =
        this != null && listOfNotNull(
            inputTokens,
            outputTokens,
            cachedInputTokens,
            cacheCreationInputTokens,
            totalTokens,
        ).any { it > 0 }

    private data class ParsedClaudeSession(
        val file: ClaudeSessionFile,
        val events: List<com.spmisha134.skillops.insights.parser.RawInsightEvent>,
        val warnings: List<String>,
        val totalSize: Long,
    )

    companion object {
        private val TOOL_USE = Regex("\"type\"\\s*:\\s*\"tool_use\"")
        private val SEARCH_TOOL = Regex(
            "\"name\"\\s*:\\s*\"(?:Grep|Glob|WebSearch|WebFetch)\"",
            RegexOption.IGNORE_CASE,
        )
    }
}

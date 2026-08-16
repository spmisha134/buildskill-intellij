package com.spmisha134.skillops.insights.codex

import com.spmisha134.skillops.insights.parser.InsightJsonlParser
import com.spmisha134.skillops.insights.run.RunInsightsService
import com.spmisha134.skillops.insights.run.SkillCatalog
import com.spmisha134.skillops.insights.run.SkillOpsRunInsightsReport
import com.spmisha134.skillops.insights.run.SkillRunInsight
import com.spmisha134.skillops.insights.run.RunInsightsProgress
import com.spmisha134.skillops.insights.settings.SkillOpsInsightsSettings
import java.nio.file.Path

class CodexRunInsightsService(
    private val sessionFileScanner: CodexSessionFileScanner = CodexSessionFileScanner(),
    private val parser: InsightJsonlParser = InsightJsonlParser(),
    private val tokenUsageExtractor: CodexUsageExtractor = CodexUsageExtractor(),
    private val skillCatalog: SkillCatalog = SkillCatalog(),
    private val efficiencySummaryCalculator: CodexEfficiencySummaryCalculator = CodexEfficiencySummaryCalculator(),
) : RunInsightsService {
    override fun buildReport(
        projectRoot: Path,
        settings: SkillOpsInsightsSettings,
        progress: RunInsightsProgress,
    ): SkillOpsRunInsightsReport {
        val normalizedSettings = settings.normalized()
        val scanResult = sessionFileScanner.scan(normalizedSettings)
        val skillNames = skillCatalog.discover(projectRoot)
        val warnings = scanResult.warnings.toMutableList()

        if (skillNames.isEmpty()) {
            warnings += "No SkillOps skills found under .agents/skills/."
        }

        // Do not retain parsed events for every session. A JSONL rollout can be very
        // large, and each event contains both a Gson tree and its original line.
        // Processing one file at a time keeps peak memory proportional to the largest
        // session rather than to all configured sessions.
        val insights = mutableListOf<SkillRunInsight>()
        var skippedSessionCount = 0
        scanResult.files.forEachIndexed { index, sessionFile ->
            progress.checkCanceled()
            progress.update("Parsing Codex session ${index + 1} of ${scanResult.files.size}", index, scanResult.files.size)
            val accumulator = CodexStreamingAccumulator(projectRoot, skillNames, tokenUsageExtractor)
            val parseWarnings = parser.stream(
                sessionFile.path,
                progress.withPrefix("Codex session ${index + 1} of ${scanResult.files.size}"),
                accumulator::accept,
            )
            if (accumulator.projectBelongsToProject() == false) {
                skippedSessionCount++
                return@forEachIndexed
            }

            val tokenUsage = accumulator.tokenUsage()
            val matchedSkillNames = accumulator.matchedSkillNames()
            val recordedSkillNames = accumulator.recordedSkillNames()
            val (searchCount, toolCallCount) = accumulator.efficiencyCounts()
            val efficiencySummary = efficiencySummaryCalculator.calculate(
                searchCount = searchCount,
                toolCallCount = toolCallCount,
                tokenUsage = tokenUsage,
                sizeBytes = sessionFile.sizeBytes,
                settings = normalizedSettings,
            )
            val sessionMetadata = accumulator.resumeMetadata(sessionFile.fileName)

            insights += SkillRunInsight(
                sessionPath = sessionFile.path,
                sessionFileName = sessionFile.fileName,
                lastModifiedMs = sessionFile.lastModifiedMs,
                sizeBytes = sessionFile.sizeBytes,
                matchedSkillName = matchedSkillNames.firstOrNull(),
                matchedSkillNames = matchedSkillNames,
                recordedSkillNames = recordedSkillNames.ifEmpty { matchedSkillNames },
                invocationCommand = accumulator.invocationCommand(),
                tokenUsage = tokenUsage,
                efficiencySummary = efficiencySummary,
                warnings = parseWarnings + efficiencySummary.warnings,
                resumeTarget = sessionMetadata?.toResumeTarget(),
            )
        }

        if (skippedSessionCount > 0) {
            warnings += "Ignored $skippedSessionCount Codex session(s) belonging to other projects."
        }

        return SkillOpsRunInsightsReport(
            insights = insights,
            warnings = warnings,
            platformName = "Codex",
        )
    }

    fun buildReport(projectRoot: Path, settings: SkillOpsInsightsSettings): SkillOpsRunInsightsReport =
        buildReport(projectRoot, settings, RunInsightsProgress.NONE)
}

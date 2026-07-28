package com.spmisha134.skillops.insights.codex

import com.spmisha134.skillops.insights.parser.InsightJsonlParser
import com.spmisha134.skillops.insights.run.RunInsightsService
import com.spmisha134.skillops.insights.run.SkillCatalog
import com.spmisha134.skillops.insights.run.SkillOpsRunInsightsReport
import com.spmisha134.skillops.insights.run.SkillRunInsight
import com.spmisha134.skillops.insights.settings.SkillOpsInsightsSettings
import java.nio.file.Path

class CodexRunInsightsService(
    private val sessionFileScanner: CodexSessionFileScanner = CodexSessionFileScanner(),
    private val parser: InsightJsonlParser = InsightJsonlParser(),
    private val tokenUsageExtractor: CodexUsageExtractor = CodexUsageExtractor(),
    private val skillCatalog: SkillCatalog = SkillCatalog(),
    private val skillUsageMatcher: CodexSkillUsageMatcher = CodexSkillUsageMatcher(),
    private val projectSessionMatcher: CodexProjectSessionMatcher = CodexProjectSessionMatcher(),
    private val efficiencySummaryCalculator: CodexEfficiencySummaryCalculator = CodexEfficiencySummaryCalculator(),
) : RunInsightsService {
    override fun buildReport(
        projectRoot: Path,
        settings: SkillOpsInsightsSettings,
    ): SkillOpsRunInsightsReport {
        val normalizedSettings = settings.normalized()
        val scanResult = sessionFileScanner.scan(normalizedSettings)
        val skillNames = skillCatalog.discover(projectRoot)
        val warnings = scanResult.warnings.toMutableList()

        if (skillNames.isEmpty()) {
            warnings += "No SkillOps skills found under .agents/skills/."
        }

        val parsedSessions = scanResult.files.map { sessionFile ->
            sessionFile to parser.parse(sessionFile.path)
        }
        val projectSessions = parsedSessions.filter { (_, parseResult) ->
            projectSessionMatcher.belongsToProject(parseResult.events, projectRoot) != false
        }
        val skippedSessionCount = parsedSessions.size - projectSessions.size
        if (skippedSessionCount > 0) {
            warnings += "Ignored $skippedSessionCount Codex session(s) belonging to other projects."
        }

        val insights = projectSessions.map { (sessionFile, parseResult) ->
            val tokenUsage = tokenUsageExtractor.extract(parseResult.events)
            val matchedSkillNames = skillUsageMatcher.matchSkills(parseResult.events, skillNames)
            val recordedSkillNames = skillUsageMatcher.detectRecordedSkillNames(parseResult.events)
            val efficiencySummary = efficiencySummaryCalculator.calculate(
                events = parseResult.events,
                tokenUsage = tokenUsage,
                sizeBytes = sessionFile.sizeBytes,
                settings = normalizedSettings,
            )

            SkillRunInsight(
                sessionPath = sessionFile.path,
                sessionFileName = sessionFile.fileName,
                lastModifiedMs = sessionFile.lastModifiedMs,
                sizeBytes = sessionFile.sizeBytes,
                matchedSkillName = matchedSkillNames.firstOrNull(),
                matchedSkillNames = matchedSkillNames,
                recordedSkillNames = recordedSkillNames.ifEmpty { matchedSkillNames },
                invocationCommand = skillUsageMatcher.invocationCommand(parseResult.events),
                tokenUsage = tokenUsage,
                efficiencySummary = efficiencySummary,
                warnings = parseResult.warnings + efficiencySummary.warnings,
            )
        }

        return SkillOpsRunInsightsReport(
            insights = insights,
            warnings = warnings,
        )
    }
}

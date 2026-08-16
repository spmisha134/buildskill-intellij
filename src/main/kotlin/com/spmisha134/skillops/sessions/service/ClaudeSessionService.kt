package com.spmisha134.skillops.sessions.service

import com.spmisha134.skillops.insights.claude.ClaudeSessionFileScanner
import com.spmisha134.skillops.insights.claude.ClaudeStreamingAccumulator
import com.spmisha134.skillops.insights.parser.InsightJsonlParser
import com.spmisha134.skillops.insights.run.RunInsightsProgress
import com.spmisha134.skillops.insights.run.SkillCatalog
import com.spmisha134.skillops.insights.settings.SkillOpsInsightsSettings
import com.spmisha134.skillops.sessions.model.ClaudeSession
import com.spmisha134.skillops.sessions.model.ClaudeSessionsResult
import com.spmisha134.skillops.sessions.model.SessionResumeTarget
import java.nio.file.Path

class ClaudeSessionService(
    private val scanner: ClaudeSessionFileScanner = ClaudeSessionFileScanner(),
    private val parser: InsightJsonlParser = InsightJsonlParser(),
    private val skillCatalog: SkillCatalog = SkillCatalog(),
) {
    fun findProjectSessions(
        projectRoot: Path,
        settings: SkillOpsInsightsSettings,
        progress: RunInsightsProgress = RunInsightsProgress.NONE,
    ): ClaudeSessionsResult {
        val scan = scanner.scan(settings)
        val skills = skillCatalog.discoverClaude(projectRoot)
        val warnings = scan.warnings.toMutableList()
        var unrelated = 0
        val sessions = scan.files.mapIndexedNotNull { index, file ->
            progress.checkCanceled()
            progress.update("Claude session ${index + 1} of ${scan.files.size}", index, scan.files.size)
            val paths = listOf(file.path) + scanner.subagentFiles(file.path)
            val accumulator = ClaudeStreamingAccumulator(projectRoot, skills)
            paths.forEach {
                warnings += parser.stream(
                    it,
                    progress.withPrefix("Claude session ${index + 1} of ${scan.files.size}"),
                    accumulator::accept,
                )
            }
            if (accumulator.belongsToProject() != true) {
                unrelated++
                return@mapIndexedNotNull null
            }
            val resumeTarget = accumulator.resumeTarget(file.fileName) ?: return@mapIndexedNotNull null
            ClaudeSession(
                resumeTarget = resumeTarget,
                initialPrompt = accumulator.invocationCommand(),
                skillNames = accumulator.recordedSkills() + accumulator.matchedSkills(),
                lastModifiedMs = file.lastModifiedMs,
                sessionPath = file.path,
            )
        }
        if (unrelated > 0) warnings += "Ignored $unrelated Claude session(s) belonging to other projects."
        return ClaudeSessionsResult(sessions, warnings.distinct())
    }

    fun findProjectSessions(projectRoot: Path, settings: SkillOpsInsightsSettings): ClaudeSessionsResult =
        findProjectSessions(projectRoot, settings, RunInsightsProgress.NONE)
}

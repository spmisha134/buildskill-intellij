package com.spmisha134.skillops.sessions.service

import com.spmisha134.skillops.insights.codex.CodexSessionFileScanner
import com.spmisha134.skillops.insights.codex.CodexStreamingAccumulator
import com.spmisha134.skillops.insights.parser.InsightJsonlParser
import com.spmisha134.skillops.insights.run.RunInsightsProgress
import com.spmisha134.skillops.insights.run.SkillCatalog
import com.spmisha134.skillops.insights.settings.SkillOpsInsightsSettings
import com.spmisha134.skillops.sessions.model.CodexSession
import com.spmisha134.skillops.sessions.model.CodexSessionsResult
import java.nio.file.Path

class CodexSessionService(
    private val scanner: CodexSessionFileScanner = CodexSessionFileScanner(),
    private val parser: InsightJsonlParser = InsightJsonlParser(),
    private val skillCatalog: SkillCatalog = SkillCatalog(),
) {
    fun findProjectSessions(
        projectRoot: Path,
        settings: SkillOpsInsightsSettings,
        progress: RunInsightsProgress = RunInsightsProgress.NONE,
    ): CodexSessionsResult {
        val scanResult = scanner.scan(settings)
        val skills = skillCatalog.discover(projectRoot)
        val warnings = scanResult.warnings.toMutableList()
        var unrelated = 0
        var missingMetadata = 0

        val sessions = scanResult.files.mapIndexedNotNull { index, file ->
            progress.checkCanceled()
            progress.update("Codex session ${index + 1} of ${scanResult.files.size}", index, scanResult.files.size)
            val accumulator = CodexStreamingAccumulator(projectRoot, skills)
            val parseWarnings = parser.stream(
                file.path,
                progress.withPrefix("Codex session ${index + 1} of ${scanResult.files.size}"),
                accumulator::accept,
            )
            warnings += parseWarnings
            if (accumulator.projectBelongsToProject() == false) {
                unrelated++
                return@mapIndexedNotNull null
            }
            val metadata = accumulator.resumeMetadata(file.fileName)
            if (metadata == null) {
                missingMetadata++
                return@mapIndexedNotNull null
            }
            CodexSession(
                resumeTarget = metadata.toResumeTarget(),
                initialPrompt = metadata.initialPrompt,
                skillNames = (accumulator.matchedSkillNames() + accumulator.recordedSkillNames()).distinctBy(String::lowercase),
                lastModifiedMs = file.lastModifiedMs,
                totalTokens = accumulator.tokenUsage()?.totalTokens,
                sessionPath = file.path,
            )
        }

        if (unrelated > 0) warnings += "Ignored $unrelated Codex session(s) belonging to other projects."
        if (missingMetadata > 0) warnings += "Ignored $missingMetadata Codex session(s) without a resumable session ID."
        return CodexSessionsResult(sessions, warnings.distinct())
    }

    fun findProjectSessions(projectRoot: Path, settings: SkillOpsInsightsSettings): CodexSessionsResult =
        findProjectSessions(projectRoot, settings, RunInsightsProgress.NONE)
}

package com.spmisha134.skillops.sessions.service

import com.spmisha134.skillops.insights.codex.CodexProjectSessionMatcher
import com.spmisha134.skillops.insights.codex.CodexSessionFileScanner
import com.spmisha134.skillops.insights.codex.CodexSkillUsageMatcher
import com.spmisha134.skillops.insights.codex.CodexUsageExtractor
import com.spmisha134.skillops.insights.parser.InsightJsonlParser
import com.spmisha134.skillops.insights.run.SkillCatalog
import com.spmisha134.skillops.insights.settings.SkillOpsInsightsSettings
import com.spmisha134.skillops.sessions.discovery.CodexSessionMetadataExtractor
import com.spmisha134.skillops.sessions.model.CodexSession
import com.spmisha134.skillops.sessions.model.CodexSessionsResult
import java.nio.file.Path

class CodexSessionService(
    private val scanner: CodexSessionFileScanner = CodexSessionFileScanner(),
    private val parser: InsightJsonlParser = InsightJsonlParser(),
    private val metadataExtractor: CodexSessionMetadataExtractor = CodexSessionMetadataExtractor(),
    private val projectMatcher: CodexProjectSessionMatcher = CodexProjectSessionMatcher(),
    private val skillCatalog: SkillCatalog = SkillCatalog(),
    private val skillMatcher: CodexSkillUsageMatcher = CodexSkillUsageMatcher(),
    private val usageExtractor: CodexUsageExtractor = CodexUsageExtractor(),
) {
    fun findProjectSessions(projectRoot: Path, settings: SkillOpsInsightsSettings): CodexSessionsResult {
        val scanResult = scanner.scan(settings)
        val skills = skillCatalog.discover(projectRoot)
        val warnings = scanResult.warnings.toMutableList()
        var unrelated = 0
        var missingMetadata = 0

        val sessions = scanResult.files.mapNotNull { file ->
            val parsed = parser.parse(file.path)
            warnings += parsed.warnings
            if (projectMatcher.belongsToProject(parsed.events, projectRoot) == false) {
                unrelated++
                return@mapNotNull null
            }
            val metadata = metadataExtractor.extract(parsed.events, file.fileName)
            if (metadata == null) {
                missingMetadata++
                return@mapNotNull null
            }
            val matchedSkills = skillMatcher.matchSkills(parsed.events, skills)
            val recordedSkills = skillMatcher.detectRecordedSkillNames(parsed.events)
            CodexSession(
                resumeTarget = metadata.toResumeTarget(),
                initialPrompt = metadata.initialPrompt,
                skillNames = (matchedSkills + recordedSkills).distinctBy(String::lowercase),
                lastModifiedMs = file.lastModifiedMs,
                totalTokens = usageExtractor.extract(parsed.events)?.totalTokens,
                sessionPath = file.path,
            )
        }

        if (unrelated > 0) warnings += "Ignored $unrelated Codex session(s) belonging to other projects."
        if (missingMetadata > 0) warnings += "Ignored $missingMetadata Codex session(s) without a resumable session ID."
        return CodexSessionsResult(sessions, warnings.distinct())
    }
}

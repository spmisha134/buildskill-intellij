package com.spmisha134.skillops.sessions.service

import com.spmisha134.skillops.insights.claude.ClaudeSessionFileScanner
import com.spmisha134.skillops.insights.claude.ClaudeSessionMatcher
import com.spmisha134.skillops.insights.claude.ClaudeSkillUsageMatcher
import com.spmisha134.skillops.insights.parser.InsightJsonlParser
import com.spmisha134.skillops.insights.run.SkillCatalog
import com.spmisha134.skillops.insights.settings.SkillOpsInsightsSettings
import com.spmisha134.skillops.sessions.model.ClaudeSession
import com.spmisha134.skillops.sessions.model.ClaudeSessionsResult
import com.spmisha134.skillops.sessions.model.SessionResumeTarget
import java.nio.file.Path

class ClaudeSessionService(
    private val scanner: ClaudeSessionFileScanner = ClaudeSessionFileScanner(),
    private val parser: InsightJsonlParser = InsightJsonlParser(),
    private val matcher: ClaudeSessionMatcher = ClaudeSessionMatcher(),
    private val skillCatalog: SkillCatalog = SkillCatalog(),
    private val skillMatcher: ClaudeSkillUsageMatcher = ClaudeSkillUsageMatcher(),
) {
    fun findProjectSessions(projectRoot: Path, settings: SkillOpsInsightsSettings): ClaudeSessionsResult {
        val scan = scanner.scan(settings)
        val skills = skillCatalog.discoverClaude(projectRoot)
        val warnings = scan.warnings.toMutableList()
        var unrelated = 0
        val sessions = scan.files.mapNotNull { file ->
            val paths = listOf(file.path) + scanner.subagentFiles(file.path)
            val parsed = paths.map(parser::parse)
            warnings += parsed.flatMap { it.warnings }
            val events = parsed.flatMap { it.events }
            if (matcher.belongsToProject(events, projectRoot) != true) {
                unrelated++
                return@mapNotNull null
            }
            val sessionId = events.firstNotNullOfOrNull { event ->
                event.payload?.get("sessionId")?.asString
            } ?: file.fileName.removeSuffix(".jsonl")
            val workingDirectory = events.firstNotNullOfOrNull { event ->
                event.payload?.get("cwd")?.takeIf { it.isJsonPrimitive }?.asString
            }?.let(Path::of)
            ClaudeSession(
                resumeTarget = SessionResumeTarget(sessionId, workingDirectory),
                initialPrompt = skillMatcher.invocationCommand(events),
                skillNames = skillMatcher.recordedSkillNames(events) + skillMatcher.matchSkills(events, skills),
                lastModifiedMs = file.lastModifiedMs,
                sessionPath = file.path,
            )
        }
        if (unrelated > 0) warnings += "Ignored $unrelated Claude session(s) belonging to other projects."
        return ClaudeSessionsResult(sessions, warnings.distinct())
    }
}

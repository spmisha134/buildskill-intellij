package com.spmisha134.skillops.sessions.service

import com.spmisha134.skillops.insights.settings.SkillOpsInsightsSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class ClaudeSessionServiceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `returns only resumable sessions for current project`() {
        val project = temporaryFolder.newFolder("project").toPath()
        Files.createDirectories(project.resolve(".claude/skills/reviewer"))
        val other = temporaryFolder.newFolder("other").toPath()
        val claudeHome = temporaryFolder.newFolder("claude").toPath()
        val sessions = Files.createDirectories(claudeHome.resolve("projects/key"))
        Files.writeString(sessions.resolve("session-123.jsonl"), """
            {"type":"user","sessionId":"session-123","cwd":"$project","message":{"content":"Review this repository using .claude/skills/reviewer"}}
            {"type":"assistant","sessionId":"session-123","cwd":"$project","attributionSkill":"reviewer","message":{"content":"done"}}
        """.trimIndent())
        Files.writeString(sessions.resolve("other-session.jsonl"), """
            {"type":"user","sessionId":"other-session","cwd":"$other","message":{"content":"Other work"}}
        """.trimIndent())

        val result = ClaudeSessionService().findProjectSessions(
            project,
            SkillOpsInsightsSettings(claudeHomePath = claudeHome.toString()),
        )

        assertEquals(1, result.sessions.size)
        assertEquals("session-123", result.sessions.single().resumeTarget.sessionId)
        assertTrue(result.sessions.single().skillNames.any { it.equals("reviewer", ignoreCase = true) })
        assertTrue(result.warnings.any { it.contains("Ignored 1 Claude session") })
    }
}

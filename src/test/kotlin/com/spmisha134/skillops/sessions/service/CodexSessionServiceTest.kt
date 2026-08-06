package com.spmisha134.skillops.sessions.service

import com.spmisha134.skillops.insights.settings.SkillOpsInsightsSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class CodexSessionServiceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `returns only resumable sessions for current project`() {
        val projectRoot = temporaryFolder.newFolder("project").toPath()
        Files.createDirectories(projectRoot.resolve(".agents/skills/session-helper"))
        val otherProject = temporaryFolder.newFolder("other").toPath()
        val codexHome = temporaryFolder.newFolder("codex").toPath()
        val sessions = Files.createDirectories(codexHome.resolve("sessions"))
        Files.writeString(sessions.resolve("rollout-current.jsonl"), """
            {"type":"session_meta","payload":{"id":"019cb301-f5cf-76c0-a1db-8ef3580d7800","cwd":"$projectRoot"}}
            {"type":"response_item","payload":{"type":"message","role":"user","content":[{"text":"# AGENTS.md instructions for the project"}]}}
            {"type":"event_msg","payload":{"type":"user_message","message":"Use .agents/skills/session-helper/SKILL.md for this task"}}
            {"type":"token_count","payload":{"usage":{"total_tokens":120}}}
        """.trimIndent())
        Files.writeString(sessions.resolve("rollout-other.jsonl"), """
            {"type":"session_meta","payload":{"id":"119cb301-f5cf-76c0-a1db-8ef3580d7800","cwd":"$otherProject"}}
        """.trimIndent())

        val result = CodexSessionService().findProjectSessions(
            projectRoot,
            SkillOpsInsightsSettings(codexHomePath = codexHome.toString()),
        )

        assertEquals(1, result.sessions.size)
        assertEquals("019cb301-f5cf-76c0-a1db-8ef3580d7800", result.sessions.single().resumeTarget.sessionId)
        assertEquals("Use .agents/skills/session-helper/SKILL.md for this task", result.sessions.single().initialPrompt)
        assertTrue(result.sessions.single().skillNames.contains("session-helper"))
        assertTrue(result.warnings.any { it.contains("Ignored 1 Codex session") })
    }
}

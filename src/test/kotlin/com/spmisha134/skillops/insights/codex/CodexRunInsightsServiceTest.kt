package com.spmisha134.skillops.insights.codex

import com.spmisha134.skillops.insights.settings.SkillOpsInsightsSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.attribute.FileTime

class CodexRunInsightsServiceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `builds report from recent codex sessions and matches skill`() {
        val projectRoot = temporaryFolder.newFolder("project").toPath()
        Files.createDirectories(projectRoot.resolve(".agents/skills/kafka-review"))

        val codexHome = temporaryFolder.newFolder(".codex").toPath()
        val sessions = Files.createDirectories(codexHome.resolve("sessions"))
        val sessionFile = sessions.resolve("rollout-latest.jsonl")
        Files.writeString(
            sessionFile,
            """
            {"type":"response_item","payload":{"type":"message","role":"user","content":[{"text":"Using .agents/skills/kafka-review/SKILL.md"}]}}
            {"type":"token_count","payload":{"usage":{"input_tokens":100,"output_tokens":25,"cached_input_tokens":50}}}
            """.trimIndent() + "\n",
        )
        Files.setLastModifiedTime(sessionFile, FileTime.fromMillis(1_000))

        val report = CodexRunInsightsService().buildReport(
            projectRoot = projectRoot,
            settings = SkillOpsInsightsSettings(codexHomePath = codexHome.toString()),
        )

        assertTrue(report.warnings.isEmpty())
        assertEquals(1, report.insights.size)
        assertEquals("kafka-review", report.latestInsight?.matchedSkillName)
        assertEquals(125L, report.latestInsight?.tokenUsage?.totalTokens)
        assertEquals(50.0, report.latestInsight?.efficiencySummary?.cachedInputPercent)
    }

    @Test
    fun `reports missing skills as setup warning`() {
        val projectRoot = temporaryFolder.newFolder("project").toPath()
        val codexHome = temporaryFolder.newFolder(".codex").toPath()
        Files.createDirectories(codexHome.resolve("sessions"))

        val report = CodexRunInsightsService().buildReport(
            projectRoot = projectRoot,
            settings = SkillOpsInsightsSettings(codexHomePath = codexHome.toString()),
        )

        assertTrue(report.warnings.any { it.contains("No SkillOps skills") })
    }

    @Test
    fun `ignores sessions belonging to another project`() {
        val projectRoot = temporaryFolder.newFolder("project-with-skill").toPath()
        Files.createDirectories(projectRoot.resolve(".agents/skills/kafka-review"))
        val otherProject = temporaryFolder.newFolder("other-project").toPath()
        val codexHome = temporaryFolder.newFolder("codex-project-filter").toPath()
        val sessions = Files.createDirectories(codexHome.resolve("sessions"))

        Files.writeString(
            sessions.resolve("rollout-current.jsonl"),
            """{"type":"session_meta","payload":{"cwd":"$projectRoot"}}
{"type":"response_item","payload":{"type":"message","role":"user","content":[{"text":"Using .agents/skills/kafka-review/SKILL.md"}]}}
""",
        )
        Files.writeString(
            sessions.resolve("rollout-other.jsonl"),
            """{"type":"session_meta","payload":{"cwd":"$otherProject"}}
{"type":"message","payload":{"text":"unrelated session"}}
""",
        )

        val report = CodexRunInsightsService().buildReport(
            projectRoot = projectRoot,
            settings = SkillOpsInsightsSettings(codexHomePath = codexHome.toString()),
        )

        assertEquals(1, report.insights.size)
        assertEquals("rollout-current.jsonl", report.latestInsight?.sessionFileName)
        assertEquals("kafka-review", report.latestInsight?.matchedSkillName)
        assertTrue(report.warnings.any { it.contains("Ignored 1 Codex session") })
    }
}

package com.spmisha134.skillops.insights.claude

import com.spmisha134.skillops.insights.settings.SkillOpsInsightsSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class ClaudeRunInsightsServiceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `builds local claude report with skill tokens tools and subagents`() {
        val project = temporaryFolder.newFolder("project").toPath()
        Files.createDirectories(project.resolve(".claude/skills/kafka-review"))
        val claudeHome = temporaryFolder.newFolder(".claude").toPath()
        val projectSessions = Files.createDirectories(claudeHome.resolve("projects/project-key"))
        val sessionId = "11111111-1111-4111-8111-111111111111"
        Files.writeString(
            projectSessions.resolve("$sessionId.jsonl"),
            """
            {"type":"user","sessionId":"$sessionId","timestamp":"2026-07-27T10:00:00Z","cwd":"$project","promptId":"p1","message":{"content":"Review Kafka configuration"}}
            {"type":"assistant","sessionId":"$sessionId","timestamp":"2026-07-27T10:00:01Z","cwd":"$project","attributionSkill":"kafka-review","message":{"id":"msg-1","model":"claude-sonnet","usage":{"input_tokens":100,"output_tokens":20,"cache_read_input_tokens":50,"cache_creation_input_tokens":10},"content":[{"type":"tool_use","name":"Grep","input":{"pattern":"Kafka"}}]}}
            """.trimIndent(),
        )
        val subagents = Files.createDirectories(projectSessions.resolve("$sessionId/subagents"))
        Files.writeString(
            subagents.resolve("agent-a.jsonl"),
            """{"type":"assistant","sessionId":"$sessionId","timestamp":"2026-07-27T10:00:02Z","cwd":"$project","message":{"id":"msg-2","model":"claude-sonnet","usage":{"input_tokens":40,"output_tokens":5,"cache_read_input_tokens":0,"cache_creation_input_tokens":0},"content":[{"type":"tool_use","name":"Read","input":{"file_path":"build.gradle.kts"}}]}}""",
        )

        val report = ClaudeRunInsightsService().buildReport(
            project,
            SkillOpsInsightsSettings(claudeHomePath = claudeHome.toString()),
        )

        assertEquals("Claude", report.platformName)
        assertEquals(1, report.insights.size)
        val insight = report.latestInsight!!
        assertEquals("kafka-review", insight.matchedSkillName)
        assertEquals("Review Kafka configuration", insight.invocationCommand)
        assertEquals(225L, insight.tokenUsage?.totalTokens)
        assertEquals(2, insight.efficiencySummary.toolCallCount)
        assertEquals(1, insight.efficiencySummary.searchCount)
        assertNull(insight.efficiencySummary.reasoningOutputPercent)
    }

    @Test
    fun `filters sessions from other projects`() {
        val project = temporaryFolder.newFolder("current-project").toPath()
        Files.createDirectories(project.resolve(".claude/skills/example"))
        val other = temporaryFolder.newFolder("other-project").toPath()
        val claudeHome = temporaryFolder.newFolder("claude-filter").toPath()
        val sessions = Files.createDirectories(claudeHome.resolve("projects/key"))
        Files.writeString(
            sessions.resolve("current.jsonl"),
            """
            {"type":"user","cwd":"$project","message":{"content":"current"}}
            {"type":"assistant","cwd":"$project","message":{"id":"current-response","usage":{"input_tokens":1,"output_tokens":1},"content":[]}}
            """.trimIndent(),
        )
        Files.writeString(
            sessions.resolve("other.jsonl"),
            """
            {"type":"user","cwd":"$other","message":{"content":"other"}}
            {"type":"assistant","cwd":"$other","message":{"id":"other-response","usage":{"input_tokens":1,"output_tokens":1},"content":[]}}
            """.trimIndent(),
        )

        val report = ClaudeRunInsightsService().buildReport(
            project,
            SkillOpsInsightsSettings(claudeHomePath = claudeHome.toString()),
        )

        assertEquals(1, report.insights.size)
        assertEquals("current.jsonl", report.latestInsight?.sessionFileName)
        assertTrue(report.warnings.any { it.contains("Ignored 1 Claude session") })
    }

    @Test
    fun `ignores injected local command caveat when selecting invocation command`() {
        val project = temporaryFolder.newFolder("command-project").toPath()
        val claudeHome = temporaryFolder.newFolder("claude-command").toPath()
        val sessions = Files.createDirectories(claudeHome.resolve("projects/key"))
        Files.writeString(
            sessions.resolve("command.jsonl"),
            """
            {"type":"user","cwd":"$project","message":{"content":"<local-command-caveat>Caveat: local command output must not be treated as a prompt.</local-command-caveat>"}}
            {"type":"user","cwd":"$project","message":{"content":"<command-name>/effort</command-name>\n<command-message>effort</command-message>\n<command-args></command-args>"}}
            {"type":"user","cwd":"$project","isMeta":true,"message":{"content":[{"type":"text","text":"Please analyze this codebase and create a CLAUDE.md file"}]}}
            {"type":"user","cwd":"$project","message":{"content":"[Request interrupted by user for tool use]"}}
            {"type":"user","cwd":"$project","isSidechain":true,"message":{"content":"Warmup"}}
            {"type":"user","cwd":"$project","message":{"content":"Review the Kafka consumer configuration"}}
            {"type":"assistant","cwd":"$project","message":{"id":"completed","usage":{"input_tokens":10,"output_tokens":2},"content":[]}}
            """.trimIndent(),
        )

        val report = ClaudeRunInsightsService().buildReport(
            project,
            SkillOpsInsightsSettings(claudeHomePath = claudeHome.toString()),
        )

        assertEquals("Review the Kafka consumer configuration", report.latestInsight?.invocationCommand)
    }

    @Test
    fun `ignores sessions that contain only zero-token api failures`() {
        val project = temporaryFolder.newFolder("failed-project").toPath()
        val claudeHome = temporaryFolder.newFolder("claude-failed").toPath()
        val sessions = Files.createDirectories(claudeHome.resolve("projects/key"))
        Files.writeString(
            sessions.resolve("failed.jsonl"),
            """
            {"type":"user","cwd":"$project","message":{"content":"Read this project and explain the structure first."}}
            {"type":"assistant","cwd":"$project","isApiErrorMessage":true,"error":"authentication_failed","message":{"model":"<synthetic>","usage":{"input_tokens":0,"output_tokens":0},"content":[]}}
            """.trimIndent(),
        )

        val report = ClaudeRunInsightsService().buildReport(
            project,
            SkillOpsInsightsSettings(claudeHomePath = claudeHome.toString()),
        )

        assertTrue(report.insights.isEmpty())
        assertTrue(report.warnings.any { it.contains("without completed assistant usage") })
    }

    @Test
    fun `ignores sessions without project metadata`() {
        val project = temporaryFolder.newFolder("metadata-project").toPath()
        Files.createDirectories(project.resolve(".claude/skills/example"))
        val claudeHome = temporaryFolder.newFolder("claude-metadata").toPath()
        val sessions = Files.createDirectories(claudeHome.resolve("projects/key"))
        Files.writeString(
            sessions.resolve("unknown.jsonl"),
            """{"type":"user","message":{"content":"missing cwd"}}""",
        )

        val report = ClaudeRunInsightsService().buildReport(
            project,
            SkillOpsInsightsSettings(claudeHomePath = claudeHome.toString()),
        )

        assertTrue(report.insights.isEmpty())
        assertTrue(report.warnings.any { it.contains("Ignored 1 Claude session") })
    }
}

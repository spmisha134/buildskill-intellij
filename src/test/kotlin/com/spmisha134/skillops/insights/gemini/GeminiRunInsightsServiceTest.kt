package com.spmisha134.skillops.insights.gemini

import com.spmisha134.skillops.insights.settings.SkillOpsInsightsSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class GeminiRunInsightsServiceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `builds project report with tokens tools searches and activated skill`() {
        val project = temporaryFolder.newFolder("project").toPath()
        Files.createDirectories(project.resolve(".gemini/skills/kafka-review"))
        val geminiHome = temporaryFolder.newFolder(".gemini").toPath()
        val cachedProject = Files.createDirectories(geminiHome.resolve("tmp/project-key"))
        Files.writeString(cachedProject.resolve(".project_root"), project.toString())
        val chats = Files.createDirectories(cachedProject.resolve("chats"))
        Files.writeString(
            chats.resolve("session-test.jsonl"),
            """
            {"kind":"session","sessionId":"s1","startTime":"2026-07-27T10:00:00Z"}
            {"type":"user","id":"u1","timestamp":"2026-07-27T10:00:01Z","content":"Review Kafka configuration"}
            {"type":"gemini","id":"g1","timestamp":"2026-07-27T10:00:02Z","model":"gemini-test","content":"done","tokens":{"input":100,"output":20,"cached":40,"thoughts":5,"tool":3,"total":168},"toolCalls":[{"name":"activate_skill","args":{"name":"kafka-review"}},{"name":"grep_search","args":{"pattern":"Kafka"}}]}
            """.trimIndent(),
        )

        val report = GeminiRunInsightsService().buildReport(
            project,
            SkillOpsInsightsSettings(geminiHomePath = geminiHome.toString()),
        )

        assertEquals("Gemini", report.platformName)
        assertEquals(1, report.insights.size)
        val insight = report.latestInsight!!
        assertEquals("kafka-review", insight.matchedSkillName)
        assertEquals("Review Kafka configuration", insight.invocationCommand)
        assertEquals(168L, insight.tokenUsage?.totalTokens)
        assertEquals(3L, insight.tokenUsage?.toolTokens)
        assertEquals(2, insight.efficiencySummary.toolCallCount)
        assertEquals(1, insight.efficiencySummary.searchCount)
        assertEquals(25.0, insight.efficiencySummary.reasoningOutputPercent)
        assertNull(insight.tokenUsage?.rateLimitUsedPercent)
    }

    @Test
    fun `filters sessions using project root mapping`() {
        val project = temporaryFolder.newFolder("current").toPath()
        Files.createDirectories(project.resolve(".gemini/skills/example"))
        val other = temporaryFolder.newFolder("other").toPath()
        val geminiHome = temporaryFolder.newFolder("gemini-filter").toPath()
        createSession(geminiHome, "current-key", project)
        createSession(geminiHome, "other-key", other)

        val report = GeminiRunInsightsService().buildReport(
            project,
            SkillOpsInsightsSettings(geminiHomePath = geminiHome.toString()),
        )

        assertEquals(1, report.insights.size)
        assertTrue(report.warnings.any { it.contains("Ignored 1 Gemini session") })
    }

    @Test
    fun `does not include sessions mapped to a nested repository`() {
        val project = temporaryFolder.newFolder("parent-project").toPath()
        Files.createDirectories(project.resolve(".gemini/skills/example"))
        val nestedProject = Files.createDirectories(project.resolve("nested-project"))
        val geminiHome = temporaryFolder.newFolder("gemini-nested-filter").toPath()
        createSession(geminiHome, "parent-key", project)
        createSession(geminiHome, "nested-key", nestedProject)

        val report = GeminiRunInsightsService().buildReport(
            project,
            SkillOpsInsightsSettings(geminiHomePath = geminiHome.toString()),
        )

        assertEquals(1, report.insights.size)
        assertEquals("session-parent-key.jsonl", report.latestInsight?.sessionFileName)
        assertTrue(report.warnings.any { it.contains("Ignored 1 Gemini session") })
    }

    @Test
    fun `ignores header only authentication aborted session`() {
        val project = temporaryFolder.newFolder("aborted-project").toPath()
        Files.createDirectories(project.resolve(".gemini/skills/example"))
        val geminiHome = temporaryFolder.newFolder("gemini-aborted").toPath()
        val cachedProject = Files.createDirectories(geminiHome.resolve("tmp/project-key"))
        Files.writeString(cachedProject.resolve(".project_root"), project.toString())
        val chats = Files.createDirectories(cachedProject.resolve("chats"))
        Files.writeString(
            chats.resolve("session-aborted.jsonl"),
            """
            {"kind":"session","sessionId":"s1","startTime":"2026-07-27T10:00:00Z"}
            {"${'$'}set":{"lastUpdated":"2026-07-27T10:00:01Z"}}
            """.trimIndent(),
        )

        val report = GeminiRunInsightsService().buildReport(
            project,
            SkillOpsInsightsSettings(geminiHomePath = geminiHome.toString()),
        )

        assertTrue(report.insights.isEmpty())
        assertTrue(report.warnings.any { it.contains("authentication-aborted Gemini session") })
    }

    private fun createSession(geminiHome: java.nio.file.Path, key: String, project: java.nio.file.Path) {
        val cachedProject = Files.createDirectories(geminiHome.resolve("tmp/$key"))
        Files.writeString(cachedProject.resolve(".project_root"), project.toString())
        val chats = Files.createDirectories(cachedProject.resolve("chats"))
        Files.writeString(
            chats.resolve("session-$key.jsonl"),
            """{"type":"user","id":"u-$key","content":"hello"}""",
        )
    }
}

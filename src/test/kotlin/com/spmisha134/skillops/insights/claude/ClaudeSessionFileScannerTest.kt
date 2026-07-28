package com.spmisha134.skillops.insights.claude

import com.spmisha134.skillops.insights.settings.SkillOpsInsightsSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class ClaudeSessionFileScannerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `discovers main transcripts and excludes sidecars and subagents`() {
        val claudeHome = temporaryFolder.newFolder(".claude").toPath()
        val directory = Files.createDirectories(claudeHome.resolve("projects/key"))
        val sessionId = "session-1"
        Files.writeString(directory.resolve("$sessionId.jsonl"), "{}")
        Files.writeString(directory.resolve("$sessionId.cost.jsonl"), "{}")
        Files.writeString(directory.resolve("$sessionId.turn-boundaries.jsonl"), "{}")
        val subagents = Files.createDirectories(directory.resolve("$sessionId/subagents"))
        Files.writeString(subagents.resolve("agent-a.jsonl"), "{}")
        Files.writeString(
            directory.resolve("agent-sibling.jsonl"),
            """{"type":"assistant","sessionId":"$sessionId"}""",
        )
        Files.writeString(
            directory.resolve("agent-other.jsonl"),
            """{"type":"assistant","sessionId":"other-session"}""",
        )

        val scanner = ClaudeSessionFileScanner()
        val result = scanner.scan(SkillOpsInsightsSettings(claudeHomePath = claudeHome.toString()))

        assertEquals(listOf("$sessionId.jsonl"), result.files.map { it.fileName })
        assertEquals(2, scanner.subagentFiles(result.files.single().path).size)
    }
}

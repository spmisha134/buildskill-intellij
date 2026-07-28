package com.spmisha134.skillops.insights.gemini

import com.spmisha134.skillops.insights.settings.SkillOpsInsightsSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class GeminiSessionFileScannerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `discovers session files with mapped project root`() {
        val userHome = temporaryFolder.newFolder("home").toPath()
        val geminiHome = Files.createDirectories(userHome.resolve(".gemini"))
        val project = temporaryFolder.newFolder("project").toPath()
        val projectCache = Files.createDirectories(geminiHome.resolve("tmp/project-key"))
        Files.writeString(projectCache.resolve(".project_root"), project.toString())
        val chats = Files.createDirectories(projectCache.resolve("chats"))
        Files.writeString(chats.resolve("session-one.jsonl"), "{}")
        Files.writeString(chats.resolve("ignored.jsonl"), "{}")

        val result = GeminiSessionFileScanner(userHome).scan(
            SkillOpsInsightsSettings(geminiHomePath = geminiHome.toString()),
        )

        assertTrue(result.warnings.isEmpty())
        assertEquals(1, result.files.size)
        assertEquals(project.toAbsolutePath().normalize(), result.files.single().projectRoot)
        assertEquals("session-one.jsonl", result.files.single().fileName)
    }
}

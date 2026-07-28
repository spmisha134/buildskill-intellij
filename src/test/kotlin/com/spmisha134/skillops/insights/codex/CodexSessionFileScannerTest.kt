package com.spmisha134.skillops.insights.codex

import com.spmisha134.skillops.insights.settings.SkillOpsInsightsSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

class CodexSessionFileScannerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `scanner finds jsonl files from fake codex home`() {
        val codexHome = temporaryFolder.newFolder(".codex").toPath()
        val sessions = Files.createDirectories(codexHome.resolve("sessions/2026/07/04"))
        val sessionFile = createSessionFile(sessions.resolve("rollout-a.jsonl"), 1_000)
        Files.writeString(sessions.resolve("notes.txt"), "ignore")

        val result = CodexSessionFileScanner().scan(settingsFor(codexHome))

        assertTrue(result.warnings.isEmpty())
        assertEquals(1, result.files.size)
        assertEquals(sessionFile.toAbsolutePath().normalize(), result.files.single().path)
        assertEquals("rollout-a.jsonl", result.files.single().fileName)
        assertEquals(1_000, result.files.single().lastModifiedMs)
        assertEquals(3, result.files.single().sizeBytes)
    }

    @Test
    fun `scanner expands tilde to supplied user home`() {
        val userHome = temporaryFolder.newFolder("home").toPath()
        val codexHome = Files.createDirectories(userHome.resolve(".codex"))
        val sessions = Files.createDirectories(codexHome.resolve("sessions"))
        createSessionFile(sessions.resolve("rollout-home.jsonl"), 1_000)

        val result = CodexSessionFileScanner(userHomePath = userHome).scan(SkillOpsInsightsSettings(codexHomePath = "~/.codex"))

        assertTrue(result.warnings.isEmpty())
        assertEquals(listOf("rollout-home.jsonl"), result.files.map { it.fileName })
    }

    @Test
    fun `scanner prefers rollout files and then sorts newest first`() {
        val codexHome = temporaryFolder.newFolder(".codex").toPath()
        val sessions = Files.createDirectories(codexHome.resolve("sessions"))
        createSessionFile(sessions.resolve("session-newer.jsonl"), 3_000)
        createSessionFile(sessions.resolve("rollout-older.jsonl"), 1_000)
        createSessionFile(sessions.resolve("rollout-newer.jsonl"), 2_000)

        val result = CodexSessionFileScanner().scan(settingsFor(codexHome))

        assertEquals(
            listOf("rollout-newer.jsonl", "rollout-older.jsonl", "session-newer.jsonl"),
            result.files.map { it.fileName },
        )
    }

    @Test
    fun `scanner limits discovered files after sorting`() {
        val codexHome = temporaryFolder.newFolder(".codex").toPath()
        val sessions = Files.createDirectories(codexHome.resolve("sessions"))
        createSessionFile(sessions.resolve("rollout-a.jsonl"), 1_000)
        createSessionFile(sessions.resolve("rollout-b.jsonl"), 2_000)
        createSessionFile(sessions.resolve("rollout-c.jsonl"), 3_000)

        val result = CodexSessionFileScanner().scan(settingsFor(codexHome, maxSessionsToScan = 2))

        assertEquals(listOf("rollout-c.jsonl", "rollout-b.jsonl"), result.files.map { it.fileName })
    }

    @Test
    fun `missing codex home returns empty result with warning`() {
        val missingCodexHome = temporaryFolder.root.toPath().resolve("missing")

        val result = CodexSessionFileScanner().scan(settingsFor(missingCodexHome))

        assertTrue(result.files.isEmpty())
        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings.single().contains("Codex home does not exist"))
    }

    @Test
    fun `missing sessions folder returns empty result with warning`() {
        val codexHome = temporaryFolder.newFolder(".codex").toPath()

        val result = CodexSessionFileScanner().scan(settingsFor(codexHome))

        assertTrue(result.files.isEmpty())
        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings.single().contains("Codex sessions folder does not exist"))
    }

    private fun settingsFor(
        codexHome: Path,
        maxSessionsToScan: Int = 200,
    ): SkillOpsInsightsSettings =
        SkillOpsInsightsSettings(
            codexHomePath = codexHome.toString(),
            maxSessionsToScan = maxSessionsToScan,
        )

    private fun createSessionFile(path: Path, lastModifiedMs: Long): Path {
        Files.createDirectories(path.parent)
        Files.writeString(path, "{}\n")
        Files.setLastModifiedTime(path, FileTime.fromMillis(lastModifiedMs))
        return path
    }
}

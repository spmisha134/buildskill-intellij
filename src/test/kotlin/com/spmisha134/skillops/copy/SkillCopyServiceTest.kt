package com.spmisha134.skillops.copy

import com.spmisha134.skillops.model.copy.SkillConflictPolicy
import com.spmisha134.skillops.model.copy.SkillCopyRequest
import com.spmisha134.skillops.model.copy.SkillCopyStatus
import com.spmisha134.skillops.model.skill.SkillPlatform
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

class SkillCopyServiceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `converts every directed platform pair and preserves supporting content`() {
        SkillPlatform.entries.forEach { source ->
            SkillPlatform.entries.filter { it != source }.forEach { target ->
                val project = temporaryFolder.newFolder("${source.name}-${target.name}").toPath()
                createSkill(project, source, "review-code", includeOpenAi = source == SkillPlatform.CODEX)

                val result = copy(project, source, target, SkillConflictPolicy.SKIP)

                assertEquals(SkillCopyStatus.COPIED, result.status)
                val destination = skillPath(project, target, "review-code")
                assertTrue(Files.isRegularFile(destination.resolve("SKILL.md")))
                assertTrue(destination.resolve("SKILL.md").readText().contains("name: review-code"))
                assertTrue(Files.isDirectory(destination.resolve("scripts")))
                assertArrayEquals(byteArrayOf(0, 1, 2, -1), Files.readAllBytes(destination.resolve("assets/icon.bin")))
                assertEquals("Keep Codex in this user-authored example.", destination.resolve("references/notes.md").readText())
                assertEquals(target == SkillPlatform.CODEX, Files.exists(destination.resolve("agents/openai.yaml")))
            }
        }
    }

    @Test
    fun `rename selects first available suffix and updates metadata`() {
        val project = temporaryFolder.newFolder("rename").toPath()
        createSkill(project, SkillPlatform.CODEX, "review-code", true)
        createSkill(project, SkillPlatform.CLAUDE, "review-code", false)
        createSkill(project, SkillPlatform.CLAUDE, "review-code-2", false)
        createSkill(project, SkillPlatform.CLAUDE, "review-code-3", false)

        val result = copy(project, SkillPlatform.CODEX, SkillPlatform.CLAUDE, SkillConflictPolicy.RENAME)

        assertEquals(SkillCopyStatus.RENAMED, result.status)
        assertEquals("review-code-4", result.targetName)
        assertTrue(skillPath(project, SkillPlatform.CLAUDE, "review-code-4").resolve("SKILL.md").readText().contains("name: review-code-4"))
    }

    @Test
    fun `replace removes stale files and installs converted skill`() {
        val project = temporaryFolder.newFolder("replace").toPath()
        createSkill(project, SkillPlatform.CODEX, "review-code", true)
        val oldTarget = createSkill(project, SkillPlatform.CLAUDE, "review-code", false)
        oldTarget.resolve("stale.txt").writeText("old")

        val result = copy(project, SkillPlatform.CODEX, SkillPlatform.CLAUDE, SkillConflictPolicy.REPLACE)

        assertEquals(SkillCopyStatus.REPLACED, result.status)
        assertFalse(Files.exists(oldTarget.resolve("stale.txt")))
        assertTrue(Files.exists(oldTarget.resolve("assets/icon.bin")))
    }

    @Test
    fun `skip leaves existing target unchanged`() {
        val project = temporaryFolder.newFolder("skip").toPath()
        createSkill(project, SkillPlatform.CODEX, "review-code", true)
        val target = createSkill(project, SkillPlatform.CLAUDE, "review-code", false)
        target.resolve("marker.txt").writeText("unchanged")

        val result = copy(project, SkillPlatform.CODEX, SkillPlatform.CLAUDE, SkillConflictPolicy.SKIP)

        assertEquals(SkillCopyStatus.SKIPPED, result.status)
        assertEquals("unchanged", target.resolve("marker.txt").readText())
    }

    @Test
    fun `invalid source fails without creating target`() {
        val project = temporaryFolder.newFolder("invalid").toPath()
        val source = skillPath(project, SkillPlatform.CODEX, "broken")
        Files.createDirectories(source)
        source.resolve("SKILL.md").writeText("No front matter")

        val result = SkillCopyService().copy(
            SkillCopyRequest(project, SkillPlatform.CODEX, SkillPlatform.CLAUDE, listOf("broken"), SkillConflictPolicy.SKIP)
        ).items.single()

        assertEquals(SkillCopyStatus.FAILED, result.status)
        assertFalse(Files.exists(skillPath(project, SkillPlatform.CLAUDE, "broken")))
    }

    private fun copy(
        project: Path,
        source: SkillPlatform,
        target: SkillPlatform,
        policy: SkillConflictPolicy,
    ) = SkillCopyService().copy(
        SkillCopyRequest(project, source, target, listOf("review-code"), policy)
    ).items.single()

    private fun createSkill(project: Path, platform: SkillPlatform, name: String, includeOpenAi: Boolean): Path {
        val directory = skillPath(project, platform, name)
        Files.createDirectories(directory.resolve("references"))
        Files.createDirectories(directory.resolve("scripts"))
        Files.createDirectories(directory.resolve("assets"))
        directory.resolve("SKILL.md").writeText(
            """
            ---
            name: $name
            description: Review code safely
            custom-field: keep-me
            ---

            Follow references/notes.md.
            """.trimIndent() + "\n"
        )
        directory.resolve("references/notes.md").writeText("Keep Codex in this user-authored example.")
        directory.resolve("assets/icon.bin").writeBytes(byteArrayOf(0, 1, 2, -1))
        if (includeOpenAi) {
            Files.createDirectories(directory.resolve("agents"))
            directory.resolve("agents/openai.yaml").writeText("interface:\n  default_prompt: Use \$$name\n")
        }
        return directory
    }

    private fun skillPath(project: Path, platform: SkillPlatform, name: String): Path =
        project.resolve(platform.projectDirectory).resolve("skills").resolve(name)
}

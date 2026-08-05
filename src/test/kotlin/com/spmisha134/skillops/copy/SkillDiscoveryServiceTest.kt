package com.spmisha134.skillops.copy

import com.spmisha134.skillops.copy.discovery.SkillDiscoveryService
import com.spmisha134.skillops.model.skill.SkillPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import kotlin.io.path.writeText

class SkillDiscoveryServiceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `discovers valid and invalid direct child skills in sorted order`() {
        val project = temporaryFolder.newFolder("project").toPath()
        val root = Files.createDirectories(project.resolve(".agents/skills"))
        Files.createDirectories(root.resolve("z-valid")).resolve("SKILL.md").writeText(
            "---\nname: z-valid\ndescription: Valid skill\n---\nBody"
        )
        Files.createDirectories(root.resolve("a-invalid"))
        root.resolve("ignored.txt").writeText("not a skill")

        val skills = SkillDiscoveryService().discover(project, SkillPlatform.CODEX)

        assertEquals(listOf("a-invalid", "z-valid"), skills.map { it.folderName })
        assertFalse(skills.first().isValid)
        assertTrue(skills.last().isValid)
    }

    @Test
    fun `missing platform root is an empty result`() {
        val project = temporaryFolder.newFolder("empty").toPath()
        assertTrue(SkillDiscoveryService().discover(project, SkillPlatform.CLAUDE).isEmpty())
    }
}

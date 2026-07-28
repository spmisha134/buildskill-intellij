package com.spmisha134.skillops.insights.run

import java.nio.file.Files
import java.nio.file.Path

class SkillCatalog {
    fun discover(projectRoot: Path): List<String> =
        discover(projectRoot, ".agents")

    fun discoverClaude(projectRoot: Path): List<String> =
        discover(projectRoot, ".claude")

    fun discoverGemini(projectRoot: Path): List<String> =
        discover(projectRoot, ".gemini")

    private fun discover(projectRoot: Path, platformDirectory: String): List<String> {
        val skillsRoot = projectRoot.resolve(platformDirectory).resolve("skills")
        if (!Files.isDirectory(skillsRoot)) {
            return emptyList()
        }

        return Files.newDirectoryStream(skillsRoot).use { entries ->
            entries
                .filter { Files.isDirectory(it) }
                .map { it.fileName.toString() }
                .sorted()
                .toList()
        }
    }
}

package com.spmisha134.skillops.copy.discovery

import com.spmisha134.skillops.copy.conversion.SkillMarkdown
import com.spmisha134.skillops.model.copy.DiscoveredSkill
import com.spmisha134.skillops.model.skill.SkillPlatform
import com.spmisha134.skillops.generator.SkillNameNormalizer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

class SkillDiscoveryService {
    fun discover(projectRoot: Path, platform: SkillPlatform): List<DiscoveredSkill> {
        val root = projectRoot.resolve(platform.projectDirectory).resolve("skills").normalize()
        if (!Files.isDirectory(root)) return emptyList()

        return Files.list(root).use { paths ->
            paths.filter(Files::isDirectory)
                .map { inspect(it) }
                .sorted(compareBy(String.CASE_INSENSITIVE_ORDER) { it.folderName })
                .toList()
        }
    }

    private fun inspect(path: Path): DiscoveredSkill {
        val folderName = path.fileName.toString()
        if (Files.isSymbolicLink(path)) return invalid(folderName, path, "Symbolic-link skill directories are not supported.")
        if (runCatching { SkillNameNormalizer.normalize(folderName) }.getOrNull() != folderName) {
            return invalid(folderName, path, "Skill folder name must be normalized and safe.")
        }
        val skillMd = path.resolve("SKILL.md")
        if (!Files.isRegularFile(skillMd)) return invalid(folderName, path, "SKILL.md is required.")
        val markdown = runCatching { SkillMarkdown.parse(skillMd.readText()) }.getOrNull()
            ?: return invalid(folderName, path, "SKILL.md must contain YAML front matter.")
        if (markdown.name.isBlank()) return invalid(folderName, path, "SKILL.md front matter must include name.")
        if (markdown.description.isBlank()) return invalid(folderName, path, "SKILL.md front matter must include description.")
        return DiscoveredSkill(folderName, markdown.name, path, true)
    }

    private fun invalid(name: String, path: Path, issue: String) =
        DiscoveredSkill(name, null, path, false, issue)
}

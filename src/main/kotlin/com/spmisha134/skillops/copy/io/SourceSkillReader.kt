package com.spmisha134.skillops.copy.io

import com.spmisha134.skillops.copy.conversion.SkillMarkdown
import com.spmisha134.skillops.model.copy.PortableSkill
import com.spmisha134.skillops.model.skill.SkillPlatform
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readBytes

class SourceSkillReader {
    fun read(skillDirectory: Path, platform: SkillPlatform): PortableSkill {
        require(Files.isDirectory(skillDirectory)) { "Source skill does not exist: $skillDirectory" }
        val root = skillDirectory.toRealPath()
        val files = linkedMapOf<Path, ByteArray>()
        val directories = linkedSetOf<Path>()
        val executableFiles = linkedSetOf<Path>()
        Files.walk(root).use { paths ->
            paths.forEach { path ->
                if (path == root) return@forEach
                if (Files.isSymbolicLink(path)) throw IllegalArgumentException("Symbolic links are not supported: ${root.relativize(path)}")
                val relative = root.relativize(path).normalize()
                require(!relative.isAbsolute && !relative.startsWith("..")) { "Unsafe source path: $relative" }
                if (Files.isDirectory(path)) directories.add(relative)
                if (Files.isRegularFile(path)) {
                    files[relative] = path.readBytes()
                    if (Files.isExecutable(path)) executableFiles.add(relative)
                }
            }
        }
        val markdown = files[Path.of("SKILL.md")]
            ?.toString(Charsets.UTF_8)
            ?.let(SkillMarkdown::parse)
            ?: throw IllegalArgumentException("SKILL.md must contain valid front matter.")
        require(markdown.name.isNotBlank()) { "SKILL.md front matter must include name." }
        require(markdown.description.isNotBlank()) { "SKILL.md front matter must include description." }
        return PortableSkill(
            skillDirectory.fileName.toString(), markdown.name, markdown.description, platform,
            files, directories, executableFiles,
        )
    }
}

package com.spmisha134.skillops.copy.validation

import com.spmisha134.skillops.copy.conversion.SkillMarkdown
import com.spmisha134.skillops.model.skill.SkillPlatform
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

class PlatformSkillValidator {
    fun validate(directory: Path, platform: SkillPlatform, expectedName: String): List<String> {
        val issues = mutableListOf<String>()
        val skillMd = directory.resolve("SKILL.md")
        val markdown = if (Files.isRegularFile(skillMd)) SkillMarkdown.parse(skillMd.readText()) else null
        if (markdown == null) issues += "SKILL.md with YAML front matter is required."
        else {
            if (markdown.name != expectedName) issues += "SKILL.md name must be $expectedName."
            if (markdown.description.isBlank()) issues += "SKILL.md description is required."
        }
        val openAiYaml = directory.resolve("agents/openai.yaml")
        if (platform == SkillPlatform.CODEX) {
            if (!Files.isRegularFile(openAiYaml)) issues += "agents/openai.yaml is required for Codex."
            else {
                val yaml = openAiYaml.readText()
                if (!yaml.contains("interface:") || !yaml.contains("\$$expectedName")) {
                    issues += "agents/openai.yaml is invalid for $expectedName."
                }
            }
        } else if (Files.exists(openAiYaml)) {
            issues += "agents/openai.yaml is not valid target metadata for ${platform.displayName}."
        }
        return issues
    }
}

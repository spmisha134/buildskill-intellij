package com.spmisha134.skillops.copy.conversion

import com.spmisha134.skillops.generator.openai.OpenAiYamlRenderer
import com.spmisha134.skillops.model.copy.PortableSkill
import com.spmisha134.skillops.model.copy.ConvertedSkill
import com.spmisha134.skillops.model.skill.SkillDefinition
import com.spmisha134.skillops.model.skill.SkillPlatform
import java.nio.file.Path

class PlatformSkillConverter(
    private val openAiYamlRenderer: OpenAiYamlRenderer = OpenAiYamlRenderer(),
) {
    fun convert(skill: PortableSkill, target: SkillPlatform, targetName: String): ConvertedSkill {
        val converted = linkedMapOf<Path, ByteArray>()
        skill.files.forEach { (path, bytes) ->
            if (path == OPENAI_YAML) return@forEach
            converted[path] = bytes
        }
        val sourceMarkdown = skill.files.getValue(SKILL_MD).toString(Charsets.UTF_8)
        val markdown = requireNotNull(SkillMarkdown.parse(sourceMarkdown))
        converted[SKILL_MD] = markdown.render(targetName).toByteArray()
        if (target == SkillPlatform.CODEX) {
            converted[OPENAI_YAML] = openAiYamlRenderer.render(
                SkillDefinition(targetName, skill.description)
            ).toByteArray()
        }
        val directories = skill.directories.filterNot { it == Path.of("agents") && target != SkillPlatform.CODEX }.toSet()
        return ConvertedSkill(converted, directories, skill.executableFiles.intersect(converted.keys))
    }

    companion object {
        private val SKILL_MD = Path.of("SKILL.md")
        private val OPENAI_YAML = Path.of("agents/openai.yaml")
    }
}

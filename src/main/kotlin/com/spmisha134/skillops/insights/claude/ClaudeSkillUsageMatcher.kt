package com.spmisha134.skillops.insights.claude

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.spmisha134.skillops.insights.parser.RawInsightEvent
import com.spmisha134.skillops.insights.parser.stringOrNull

class ClaudeSkillUsageMatcher {
    fun matchSkills(events: List<RawInsightEvent>, repositorySkills: List<String>): List<String> {
        val attributed = events.mapNotNull {
            it.payload?.get("attributionSkill")?.stringOrNull()
        }.distinctBy(String::lowercase)
        if (attributed.isNotEmpty()) {
            return repositorySkills.filter { skill ->
                attributed.any { it.equals(skill, ignoreCase = true) }
            }
        }

        val userText = events.mapNotNull(::userText).joinToString("\n").lowercase()
        return repositorySkills.filter { skill ->
            userText.contains(".claude/skills/${skill.lowercase()}") ||
                userText.contains("skill: ${skill.lowercase()}")
        }
    }

    fun recordedSkillNames(events: List<RawInsightEvent>): List<String> =
        events.mapNotNull { it.payload?.get("attributionSkill")?.stringOrNull() }
            .distinctBy(String::lowercase)

    fun invocationCommand(events: List<RawInsightEvent>): String? =
        events.asSequence()
            .mapNotNull(::userText)
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.isSyntheticClaudeControlMessage() }
            .lastOrNull()

    private fun userText(event: RawInsightEvent): String? {
        val payload = event.payload ?: return null
        if (payload.get("type")?.stringOrNull() != "user") return null
        if (payload.get("isMeta")?.takeIf(JsonElement::isJsonPrimitive)?.asBoolean == true) return null
        if (payload.get("isSidechain")?.takeIf(JsonElement::isJsonPrimitive)?.asBoolean == true) return null
        val content = payload.getAsJsonObject("message")?.get("content") ?: return null
        return when {
            content.isJsonPrimitive && content.asJsonPrimitive.isString -> content.asString
            content.isJsonArray -> content.asJsonArray.textBlocks()
            else -> null
        }
    }

    private fun JsonArray.textBlocks(): String =
        mapNotNull { block ->
            block.takeIf(JsonElement::isJsonObject)?.asJsonObject
                ?.takeIf { it.get("type")?.stringOrNull() == "text" }
                ?.get("text")?.stringOrNull()
        }.joinToString("\n")

    private fun String.isSyntheticClaudeControlMessage(): Boolean =
        CLAUDE_CONTROL_TAGS.any { tag -> contains("<$tag", ignoreCase = true) } ||
            equals("[Request interrupted by user for tool use]", ignoreCase = true)

    companion object {
        private val CLAUDE_CONTROL_TAGS = listOf(
            "local-command-caveat",
            "local-command-stdout",
            "local-command-stderr",
            "command-name",
            "command-message",
            "command-args",
        )
    }
}

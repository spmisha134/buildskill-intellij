package com.spmisha134.skillops.insights.gemini

import com.spmisha134.skillops.insights.parser.RawInsightEvent
import com.spmisha134.skillops.insights.parser.objectOrNull
import com.spmisha134.skillops.insights.parser.stringAt
import com.spmisha134.skillops.insights.parser.stringOrNull

class GeminiSkillUsageMatcher {
    fun matchSkills(events: List<RawInsightEvent>, repositorySkills: List<String>): List<String> {
        val activated = activatedSkillNames(events)
        if (activated.isNotEmpty()) {
            return repositorySkills.filter { skill ->
                activated.any { it.equals(skill, ignoreCase = true) }
            }
        }
        val searchable = events.joinToString("\n") { it.rawText }.lowercase()
        return repositorySkills.filter { skill ->
            searchable.contains(".gemini/skills/${skill.lowercase()}") ||
                searchable.contains("skill: ${skill.lowercase()}")
        }
    }

    fun recordedSkillNames(events: List<RawInsightEvent>): List<String> =
        activatedSkillNames(events).distinctBy(String::lowercase)

    fun invocationCommand(events: List<RawInsightEvent>): String? =
        events.firstNotNullOfOrNull { event ->
            event.payload
                ?.takeIf { it.stringAt("type") == "user" }
                ?.get("content")
                ?.stringOrNull()
                ?.trim()
                ?.takeIf(String::isNotEmpty)
        }

    private fun activatedSkillNames(events: List<RawInsightEvent>): List<String> =
        events.flatMap { event ->
            event.payload?.getAsJsonArray("toolCalls")
                ?.mapNotNull { it.objectOrNull() }
                ?.filter { it.stringAt("name") == "activate_skill" }
                ?.mapNotNull { it.getAsJsonObject("args")?.get("name")?.stringOrNull() }
                .orEmpty()
        }
}

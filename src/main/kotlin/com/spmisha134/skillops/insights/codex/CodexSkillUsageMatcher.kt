package com.spmisha134.skillops.insights.codex

import com.spmisha134.skillops.insights.parser.RawInsightEvent

class CodexSkillUsageMatcher {
    fun matchSkill(events: List<RawInsightEvent>, skillNames: List<String>): String? {
        return matchSkills(events, skillNames).firstOrNull()
    }

    fun matchSkills(events: List<RawInsightEvent>, skillNames: List<String>): List<String> {
        if (events.isEmpty() || skillNames.isEmpty()) {
            return emptyList()
        }

        val recordedSkillNames = detectRecordedSkillNames(events)
        if (recordedSkillNames.isNotEmpty()) {
            return skillNames.filter { repositorySkill ->
                recordedSkillNames.any { recorded -> recorded.equals(repositorySkill, ignoreCase = true) }
            }
        }

        val searchableText = events
            .asSequence()
            .filter(::isUserAuthored)
            .joinToString(separator = "\n") { it.rawText.lowercase() }
        return skillNames.filter { skillName ->
            val normalizedSkillName = skillName.lowercase()
            searchableText.contains(".agents/skills/$normalizedSkillName") ||
                searchableText.contains("agents/skills/$normalizedSkillName") ||
                searchableText.contains("<name>$normalizedSkillName</name>") ||
                searchableText.contains("<name> $normalizedSkillName </name>") ||
                searchableText.contains("skill: $normalizedSkillName") ||
                searchableText.contains("name: $normalizedSkillName") ||
                searchableText.contains("\"name\":\"$normalizedSkillName\"") ||
                searchableText.contains("\"name\": \"$normalizedSkillName\"") ||
                searchableText.contains("`$normalizedSkillName`") ||
                searchableText.contains("\"$normalizedSkillName\"")
        }
    }

    fun detectRecordedSkillNames(events: List<RawInsightEvent>): List<String> {
        val searchableText = events
            .asSequence()
            .map(RawInsightEvent::rawText)
            .filter { rawText ->
                rawText.contains("\"role\":\"user\"") &&
                    rawText.contains("<skill>") &&
                    rawText.contains("</skill>")
            }
            .joinToString(separator = "\n")
        return RECORDED_SKILL_PATTERNS
            .flatMap { pattern -> pattern.findAll(searchableText).map { it.groupValues[1] }.toList() }
            .distinctBy(String::lowercase)
    }

    fun invocationCommand(events: List<RawInsightEvent>): String? {
        val skillEventIndex = events.indexOfFirst { event ->
            event.rawText.contains("\"role\":\"user\"") &&
                event.rawText.contains("<skill>") &&
                event.rawText.contains("</skill>")
        }
        if (skillEventIndex < 0) {
            return events.firstNotNullOfOrNull(::userMessage)
        }

        return events.subList(0, skillEventIndex)
            .asReversed()
            .firstNotNullOfOrNull(::userMessage)
    }

    private fun userMessage(event: RawInsightEvent): String? {
        val payload = event.payload?.getAsJsonObject("payload") ?: return null
        if (payload.get("type")?.asString != "user_message") {
            return null
        }
        return payload.get("message")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }

    private fun isUserAuthored(event: RawInsightEvent): Boolean {
        val payload = event.payload?.getAsJsonObject("payload") ?: return false
        return payload.get("role")?.asString == "user" ||
            payload.get("type")?.asString == "user_message"
    }

    companion object {
        private val RECORDED_SKILL_PATTERNS = listOf(
            Regex("<name>\\s*([a-z0-9][a-z0-9._-]*)\\s*</name>", RegexOption.IGNORE_CASE),
            Regex("(?:\\.agents|agents)/skills/([a-z0-9][a-z0-9._-]*)/SKILL\\.md", RegexOption.IGNORE_CASE),
        )
    }
}

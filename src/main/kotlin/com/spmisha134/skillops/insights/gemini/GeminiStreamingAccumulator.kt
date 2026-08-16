package com.spmisha134.skillops.insights.gemini

import com.spmisha134.skillops.insights.parser.RawInsightEvent
import com.spmisha134.skillops.insights.usage.TokenUsage

class GeminiStreamingAccumulator(private val repositorySkills: List<String>) {
    private val responses = linkedMapOf<String, Usage>()
    private val activatedSkills = linkedSetOf<String>()
    private val fallbackMatches = repositorySkills.associateWith { false }.toMutableMap()
    private var firstInvocation: String? = null
    private var searchCount = 0
    private var toolCallCount = 0
    private var meaningfulRecord = false

    fun accept(event: RawInsightEvent) {
        val payload = event.payload ?: return
        val type = payload.get("type")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        if (type in MEANINGFUL_RECORD_TYPES) meaningfulRecord = true
        if (type == "gemini") {
            val id = payload.get("id")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            val tokens = payload.getAsJsonObject("tokens")
            if (id != null && tokens != null) {
                responses[id] = Usage(tokens.long("input"), tokens.long("output"), tokens.long("cached"), tokens.long("thoughts"), tokens.long("total"), tokens.long("tool"))
            }
        }
        payload.getAsJsonArray("toolCalls")?.forEach { toolCall ->
            val tool = toolCall.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val name = tool.get("name")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            if (name == "activate_skill") {
                tool.getAsJsonObject("args")?.get("name")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.let(activatedSkills::add)
            }
        }
        if (type == "user") {
            payload.get("content")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.trim()?.takeIf(String::isNotEmpty)?.let { text ->
                firstInvocation = firstInvocation ?: text
                val lower = text.lowercase()
                fallbackMatches.keys.forEach { skill ->
                    if (lower.contains(".gemini/skills/${skill.lowercase()}") || lower.contains("skill: ${skill.lowercase()}")) fallbackMatches[skill] = true
                }
            }
        }
        val text = payload.toString().lowercase()
        searchCount += SEARCH_TOOLS.count { text.contains("\"$it\"") }
        toolCallCount += payload.getAsJsonArray("toolCalls")?.size() ?: 0
    }

    fun hasMeaningfulRecord(): Boolean = meaningfulRecord

    fun tokenUsage(): TokenUsage? {
        if (responses.isEmpty()) return null
        return TokenUsage(
            responses.values.sumOf { it.input }, responses.values.sumOf { it.output },
            responses.values.sumOf { it.cached }, responses.values.sumOf { it.thoughts },
            responses.values.sumOf { it.total }, null, null,
            mapOf("distinctGeminiResponses" to responses.size, "definition" to "sum of recorded Gemini response token totals"),
            null, responses.values.sumOf { it.tool },
        )
    }

    fun matchedSkills(): List<String> = if (activatedSkills.isNotEmpty()) {
        repositorySkills.filter { skill -> activatedSkills.any { it.equals(skill, ignoreCase = true) } }
    } else fallbackMatches.filterValues { it }.keys.toList()

    fun recordedSkills(): List<String> = activatedSkills.toList()
    fun invocationCommand(): String? = firstInvocation
    fun counts(): Pair<Int, Int> = searchCount to toolCallCount

    private data class Usage(val input: Long, val output: Long, val cached: Long, val thoughts: Long, val total: Long, val tool: Long)
    private fun com.google.gson.JsonObject.long(name: String): Long = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong ?: 0L

    companion object {
        private val SEARCH_TOOLS = setOf("grep_search", "google_web_search", "search_file_content")
        private val MEANINGFUL_RECORD_TYPES = setOf("user", "gemini", "error", "warning")
    }
}

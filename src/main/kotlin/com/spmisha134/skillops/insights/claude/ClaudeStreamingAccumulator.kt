package com.spmisha134.skillops.insights.claude

import com.spmisha134.skillops.insights.parser.RawInsightEvent
import com.spmisha134.skillops.insights.usage.TokenUsage
import com.spmisha134.skillops.sessions.model.SessionResumeTarget
import java.nio.file.Path

class ClaudeStreamingAccumulator(
    private val projectRoot: Path,
    private val repositorySkills: List<String>,
) {
    private val root = projectRoot.toAbsolutePath().normalize()
    private val assistantCalls = linkedMapOf<String, Usage>()
    private val attributedSkills = linkedSetOf<String>()
    private val fallbackMatches = repositorySkills.associateWith { false }.toMutableMap()
    private var projectPathSeen = false
    private var projectMatched = false
    private var invocation: String? = null
    private var sessionId: String? = null
    private var workingDirectory: Path? = null
    private var searchCount = 0
    private var toolCallCount = 0

    fun accept(event: RawInsightEvent) {
        val payload = event.payload ?: return
        val text = payload.toString()
        val lower = text.lowercase()
        payload.get("cwd")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.let { raw ->
            projectPathSeen = true
            runCatching { Path.of(raw).toAbsolutePath().normalize() }.getOrNull()?.let {
                if (it.startsWith(root)) projectMatched = true
            }
        }
        payload.get("sessionId")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.let { sessionId = sessionId ?: it }
        payload.get("cwd")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.let { raw ->
            workingDirectory = workingDirectory ?: runCatching { Path.of(raw).toAbsolutePath().normalize() }.getOrNull()
        }

        if (payload.get("type")?.asString == "assistant") {
            val message = payload.getAsJsonObject("message")
            val id = message?.get("id")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            val usage = message?.getAsJsonObject("usage")
            if (id != null && usage != null) {
                assistantCalls[id] = Usage(
                    usage.long("input_tokens"), usage.long("output_tokens"),
                    usage.long("cache_read_input_tokens"), usage.long("cache_creation_input_tokens"),
                )
            }
        }
        payload.get("attributionSkill")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.let(attributedSkills::add)
        if (payload.get("type")?.asString == "user" && payload.get("isMeta")?.asBoolean != true && payload.get("isSidechain")?.asBoolean != true) {
            val content = payload.getAsJsonObject("message")?.get("content")
            val userText = when {
                content?.isJsonPrimitive == true && content.asJsonPrimitive.isString -> content.asString
                content?.isJsonArray == true -> content.asJsonArray.mapNotNull { block ->
                    block.takeIf { it.isJsonObject }?.asJsonObject?.get("text")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                }.joinToString("\n")
                else -> null
            }?.trim()?.takeIf(String::isNotEmpty)
            if (userText != null && !isSynthetic(userText)) {
                invocation = userText
                val lowerText = userText.lowercase()
                fallbackMatches.keys.forEach { skill ->
                    if (lowerText.contains(".claude/skills/${skill.lowercase()}") || lowerText.contains("skill: ${skill.lowercase()}")) {
                        fallbackMatches[skill] = true
                    }
                }
            }
        }
        searchCount += SEARCH_TOOL.findAll(lower).count()
        toolCallCount += TOOL_USE.findAll(text).count()
    }

    fun belongsToProject(): Boolean? = if (!projectPathSeen) null else projectMatched

    fun tokenUsage(): TokenUsage? {
        if (assistantCalls.isEmpty()) return null
        val input = assistantCalls.values.sumOf { it.input }
        val output = assistantCalls.values.sumOf { it.output }
        val cacheRead = assistantCalls.values.sumOf { it.cacheRead }
        val cacheCreation = assistantCalls.values.sumOf { it.cacheCreation }
        return TokenUsage(input, output, cacheRead, null, input + output + cacheRead + cacheCreation, null, null,
            mapOf("distinctAssistantCalls" to assistantCalls.size, "definition" to "input + output + cache read + cache creation"), cacheCreation, null)
    }

    fun matchedSkills(): List<String> = if (attributedSkills.isNotEmpty()) {
        repositorySkills.filter { skill -> attributedSkills.any { it.equals(skill, ignoreCase = true) } }
    } else fallbackMatches.filterValues { it }.keys.toList()

    fun recordedSkills(): List<String> = attributedSkills.toList()
    fun invocationCommand(): String? = invocation
    fun counts(): Pair<Int, Int> = searchCount to toolCallCount
    fun resumeTarget(fileName: String): SessionResumeTarget? {
        val id = sessionId ?: fileName.removeSuffix(".jsonl").takeIf(String::isNotBlank) ?: return null
        return SessionResumeTarget(id, workingDirectory)
    }

    private fun isSynthetic(text: String): Boolean = CONTROL_TAGS.any { text.contains("<$it", ignoreCase = true) } ||
        text.equals("[Request interrupted by user for tool use]", ignoreCase = true)

    private data class Usage(val input: Long, val output: Long, val cacheRead: Long, val cacheCreation: Long)

    private fun com.google.gson.JsonObject.long(name: String): Long = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong ?: 0L

    companion object {
        private val TOOL_USE = Regex("\"type\"\\s*:\\s*\"tool_use\"")
        private val SEARCH_TOOL = Regex("\"name\"\\s*:\\s*\"(?:Grep|Glob|WebSearch|WebFetch)\"", RegexOption.IGNORE_CASE)
        private val CONTROL_TAGS = listOf("local-command-caveat", "local-command-stdout", "local-command-stderr", "command-name", "command-message", "command-args")
    }
}

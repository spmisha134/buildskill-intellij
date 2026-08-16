package com.spmisha134.skillops.insights.codex

import com.spmisha134.skillops.insights.parser.RawInsightEvent
import com.spmisha134.skillops.insights.usage.TokenUsage
import com.spmisha134.skillops.sessions.model.CodexSessionMetadata
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.UUID

class CodexStreamingAccumulator(
    private val projectRoot: Path,
    private val skillNames: List<String>,
    private val tokenUsageExtractor: CodexUsageExtractor = CodexUsageExtractor(),
) {
    private val normalizedProjectRoot = projectRoot.toAbsolutePath().normalize()
    private val fallbackMatches = skillNames.associateWith { false }.toMutableMap()
    private val recordedNames = linkedSetOf<String>()
    private var projectPathSeen = false
    private var projectMatched = false
    private var latestTokenUsage: TokenUsage? = null
    private var firstUserCommand: String? = null
    private var previousUserCommand: String? = null
    private var invocationCommand: String? = null
    private var sessionId: String? = null
    private var workingDirectory: Path? = null
    private var explicitInitialPrompt: String? = null
    private var fallbackInitialPrompt: String? = null
    private var searchCount = 0
    private var toolCallCount = 0

    fun accept(event: RawInsightEvent) {
        val text = event.payload?.toString().orEmpty()
        val lowerText = text.lowercase()
        updateProjectMatch(event)
        tokenUsageExtractor.extract(listOf(event))?.let { latestTokenUsage = it }

        val userCommand = userMessage(event)
        if (userCommand != null) {
            firstUserCommand = firstUserCommand ?: userCommand
            previousUserCommand = userCommand
            explicitInitialPrompt = explicitInitialPrompt ?: userCommand
        }

        if (isUserAuthored(event)) {
            skillNames.forEach { skillName ->
                if (containsSkillReference(lowerText, skillName)) fallbackMatches[skillName] = true
            }
        }

        if (isRecordedSkillEvent(text)) {
            RECORDED_SKILL_PATTERNS.forEach { pattern ->
                pattern.findAll(text).forEach { recordedNames += it.groupValues[1] }
            }
            if (invocationCommand == null) invocationCommand = previousUserCommand
        }

        if (searchText(lowerText)) searchCount++
        toolCallCount += TOOL_USE_PATTERN.findAll(text).count()
        updateMetadata(event)
    }

    fun projectBelongsToProject(): Boolean? =
        if (!projectPathSeen) null else projectMatched

    fun tokenUsage(): TokenUsage? = latestTokenUsage

    fun matchedSkillNames(): List<String> {
        if (recordedNames.isNotEmpty()) {
            return skillNames.filter { repositorySkill ->
                recordedNames.any { recorded -> recorded.equals(repositorySkill, ignoreCase = true) }
            }
        }
        return fallbackMatches.filterValues { it }.keys.toList()
    }

    fun recordedSkillNames(): List<String> = recordedNames.toList()

    fun invocationCommand(): String? = invocationCommand ?: firstUserCommand

    fun efficiencyCounts(): Pair<Int, Int> = searchCount to toolCallCount

    fun resumeMetadata(fileName: String): CodexSessionMetadata? {
        val id = sessionId?.takeIf(::isUuid)
            ?: FILE_SESSION_ID.find(fileName)?.groupValues?.get(1)?.takeIf(::isUuid)
            ?: return null
        return CodexSessionMetadata(
            id,
            workingDirectory,
            (explicitInitialPrompt ?: fallbackInitialPrompt)?.summarize(),
        )
    }

    private fun updateProjectMatch(event: RawInsightEvent) {
        val payload = event.payload?.getAsJsonObject("payload") ?: return
        val paths = buildList {
            payload.get("cwd")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.let(::add)
            payload.getAsJsonArray("workspace_roots")?.forEach { root ->
                root.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.let(::add)
            }
            payload.getAsJsonObject("state")?.getAsJsonObject("environments")
                ?.getAsJsonObject("environments")?.entrySet()?.forEach { (_, environment) ->
                    environment.takeIf { it.isJsonObject }?.asJsonObject?.get("cwd")
                        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.let(::add)
                }
        }
        paths.forEach { rawPath ->
            projectPathSeen = true
            try {
                if (Path.of(rawPath).toAbsolutePath().normalize().startsWith(normalizedProjectRoot)) {
                    projectMatched = true
                }
            } catch (_: InvalidPathException) {
                // Ignore malformed metadata, matching the existing project matcher.
            }
        }
    }

    private fun updateMetadata(event: RawInsightEvent) {
        val payload = event.payload?.getAsJsonObject("payload") ?: return
        if (event.type == "session_meta") {
            sessionId = string(payload, "id") ?: sessionId
            workingDirectory = string(payload, "cwd")?.toPathOrNull() ?: workingDirectory
        }
        if (fallbackInitialPrompt == null && event.type == "response_item" && string(payload, "type") == "message" && string(payload, "role") == "user") {
            fallbackInitialPrompt = payload.getAsJsonArray("content")?.mapNotNull { block ->
                block.takeIf { it.isJsonObject }?.asJsonObject?.let {
                    string(it, "text") ?: string(it, "input_text")
                }
            }?.joinToString(" ")?.takeIf(String::isNotEmpty)
        }
    }

    private fun userMessage(event: RawInsightEvent): String? {
        val payload = event.payload?.getAsJsonObject("payload") ?: return null
        if (string(payload, "type") != "user_message") return null
        return string(payload, "message")?.trim()?.takeIf(String::isNotEmpty)
    }

    private fun isUserAuthored(event: RawInsightEvent): Boolean {
        val payload = event.payload?.getAsJsonObject("payload") ?: return false
        return string(payload, "role") == "user" || string(payload, "type") == "user_message"
    }

    private fun isRecordedSkillEvent(text: String): Boolean =
        text.contains("\"role\":\"user\"") && text.contains("<skill>") && text.contains("</skill>")

    private fun containsSkillReference(text: String, skillName: String): Boolean {
        val normalized = skillName.lowercase()
        return text.contains(".agents/skills/$normalized") ||
            text.contains("agents/skills/$normalized") ||
            text.contains("<name>$normalized</name>") ||
            text.contains("<name> $normalized </name>") ||
            text.contains("skill: $normalized") ||
            text.contains("name: $normalized") ||
            text.contains("\"name\":\"$normalized\"") ||
            text.contains("\"name\": \"$normalized\"") ||
            text.contains("`$normalized`") ||
            text.contains("\"$normalized\"")
    }

    private fun searchText(text: String): Boolean =
        text.contains("\"search_query\"") || text.contains("\"find\"") ||
            text.contains("\"cmd\":\"rg") || text.contains("\"cmd\":\"grep") ||
            text.contains(" rg ") || text.contains(" grep ")

    private fun string(payload: com.google.gson.JsonObject, name: String): String? =
        payload.get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun String.toPathOrNull(): Path? = try {
        Path.of(this).toAbsolutePath().normalize()
    } catch (_: InvalidPathException) {
        null
    }

    private fun String.summarize(): String {
        val normalized = replace(WHITESPACE, " ").trim()
        return if (normalized.length <= MAX_PROMPT_LENGTH) normalized else normalized.take(MAX_PROMPT_LENGTH - 1) + "…"
    }

    private fun isUuid(value: String): Boolean = try {
        UUID.fromString(value)
        true
    } catch (_: IllegalArgumentException) {
        false
    }

    companion object {
        private val RECORDED_SKILL_PATTERNS = listOf(
            Regex("<name>\\s*([a-z0-9][a-z0-9._-]*)\\s*</name>", RegexOption.IGNORE_CASE),
            Regex("(?:\\.agents|agents)/skills/([a-z0-9][a-z0-9._-]*)/SKILL\\.md", RegexOption.IGNORE_CASE),
        )
        private val TOOL_USE_PATTERN = Regex("\"type\"\\s*:\\s*\"tool_use\"")
        private val FILE_SESSION_ID = Regex("([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(?:\\.jsonl)?$")
        private val WHITESPACE = Regex("\\s+")
        private const val MAX_PROMPT_LENGTH = 180
    }
}

package com.spmisha134.skillops.sessions.discovery

import com.google.gson.JsonObject
import com.spmisha134.skillops.insights.parser.RawInsightEvent

/** Extracts human requests while keeping injected repository context out of titles. */
object CodexPromptSummary {
    fun userMessage(event: RawInsightEvent): String? {
        val payload = event.payload?.getAsJsonObject("payload") ?: return null
        if (payload.string("type") != "user_message") return null
        return payload.string("message")?.normalize()?.takeIf(String::isNotEmpty)
    }

    fun responseItemPrompt(event: RawInsightEvent): String? {
        val payload = event.payload?.getAsJsonObject("payload") ?: return null
        if (event.type != "response_item" || payload.string("type") != "message" || payload.string("role") != "user") return null
        return payload.getAsJsonArray("content")?.mapNotNull { block ->
            block.takeIf { it.isJsonObject }?.asJsonObject?.let { it.string("text") ?: it.string("input_text") }
        }?.joinToString(" ")?.normalize()?.takeIf(String::isNotEmpty)
    }

    fun isInjectedContext(prompt: String): Boolean {
        val normalized = prompt.trim().lowercase()
        return normalized.startsWith("# agents.md instructions") ||
            normalized.startsWith("# claude.md instructions") ||
            normalized.startsWith("# gemini.md instructions") ||
            normalized.contains("<environment_context>") ||
            normalized.contains("<developer_instructions>")
    }

    fun title(prompts: List<String>): String? = prompts.lastOrNull()?.let { prompt ->
        val value = prompt.replaceFirst(Regex("^#+\\s*"), "").trim()
        if (value.length <= MAX_TITLE_LENGTH) value else value.take(MAX_TITLE_LENGTH - 1) + "…"
    }

    private fun String.normalize(): String = replace(WHITESPACE, " ").trim()

    private fun JsonObject.string(name: String): String? = get(name)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString

    private val WHITESPACE = Regex("\\s+")
    private const val MAX_TITLE_LENGTH = 100
}

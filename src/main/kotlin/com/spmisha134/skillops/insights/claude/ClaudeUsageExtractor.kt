package com.spmisha134.skillops.insights.claude

import com.google.gson.JsonObject
import com.spmisha134.skillops.insights.parser.RawInsightEvent
import com.spmisha134.skillops.insights.parser.longAt
import com.spmisha134.skillops.insights.parser.objectAt
import com.spmisha134.skillops.insights.parser.stringAt
import com.spmisha134.skillops.insights.usage.TokenUsage

class ClaudeUsageExtractor {
    fun extract(events: List<RawInsightEvent>): TokenUsage? {
        val calls = events.mapNotNull(::assistantCall)
            .distinctBy { it.messageId }
        if (calls.isEmpty()) return null

        val input = calls.sumOf { it.usage.long("input_tokens") }
        val output = calls.sumOf { it.usage.long("output_tokens") }
        val cacheRead = calls.sumOf { it.usage.long("cache_read_input_tokens") }
        val cacheCreation = calls.sumOf { it.usage.long("cache_creation_input_tokens") }
        return TokenUsage(
            inputTokens = input,
            outputTokens = output,
            cachedInputTokens = cacheRead,
            reasoningOutputTokens = null,
            totalTokens = input + output + cacheRead + cacheCreation,
            rateLimitUsedPercent = null,
            rateLimitResetAt = null,
            rawEvidence = mapOf(
                "distinctAssistantCalls" to calls.size,
                "definition" to "input + output + cache read + cache creation",
            ),
            cacheCreationInputTokens = cacheCreation,
            toolTokens = null,
        )
    }

    private fun assistantCall(event: RawInsightEvent): AssistantCall? {
        val payload = event.payload ?: return null
        if (payload.stringAt("type") != "assistant") return null
        val message = payload.objectAt("message") ?: return null
        val messageId = message.stringAt("id") ?: return null
        val usage = message.objectAt("usage") ?: return null
        return AssistantCall(messageId, usage)
    }

    private fun JsonObject.long(key: String): Long = longAt(key) ?: 0L

    private data class AssistantCall(val messageId: String, val usage: JsonObject)
}

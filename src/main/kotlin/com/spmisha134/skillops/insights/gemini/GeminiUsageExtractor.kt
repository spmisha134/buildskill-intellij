package com.spmisha134.skillops.insights.gemini

import com.google.gson.JsonObject
import com.spmisha134.skillops.insights.parser.RawInsightEvent
import com.spmisha134.skillops.insights.parser.longAt
import com.spmisha134.skillops.insights.parser.objectAt
import com.spmisha134.skillops.insights.parser.stringAt
import com.spmisha134.skillops.insights.usage.TokenUsage

class GeminiUsageExtractor {
    fun extract(events: List<RawInsightEvent>): TokenUsage? {
        val responses = events.mapNotNull(::response).distinctBy(Response::id)
        if (responses.isEmpty()) return null

        return TokenUsage(
            inputTokens = responses.sumOf { it.tokens.long("input") },
            outputTokens = responses.sumOf { it.tokens.long("output") },
            cachedInputTokens = responses.sumOf { it.tokens.long("cached") },
            reasoningOutputTokens = responses.sumOf { it.tokens.long("thoughts") },
            totalTokens = responses.sumOf { it.tokens.long("total") },
            rateLimitUsedPercent = null,
            rateLimitResetAt = null,
            rawEvidence = mapOf(
                "distinctGeminiResponses" to responses.size,
                "definition" to "sum of recorded Gemini response token totals",
            ),
            cacheCreationInputTokens = null,
            toolTokens = responses.sumOf { it.tokens.long("tool") },
        )
    }

    private fun response(event: RawInsightEvent): Response? {
        val payload = event.payload ?: return null
        if (payload.stringAt("type") != "gemini") return null
        val id = payload.stringAt("id") ?: return null
        val tokens = payload.objectAt("tokens") ?: return null
        return Response(id, tokens)
    }

    private fun JsonObject.long(key: String): Long = longAt(key) ?: 0L

    private data class Response(val id: String, val tokens: JsonObject)
}

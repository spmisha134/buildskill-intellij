package com.spmisha134.skillops.insights.codex

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.spmisha134.skillops.insights.parser.RawInsightEvent
import com.spmisha134.skillops.insights.parser.doubleAt
import com.spmisha134.skillops.insights.parser.longAt
import com.spmisha134.skillops.insights.parser.objectAt
import com.spmisha134.skillops.insights.parser.stringAt
import com.spmisha134.skillops.insights.usage.TokenUsage

class CodexUsageExtractor {
    fun extract(events: List<RawInsightEvent>): TokenUsage? {
        val event = events.asReversed().firstOrNull(::isTokenUsageEvent) ?: return null
        val payload = event.payload ?: return null
        val candidates = tokenUsageCandidates(payload)

        val inputTokens = candidates.findLong(INPUT_TOKEN_KEYS)
        val outputTokens = candidates.findLong(OUTPUT_TOKEN_KEYS)
        val totalTokens = candidates.findLong(TOTAL_TOKEN_KEYS)
            ?: if (inputTokens != null && outputTokens != null) inputTokens + outputTokens else null

        return TokenUsage(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cachedInputTokens = candidates.findLong(CACHED_INPUT_TOKEN_KEYS),
            reasoningOutputTokens = candidates.findLong(REASONING_OUTPUT_TOKEN_KEYS),
            totalTokens = totalTokens,
            rateLimitUsedPercent = candidates.findDouble(RATE_LIMIT_USED_PERCENT_KEYS),
            rateLimitResetAt = candidates.findString(RATE_LIMIT_RESET_AT_KEYS),
            rawEvidence = event.rawEvidence(payload),
            cacheCreationInputTokens = candidates.findLong(CACHE_CREATION_INPUT_TOKEN_KEYS),
            toolTokens = null,
        )
    }

    private fun isTokenUsageEvent(event: RawInsightEvent): Boolean {
        val payload = event.payload ?: return false
        return event.type == TOKEN_COUNT_EVENT_TYPE ||
            payload.objectAt("payload")?.stringAt("type") == TOKEN_COUNT_EVENT_TYPE ||
            payload.containsAny(TOKEN_USAGE_OBJECT_KEYS) ||
            payload.objectAt("payload")?.containsAny(TOKEN_USAGE_OBJECT_KEYS) == true
    }

    private fun tokenUsageCandidates(payload: JsonObject): List<JsonObject> {
        val candidates = mutableListOf<JsonObject>()
        candidates += payload
        payload.objectAt("payload")?.let(candidates::add)

        var index = 0
        while (index < candidates.size) {
            val candidate = candidates[index++]
            TOKEN_USAGE_OBJECT_KEYS
                .mapNotNull { key -> candidate.objectAt(key) }
                .filterNot(candidates::contains)
                .forEach(candidates::add)
        }

        val rateLimits = candidates.flatMap { candidate ->
            listOfNotNull(candidate.objectAt("rate_limits"), candidate.objectAt("rateLimits"))
        }
        candidates += rateLimits

        return candidates.distinct()
    }

    private fun JsonObject.containsAny(keys: Set<String>): Boolean =
        keys.any(::has)

    private fun List<JsonObject>.findLong(keys: List<String>): Long? =
        firstNotNullOfOrNull { candidate -> keys.firstNotNullOfOrNull { key -> candidate.longAt(key) } }

    private fun List<JsonObject>.findDouble(keys: List<String>): Double? =
        firstNotNullOfOrNull { candidate -> keys.firstNotNullOfOrNull { key -> candidate.doubleAt(key) } }

    private fun List<JsonObject>.findString(keys: List<String>): String? =
        firstNotNullOfOrNull { candidate -> keys.firstNotNullOfOrNull { key -> candidate.stringAt(key) } }

    private fun RawInsightEvent.rawEvidence(payload: JsonObject): Map<String, Any?> =
        mapOf(
            "lineNumber" to lineNumber,
            "timestamp" to timestamp,
            "type" to type,
            "payload" to payload.toPlainValue(),
        )

    private fun JsonElement.toPlainValue(): Any? =
        when (this) {
            is JsonNull -> null
            is JsonObject -> entrySet().associate { (key, value) -> key to value.toPlainValue() }
            is JsonArray -> map { it.toPlainValue() }
            is JsonPrimitive -> toPrimitiveValue()
            else -> toString()
        }

    private fun JsonPrimitive.toPrimitiveValue(): Any? =
        when {
            isBoolean -> asBoolean
            isNumber -> asNumber
            isString -> asString
            else -> null
        }

    companion object {
        private const val TOKEN_COUNT_EVENT_TYPE = "token_count"

        private val TOKEN_USAGE_OBJECT_KEYS = setOf(
            "token_count",
            "tokenCount",
            "usage",
            "info",
            "total_token_usage",
            "totalTokenUsage",
            "last_token_usage",
            "lastTokenUsage",
            "rate_limits",
            "rateLimits",
            "primary",
            "secondary",
        )

        private val INPUT_TOKEN_KEYS = listOf(
            "input_tokens",
            "inputTokens",
            "prompt_tokens",
            "promptTokens",
        )

        private val OUTPUT_TOKEN_KEYS = listOf(
            "output_tokens",
            "outputTokens",
            "completion_tokens",
            "completionTokens",
        )

        private val CACHED_INPUT_TOKEN_KEYS = listOf(
            "cached_input_tokens",
            "cachedInputTokens",
            "cache_read_input_tokens",
            "cacheReadInputTokens",
        )

        private val REASONING_OUTPUT_TOKEN_KEYS = listOf(
            "reasoning_output_tokens",
            "reasoningOutputTokens",
            "reasoning_tokens",
            "reasoningTokens",
        )

        private val CACHE_CREATION_INPUT_TOKEN_KEYS = listOf(
            "cache_creation_input_tokens",
            "cacheCreationInputTokens",
        )

        private val TOTAL_TOKEN_KEYS = listOf(
            "total_tokens",
            "totalTokens",
            "total_token_count",
            "totalTokenCount",
        )

        private val RATE_LIMIT_USED_PERCENT_KEYS = listOf(
            "rate_limit_used_percent",
            "rateLimitUsedPercent",
            "used_percent",
            "usedPercent",
            "percent_used",
            "percentUsed",
        )

        private val RATE_LIMIT_RESET_AT_KEYS = listOf(
            "rate_limit_reset_at",
            "rateLimitResetAt",
            "reset_at",
            "resetAt",
        )
    }
}

package com.spmisha134.skillops.sessions.discovery

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.spmisha134.skillops.insights.parser.RawInsightEvent
import com.spmisha134.skillops.sessions.model.CodexSessionMetadata
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.UUID

class CodexSessionMetadataExtractor {
    fun extract(events: List<RawInsightEvent>, fileName: String): CodexSessionMetadata? {
        val sessionMeta = events.firstOrNull { it.type == "session_meta" }?.payload?.getAsJsonObject("payload")
        val sessionId = sessionMeta.string("id")?.takeIf(::isUuid)
            ?: FILE_SESSION_ID.find(fileName)?.groupValues?.get(1)?.takeIf(::isUuid)
            ?: return null
        return CodexSessionMetadata(
            sessionId = sessionId,
            workingDirectory = sessionMeta.string("cwd")?.toPathOrNull(),
            initialPrompt = initialPrompt(events),
        )
    }

    private fun initialPrompt(events: List<RawInsightEvent>): String? =
        events.firstNotNullOfOrNull(::explicitUserMessage)?.summarized()
            ?: responseItemFallback(events)

    private fun explicitUserMessage(event: RawInsightEvent): String? {
        val payload = event.payload?.getAsJsonObject("payload") ?: return null
        if (payload.string("type") != "user_message") return null
        return payload.string("message")?.trim()?.takeIf(String::isNotEmpty)
    }

    private fun responseItemFallback(events: List<RawInsightEvent>): String? = events.asSequence()
        .mapNotNull { event ->
            val payload = event.payload?.getAsJsonObject("payload") ?: return@mapNotNull null
            if (event.type != "response_item" || payload.string("type") != "message" || payload.string("role") != "user") {
                return@mapNotNull null
            }
            payload.getAsJsonArray("content")
                ?.mapNotNull { it.asObjectOrNull() }
                ?.mapNotNull { it.string("text") ?: it.string("input_text") }
                ?.joinToString(" ")
                ?.takeIf(String::isNotEmpty)
        }
        .firstOrNull()
        ?.summarized()

    private fun String.summarized(): String {
        val normalized = replace(WHITESPACE, " ").trim()
        return if (normalized.length <= MAX_PROMPT_LENGTH) normalized else normalized.take(MAX_PROMPT_LENGTH - 1) + "…"
    }

    private fun JsonObject?.string(name: String): String? = this?.get(name)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString

    private fun JsonElement.asObjectOrNull(): JsonObject? = takeIf(JsonElement::isJsonObject)?.asJsonObject

    private fun String.toPathOrNull(): Path? = try {
        Path.of(this).toAbsolutePath().normalize()
    } catch (_: InvalidPathException) {
        null
    }

    private fun isUuid(value: String): Boolean = try {
        UUID.fromString(value)
        true
    } catch (_: IllegalArgumentException) {
        false
    }

    companion object {
        private val FILE_SESSION_ID = Regex("([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(?:\\.jsonl)?$")
        private val WHITESPACE = Regex("\\s+")
        private const val MAX_PROMPT_LENGTH = 180
    }
}

package com.spmisha134.skillops.sessions.discovery

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
            title = CodexPromptSummary.title(prompts(events)),
            userPrompts = prompts(events),
        )
    }

    private fun prompts(events: List<RawInsightEvent>): List<String> = buildList {
        events.forEach { event ->
            val prompt = CodexPromptSummary.userMessage(event)
                ?: CodexPromptSummary.responseItemPrompt(event)
                ?: return@forEach
            if (!CodexPromptSummary.isInjectedContext(prompt) && prompt !in this) add(prompt)
        }
    }

    private fun JsonObject?.string(name: String): String? = this?.get(name)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString

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
    }
}

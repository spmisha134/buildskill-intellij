package com.spmisha134.skillops.insights.parser

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class InsightJsonlParser {
    fun parse(path: Path): InsightParseResult {
        val events = mutableListOf<RawInsightEvent>()
        val warnings = mutableListOf<String>()

        try {
            Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
                reader.lineSequence().forEachIndexed { index, line ->
                    parseLine(lineNumber = index + 1, rawText = line, warnings = warnings)?.let(events::add)
                }
            }
        } catch (exception: IOException) {
            warnings += "Could not read JSONL file $path: ${exception.message ?: exception.javaClass.simpleName}"
        } catch (exception: SecurityException) {
            warnings += "Permission denied reading JSONL file $path: ${exception.message ?: exception.javaClass.simpleName}"
        }

        return InsightParseResult(
            filePath = path.toAbsolutePath().normalize(),
            events = events,
            warnings = warnings,
        )
    }

    private fun parseLine(
        lineNumber: Int,
        rawText: String,
        warnings: MutableList<String>,
    ): RawInsightEvent? {
        if (rawText.isBlank()) {
            return null
        }

        val parsed = try {
            JsonParser.parseString(rawText)
        } catch (exception: JsonSyntaxException) {
            val message = "Line $lineNumber: malformed JSON (${exception.message ?: exception.javaClass.simpleName})"
            warnings += message
            return RawInsightEvent(
                lineNumber = lineNumber,
                timestamp = null,
                type = null,
                payload = null,
                rawText = rawText,
                parseError = message,
            )
        }

        if (!parsed.isJsonObject) {
            val message = "Line $lineNumber: JSONL event is not an object"
            warnings += message
            return RawInsightEvent(
                lineNumber = lineNumber,
                timestamp = null,
                type = null,
                payload = null,
                rawText = rawText,
                parseError = message,
            )
        }

        val payload = parsed.asJsonObject
        return RawInsightEvent(
            lineNumber = lineNumber,
            timestamp = payload.stringAt("timestamp"),
            type = payload.eventType(),
            payload = payload,
            rawText = rawText,
            parseError = null,
        )
    }

    private fun JsonObject.eventType(): String? =
        stringAt("type")
            ?: stringAt("event")
            ?: objectAt("event")?.stringAt("type")
            ?: objectAt("message")?.stringAt("type")
            ?: stringAt("message")
}

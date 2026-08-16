package com.spmisha134.skillops.insights.parser

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.spmisha134.skillops.insights.run.RunInsightsProgress
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class InsightJsonlParser {
    fun parse(path: Path): InsightParseResult = parse(path, RunInsightsProgress.NONE)

    fun parse(path: Path, progress: RunInsightsProgress = RunInsightsProgress.NONE): InsightParseResult {
        val events = mutableListOf<RawInsightEvent>()
        val warnings = stream(path, progress) { event -> events += event }

        return InsightParseResult(
            filePath = path.toAbsolutePath().normalize(),
            events = events,
            warnings = warnings,
        )
    }

    /**
     * Reads a JSONL file incrementally and emits each parsed event immediately.
     * Consumers that need bounded memory should aggregate from [consumer] rather
     * than calling [parse], which intentionally retains events for existing APIs.
     */
    fun stream(
        path: Path,
        progress: RunInsightsProgress = RunInsightsProgress.NONE,
        consumer: (RawInsightEvent) -> Unit,
    ): List<String> {
        val warnings = mutableListOf<String>()
        val totalBytes = runCatching { Files.size(path) }.getOrDefault(0L)
        var processedBytes = 0L

        try {
            Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
                reader.lineSequence().forEachIndexed { index, line ->
                    progress.checkCanceled()
                    processedBytes += line.toByteArray(StandardCharsets.UTF_8).size + 1
                    progress.update("Parsing ${path.fileName}", processedBytes.coerceAtMost(totalBytes).toInt(), totalBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    parseLine(lineNumber = index + 1, rawText = line, warnings = warnings)?.let(consumer)
                }
            }
        } catch (exception: IOException) {
            warnings += "Could not read JSONL file $path: ${exception.message ?: exception.javaClass.simpleName}"
        } catch (exception: SecurityException) {
            warnings += "Permission denied reading JSONL file $path: ${exception.message ?: exception.javaClass.simpleName}"
        }

        return warnings
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
                parseError = message,
            )
        }

        val payload = parsed.asJsonObject
        return RawInsightEvent(
            lineNumber = lineNumber,
            timestamp = payload.stringAt("timestamp"),
            type = payload.eventType(),
            payload = payload,
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

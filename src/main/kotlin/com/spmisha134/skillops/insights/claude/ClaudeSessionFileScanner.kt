package com.spmisha134.skillops.insights.claude

import com.google.gson.JsonParser
import com.spmisha134.skillops.insights.settings.SkillOpsInsightsSettings
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ClaudeSessionFileScanner(
    private val userHomePath: Path = Paths.get(System.getProperty("user.home")),
) {
    fun scan(settings: SkillOpsInsightsSettings): ClaudeSessionScanResult {
        val normalized = settings.normalized()
        val claudeHome = resolveHome(normalized.claudeHomePath)
        val projects = claudeHome.resolve("projects")
        if (!Files.isDirectory(projects)) {
            return ClaudeSessionScanResult(
                emptyList(),
                listOf("Claude projects folder does not exist: $projects"),
            )
        }

        val warnings = mutableListOf<String>()
        val files = try {
            Files.walk(projects).use { paths ->
                paths
                    .filter(Files::isRegularFile)
                    .filter(::isMainTranscript)
                    .map { path ->
                        ClaudeSessionFile(
                            path = path.toAbsolutePath().normalize(),
                            fileName = path.fileName.toString(),
                            lastModifiedMs = Files.getLastModifiedTime(path).toMillis(),
                            sizeBytes = Files.size(path),
                        )
                    }
                    .sorted(compareByDescending(ClaudeSessionFile::lastModifiedMs))
                    .limit(normalized.maxSessionsToScan.toLong())
                    .toList()
            }
        } catch (exception: Exception) {
            warnings += "Could not scan Claude sessions: ${exception.message ?: exception.javaClass.simpleName}"
            emptyList()
        }
        return ClaudeSessionScanResult(files, warnings)
    }

    fun subagentFiles(sessionFile: Path): List<Path> {
        val sessionId = sessionFile.fileName.toString().removeSuffix(".jsonl")
        val directory = sessionFile.parent.resolve(sessionId).resolve("subagents")
        val nested = if (Files.isDirectory(directory)) {
            try {
                Files.list(directory).use { paths ->
                    paths.filter(Files::isRegularFile)
                        .filter { it.fileName.toString().startsWith("agent-") }
                        .filter { it.fileName.toString().endsWith(".jsonl") }
                        .toList()
                }
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        val siblings = try {
            Files.list(sessionFile.parent).use { paths ->
                paths.filter(Files::isRegularFile)
                    .filter { it.fileName.toString().startsWith("agent-") }
                    .filter { it.fileName.toString().endsWith(".jsonl") }
                    .filter { belongsToSession(it, sessionId) }
                    .toList()
            }
        } catch (_: Exception) {
            emptyList()
        }
        return (nested + siblings).distinct().sorted()
    }

    private fun resolveHome(configuredPath: String): Path {
        val trimmed = configuredPath.trim()
        return when {
            trimmed == "~" -> userHomePath
            trimmed.startsWith("~/") -> userHomePath.resolve(trimmed.removePrefix("~/"))
            else -> Paths.get(trimmed)
        }.toAbsolutePath().normalize()
    }

    private fun isMainTranscript(path: Path): Boolean {
        val name = path.fileName.toString()
        return name.endsWith(".jsonl") &&
            !name.startsWith("agent-") &&
            !name.endsWith(".cost.jsonl") &&
            !name.endsWith(".turn-boundaries.jsonl") &&
            name != "cost-log.jsonl" &&
            path.parent?.fileName?.toString() != "subagents"
    }

    private fun belongsToSession(path: Path, sessionId: String): Boolean =
        try {
            Files.newBufferedReader(path).useLines { lines ->
                lines.filter(String::isNotBlank).any { line ->
                    val parsed = runCatching { JsonParser.parseString(line) }.getOrNull()
                    parsed?.takeIf { it.isJsonObject }?.asJsonObject
                        ?.get("sessionId")
                        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                        ?.asString == sessionId
                }
            }
        } catch (_: Exception) {
            false
        }
}

data class ClaudeSessionFile(
    val path: Path,
    val fileName: String,
    val lastModifiedMs: Long,
    val sizeBytes: Long,
)

data class ClaudeSessionScanResult(
    val files: List<ClaudeSessionFile>,
    val warnings: List<String>,
)

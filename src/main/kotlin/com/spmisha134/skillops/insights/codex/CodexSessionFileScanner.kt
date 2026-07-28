package com.spmisha134.skillops.insights.codex

import com.spmisha134.skillops.insights.settings.SkillOpsInsightsSettings
import java.io.IOException
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class CodexSessionFileScanner(
    private val userHomePath: Path = Paths.get(System.getProperty("user.home")),
) {
    fun scan(settings: SkillOpsInsightsSettings): CodexSessionScanResult {
        val normalizedSettings = settings.normalized()
        val warnings = mutableListOf<String>()
        val codexHome = resolveCodexHomePath(normalizedSettings.codexHomePath)

        if (!Files.exists(codexHome)) {
            return CodexSessionScanResult(
                files = emptyList(),
                warnings = listOf("Codex home does not exist: $codexHome"),
            )
        }
        if (!Files.isDirectory(codexHome)) {
            return CodexSessionScanResult(
                files = emptyList(),
                warnings = listOf("Codex home is not a directory: $codexHome"),
            )
        }

        val sessionsDirectory = codexHome.resolve("sessions")
        if (!Files.exists(sessionsDirectory)) {
            return CodexSessionScanResult(
                files = emptyList(),
                warnings = listOf("Codex sessions folder does not exist: $sessionsDirectory"),
            )
        }
        if (!Files.isDirectory(sessionsDirectory)) {
            return CodexSessionScanResult(
                files = emptyList(),
                warnings = listOf("Codex sessions path is not a directory: $sessionsDirectory"),
            )
        }

        val files = mutableListOf<CodexSessionFile>()
        collectJsonlFiles(sessionsDirectory, files, warnings)

        return CodexSessionScanResult(
            files = files
                .sortedWith(
                    compareByDescending<CodexSessionFile> { it.fileName.startsWith(ROLLOUT_PREFIX) }
                        .thenByDescending { it.lastModifiedMs }
                        .thenBy { it.fileName }
                )
                .take(normalizedSettings.maxSessionsToScan),
            warnings = warnings,
        )
    }

    fun resolveCodexHomePath(codexHomePath: String): Path {
        val trimmedPath = codexHomePath.trim()
        return when {
            trimmedPath == "~" -> userHomePath
            trimmedPath.startsWith("~/") -> userHomePath.resolve(trimmedPath.removePrefix("~/"))
            else -> Paths.get(trimmedPath)
        }.toAbsolutePath().normalize()
    }

    private fun collectJsonlFiles(
        directory: Path,
        files: MutableList<CodexSessionFile>,
        warnings: MutableList<String>,
    ) {
        val entries = openDirectory(directory, warnings) ?: return

        entries.use { stream ->
            for (entry in stream) {
                when {
                    Files.isDirectory(entry) -> collectJsonlFiles(entry, files, warnings)
                    Files.isRegularFile(entry) && entry.fileName.toString().endsWith(JSONL_EXTENSION) -> {
                        toSessionFile(entry, warnings)?.let(files::add)
                    }
                }
            }
        }
    }

    private fun openDirectory(
        directory: Path,
        warnings: MutableList<String>,
    ): DirectoryStream<Path>? =
        try {
            Files.newDirectoryStream(directory)
        } catch (exception: IOException) {
            warnings += "Could not read directory $directory: ${exception.message ?: exception.javaClass.simpleName}"
            null
        } catch (exception: SecurityException) {
            warnings += "Permission denied reading directory $directory: ${exception.message ?: exception.javaClass.simpleName}"
            null
        }

    private fun toSessionFile(
        path: Path,
        warnings: MutableList<String>,
    ): CodexSessionFile? =
        try {
            CodexSessionFile(
                path = path.toAbsolutePath().normalize(),
                fileName = path.fileName.toString(),
                lastModifiedMs = Files.getLastModifiedTime(path).toMillis(),
                sizeBytes = Files.size(path),
            )
        } catch (exception: IOException) {
            warnings += "Could not inspect session file $path: ${exception.message ?: exception.javaClass.simpleName}"
            null
        } catch (exception: SecurityException) {
            warnings += "Permission denied inspecting session file $path: ${exception.message ?: exception.javaClass.simpleName}"
            null
        }

    companion object {
        private const val JSONL_EXTENSION = ".jsonl"
        private const val ROLLOUT_PREFIX = "rollout-"
    }
}

package com.spmisha134.skillops.insights.gemini

import com.spmisha134.skillops.insights.settings.SkillOpsInsightsSettings
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class GeminiSessionFile(
    val path: Path,
    val projectRoot: Path,
    val fileName: String,
    val lastModifiedMs: Long,
    val sizeBytes: Long,
)

data class GeminiSessionScanResult(
    val files: List<GeminiSessionFile>,
    val warnings: List<String>,
)

class GeminiSessionFileScanner(
    private val userHomePath: Path = Paths.get(System.getProperty("user.home")),
) {
    fun scan(settings: SkillOpsInsightsSettings): GeminiSessionScanResult {
        val normalized = settings.normalized()
        val tmpRoot = resolveHome(normalized.geminiHomePath).resolve("tmp")
        if (!Files.isDirectory(tmpRoot)) {
            return GeminiSessionScanResult(
                emptyList(),
                listOf("Gemini session folder does not exist: $tmpRoot"),
            )
        }

        val warnings = mutableListOf<String>()
        val files = try {
            Files.list(tmpRoot).use { projects ->
                projects.filter(Files::isDirectory)
                    .flatMap { projectDirectory ->
                        projectSessions(projectDirectory, warnings).stream()
                    }
                    .sorted(compareByDescending(GeminiSessionFile::lastModifiedMs))
                    .limit(normalized.maxSessionsToScan.toLong())
                    .toList()
            }
        } catch (exception: Exception) {
            warnings += "Could not scan Gemini sessions: ${exception.message ?: exception.javaClass.simpleName}"
            emptyList()
        }
        return GeminiSessionScanResult(files, warnings)
    }

    private fun projectSessions(
        projectDirectory: Path,
        warnings: MutableList<String>,
    ): List<GeminiSessionFile> {
        val projectRootFile = projectDirectory.resolve(".project_root")
        val chats = projectDirectory.resolve("chats")
        if (!Files.isRegularFile(projectRootFile) || !Files.isDirectory(chats)) return emptyList()
        val projectRoot = try {
            resolveHome(Files.readString(projectRootFile).trim())
        } catch (exception: Exception) {
            warnings += "Could not read Gemini project mapping for ${projectDirectory.fileName}: ${exception.message ?: exception.javaClass.simpleName}"
            return emptyList()
        }

        return try {
            Files.list(chats).use { paths ->
                paths.filter(Files::isRegularFile)
                    .filter { it.fileName.toString().startsWith("session-") }
                    .filter { it.fileName.toString().endsWith(".jsonl") }
                    .map { path ->
                        GeminiSessionFile(
                            path = path.toAbsolutePath().normalize(),
                            projectRoot = projectRoot,
                            fileName = path.fileName.toString(),
                            lastModifiedMs = Files.getLastModifiedTime(path).toMillis(),
                            sizeBytes = Files.size(path),
                        )
                    }
                    .toList()
            }
        } catch (exception: Exception) {
            warnings += "Could not read Gemini chats for ${projectDirectory.fileName}: ${exception.message ?: exception.javaClass.simpleName}"
            emptyList()
        }
    }

    private fun resolveHome(configuredPath: String): Path {
        val trimmed = configuredPath.trim()
        return when {
            trimmed == "~" -> userHomePath
            trimmed.startsWith("~/") -> userHomePath.resolve(trimmed.removePrefix("~/"))
            else -> Paths.get(trimmed)
        }.toAbsolutePath().normalize()
    }
}

package com.spmisha134.skillops.copy

import com.spmisha134.skillops.generator.SkillNameNormalizer
import com.spmisha134.skillops.copy.conversion.PlatformSkillConverter
import com.spmisha134.skillops.copy.io.SourceSkillReader
import com.spmisha134.skillops.copy.io.TargetSkillWriter
import com.spmisha134.skillops.copy.validation.PlatformSkillValidator
import com.spmisha134.skillops.model.copy.SkillConflictPolicy
import com.spmisha134.skillops.model.copy.SkillConflictResolver
import com.spmisha134.skillops.model.copy.SkillCopyItemResult
import com.spmisha134.skillops.model.copy.SkillCopyRequest
import com.spmisha134.skillops.model.copy.SkillCopyResult
import com.spmisha134.skillops.model.copy.SkillCopyStatus
import java.nio.file.Files
import java.nio.file.Path

class SkillCopyService(
    private val reader: SourceSkillReader = SourceSkillReader(),
    private val converter: PlatformSkillConverter = PlatformSkillConverter(),
    private val validator: PlatformSkillValidator = PlatformSkillValidator(),
    private val writer: TargetSkillWriter = TargetSkillWriter(),
) {
    fun copy(
        request: SkillCopyRequest,
        conflictResolver: SkillConflictResolver = SkillConflictResolver { _, _ -> SkillConflictPolicy.SKIP },
    ): SkillCopyResult {
        require(request.sourcePlatform != request.targetPlatform) { "Source and target platforms must be different." }
        val projectRoot = request.projectRoot.toAbsolutePath().normalize()
        val sourceRoot = projectRoot.resolve(request.sourcePlatform.projectDirectory).resolve("skills").normalize()
        val targetRoot = projectRoot.resolve(request.targetPlatform.projectDirectory).resolve("skills").normalize()
        require(sourceRoot.startsWith(projectRoot) && targetRoot.startsWith(projectRoot)) { "Skill roots must stay inside the project." }

        return SkillCopyResult(request.sourceSkillNames.map { sourceName ->
            runCatching { copyOne(sourceName, sourceRoot, targetRoot, request, conflictResolver) }
                .getOrElse { error -> SkillCopyItemResult(sourceName, status = SkillCopyStatus.FAILED, issue = error.message ?: error.javaClass.simpleName) }
        })
    }

    private fun copyOne(
        sourceName: String,
        sourceRoot: Path,
        targetRoot: Path,
        request: SkillCopyRequest,
        conflictResolver: SkillConflictResolver,
    ): SkillCopyItemResult {
        val safeSourceName = SkillNameNormalizer.normalize(sourceName)
        require(safeSourceName == sourceName) { "Unsafe source skill name: $sourceName" }
        val source = sourceRoot.resolve(sourceName).normalize()
        require(source.parent == sourceRoot) { "Unsafe source skill path: $sourceName" }
        val portable = reader.read(source, request.sourcePlatform)
        var targetName = SkillNameNormalizer.normalize(portable.folderName)
        var destination = targetRoot.resolve(targetName)
        var policy = request.conflictPolicy
        var renamed = false
        if (Files.exists(destination)) {
            if (policy == SkillConflictPolicy.ASK) policy = conflictResolver.resolve(sourceName, destination)
            if (policy == SkillConflictPolicy.SKIP || policy == SkillConflictPolicy.ASK) {
                return SkillCopyItemResult(sourceName, targetName, destination, SkillCopyStatus.SKIPPED)
            }
            if (policy == SkillConflictPolicy.RENAME) {
                targetName = availableName(targetRoot, targetName)
                destination = targetRoot.resolve(targetName)
                renamed = true
            }
        }

        val converted = converter.convert(portable, request.targetPlatform, targetName)
        val staging = writer.writeStaged(targetRoot, converted)
        val issues = validator.validate(staging, request.targetPlatform, targetName)
        if (issues.isNotEmpty()) {
            writer.discard(staging)
            throw IllegalArgumentException(issues.joinToString(" "))
        }
        val existed = Files.exists(destination)
        writer.install(staging, destination, replace = policy == SkillConflictPolicy.REPLACE)
        val status = when {
            renamed -> SkillCopyStatus.RENAMED
            existed -> SkillCopyStatus.REPLACED
            else -> SkillCopyStatus.COPIED
        }
        return SkillCopyItemResult(sourceName, targetName, destination, status)
    }

    private fun availableName(targetRoot: Path, baseName: String): String {
        var suffix = 2
        while (Files.exists(targetRoot.resolve("$baseName-$suffix"))) suffix++
        return "$baseName-$suffix"
    }
}

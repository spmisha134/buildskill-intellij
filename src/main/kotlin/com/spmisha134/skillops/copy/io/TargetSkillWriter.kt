package com.spmisha134.skillops.copy.io

import com.spmisha134.skillops.model.copy.ConvertedSkill
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.util.UUID

class TargetSkillWriter {
    fun writeStaged(targetRoot: Path, skill: ConvertedSkill): Path {
        Files.createDirectories(targetRoot)
        val staging = targetRoot.resolve(".skillops-stage-${UUID.randomUUID()}")
        Files.createDirectory(staging)
        try {
            skill.directories.sortedBy { it.nameCount }.forEach { relative ->
                Files.createDirectories(safeResolve(staging, relative))
            }
            skill.files.forEach { (relative, bytes) ->
                val file = safeResolve(staging, relative)
                file.parent?.let(Files::createDirectories)
                Files.write(file, bytes)
                if (relative in skill.executableFiles) preserveExecutable(file)
            }
            return staging
        } catch (error: Throwable) {
            deleteTree(staging)
            throw error
        }
    }

    fun install(staging: Path, destination: Path, replace: Boolean) {
        var backup: Path? = null
        try {
            if (Files.exists(destination)) {
                require(replace) { "Target skill already exists: $destination" }
                backup = destination.parent.resolve(".skillops-backup-${UUID.randomUUID()}")
                move(destination, backup)
            }
            move(staging, destination)
            backup?.let { runCatching { deleteTree(it) } }
        } catch (error: Throwable) {
            if (!Files.exists(destination) && backup != null && Files.exists(backup)) {
                runCatching { move(backup, destination) }
            }
            if (Files.exists(staging)) deleteTree(staging)
            throw error
        }
    }

    fun discard(staging: Path) = deleteTree(staging)

    private fun safeResolve(root: Path, relative: Path): Path {
        require(!relative.isAbsolute && !relative.startsWith("..")) { "Unsafe target path: $relative" }
        return root.resolve(relative).normalize().also { require(it.startsWith(root)) { "Unsafe target path: $relative" } }
    }

    private fun move(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target)
        }
    }

    private fun preserveExecutable(path: Path) {
        runCatching {
            val permissions = Files.getPosixFilePermissions(path).toMutableSet()
            permissions += PosixFilePermission.OWNER_EXECUTE
            Files.setPosixFilePermissions(path, permissions)
        }
    }

    private fun deleteTree(root: Path) {
        if (!Files.exists(root)) return
        Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }
}

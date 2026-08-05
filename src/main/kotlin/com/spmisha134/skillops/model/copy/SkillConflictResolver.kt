package com.spmisha134.skillops.model.copy

import java.nio.file.Path

fun interface SkillConflictResolver {
    fun resolve(sourceName: String, targetPath: Path): SkillConflictPolicy
}

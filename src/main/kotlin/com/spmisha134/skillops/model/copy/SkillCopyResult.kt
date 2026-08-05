package com.spmisha134.skillops.model.copy

import java.nio.file.Path

enum class SkillCopyStatus {
    COPIED,
    REPLACED,
    RENAMED,
    SKIPPED,
    FAILED,
}

data class SkillCopyItemResult(
    val sourceName: String,
    val targetName: String? = null,
    val targetPath: Path? = null,
    val status: SkillCopyStatus,
    val issue: String? = null,
)

data class SkillCopyResult(val items: List<SkillCopyItemResult>)

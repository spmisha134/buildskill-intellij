package com.spmisha134.skillops.model.copy

import com.spmisha134.skillops.model.skill.SkillPlatform
import java.nio.file.Path

data class SkillCopyRequest(
    val projectRoot: Path,
    val sourcePlatform: SkillPlatform,
    val targetPlatform: SkillPlatform,
    val sourceSkillNames: List<String>,
    val conflictPolicy: SkillConflictPolicy,
)

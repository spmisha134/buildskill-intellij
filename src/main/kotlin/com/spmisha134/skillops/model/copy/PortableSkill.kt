package com.spmisha134.skillops.model.copy

import com.spmisha134.skillops.model.skill.SkillPlatform
import java.nio.file.Path

data class PortableSkill(
    val folderName: String,
    val name: String,
    val description: String,
    val sourcePlatform: SkillPlatform,
    val files: Map<Path, ByteArray>,
    val directories: Set<Path>,
    val executableFiles: Set<Path>,
)

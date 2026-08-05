package com.spmisha134.skillops.model.copy

import java.nio.file.Path

data class ConvertedSkill(
    val files: Map<Path, ByteArray>,
    val directories: Set<Path>,
    val executableFiles: Set<Path>,
)

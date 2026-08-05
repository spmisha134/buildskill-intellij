package com.spmisha134.skillops.model.copy

import java.nio.file.Path

data class DiscoveredSkill(
    val folderName: String,
    val displayName: String?,
    val path: Path,
    val isValid: Boolean,
    val issue: String? = null,
)

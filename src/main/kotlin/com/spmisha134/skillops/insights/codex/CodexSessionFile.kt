package com.spmisha134.skillops.insights.codex

import java.nio.file.Path

data class CodexSessionFile(
    val path: Path,
    val fileName: String,
    val lastModifiedMs: Long,
    val sizeBytes: Long,
)

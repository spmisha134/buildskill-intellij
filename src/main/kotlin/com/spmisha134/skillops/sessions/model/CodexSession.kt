package com.spmisha134.skillops.sessions.model

import java.nio.file.Path

data class CodexSession(
    val resumeTarget: SessionResumeTarget,
    val initialPrompt: String?,
    val skillNames: List<String>,
    val lastModifiedMs: Long,
    val totalTokens: Long?,
    val sessionPath: Path,
)

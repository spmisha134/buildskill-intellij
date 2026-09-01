package com.spmisha134.skillops.sessions.model

import java.nio.file.Path

data class CodexSession(
    val resumeTarget: SessionResumeTarget,
    val title: String?,
    val userPrompts: List<String>,
    val skillNames: List<String>,
    val lastModifiedMs: Long,
    val totalTokens: Long?,
    val sessionPath: Path,
) {
    val initialPrompt: String?
        get() = userPrompts.firstOrNull()
}

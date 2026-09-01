package com.spmisha134.skillops.sessions.model

import java.nio.file.Path

data class CodexSessionMetadata(
    val sessionId: String,
    val workingDirectory: Path?,
    val title: String?,
    val userPrompts: List<String>,
) {
    val initialPrompt: String?
        get() = userPrompts.firstOrNull()
    fun toResumeTarget(): SessionResumeTarget = SessionResumeTarget(sessionId, workingDirectory)
}

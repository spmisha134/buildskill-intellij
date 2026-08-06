package com.spmisha134.skillops.sessions.model

import java.nio.file.Path

data class CodexSessionMetadata(
    val sessionId: String,
    val workingDirectory: Path?,
    val initialPrompt: String?,
) {
    fun toResumeTarget(): SessionResumeTarget = SessionResumeTarget(sessionId, workingDirectory)
}

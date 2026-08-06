package com.spmisha134.skillops.sessions.model

import java.nio.file.Path

data class SessionResumeTarget(
    val sessionId: String,
    val workingDirectory: Path?,
)

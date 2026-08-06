package com.spmisha134.skillops.sessions.model

data class CodexSessionsResult(
    val sessions: List<CodexSession>,
    val warnings: List<String>,
)

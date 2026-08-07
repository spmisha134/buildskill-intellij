package com.spmisha134.skillops.sessions.model

data class ClaudeSessionsResult(
    val sessions: List<ClaudeSession>,
    val warnings: List<String>,
)

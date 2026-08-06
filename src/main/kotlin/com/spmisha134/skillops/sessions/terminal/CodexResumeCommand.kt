package com.spmisha134.skillops.sessions.terminal

object CodexResumeCommand {
    fun build(sessionId: String): String = "codex resume $sessionId"
}

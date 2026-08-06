package com.spmisha134.skillops.sessions.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class CodexResumeCommandTest {
    @Test
    fun `builds explicit codex resume command`() {
        assertEquals(
            "codex resume 019cb301-f5cf-76c0-a1db-8ef3580d7800",
            CodexResumeCommand.build("019cb301-f5cf-76c0-a1db-8ef3580d7800"),
        )
    }
}

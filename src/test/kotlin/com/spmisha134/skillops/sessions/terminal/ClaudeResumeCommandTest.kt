package com.spmisha134.skillops.sessions.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class ClaudeResumeCommandTest {
    @Test
    fun `builds Claude resume command variants`() {
        assertEquals("claude --resume session-123", ClaudeResumeCommand.build("session-123"))
        assertEquals("claude --continue", ClaudeResumeCommand.continueMostRecent())
        assertEquals("claude --resume", ClaudeResumeCommand.chooseSession())
        assertEquals("claude -n 'my session'", ClaudeResumeCommand.resumeNamed("my session"))
        assertEquals("claude -n 'Bob'\\''s session'", ClaudeResumeCommand.resumeNamed("Bob's session"))
    }
}

package com.spmisha134.skillops.insights.codex

import com.spmisha134.skillops.insights.parser.InsightJsonlParser
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class CodexProjectSessionMatcherTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `matches session metadata cwd to project`() {
        val events = parseEvents(
            """{"type":"session_meta","payload":{"cwd":"/work/repository"}}""",
        )

        assertEquals(true, CodexProjectSessionMatcher().belongsToProject(events, java.nio.file.Path.of("/work/repository")))
        assertEquals(false, CodexProjectSessionMatcher().belongsToProject(events, java.nio.file.Path.of("/work/other")))
    }

    @Test
    fun `matches a turn workspace root to project`() {
        val events = parseEvents(
            """{"type":"turn_context","payload":{"workspace_roots":["/work/repository"]}}""",
        )

        assertEquals(true, CodexProjectSessionMatcher().belongsToProject(events, java.nio.file.Path.of("/work/repository")))
    }

    @Test
    fun `returns unknown for legacy session without project metadata`() {
        val events = parseEvents("""{"type":"message","payload":{"text":"hello"}}""")

        assertEquals(null, CodexProjectSessionMatcher().belongsToProject(events, java.nio.file.Path.of("/work/repository")))
    }

    private fun parseEvents(vararg lines: String) =
        InsightJsonlParser().parse(
            temporaryFolder.newFile("session.jsonl").toPath().also {
                Files.writeString(it, lines.joinToString(separator = "\n", postfix = "\n"))
            }
        ).events
}

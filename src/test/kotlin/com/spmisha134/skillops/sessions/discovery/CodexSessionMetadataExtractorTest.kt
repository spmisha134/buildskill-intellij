package com.spmisha134.skillops.sessions.discovery

import com.spmisha134.skillops.insights.parser.InsightJsonlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class CodexSessionMetadataExtractorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val extractor = CodexSessionMetadataExtractor()

    @Test
    fun `extracts resumable metadata and initial prompt`() {
        val workingDirectory = temporaryFolder.newFolder("project").toPath()
        val file = temporaryFolder.newFile("rollout.jsonl").toPath()
        Files.writeString(file, """
            {"type":"session_meta","payload":{"id":"019cb301-f5cf-76c0-a1db-8ef3580d7800","cwd":"$workingDirectory"}}
            {"type":"response_item","payload":{"type":"message","role":"user","content":[{"type":"input_text","text":"# AGENTS.md instructions for the repository"}]}}
            {"type":"event_msg","payload":{"type":"user_message","message":"Implement   the session resume feature"}}
        """.trimIndent())

        val metadata = extractor.extract(InsightJsonlParser().parse(file).events, file.fileName.toString())

        assertEquals("019cb301-f5cf-76c0-a1db-8ef3580d7800", metadata?.sessionId)
        assertEquals(workingDirectory, metadata?.workingDirectory)
        assertEquals("Implement the session resume feature", metadata?.initialPrompt)
    }

    @Test
    fun `falls back to uuid in rollout filename`() {
        val id = "019cb301-f5cf-76c0-a1db-8ef3580d7800"
        val metadata = extractor.extract(emptyList(), "rollout-2026-08-06-$id.jsonl")

        assertEquals(id, metadata?.sessionId)
        assertNull(metadata?.workingDirectory)
    }

    @Test
    fun `rejects session without valid uuid`() {
        val file = temporaryFolder.newFile("invalid.jsonl").toPath()
        Files.writeString(file, """{"type":"session_meta","payload":{"id":"not-a-session"}}""")

        assertNull(extractor.extract(InsightJsonlParser().parse(file).events, file.fileName.toString()))
    }

    @Test
    fun `keeps human prompt history and titles session from latest prompt`() {
        val file = temporaryFolder.newFile("rollout.jsonl").toPath()
        Files.writeString(file, """
            {"type":"session_meta","payload":{"id":"019cb301-f5cf-76c0-a1db-8ef3580d7800"}}
            {"type":"response_item","payload":{"type":"message","role":"user","content":[{"text":"# AGENTS.md instructions for /repo"}]}}
            {"type":"event_msg","payload":{"type":"user_message","message":"Investigate the resume session title"}}
            {"type":"event_msg","payload":{"type":"user_message","message":"Then show all meaningful requests"}}
        """.trimIndent())

        val metadata = extractor.extract(InsightJsonlParser().parse(file).events, file.fileName.toString())

        assertEquals("Then show all meaningful requests", metadata?.title)
        assertEquals(
            listOf("Investigate the resume session title", "Then show all meaningful requests"),
            metadata?.userPrompts,
        )
    }
}

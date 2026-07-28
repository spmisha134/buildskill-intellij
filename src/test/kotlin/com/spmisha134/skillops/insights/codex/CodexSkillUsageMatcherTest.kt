package com.spmisha134.skillops.insights.codex

import com.spmisha134.skillops.insights.parser.InsightJsonlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class CodexSkillUsageMatcherTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `matches generated skill path in session text`() {
        val events = parseEvents(
            """{"type":"response_item","payload":{"type":"message","role":"user","content":[{"text":"Read .agents/skills/kafka-review/SKILL.md first"}]}}""",
        )

        val match = CodexSkillUsageMatcher().matchSkill(events, listOf("api-helper", "kafka-review"))

        assertEquals("kafka-review", match)
    }

    @Test
    fun `returns null when no skill is referenced`() {
        val events = parseEvents(
            """{"type":"message","payload":{"text":"ordinary coding task"}}""",
        )

        val match = CodexSkillUsageMatcher().matchSkill(events, listOf("kafka-review"))

        assertNull(match)
    }

    @Test
    fun `matches all skills from explicit codex skill metadata`() {
        val events = parseEvents(
            """{"type":"response_item","payload":{"type":"message","role":"user","content":[{"text":"<skill><name>api-helper</name><path>/repo/.agents/skills/api-helper/SKILL.md</path></skill>"}]}}""",
            """{"type":"response_item","payload":{"type":"message","role":"user","content":[{"text":"<skill><name>kafka-review</name></skill>"}]}}""",
        )

        val matches = CodexSkillUsageMatcher().matchSkills(events, listOf("api-helper", "kafka-review", "unused"))

        assertEquals(listOf("api-helper", "kafka-review"), matches)
    }

    @Test
    fun `detects recorded skill name even when it is not in repository catalog`() {
        val events = parseEvents(
            """{"type":"response_item","payload":{"type":"message","role":"user","content":[{"text":"<skill><name>external-helper</name><path>/repo/.agents/skills/external-helper/SKILL.md</path></skill>"}]}}""",
        )

        val matcher = CodexSkillUsageMatcher()

        assertEquals(emptyList<String>(), matcher.matchSkills(events, listOf("repo-skill")))
        assertEquals(listOf("external-helper"), matcher.detectRecordedSkillNames(events))
    }

    @Test
    fun `does not treat available skills or ordinary task text as executed skills`() {
        val events = parseEvents(
            """{"type":"response_item","payload":{"type":"message","role":"developer","content":[{"text":"Available: <name>another-dep</name> /repo/.agents/skills/another-dep/SKILL.md"}]}}""",
            """{"type":"message","payload":{"text":"update resilience4j"}}""",
            """{"type":"response_item","payload":{"type":"message","role":"user","content":[{"text":"<skill><name>update-deps</name><path>/repo/.agents/skills/update-deps/SKILL.md</path></skill>"}]}}""",
        )

        val matcher = CodexSkillUsageMatcher()

        assertEquals(listOf("update-deps"), matcher.detectRecordedSkillNames(events))
        assertEquals(listOf("update-deps"), matcher.matchSkills(events, listOf("another-dep", "update-deps")))
    }

    @Test
    fun `does not match a skill that is only listed in developer instructions`() {
        val events = parseEvents(
            """{"type":"response_item","payload":{"type":"message","role":"developer","content":[{"text":"Available skill: <name>update-deps</name> /repo/.agents/skills/update-deps/SKILL.md"}]}}""",
            """{"type":"event_msg","payload":{"type":"user_message","message":"Review this project without a skill"}}""",
        )

        assertEquals(emptyList<String>(), CodexSkillUsageMatcher().matchSkills(events, listOf("update-deps")))
    }

    @Test
    fun `extracts command that invoked the skill`() {
        val events = parseEvents(
            """{"type":"event_msg","payload":{"type":"user_message","message":"${'$'}update-deps run the skill"}}""",
            """{"type":"response_item","payload":{"type":"message","role":"user","content":[{"text":"<skill><name>update-deps</name></skill>"}]}}""",
        )

        assertEquals("${'$'}update-deps run the skill", CodexSkillUsageMatcher().invocationCommand(events))
    }

    @Test
    fun `extracts initial prompt from session without a skill`() {
        val events = parseEvents(
            """{"type":"event_msg","payload":{"type":"user_message","message":"Review the Kafka consumer"}}""",
            """{"type":"event_msg","payload":{"type":"user_message","message":"Then update its tests"}}""",
        )

        assertEquals("Review the Kafka consumer", CodexSkillUsageMatcher().invocationCommand(events))
    }

    private fun parseEvents(vararg lines: String) =
        InsightJsonlParser().parse(
            temporaryFolder.newFile("session.jsonl").toPath().also {
                Files.writeString(it, lines.joinToString(separator = "\n", postfix = "\n"))
            }
        ).events
}

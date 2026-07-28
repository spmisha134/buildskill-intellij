package com.spmisha134.skillops.insights.codex

import com.spmisha134.skillops.insights.parser.RawInsightEvent
import com.spmisha134.skillops.insights.parser.objectOrNull
import com.spmisha134.skillops.insights.parser.stringAt
import com.spmisha134.skillops.insights.parser.stringOrNull
import java.nio.file.InvalidPathException
import java.nio.file.Path

class CodexProjectSessionMatcher {
    fun belongsToProject(events: List<RawInsightEvent>, projectRoot: Path): Boolean? {
        val recordedPaths = events.flatMap(::projectPaths)
        if (recordedPaths.isEmpty()) {
            return null
        }

        val normalizedProjectRoot = projectRoot.toAbsolutePath().normalize()
        return recordedPaths.any { recordedPath ->
            normalize(recordedPath)?.startsWith(normalizedProjectRoot) == true
        }
    }

    private fun projectPaths(event: RawInsightEvent): List<String> {
        val payload = event.payload?.getAsJsonObject("payload") ?: return emptyList()
        return buildList {
            payload.stringAt("cwd")?.let(::add)
            payload.getAsJsonArray("workspace_roots")
                ?.mapNotNull { it.stringOrNull() }
                ?.let(::addAll)

            payload.getAsJsonObject("state")
                ?.getAsJsonObject("environments")
                ?.getAsJsonObject("environments")
                ?.entrySet()
                ?.mapNotNull { (_, environment) -> environment.objectOrNull()?.stringAt("cwd") }
                ?.let(::addAll)
        }
    }

    private fun normalize(path: String): Path? =
        try {
            Path.of(path).toAbsolutePath().normalize()
        } catch (_: InvalidPathException) {
            null
        }
}

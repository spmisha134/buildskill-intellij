package com.spmisha134.skillops.insights.claude

import com.spmisha134.skillops.insights.parser.RawInsightEvent
import com.spmisha134.skillops.insights.parser.stringOrNull
import java.nio.file.InvalidPathException
import java.nio.file.Path

class ClaudeSessionMatcher {
    fun belongsToProject(events: List<RawInsightEvent>, projectRoot: Path): Boolean? {
        val paths = events.mapNotNull { it.payload?.get("cwd")?.stringOrNull() }.distinct()
        if (paths.isEmpty()) return null
        val root = projectRoot.toAbsolutePath().normalize()
        return paths.any { recorded ->
            try {
                Path.of(recorded).toAbsolutePath().normalize().startsWith(root)
            } catch (_: InvalidPathException) {
                false
            }
        }
    }
}

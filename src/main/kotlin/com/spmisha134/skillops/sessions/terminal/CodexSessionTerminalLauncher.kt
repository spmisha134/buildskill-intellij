package com.spmisha134.skillops.sessions.terminal

import com.intellij.openapi.project.Project
import com.spmisha134.skillops.sessions.model.SessionResumeTarget
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.nio.file.Files
import java.nio.file.Path

class CodexSessionTerminalLauncher {
    fun resume(project: Project, target: SessionResumeTarget, projectRoot: Path) {
        val workingDirectory = target.workingDirectory
            ?.takeIf(Files::isDirectory)
            ?: projectRoot.toAbsolutePath().normalize()
        val shortId = target.sessionId.take(8)
        val terminalManager = TerminalToolWindowManager.getInstance(project)
        val widget = terminalManager
            .createShellWidget(workingDirectory.toString(), "Codex: $shortId", true, true)
        terminalManager.toolWindow.activate {
            widget.requestFocus()
            widget.sendCommandToExecute(CodexResumeCommand.build(target.sessionId))
        }
    }
}

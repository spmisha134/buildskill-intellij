package com.spmisha134.skillops.sessions.terminal

import com.intellij.openapi.project.Project
import com.intellij.terminal.ui.TerminalWidget
import com.spmisha134.skillops.sessions.model.SessionResumeTarget
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.nio.file.Files
import java.nio.file.Path

class ClaudeSessionTerminalLauncher {
    fun resume(project: Project, target: SessionResumeTarget, projectRoot: Path) =
        launch(project, target, projectRoot, ClaudeResumeCommand.build(target.sessionId))

    fun launch(project: Project, target: SessionResumeTarget?, projectRoot: Path, command: String) {
        val workingDirectory = target?.workingDirectory?.takeIf(Files::isDirectory)
            ?: projectRoot.toAbsolutePath().normalize()
        val shortId = target?.sessionId?.take(8) ?: "session"
        val terminalManager = TerminalToolWindowManager.getInstance(project)
        val widget = terminalManager.createCompatibleShellWidget(workingDirectory.toString(), "Claude: $shortId")
        terminalManager.toolWindow.activate {
            widget.requestFocus()
            widget.sendCommandToExecute(command)
        }
    }

    private fun TerminalToolWindowManager.createCompatibleShellWidget(
        workingDirectory: String,
        tabName: String,
    ): TerminalWidget {
        val modernMethod = javaClass.methods.firstOrNull { method ->
            method.name == "createNewSession" && method.parameterTypes.contentEquals(
                arrayOf(String::class.java, String::class.java, List::class.java,
                    Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType),
            )
        }
        val method = modernMethod ?: javaClass.getMethod(
            "createShellWidget", String::class.java, String::class.java,
            Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
        )
        val arguments = if (modernMethod != null) {
            arrayOf<Any?>(workingDirectory, tabName, null, true, true)
        } else {
            arrayOf<Any?>(workingDirectory, tabName, true, true)
        }
        return method.invoke(this, *arguments) as TerminalWidget
    }
}

package com.spmisha134.skillops.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.spmisha134.skillops.insights.settings.SkillOpsInsightsSettingsState
import com.spmisha134.skillops.presentation.NotificationPresenter
import com.spmisha134.skillops.sessions.model.CodexSessionsResult
import com.spmisha134.skillops.sessions.service.CodexSessionService
import com.spmisha134.skillops.sessions.ui.CodexSessionsDialog
import java.nio.file.Path

class ResumeCodexSessionAction(
    private val sessionService: CodexSessionService = CodexSessionService(),
) : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project?.basePath != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val projectRoot = project.basePath?.let(Path::of)
            ?: return NotificationPresenter.showError(project, "No project base path is available.")
        val settings = SkillOpsInsightsSettingsState.getInstance().settings.copy()

        ProgressManager.getInstance().run(
            object : Task.Modal(project, "Loading Codex sessions", false) {
                private lateinit var result: CodexSessionsResult

                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    indicator.text = "Scanning local Codex sessions…"
                    result = sessionService.findProjectSessions(projectRoot, settings)
                }

                override fun onSuccess() {
                    if (result.sessions.isEmpty()) {
                        val details = result.warnings.joinToString("\n").takeIf(String::isNotBlank)
                        NotificationPresenter.showInfo(project, details ?: "No resumable Codex sessions were found for this project.")
                    } else {
                        CodexSessionsDialog(project, projectRoot, result).show()
                    }
                }

                override fun onThrowable(error: Throwable) {
                    NotificationPresenter.showError(
                        project,
                        "Could not load Codex sessions: ${error.message ?: error.javaClass.simpleName}",
                    )
                }
            }
        )
    }
}

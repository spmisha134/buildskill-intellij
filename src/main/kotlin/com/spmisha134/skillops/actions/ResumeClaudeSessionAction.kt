package com.spmisha134.skillops.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.spmisha134.skillops.insights.settings.SkillOpsInsightsSettingsState
import com.spmisha134.skillops.insights.run.RunInsightsCanceledException
import com.spmisha134.skillops.insights.run.RunInsightsProgress
import com.spmisha134.skillops.presentation.NotificationPresenter
import com.spmisha134.skillops.sessions.model.ClaudeSessionsResult
import com.spmisha134.skillops.sessions.service.ClaudeSessionService
import com.spmisha134.skillops.sessions.ui.ClaudeSessionsDialog
import java.nio.file.Path

class ResumeClaudeSessionAction(
    private val sessionService: ClaudeSessionService = ClaudeSessionService(),
) : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project?.basePath != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val root = project.basePath?.let(Path::of)
            ?: return NotificationPresenter.showError(project, "No project base path is available.")
        val settings = SkillOpsInsightsSettingsState.getInstance().settings.copy()
        ProgressManager.getInstance().run(object : Task.Modal(project, "Loading Claude Sessions", true) {
            private lateinit var result: ClaudeSessionsResult
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                indicator.text = "Scanning local Claude sessions…"
                result = sessionService.findProjectSessions(root, settings, object : RunInsightsProgress {
                    override fun update(message: String, completed: Int, total: Int) {
                        indicator.text = message
                        indicator.fraction = if (total > 0) completed.toDouble() / total else 0.0
                    }

                    override fun checkCanceled() {
                        if (indicator.isCanceled) throw RunInsightsCanceledException()
                    }
                })
            }
            override fun onSuccess() {
                if (result.sessions.isEmpty()) {
                    NotificationPresenter.showInfo(project, result.warnings.joinToString("\n").ifBlank {
                        "No resumable Claude sessions were found for this project."
                    })
                } else ClaudeSessionsDialog(project, root, result).show()
            }
            override fun onThrowable(error: Throwable) {
                if (error is RunInsightsCanceledException) return
                NotificationPresenter.showError(
                    project, "Could not load Claude sessions: ${error.message ?: error.javaClass.simpleName}",
                )
            }
        })
    }
}

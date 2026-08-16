package com.spmisha134.skillops.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.spmisha134.skillops.insights.claude.ClaudeRunInsightsService
import com.spmisha134.skillops.insights.codex.CodexRunInsightsService
import com.spmisha134.skillops.insights.gemini.GeminiRunInsightsService
import com.spmisha134.skillops.insights.presentation.RunInsightsReportFormatter
import com.spmisha134.skillops.insights.run.RunInsightsService
import com.spmisha134.skillops.insights.run.SkillOpsRunInsightsReport
import com.spmisha134.skillops.insights.run.RunInsightsCanceledException
import com.spmisha134.skillops.insights.run.RunInsightsProgress
import com.spmisha134.skillops.insights.settings.SkillOpsInsightsSettingsState
import com.spmisha134.skillops.insights.ui.RunInsightsDialog
import com.spmisha134.skillops.presentation.NotificationPresenter
import java.nio.file.Path

open class ShowRunInsightsAction(
    private val service: RunInsightsService = CodexRunInsightsService(),
    private val platformLabel: String = "Codex",
) : AnAction() {
    private val formatter = RunInsightsReportFormatter()

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
            object : Task.Modal(project, "Loading $platformLabel sessions", false) {
                private lateinit var report: SkillOpsRunInsightsReport

                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = false
                    indicator.text = "Scanning local $platformLabel sessions…"
                    report = service.buildReport(projectRoot, settings, object : RunInsightsProgress {
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
                    RunInsightsDialog(project, report, formatter).show()
                }

                override fun onThrowable(error: Throwable) {
                    if (error is RunInsightsCanceledException) return
                    NotificationPresenter.showError(
                        project,
                        "Could not show $platformLabel run insights: ${error.message ?: error.javaClass.simpleName}",
                    )
                }
            }
        )
    }

}

class ShowClaudeRunInsightsAction : ShowRunInsightsAction(ClaudeRunInsightsService(), "Claude")

class ShowGeminiRunInsightsAction : ShowRunInsightsAction(GeminiRunInsightsService(), "Gemini")

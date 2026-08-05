package com.spmisha134.skillops.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.spmisha134.skillops.copy.SkillCopyService
import com.spmisha134.skillops.copy.discovery.SkillDiscoveryService
import com.spmisha134.skillops.model.copy.DiscoveredSkill
import com.spmisha134.skillops.model.copy.SkillConflictPolicy
import com.spmisha134.skillops.model.copy.SkillConflictResolver
import com.spmisha134.skillops.model.copy.SkillCopyResult
import com.spmisha134.skillops.model.copy.SkillCopyStatus
import com.spmisha134.skillops.model.skill.SkillPlatform
import com.spmisha134.skillops.presentation.NotificationPresenter
import com.spmisha134.skillops.ui.copy.CopySkillsDialog
import com.spmisha134.skillops.ui.copy.CopySkillsProgressDialog
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

class CopySkillsAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project?.basePath != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val projectRoot = project.basePath?.let(Path::of) ?: return
        loadSkills(project, projectRoot)
    }

    private fun loadSkills(project: Project, projectRoot: Path) {
        ProgressManager.getInstance().run(object : Task.Modal(project, "Loading Skills", true) {
            private var discoveredSkills: Map<SkillPlatform, List<DiscoveredSkill>> = emptyMap()

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                val discovery = SkillDiscoveryService()
                discoveredSkills = SkillPlatform.entries.associateWith { discovery.discover(projectRoot, it) }
            }

            override fun onSuccess() {
                showCopyDialog(project, projectRoot, discoveredSkills)
            }

            override fun onThrowable(error: Throwable) {
                NotificationPresenter.showError(project, "Could not load skills: ${error.message ?: error.javaClass.simpleName}")
            }
        })
    }

    private fun showCopyDialog(
        project: Project,
        projectRoot: Path,
        discoveredSkills: Map<SkillPlatform, List<DiscoveredSkill>>,
    ) {
        val dialog = CopySkillsDialog(project, projectRoot, discoveredSkills)
        if (!dialog.showAndGet()) return
        val request = dialog.copyRequest()
        val progressDialog = CopySkillsProgressDialog(project, request.sourceSkillNames.size)
        progressDialog.show()
        ApplicationManager.getApplication().executeOnPooledThread {
            val outcome = runCatching { SkillCopyService().copy(request, conflictResolver(project)) }
            ApplicationManager.getApplication().invokeLater {
                progressDialog.close(0)
                outcome.onSuccess { completed ->
                LocalFileSystem.getInstance().refreshAndFindFileByNioFile(projectRoot)?.refresh(false, true)
                showResult(project, request.targetPlatform.displayName, completed)
                }.onFailure { error ->
                    NotificationPresenter.showError(project, "Could not copy skills: ${error.message ?: error.javaClass.simpleName}")
                }
            }
        }
    }

    private fun conflictResolver(project: Project) = SkillConflictResolver { sourceName, targetPath ->
        val answer = AtomicReference(SkillConflictPolicy.SKIP)
        ApplicationManager.getApplication().invokeAndWait {
            val selected = Messages.showDialog(
                project,
                "A target skill already exists at $targetPath.",
                "Conflict for $sourceName",
                arrayOf("Replace", "Rename", "Skip"),
                2,
                Messages.getQuestionIcon(),
            )
            answer.set(when (selected) {
                0 -> SkillConflictPolicy.REPLACE
                1 -> SkillConflictPolicy.RENAME
                else -> SkillConflictPolicy.SKIP
            })
        }
        answer.get()
    }

    private fun showResult(project: Project, target: String, result: SkillCopyResult) {
        val failures = result.items.filter { it.status == SkillCopyStatus.FAILED }
        val copied = result.items.filter { it.status == SkillCopyStatus.COPIED }
        val exceptional = result.items.filter { it.status != SkillCopyStatus.COPIED && it.status != SkillCopyStatus.FAILED }
        val summary = buildList {
            if (copied.isNotEmpty()) {
                val noun = if (copied.size == 1) "skill" else "skills"
                add("${copied.size} $noun copied to $target: ${copied.joinToString(", ") { it.targetName ?: it.sourceName }}")
            }
            exceptional.forEach { item ->
                add(when (item.status) {
                    SkillCopyStatus.RENAMED -> "${item.sourceName} copied as ${item.targetName}"
                    SkillCopyStatus.REPLACED -> "${item.sourceName} replaced the existing target"
                    SkillCopyStatus.SKIPPED -> "${item.sourceName} skipped"
                    else -> "${item.sourceName}: ${item.status.name.lowercase()}"
                })
            }
            failures.forEach { add("${it.sourceName} failed: ${it.issue}") }
        }.joinToString(". ", postfix = ".")
        if (failures.isEmpty()) NotificationPresenter.showInfo(project, summary)
        else NotificationPresenter.showError(project, summary)
    }
}

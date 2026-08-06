package com.spmisha134.skillops.insights.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.spmisha134.skillops.insights.presentation.RunInsightsReportFormatter
import com.spmisha134.skillops.insights.run.SkillOpsRunInsightsReport
import com.spmisha134.skillops.insights.run.SkillRunInsight
import com.spmisha134.skillops.presentation.NotificationPresenter
import com.spmisha134.skillops.sessions.terminal.CodexSessionTerminalLauncher
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent

class RunInsightsDialog(
    private val project: Project,
    private val report: SkillOpsRunInsightsReport,
    private val formatter: RunInsightsReportFormatter,
    private val terminalLauncher: CodexSessionTerminalLauncher = CodexSessionTerminalLauncher(),
) : DialogWrapper(project) {
    private var selectedInsight: SkillRunInsight? = null
    private val textArea = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }
    private val skillSelector = ComboBox(
        DefaultComboBoxModel(
            report.insights
                .flatMap(formatter::skillNames)
                .distinctBy(String::lowercase)
                .toTypedArray()
        )
    ).apply {
        isEnabled = report.insights.isNotEmpty()
        addActionListener { refreshRuns() }
    }
    private val runSelector = ComboBox<InsightOption>().apply {
        isEnabled = report.insights.isNotEmpty()
        addActionListener { showSelectedInsight() }
    }
    private val resumeButton = JButton("Resume Session").apply {
        addActionListener { resumeSelectedSession() }
    }
    private val copySessionIdButton = JButton("Copy Session ID").apply {
        addActionListener {
            selectedInsight?.resumeTarget?.sessionId?.let { sessionId ->
                CopyPasteManager.getInstance().setContents(StringSelection(sessionId))
            }
        }
    }

    init {
        title = "SkillOps ${report.platformName} Run Insights"
        refreshRuns()
        init()
    }

    override fun createCenterPanel(): JComponent =
        JBPanel<JBPanel<*>>(BorderLayout(0, 8)).apply {
            preferredSize = Dimension(720, 420)
            add(
                JBPanel<JBPanel<*>>(java.awt.GridLayout(2, 1, 0, 6)).apply {
                    add(JBPanel<JBPanel<*>>(BorderLayout(8, 0)).apply {
                        add(JBLabel("Skill:"), BorderLayout.WEST)
                        add(skillSelector, BorderLayout.CENTER)
                    })
                    add(JBPanel<JBPanel<*>>(BorderLayout(8, 0)).apply {
                        add(JBLabel("Run:"), BorderLayout.WEST)
                        add(runSelector, BorderLayout.CENTER)
                    })
                },
                BorderLayout.NORTH,
            )
            add(JBScrollPane(textArea), BorderLayout.CENTER)
            add(JBPanel<JBPanel<*>>(java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0)).apply {
                add(copySessionIdButton)
                add(resumeButton)
            }, BorderLayout.SOUTH)
        }

    private fun showSelectedInsight() {
        val insight = (runSelector.selectedItem as? InsightOption)
            ?.index
            ?.let(report.insights::getOrNull)
        selectedInsight = insight
        textArea.text = if (insight == null) {
            formatter.format(report)
        } else {
            formatter.format(insight, report.platformName)
        }
        textArea.caretPosition = 0
        val resumable = selectedInsight?.resumeTarget != null
        resumeButton.isVisible = report.platformName == "Codex"
        copySessionIdButton.isVisible = report.platformName == "Codex"
        resumeButton.isEnabled = resumable
        copySessionIdButton.isEnabled = resumable
    }

    private fun refreshRuns() {
        val selectedSkill = skillSelector.selectedItem as? String
        val options = report.insights.mapIndexedNotNull { index, insight ->
            val hasSelectedSkill = selectedSkill == null || formatter.skillNames(insight)
                .any { it.equals(selectedSkill, ignoreCase = true) }
            if (hasSelectedSkill) InsightOption(index, formatter.runLabel(insight)) else null
        }
        runSelector.model = DefaultComboBoxModel(options.toTypedArray())
        runSelector.isEnabled = options.isNotEmpty()
        if (options.isNotEmpty()) {
            runSelector.selectedIndex = 0
        }
        showSelectedInsight()
    }

    private fun resumeSelectedSession() {
        val target = selectedInsight?.resumeTarget ?: return
        val projectRoot = project.basePath?.let(Path::of)
            ?: return NotificationPresenter.showError(project, "No project base path is available.")
        try {
            terminalLauncher.resume(project, target, projectRoot)
            close(OK_EXIT_CODE)
        } catch (error: Throwable) {
            NotificationPresenter.showError(
                project,
                "Could not resume Codex session: ${error.message ?: error.javaClass.simpleName}",
            )
        }
    }

    private data class InsightOption(
        val index: Int,
        val label: String,
    ) {
        override fun toString(): String = label
    }
}

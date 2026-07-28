package com.spmisha134.skillops.insights.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.spmisha134.skillops.insights.presentation.RunInsightsReportFormatter
import com.spmisha134.skillops.insights.run.SkillOpsRunInsightsReport
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent

class RunInsightsDialog(
    project: Project,
    private val report: SkillOpsRunInsightsReport,
    private val formatter: RunInsightsReportFormatter,
) : DialogWrapper(project) {
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
        }

    private fun showSelectedInsight() {
        val selectedInsight = (runSelector.selectedItem as? InsightOption)
            ?.index
            ?.let(report.insights::getOrNull)
        textArea.text = if (selectedInsight == null) {
            formatter.format(report)
        } else {
            formatter.format(selectedInsight, report.platformName)
        }
        textArea.caretPosition = 0
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

    private data class InsightOption(
        val index: Int,
        val label: String,
    ) {
        override fun toString(): String = label
    }
}

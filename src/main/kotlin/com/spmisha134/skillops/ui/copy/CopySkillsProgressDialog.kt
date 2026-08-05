package com.spmisha134.skillops.ui.copy

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar

class CopySkillsProgressDialog(project: Project, private val skillCount: Int) : DialogWrapper(project, false) {
    init {
        title = "Copying Skills"
        isModal = false
        init()
    }

    override fun createActions(): Array<Action> = emptyArray()

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout(0, 10)).apply {
        preferredSize = Dimension(420, 70)
        add(JLabel("Converting and validating $skillCount selected skill${if (skillCount == 1) "" else "s"}…"), BorderLayout.NORTH)
        add(JProgressBar().apply { isIndeterminate = true }, BorderLayout.CENTER)
    }
}

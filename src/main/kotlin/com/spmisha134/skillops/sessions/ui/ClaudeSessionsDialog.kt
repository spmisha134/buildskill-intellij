package com.spmisha134.skillops.sessions.ui

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import com.spmisha134.skillops.presentation.NotificationPresenter
import com.spmisha134.skillops.sessions.model.ClaudeSession
import com.spmisha134.skillops.sessions.model.ClaudeSessionsResult
import com.spmisha134.skillops.sessions.terminal.ClaudeResumeCommand
import com.spmisha134.skillops.sessions.terminal.ClaudeSessionTerminalLauncher
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.datatransfer.StringSelection
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.table.AbstractTableModel
import com.intellij.ui.components.JBPanel

class ClaudeSessionsDialog(
    private val project: Project,
    private val projectRoot: Path,
    private val result: ClaudeSessionsResult,
    private val launcher: ClaudeSessionTerminalLauncher = ClaudeSessionTerminalLauncher(),
) : DialogWrapper(project) {
    private val search = JBTextField()
    private val model = SessionTableModel(result.sessions)
    private val table = JBTable(model)
    private val details = JBTextArea(5, 80).apply { isEditable = false; lineWrap = true; wrapStyleWord = true }
    private val copy = JButton("Copy Session ID")
    private val continueButton = JButton("Continue Most Recent")
    private val pickerButton = JButton("Open Claude Session Picker")
    private val namedButton = JButton("Resume Named Session")

    init {
        title = "Claude Sessions"
        setOKButtonText("Resume Session")
        init()
        table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        table.autoCreateRowSorter = true
        if (table.rowCount > 0) table.setRowSelectionInterval(0, 0)
        table.selectionModel.addListSelectionListener { refresh() }
        search.emptyText.text = "Search task, skill, session ID, or directory"
        search.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) { model.filter(search.text); if (table.rowCount > 0) table.setRowSelectionInterval(0, 0); refresh() }
        })
        copy.addActionListener { selected()?.resumeTarget?.sessionId?.let { CopyPasteManager.getInstance().setContents(StringSelection(it)) } }
        continueButton.addActionListener { runCommand(ClaudeResumeCommand.continueMostRecent(), null) }
        pickerButton.addActionListener { runCommand(ClaudeResumeCommand.chooseSession(), null) }
        namedButton.addActionListener {
            val name = Messages.showInputDialog(project, "Claude session name:", "Resume Named Session", null)
                ?.trim()
                ?.takeIf(String::isNotBlank)
            name?.let { runCommand(ClaudeResumeCommand.resumeNamed(it), null) }
        }
        refresh()
    }

    override fun createCenterPanel(): JComponent = JBPanel<JBPanel<*>>(BorderLayout(0, 8)).apply {
        preferredSize = Dimension(1000, 560)
        add(JBPanel<JBPanel<*>>(BorderLayout(8, 0)).apply { add(JBLabel("Search:"), BorderLayout.WEST); add(search, BorderLayout.CENTER) }, BorderLayout.NORTH)
        add(JBPanel<JBPanel<*>>(BorderLayout(0, 8)).apply { add(JBScrollPane(table), BorderLayout.CENTER); add(JBScrollPane(details), BorderLayout.SOUTH) }, BorderLayout.CENTER)
        add(JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
            add(continueButton); add(pickerButton); add(namedButton); add(copy)
            result.warnings.joinToString(" ").takeIf(String::isNotBlank)?.let { add(JBLabel(it), 0) }
        }, BorderLayout.SOUTH)
    }

    override fun createActions() = arrayOf(okAction, cancelAction)

    override fun doOKAction() {
        val session = selected() ?: return
        runCommand(ClaudeResumeCommand.build(session.resumeTarget.sessionId), session)
    }

    private fun runCommand(command: String, session: ClaudeSession?) {
        try {
            launcher.launch(project, session?.resumeTarget, projectRoot, command)
            close(OK_EXIT_CODE)
        } catch (error: Throwable) {
            NotificationPresenter.showError(project, "Could not resume Claude session: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun selected(): ClaudeSession? = table.selectedRow.takeIf { it >= 0 }?.let { model.sessionAt(table.convertRowIndexToModel(it)) }

    private fun refresh() {
        val session = selected()
        isOKActionEnabled = session != null
        copy.isEnabled = session != null
        details.text = session?.let { "Task: ${it.initialPrompt ?: "(No task summary recorded)"}\nWorking directory: ${it.resumeTarget.workingDirectory ?: projectRoot}\nCommand: ${ClaudeResumeCommand.build(it.resumeTarget.sessionId)}" }
            ?: "Select a session to see its task and resume command."
        details.caretPosition = 0
    }

    private class SessionTableModel(private val all: List<ClaudeSession>) : AbstractTableModel() {
        private var rows = all
        private val columns = arrayOf("Updated", "Session", "Task", "Skill")
        override fun getRowCount() = rows.size
        override fun getColumnCount() = columns.size
        override fun getColumnName(column: Int) = columns[column]
        override fun getValueAt(row: Int, column: Int): Any = rows[row].let {
            when (column) {
                0 -> DATE_FORMAT.format(Instant.ofEpochMilli(it.lastModifiedMs).atZone(ZoneId.systemDefault()))
                1 -> it.resumeTarget.sessionId.take(8)
                2 -> it.initialPrompt ?: "(No task summary)"
                else -> it.skillNames.distinctBy(String::lowercase).joinToString().ifBlank { "No skill" }
            }
        }
        fun sessionAt(row: Int) = rows.getOrNull(row)
        fun filter(query: String) { val q = query.trim().lowercase(); rows = if (q.isEmpty()) all else all.filter { s -> listOfNotNull(s.resumeTarget.sessionId, s.resumeTarget.workingDirectory?.toString(), s.initialPrompt, s.skillNames.joinToString()).any { it.lowercase().contains(q) } }; fireTableDataChanged() }
    }

    companion object { private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") }
}

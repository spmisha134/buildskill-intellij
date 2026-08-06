package com.spmisha134.skillops.sessions.ui

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import com.spmisha134.skillops.presentation.NotificationPresenter
import com.spmisha134.skillops.sessions.model.CodexSession
import com.spmisha134.skillops.sessions.model.CodexSessionsResult
import com.spmisha134.skillops.sessions.terminal.CodexResumeCommand
import com.spmisha134.skillops.sessions.terminal.CodexSessionTerminalLauncher
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.table.AbstractTableModel

class CodexSessionsDialog(
    private val project: Project,
    private val projectRoot: Path,
    private val result: CodexSessionsResult,
    private val launcher: CodexSessionTerminalLauncher = CodexSessionTerminalLauncher(),
) : DialogWrapper(project) {
    private val searchField = JBTextField()
    private val tableModel = SessionTableModel(result.sessions)
    private val table = JBTable(tableModel)
    private val copyButton = JButton("Copy Session ID")
    private val detailsArea = JBTextArea(6, 80).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }

    init {
        title = "Codex Sessions"
        setOKButtonText("Resume Session")
        init()
        table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        table.autoCreateRowSorter = true
        table.columnModel.getColumn(0).preferredWidth = 145
        table.columnModel.getColumn(1).preferredWidth = 90
        table.columnModel.getColumn(2).preferredWidth = 420
        table.columnModel.getColumn(3).preferredWidth = 110
        table.columnModel.getColumn(4).preferredWidth = 90
        if (table.rowCount > 0) table.setRowSelectionInterval(0, 0)
        table.selectionModel.addListSelectionListener { updateActions() }
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2 && selectedSession() != null) doOKAction()
            }
        })
        table.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "resumeSession")
        table.actionMap.put("resumeSession", object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent) = doOKAction()
        })
        searchField.emptyText.text = "Search task, skill, session ID, or directory"
        searchField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) {
                tableModel.filter(searchField.text)
                if (table.rowCount > 0) table.setRowSelectionInterval(0, 0)
                updateActions()
            }
        })
        copyButton.addActionListener { copySelectedSessionId() }
        updateActions()
    }

    override fun createCenterPanel(): JComponent = JBPanel<JBPanel<*>>(BorderLayout(0, 8)).apply {
        preferredSize = Dimension(1000, 560)
        add(JBPanel<JBPanel<*>>(BorderLayout(8, 0)).apply {
            add(JBLabel("Search:"), BorderLayout.WEST)
            add(searchField, BorderLayout.CENTER)
        }, BorderLayout.NORTH)
        add(JBPanel<JBPanel<*>>(BorderLayout(0, 8)).apply {
            add(JBScrollPane(table), BorderLayout.CENTER)
            add(JBPanel<JBPanel<*>>(BorderLayout(0, 4)).apply {
                add(JBLabel("Selected session:"), BorderLayout.NORTH)
                add(JBScrollPane(detailsArea), BorderLayout.CENTER)
            }, BorderLayout.SOUTH)
        }, BorderLayout.CENTER)
        add(JBPanel<JBPanel<*>>(BorderLayout()).apply {
            val warning = result.warnings.joinToString(" ").takeIf(String::isNotBlank)
            if (warning != null) add(JBLabel(warning), BorderLayout.CENTER)
            add(copyButton, BorderLayout.EAST)
        }, BorderLayout.SOUTH)
    }

    override fun createActions(): Array<Action> = arrayOf(okAction, cancelAction)

    override fun doOKAction() {
        val session = selectedSession() ?: return
        try {
            launcher.resume(project, session.resumeTarget, projectRoot)
            close(OK_EXIT_CODE)
        } catch (error: Throwable) {
            NotificationPresenter.showError(
                project,
                "Could not resume Codex session: ${error.message ?: error.javaClass.simpleName}",
            )
        }
    }

    private fun selectedSession(): CodexSession? {
        val viewRow = table.selectedRow
        if (viewRow < 0) return null
        return tableModel.sessionAt(table.convertRowIndexToModel(viewRow))
    }

    private fun copySelectedSessionId() {
        val sessionId = selectedSession()?.resumeTarget?.sessionId ?: return
        CopyPasteManager.getInstance().setContents(StringSelection(sessionId))
    }

    private fun updateActions() {
        val session = selectedSession()
        val hasSelection = session != null
        isOKActionEnabled = hasSelection
        copyButton.isEnabled = hasSelection
        detailsArea.text = session?.let(::formatDetails) ?: "Select a session to see its task and resume command."
        detailsArea.caretPosition = 0
    }

    private fun formatDetails(session: CodexSession): String = buildString {
        appendLine("Task: ${session.initialPrompt ?: "(No task summary recorded)"}")
        appendLine("Working directory: ${session.resumeTarget.workingDirectory ?: projectRoot}")
        append("Command: ${CodexResumeCommand.build(session.resumeTarget.sessionId)}")
    }

    private class SessionTableModel(private val allSessions: List<CodexSession>) : AbstractTableModel() {
        private var sessions = allSessions
        private val columns = arrayOf("Updated", "Session", "Task", "Skill", "Tokens")

        override fun getRowCount(): Int = sessions.size
        override fun getColumnCount(): Int = columns.size
        override fun getColumnName(column: Int): String = columns[column]
        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val session = sessions[rowIndex]
            return when (columnIndex) {
                0 -> DATE_FORMAT.format(Instant.ofEpochMilli(session.lastModifiedMs).atZone(ZoneId.systemDefault()))
                1 -> session.resumeTarget.sessionId.take(8)
                2 -> session.initialPrompt ?: "(No task summary)"
                3 -> session.skillNames.joinToString().ifBlank { "No skill" }
                else -> session.totalTokens?.let { "%,d".format(it) } ?: "—"
            }
        }

        fun sessionAt(row: Int): CodexSession? = sessions.getOrNull(row)

        fun filter(query: String) {
            val normalized = query.trim().lowercase()
            sessions = if (normalized.isEmpty()) allSessions else allSessions.filter { session ->
                listOfNotNull(
                    session.resumeTarget.sessionId,
                    session.resumeTarget.workingDirectory?.toString(),
                    session.initialPrompt,
                    session.skillNames.joinToString(" "),
                ).any { it.lowercase().contains(normalized) }
            }
            fireTableDataChanged()
        }
    }

    companion object {
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}

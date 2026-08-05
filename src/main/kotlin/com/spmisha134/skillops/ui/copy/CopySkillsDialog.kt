package com.spmisha134.skillops.ui.copy

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBList
import com.spmisha134.skillops.model.copy.DiscoveredSkill
import com.spmisha134.skillops.model.copy.SkillCopyRequest
import com.spmisha134.skillops.model.copy.SkillConflictPolicy
import com.spmisha134.skillops.model.skill.SkillPlatform
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Path
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ListCellRenderer

class CopySkillsDialog(
    project: Project,
    private val projectRoot: Path,
    private val discoveredSkills: Map<SkillPlatform, List<DiscoveredSkill>>,
) : DialogWrapper(project) {
    private val sourceBox = JComboBox(SkillPlatform.entries.toTypedArray())
    private val targetBox = JComboBox<SkillPlatform>()
    private val listModel = DefaultListModel<SkillRow>()
    private val skillList = JBList(listModel)
    private val emptyLabel = JLabel()
    private val panel = buildPanel()

    init {
        title = "Copy and Convert Skills"
        sourceBox.renderer = PlatformRenderer()
        targetBox.renderer = PlatformRenderer()
        sourceBox.selectedItem = SkillPlatform.CODEX
        sourceBox.addActionListener { refreshSource() }
        targetBox.addActionListener { refreshConflictStates() }
        skillList.cellRenderer = SkillRowRenderer()
        skillList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                val index = skillList.locationToIndex(event.point)
                if (index < 0) return
                val row = listModel[index]
                if (row.skill.isValid) {
                    row.selected = !row.selected
                    skillList.repaint(skillList.getCellBounds(index, index))
                }
            }
        })
        refreshSource()
        init()
    }

    override fun createCenterPanel(): JComponent = panel

    override fun getPreferredFocusedComponent(): JComponent = sourceBox

    override fun doValidate(): ValidationInfo? = when {
        selectedSkills().isEmpty() -> ValidationInfo("Select at least one valid skill.", skillList)
        sourcePlatform() == targetPlatform() -> ValidationInfo("Source and target must be different.", targetBox)
        else -> null
    }

    fun copyRequest(): SkillCopyRequest = SkillCopyRequest(
        projectRoot = projectRoot,
        sourcePlatform = sourcePlatform(),
        targetPlatform = targetPlatform(),
        sourceSkillNames = selectedSkills().map { it.folderName },
        conflictPolicy = SkillConflictPolicy.ASK,
    )

    private fun buildPanel(): JComponent = JPanel(BorderLayout(0, 8)).apply {
        preferredSize = Dimension(680, 440)
        add(JPanel(GridBagLayout()).apply {
            addLabeled("Source", sourceBox, 0)
            addLabeled("Target", targetBox, 1)
        }, BorderLayout.NORTH)
        add(JPanel(BorderLayout()).apply {
            add(emptyLabel, BorderLayout.NORTH)
            add(JScrollPane(skillList), BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                add(JButton("Select all").apply { addActionListener { setAll(true) } })
                add(JButton("Clear").apply { addActionListener { setAll(false) } })
            }, BorderLayout.SOUTH)
        }, BorderLayout.CENTER)
    }

    private fun JPanel.addLabeled(label: String, component: JComponent, row: Int) {
        add(JLabel(label), GridBagConstraints().apply {
            gridx = 0; gridy = row; anchor = GridBagConstraints.WEST; insets.set(4, 0, 4, 12)
        })
        add(component, GridBagConstraints().apply {
            gridx = 1; gridy = row; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; insets.set(4, 0, 4, 0)
        })
    }

    private fun refreshSource() {
        val source = sourcePlatform()
        val previousTarget = targetBox.selectedItem as? SkillPlatform
        val targets = SkillPlatform.entries.filter { it != source }
        targetBox.model = DefaultComboBoxModel(targets.toTypedArray())
        targetBox.selectedItem = previousTarget?.takeIf { it in targets } ?: targets.first()
        listModel.clear()
        discoveredSkills[source].orEmpty().forEach { listModel.addElement(SkillRow(it)) }
        emptyLabel.text = if (listModel.isEmpty) "No skills found under ${source.projectDirectory}/skills/." else "Select skills to convert:"
        refreshConflictStates()
    }

    private fun refreshConflictStates() {
        val target = targetBox.selectedItem as? SkillPlatform ?: return
        for (index in 0 until listModel.size()) {
            val row = listModel[index]
            row.hasConflict = projectRoot.resolve(target.projectDirectory).resolve("skills").resolve(row.skill.folderName).toFile().exists()
        }
        skillList.repaint()
    }

    private fun setAll(selected: Boolean) {
        for (index in 0 until listModel.size()) listModel[index].selected = selected && listModel[index].skill.isValid
        skillList.repaint()
    }

    private fun sourcePlatform() = sourceBox.selectedItem as SkillPlatform
    private fun targetPlatform() = targetBox.selectedItem as SkillPlatform
    private fun selectedSkills() = (0 until listModel.size()).map { listModel[it] }.filter { it.selected }.map { it.skill }

    private data class SkillRow(val skill: DiscoveredSkill, var selected: Boolean = false, var hasConflict: Boolean = false)

    private class SkillRowRenderer : JCheckBox(), ListCellRenderer<SkillRow> {
        override fun getListCellRendererComponent(
            list: JList<out SkillRow>, value: SkillRow, index: Int, isSelected: Boolean, cellHasFocus: Boolean,
        ): Component {
            this.isSelected = value.selected
            isEnabled = value.skill.isValid
            val status = when {
                !value.skill.isValid -> " — Invalid: ${value.skill.issue}"
                value.hasConflict -> " — Target conflict"
                else -> " — Ready"
            }
            text = "${value.skill.folderName}$status"
            background = if (isSelected) list.selectionBackground else list.background
            foreground = if (isSelected) list.selectionForeground else list.foreground
            isOpaque = true
            return this
        }
    }

    private class PlatformRenderer : JLabel(), ListCellRenderer<SkillPlatform> {
        override fun getListCellRendererComponent(
            list: JList<out SkillPlatform>, value: SkillPlatform?, index: Int, isSelected: Boolean, cellHasFocus: Boolean,
        ): Component {
            text = value?.displayName.orEmpty()
            background = if (isSelected) list.selectionBackground else list.background
            foreground = if (isSelected) list.selectionForeground else list.foreground
            isOpaque = true
            return this
        }
    }
}

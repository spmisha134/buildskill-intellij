package com.spmisha134.skillops.insights.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.ui.components.JBTextField
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class SkillOpsInsightsConfigurable(
    private val settingsState: SkillOpsInsightsSettingsState = SkillOpsInsightsSettingsState.getInstance()
) : Configurable {
    private var form: SkillOpsInsightsSettingsForm? = null

    override fun getDisplayName(): String = "SkillOps Insights"

    override fun createComponent(): JComponent {
        return SkillOpsInsightsSettingsForm()
            .also {
                form = it
                it.reset(settingsState.settings)
            }
            .panel
    }

    override fun isModified(): Boolean {
        val currentForm = form ?: return false
        return currentForm.settingsOrNull() != settingsState.settings
    }

    override fun apply() {
        val currentForm = form ?: return
        val settings = currentForm.settingsOrThrow()
        val errors = settings.validationErrors()
        if (errors.isNotEmpty()) {
            throw ConfigurationException(errors.joinToString("\n"))
        }
        settingsState.settings = settings
    }

    override fun reset() {
        form?.reset(settingsState.settings)
    }

    override fun disposeUIResources() {
        form = null
    }
}

private class SkillOpsInsightsSettingsForm {
    private val codexHomePathField = JBTextField()
    private val claudeHomePathField = JBTextField()
    private val geminiHomePathField = JBTextField()
    private val maxSessionsToScanField = JBTextField()
    private val autoRefreshEnabledCheckBox = JCheckBox("Auto refresh enabled")
    private val refreshIntervalSecondsField = JBTextField()
    private val completedRunQuietPeriodSecondsField = JBTextField()
    private val fallbackCompletedRunQuietPeriodSecondsField = JBTextField()
    private val largeOutputWarningBytesField = JBTextField()
    private val highOutputWarningBytesField = JBTextField()
    private val manySearchesThresholdField = JBTextField()
    private val showStatusBarWidgetCheckBox = JCheckBox("Show status bar widget")
    private val enableSuggestionsCheckBox = JCheckBox("Enable suggestions")

    val panel: JComponent = JPanel(GridBagLayout()).apply {
        var row = 0
        addLabeled("Codex home path", codexHomePathField, row++)
        addLabeled("Claude home path", claudeHomePathField, row++)
        addLabeled("Gemini home path", geminiHomePathField, row++)
        addLabeled("Maximum sessions to scan", maxSessionsToScanField, row++)
        addFullWidth(autoRefreshEnabledCheckBox, row++)
        addLabeled("Refresh interval seconds", refreshIntervalSecondsField, row++)
        addLabeled("Completed-run quiet period seconds", completedRunQuietPeriodSecondsField, row++)
        addLabeled("Fallback completed-run quiet period seconds", fallbackCompletedRunQuietPeriodSecondsField, row++)
        addLabeled("Large output warning bytes", largeOutputWarningBytesField, row++)
        addLabeled("High output warning bytes", highOutputWarningBytesField, row++)
        addLabeled("Many searches threshold", manySearchesThresholdField, row++)
        addFullWidth(showStatusBarWidgetCheckBox, row++)
        addFullWidth(enableSuggestionsCheckBox, row++)
        addFiller(row)
    }

    fun reset(settings: SkillOpsInsightsSettings) {
        codexHomePathField.text = settings.codexHomePath
        claudeHomePathField.text = settings.claudeHomePath
        geminiHomePathField.text = settings.geminiHomePath
        maxSessionsToScanField.text = settings.maxSessionsToScan.toString()
        autoRefreshEnabledCheckBox.isSelected = settings.autoRefreshEnabled
        refreshIntervalSecondsField.text = settings.refreshIntervalSeconds.toString()
        completedRunQuietPeriodSecondsField.text = settings.completedRunQuietPeriodSeconds.toString()
        fallbackCompletedRunQuietPeriodSecondsField.text = settings.fallbackCompletedRunQuietPeriodSeconds.toString()
        largeOutputWarningBytesField.text = settings.largeOutputWarningBytes.toString()
        highOutputWarningBytesField.text = settings.highOutputWarningBytes.toString()
        manySearchesThresholdField.text = settings.manySearchesThreshold.toString()
        showStatusBarWidgetCheckBox.isSelected = settings.showStatusBarWidget
        enableSuggestionsCheckBox.isSelected = settings.enableSuggestions
    }

    fun settingsOrNull(): SkillOpsInsightsSettings? =
        runCatching { settingsOrThrow() }.getOrNull()

    fun settingsOrThrow(): SkillOpsInsightsSettings =
        SkillOpsInsightsSettings(
            codexHomePath = codexHomePathField.text.trim(),
            claudeHomePath = claudeHomePathField.text.trim(),
            geminiHomePath = geminiHomePathField.text.trim(),
            maxSessionsToScan = maxSessionsToScanField.requiredInt("Maximum sessions to scan"),
            autoRefreshEnabled = autoRefreshEnabledCheckBox.isSelected,
            refreshIntervalSeconds = refreshIntervalSecondsField.requiredInt("Refresh interval seconds"),
            completedRunQuietPeriodSeconds = completedRunQuietPeriodSecondsField.requiredInt("Completed-run quiet period seconds"),
            fallbackCompletedRunQuietPeriodSeconds = fallbackCompletedRunQuietPeriodSecondsField.requiredInt("Fallback completed-run quiet period seconds"),
            largeOutputWarningBytes = largeOutputWarningBytesField.requiredLong("Large output warning bytes"),
            highOutputWarningBytes = highOutputWarningBytesField.requiredLong("High output warning bytes"),
            manySearchesThreshold = manySearchesThresholdField.requiredInt("Many searches threshold"),
            showStatusBarWidget = showStatusBarWidgetCheckBox.isSelected,
            enableSuggestions = enableSuggestionsCheckBox.isSelected,
        )

    private fun JBTextField.requiredInt(label: String): Int =
        text.trim().toIntOrNull() ?: throw ConfigurationException("$label must be a whole number.")

    private fun JBTextField.requiredLong(label: String): Long =
        text.trim().toLongOrNull() ?: throw ConfigurationException("$label must be a whole number.")

    private fun JPanel.addLabeled(label: String, component: JComponent, row: Int) {
        add(
            JLabel(label),
            GridBagConstraints().apply {
                gridx = 0
                gridy = row
                anchor = GridBagConstraints.NORTHWEST
                insets.set(4, 0, 4, 8)
            }
        )
        add(
            component,
            GridBagConstraints().apply {
                gridx = 1
                gridy = row
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                insets.set(4, 0, 4, 0)
            }
        )
    }

    private fun JPanel.addFullWidth(component: JComponent, row: Int) {
        add(
            component,
            GridBagConstraints().apply {
                gridx = 1
                gridy = row
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                insets.set(2, 0, 2, 0)
            }
        )
    }

    private fun JPanel.addFiller(row: Int) {
        add(
            JPanel(),
            GridBagConstraints().apply {
                gridx = 0
                gridy = row
                gridwidth = 2
                weightx = 1.0
                weighty = 1.0
                fill = GridBagConstraints.BOTH
            }
        )
    }
}

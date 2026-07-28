package com.spmisha134.skillops.insights.settings

data class SkillOpsInsightsSettings(
    var codexHomePath: String = "~/.codex",
    var claudeHomePath: String = "~/.claude",
    var maxSessionsToScan: Int = 200,
    var autoRefreshEnabled: Boolean = true,
    var refreshIntervalSeconds: Int = 10,
    var completedRunQuietPeriodSeconds: Int = 30,
    var fallbackCompletedRunQuietPeriodSeconds: Int = 120,
    var largeOutputWarningBytes: Long = 50_000,
    var highOutputWarningBytes: Long = 250_000,
    var manySearchesThreshold: Int = 10,
    var showStatusBarWidget: Boolean = true,
    var enableSuggestions: Boolean = true,
    var geminiHomePath: String = "~/.gemini",
) {
    fun normalized(): SkillOpsInsightsSettings {
        val normalizedLargeOutputWarningBytes = largeOutputWarningBytes.coerceAtLeast(1)
        return copy(
            codexHomePath = codexHomePath.ifBlank { DEFAULT_CODEX_HOME_PATH },
            claudeHomePath = claudeHomePath.ifBlank { DEFAULT_CLAUDE_HOME_PATH },
            maxSessionsToScan = maxSessionsToScan.coerceAtLeast(1),
            refreshIntervalSeconds = refreshIntervalSeconds.coerceAtLeast(5),
            completedRunQuietPeriodSeconds = completedRunQuietPeriodSeconds.coerceAtLeast(0),
            fallbackCompletedRunQuietPeriodSeconds = fallbackCompletedRunQuietPeriodSeconds.coerceAtLeast(0),
            largeOutputWarningBytes = normalizedLargeOutputWarningBytes,
            highOutputWarningBytes = highOutputWarningBytes.coerceAtLeast(normalizedLargeOutputWarningBytes),
            manySearchesThreshold = manySearchesThreshold.coerceAtLeast(1),
            geminiHomePath = geminiHomePath.ifBlank { DEFAULT_GEMINI_HOME_PATH },
        )
    }

    fun validationErrors(): List<String> {
        val errors = mutableListOf<String>()

        if (maxSessionsToScan <= 0) {
            errors += "Maximum sessions to scan must be greater than 0."
        }
        if (refreshIntervalSeconds < 5) {
            errors += "Refresh interval must be at least 5 seconds."
        }
        if (largeOutputWarningBytes <= 0) {
            errors += "Large output threshold must be greater than 0 bytes."
        }
        if (highOutputWarningBytes < largeOutputWarningBytes) {
            errors += "High output threshold must be greater than or equal to large output threshold."
        }
        if (manySearchesThreshold <= 0) {
            errors += "Many searches threshold must be greater than 0."
        }

        return errors
    }

    companion object {
        const val DEFAULT_CODEX_HOME_PATH = "~/.codex"
        const val DEFAULT_CLAUDE_HOME_PATH = "~/.claude"
        const val DEFAULT_GEMINI_HOME_PATH = "~/.gemini"
    }
}

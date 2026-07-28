package com.spmisha134.skillops.insights.run

data class SkillOpsRunInsightsReport(
    val insights: List<SkillRunInsight>,
    val warnings: List<String>,
    val platformName: String,
) {
    constructor(
        insights: List<SkillRunInsight>,
        warnings: List<String>,
    ) : this(insights, warnings, "Codex")

    val latestInsight: SkillRunInsight?
        get() = insights.firstOrNull()
}

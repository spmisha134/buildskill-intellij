package com.spmisha134.skillops.insights.run

data class EfficiencySummary(
    val outputInputRatio: Double?,
    val cachedInputPercent: Double?,
    val reasoningOutputPercent: Double?,
    val searchCount: Int,
    val warnings: List<String>,
    val toolCallCount: Int,
) {
    constructor(
        outputInputRatio: Double?,
        cachedInputPercent: Double?,
        reasoningOutputPercent: Double?,
        searchCount: Int,
        warnings: List<String>,
    ) : this(
        outputInputRatio,
        cachedInputPercent,
        reasoningOutputPercent,
        searchCount,
        warnings,
        0,
    )
}

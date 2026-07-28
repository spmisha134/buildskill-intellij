package com.spmisha134.skillops.insights.parser

import java.nio.file.Path

data class InsightParseResult(
    val filePath: Path,
    val events: List<RawInsightEvent>,
    val warnings: List<String>,
)

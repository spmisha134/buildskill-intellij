package com.spmisha134.skillops.insights.codex

data class CodexSessionScanResult(
    val files: List<CodexSessionFile>,
    val warnings: List<String>,
)

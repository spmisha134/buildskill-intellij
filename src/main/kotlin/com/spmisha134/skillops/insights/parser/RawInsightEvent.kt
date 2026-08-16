package com.spmisha134.skillops.insights.parser

import com.google.gson.JsonObject

data class RawInsightEvent(
    val lineNumber: Int,
    val timestamp: String?,
    val type: String?,
    val payload: JsonObject?,
    val parseError: String?,
)

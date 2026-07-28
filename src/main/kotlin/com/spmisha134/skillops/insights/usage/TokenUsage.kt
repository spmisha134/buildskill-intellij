package com.spmisha134.skillops.insights.usage

data class TokenUsage(
    val inputTokens: Long?,
    val outputTokens: Long?,
    val cachedInputTokens: Long?,
    val reasoningOutputTokens: Long?,
    val totalTokens: Long?,
    val rateLimitUsedPercent: Double?,
    val rateLimitResetAt: String?,
    val rawEvidence: Map<String, Any?>,
    val cacheCreationInputTokens: Long?,
    val toolTokens: Long?,
) {
    constructor(
        inputTokens: Long?,
        outputTokens: Long?,
        cachedInputTokens: Long?,
        reasoningOutputTokens: Long?,
        totalTokens: Long?,
        rateLimitUsedPercent: Double?,
        rateLimitResetAt: String?,
        rawEvidence: Map<String, Any?>,
        cacheCreationInputTokens: Long?,
    ) : this(
        inputTokens,
        outputTokens,
        cachedInputTokens,
        reasoningOutputTokens,
        totalTokens,
        rateLimitUsedPercent,
        rateLimitResetAt,
        rawEvidence,
        cacheCreationInputTokens,
        null,
    )

    constructor(
        inputTokens: Long?,
        outputTokens: Long?,
        cachedInputTokens: Long?,
        reasoningOutputTokens: Long?,
        totalTokens: Long?,
        rateLimitUsedPercent: Double?,
        rateLimitResetAt: String?,
        rawEvidence: Map<String, Any?>,
    ) : this(
        inputTokens,
        outputTokens,
        cachedInputTokens,
        reasoningOutputTokens,
        totalTokens,
        rateLimitUsedPercent,
        rateLimitResetAt,
        rawEvidence,
        null,
        null,
    )
}

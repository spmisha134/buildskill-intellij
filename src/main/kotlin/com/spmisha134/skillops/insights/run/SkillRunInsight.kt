package com.spmisha134.skillops.insights.run

import com.spmisha134.skillops.insights.usage.TokenUsage
import com.spmisha134.skillops.sessions.model.SessionResumeTarget
import java.nio.file.Path

data class SkillRunInsight(
    val sessionPath: Path,
    val sessionFileName: String,
    val lastModifiedMs: Long,
    val sizeBytes: Long,
    val matchedSkillName: String?,
    val tokenUsage: TokenUsage?,
    val efficiencySummary: EfficiencySummary,
    val warnings: List<String>,
    val matchedSkillNames: List<String> = listOfNotNull(matchedSkillName),
    val recordedSkillNames: List<String> = matchedSkillNames,
    val invocationCommand: String? = null,
    val resumeTarget: SessionResumeTarget? = null,
)

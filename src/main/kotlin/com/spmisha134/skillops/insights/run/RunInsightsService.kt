package com.spmisha134.skillops.insights.run

import com.spmisha134.skillops.insights.settings.SkillOpsInsightsSettings
import java.nio.file.Path

interface RunInsightsService {
    fun buildReport(projectRoot: Path, settings: SkillOpsInsightsSettings): SkillOpsRunInsightsReport
}

<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# skillops-intellij Changelog

## [Unreleased]

### Added
- Local Claude Code run insights with input, output, cache-read, cache-creation, tool-call, and search metrics.
- Claude subagent transcript merging, assistant-call deduplication, and explicit skill attribution.
- Local Gemini CLI run insights with recorded token totals, tool/search activity, and structured skill activation.
- Configurable Claude and Gemini home paths in SkillOps Insights settings.

### Changed
- Refactored Codex session analysis into provider-specific code backed by shared parsing, reporting, and usage models.
- Made run-insights actions and reports provider-aware across Codex, Claude, and Gemini.

### Fixed
- Enforced exact Gemini repository ownership so sessions from nested repositories are not included in parent-project reports.

## [0.1.0]
### Added
- SkillOps plugin foundation.
- Project-view actions for creating and validating Codex skills.
- Deterministic skill generation and validation logic.
- Mandatory `agents/openai.yaml` generation with interface metadata.
- Run insights action for recent Codex sessions with skill detection, token usage, and efficiency metrics.
- Platform-specific skill creation under `.agents/skills`, `.claude/skills`, and `.gemini/skills`.
- Codex run history grouped by skill and timestamp, including invocation commands when available.
- Local-only session analysis with no remote telemetry or data upload.

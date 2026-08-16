<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# skillops-intellij Changelog

## [Unreleased]

## [0.6.0] - 2026-08-16

### Added
- Shared progress reporting and cancellation for large Codex, Claude, and Gemini session scans.

### Changed
- Stream Codex, Claude, and Gemini session analysis with bounded per-session memory.
- Stream Codex and Claude resume-session discovery without retaining complete transcript event lists.
- Preserve provider-specific token, skill, project, efficiency, and resume metadata while processing incrementally.

### Fixed
- Reduce heap pressure when Run Insights scans large local session histories.
- Avoid retaining duplicate raw JSON text in parsed events.
- Show the active provider, session number, and filename during parsing.

## [0.4.0] - 2026-08-06

### Added
- First-class `Resume Session` action for searching and continuing project Codex sessions in a new IntelliJ terminal.
- Contextual session resume and session-ID copy actions in Codex Run Insights.
- Separate `runIde` and `runIdeOldest` development tasks for current and minimum supported IntelliJ versions.

### Changed
- Extended IntelliJ IDEA compatibility from build 242 (2024.2) through current releases without an upper build limit.
- Display the explicit Codex user request in the session picker instead of injected project instructions.
- Organized session models, discovery, services, terminal integration, UI, and tests into responsibility-based packages.

### Fixed
- Close Run Insights and focus the terminal after resuming a session.
- Improve session task readability and show the exact resume command and working directory.

## [0.3.0] - 2026-08-05

### Added
- Copy and convert repository skills between Codex, Claude Code, and Gemini CLI.
- Multi-skill selection with target-aware conversion and validation.
- Visible conversion progress and conflict prompts for replace, rename, or skip decisions.

### Changed
- Preserve compatible references, scripts, assets, nested files, empty directories, and executable permissions during conversion.
- Generate Codex `agents/openai.yaml` metadata when targeting Codex and remove it from Claude and Gemini targets.

### Safety
- Stage and validate converted skills before installation.
- Back up and restore existing target skills if replacement fails.

## [0.2.0] - 2026-07-28

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

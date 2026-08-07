# SkillOps 0.5.0

SkillOps 0.5.0 adds Claude Code session continuation from IntelliJ IDEA and
unifies the resume-session workflow across Codex and Claude.

## Resume Claude sessions

- Open `Tools → SkillOps → Claude → Resume Session` to find transcripts belonging to the current project.
- Search by the user task, skill, session ID, or working directory.
- Review the task, working directory, and exact resume command before continuing.
- Resume with `claude --resume <session-id>`, continue the latest session, or open Claude's session picker.
- Support named Claude sessions with `claude -n <name>`.
- Resume the selected session or copy its ID directly from Claude Run Insights.

## Shared resume-session behavior

- Resume dialogs show the selected task, working directory, last activity, and command.
- Sessions are discovered locally and filtered to the current project.
- Missing roots, unreadable files, and malformed records are reported as non-fatal warnings.

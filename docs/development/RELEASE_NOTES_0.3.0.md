# SkillOps 0.3.0

SkillOps 0.3.0 adds repository skill conversion between Codex, Claude Code, and Gemini CLI.

- Select one or more skills from `.agents/skills`, `.claude/skills`, or `.gemini/skills`.
- Convert them into the selected target platform's repository structure.
- Preserve references, scripts, assets, nested files, empty directories, and executable scripts.
- Generate Codex `agents/openai.yaml` metadata when Codex is the target.
- Remove Codex-only metadata when Claude or Gemini is the target.
- Validate every converted skill before it is installed.
- Resolve actual target conflicts with replace, rename, or skip choices.
- Stage writes and restore existing targets if replacement fails.
- Keep conversion deterministic, local, and free of API or network calls.

## Compatibility

- Supports IntelliJ IDEA 2024.2 and newer releases.

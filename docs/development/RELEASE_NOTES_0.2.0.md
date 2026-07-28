# SkillOps 0.2.0

SkillOps run insights now support Claude Code and Gemini CLI alongside Codex.

- Review local Claude sessions with input, output, cache-read, cache-creation, tool-call, and search metrics.
- Merge Claude subagent transcripts into their parent sessions and deduplicate assistant usage.
- Review local Gemini sessions with recorded token totals, thought and tool tokens, and structured tool activity.
- Attribute skills through Claude metadata and Gemini `activate_skill` calls, with repository skill references as fallback.
- Configure Codex, Claude, and Gemini home directories from the SkillOps Insights settings.
- Keep all session parsing and reporting local, with no API calls, telemetry, database, or external runtime.

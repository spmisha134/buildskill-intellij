# SkillOps 0.6.0

SkillOps 0.6.0 improves run insights for Codex, Claude, and Gemini, and
makes the Codex and Claude resume-session workflows safer and more responsive
for large local session histories.

- Parse Codex, Claude, and Gemini JSONL sessions incrementally with bounded per-session memory.
- Avoid retaining duplicate raw JSON text in parsed events.
- Show the provider, session position, and filename while parsing.
- Support cancellation during large session scans.
- Preserve token totals, skill attribution, project filtering, efficiency metrics, and resume metadata while streaming.
- Stream Codex and Claude resume-session discovery without retaining full transcript event lists.
- Keep the existing Codex and Claude resume workflow while reducing memory use during transcript discovery.

## Compatibility

- Supports IntelliJ IDEA 2024.2 and newer releases.

# SkillOps 0.4.0

SkillOps 0.4.0 adds direct Codex session continuation from IntelliJ IDEA and broadens IDE compatibility.

## Resume Codex sessions

- Open `Tools → SkillOps → Codex → Resume Session` to find sessions belonging to the current project.
- Search by the user request, skill, session ID, or working directory.
- Review the real user request, working directory, and exact `codex resume` command before continuing.
- Double-click a session, press Enter, or select `Resume Session` to open it in a new IntelliJ terminal.
- Resume the selected session directly from Codex Run Insights or copy its session ID.

## IntelliJ compatibility

- Supports IntelliJ IDEA 2024.2 and newer releases.
- Verified against 2024.2.5, 2024.3.7, 2025.1.7, and 2025.2.6.2.
- `./gradlew runIde` launches the latest development target.
- `./gradlew runIdeOldest` launches the minimum supported target for manual compatibility checks.

## Improvements

- Run Insights closes and transfers focus to the terminal after resuming a session.
- Session task details prefer the explicit Codex user request over injected repository instructions.
- Session feature code and tests are organized into model, discovery, service, terminal, and UI packages.

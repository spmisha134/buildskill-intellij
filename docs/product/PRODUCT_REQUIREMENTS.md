# Product Requirements: SkillOps for IntelliJ IDEA

## 1. Product summary

SkillOps is an IntelliJ IDEA plugin that creates and validates repository-scoped Codex/OpenAI skills.

The plugin is a deterministic project tool. It does not generate content through an LLM. It creates a correct skill folder structure, renders a small `SKILL.md`, creates required reference files, and validates the skill before reporting success.

## 2. Official structure target

The plugin must generate skills under:

```text
.agents/skills/<skill-name>/
```

Generated skills must follow this structure:

```text
.agents/
  skills/
    <skill-name>/
      SKILL.md
      references/
        instructions.md
        validation.md
        examples.md
      scripts/
      assets/
      agents/
        openai.yaml
```

## 2.1 Repository structure target

The plugin repository must follow the sibling reference structure from:

```text
../plugin-reference/sonarlint-intellij
```

Use the reference as a package and folder organization guide, not as a source of unrelated SonarLint behavior. This project must remain a single Gradle module:

```text
.
├── src/
├── docs/
└── gradle/
```

The `src` tree owns IntelliJ Platform integration, domain logic, templates, and tests. Keep the SonarLint-style separation by package: IDE integration in `actions`, `ui`, and `presentation`; reusable domain logic in `model`, `generator`, and `validator`.

## 3. Problem statement

Manual skill creation is error-prone.

Common mistakes:

- wrong folder location
- missing `SKILL.md`
- large `SKILL.md`
- missing front matter
- missing `name`
- missing `description`
- vague description
- missing reference files
- empty reference folders
- no validation checklist
- no examples
- no instruction to read repository-level agent instructions
- unsafe folder names
- duplicate skill folders

## 4. Product goal

The plugin must allow a developer to create a valid Codex/OpenAI skill from IntelliJ with minimal manual work.

The generated skill must be:

- stored under `.agents/skills/`
- small at the `SKILL.md` level
- supported by reference files
- validated immediately after generation
- safe to commit to the repository

## 5. Target user

The primary user is a developer using IntelliJ IDEA and Codex/OpenAI skills in a local repository.

The user wants repeatable skill creation without manually copying templates or remembering the correct folder structure.

## 6. Core workflow

```text
User right-clicks a project or folder
→ selects New → "SkillOps"
→ enters skill name and description
→ plugin creates .agents/skills/<skill-name>/
→ plugin creates SKILL.md
→ plugin creates references files
→ plugin creates mandatory agents/openai.yaml
→ plugin creates optional scripts and assets folders
→ plugin validates generated skill
→ plugin shows success, warnings, or errors
```

## 7. Functional requirements

### 7.0 Resume session

The Codex menu must expose a first-class `Resume Session` action. It must discover resumable local Codex sessions belonging to the current project, support search by task, skill, session ID, or working directory, and resume the selected session in a new IntelliJ terminal tab. The session list must prefer the explicit Codex user message over injected project instructions when presenting the task. The same action must be available contextually for the selected Codex Run Insights entry.

Claude Code must provide the equivalent first-class `Resume Session` action. It must discover local Claude transcripts belonging to the current project, support search by task, skill, session ID, or working directory, and open the selected session with `claude --resume <session-id>`. The dialog must also provide `claude --continue` and Claude's no-argument resume picker. Named Claude sessions are supported through the `claude -n <name>` command form. Claude Run Insights entries must expose the same resume and copy-session-ID actions.

### 7.1 Create skill action

The plugin must add an IntelliJ project-view New-menu action:

```text
SkillOps
```

The action must be available when the user right-clicks:

- project root
- any project directory

The action must not run when there is no open project.

### 7.2 Validate skill action

The plugin must add an IntelliJ project-view context menu action:

```text
Validate SkillOps
```

The action must be available when the selected folder is either:

- a skill folder under `.agents/skills/`
- a project folder containing `.agents/skills/`

### 7.3 Skill creation dialog

The plugin must show a dialog with these fields:

| Field | Required | Description |
|---|---:|---|
| Skill name | Yes | Human-readable skill name |
| Description | Yes | Trigger-oriented description |
| Create scripts folder | No | Whether to create `scripts/` |
| Create assets folder | No | Whether to create `assets/` |

`agents/openai.yaml` is mandatory and must not be exposed as an optional checkbox.

### 7.4 Skill name normalization

The plugin must convert the skill name into a safe folder name.

Examples:

| Input | Output |
|---|---|
| `PhotoAct Spec Writer` | `photoact-spec-writer` |
| `Kafka MCP Tool` | `kafka-mcp-tool` |
| `  My Skill  ` | `my-skill` |

Rules:

- trim leading and trailing spaces
- convert to lowercase
- replace whitespace with hyphens
- remove unsupported filesystem characters
- collapse repeated hyphens
- reject empty result

### 7.5 Generated `SKILL.md`

`SKILL.md` must stay small.

Generated template:

```markdown
---
name: <normalized-skill-name>
description: <description>
---

Use this skill when the task matches the description.

Before suggesting code structure, package layout, implementation approach, naming conventions, testing approach, or architectural changes, search the repository for project instruction files:

- `AGENTS.md` or `agents.md`
- `CLAUDE.md` or `claude.md`
- `GEMINI.md` or `gemini.md`

Treat those files as core project instructions. If they conflict with this skill, follow the project instruction files first.

Follow the workflow in `references/instructions.md`.

Before finishing, validate the output using `references/validation.md`.

Use `references/examples.md` when examples are needed.
```

### 7.6 Generated reference files

The plugin must always create the following files:

```text
references/instructions.md
references/validation.md
references/examples.md
```

These files must not be empty.

#### `references/instructions.md`

Must contain:

- purpose
- workflow
- implementation constraints
- project instruction discovery reminder
- output rules

#### `references/validation.md`

Must contain:

- validation checklist
- structural checks
- content quality checks
- final response checks

#### `references/examples.md`

Must contain:

- at least one good example placeholder
- at least one bad example placeholder
- guidance for adding project-specific examples

### 7.7 Support folders and files

The plugin must always create:

```text
agents/openai.yaml
```

The plugin may create these optional folders:

```text
scripts/
assets/
```

`agents/openai.yaml` must be valid YAML.

### 7.8 Duplicate handling

The plugin must not overwrite an existing skill directory.

If `.agents/skills/<skill-name>/` already exists, the plugin must show:

```text
A skill folder with this name already exists.
```

### 7.9 Validation

Validation must check:

| Rule | Severity |
|---|---|
| skill is under `.agents/skills/` | Error |
| `SKILL.md` exists | Error |
| front matter exists | Error |
| `name` exists | Error |
| `description` exists | Error |
| description is not blank | Error |
| `SKILL.md` is small | Warning |
| `references/instructions.md` exists | Error |
| `references/validation.md` exists | Error |
| `references/examples.md` exists | Error |
| reference files are not empty | Warning |
| project instruction discovery exists | Error |
| safe skill folder name | Error |
| `agents/openai.yaml` exists | Error |
| `agents/openai.yaml` is valid YAML | Warning |

### 7.10 User feedback

Success:

```text
Codex skill created successfully.
```

Warning:

```text
Skill created with warnings:
- SKILL.md is larger than expected.
- references/examples.md contains only placeholder examples.
```

Error:

```text
Could not create skill: A skill folder with this name already exists.
```

### 7.11 Run insights

The plugin must add an IntelliJ Tools-menu submenu:

```text
SkillOps
├── Codex
│   ├── Create Skill
│   └── Show Run Insights
├── Claude
│   ├── Create Skill
│   └── Show Run Insights
├── Gemini
│   ├── Create Skill
│   └── Show Run Insights
```

The action must scan recent Codex JSONL session files from the configured Codex home and show a local report with:

- latest session file
- detected SkillOps skill, when a generated skill is referenced in the session
- total, input, output, cached input, and reasoning output token counts when available
- cached input percentage
- output/input ratio
- reasoning output percentage
- repository/search activity count
- rate-limit usage when available
- warnings for missing token usage, large session logs, high search activity, and high rate-limit usage

The action must be local-first and must not call OpenAI APIs or remote services.

The Claude action must scan local Claude Code transcripts from the configured Claude home. It must:

- associate sessions with the open project using transcript `cwd`
- deduplicate assistant calls by `message.id`
- aggregate input, output, cache-read, and cache-creation tokens
- merge `subagents/agent-*.jsonl` activity into the parent session
- prefer explicit `attributionSkill` metadata for skill matching
- show tool-call and repository/search activity
- omit reasoning-token and rate-limit metrics when the transcript does not provide them

Claude insights must not depend on Claude Lens, Node.js, a database, a web server, or a remote service.

The Gemini action must scan local Gemini CLI sessions from the configured Gemini home. It must:

- resolve repository ownership through each cached project's `.project_root`
- scan `tmp/<project>/chats/session-*.jsonl`
- deduplicate Gemini responses by record `id`
- aggregate recorded total, input, output, cached, thought, and tool tokens
- count structured tool calls and repository/search tools
- prefer structured `activate_skill` calls for skill attribution
- fall back to `.gemini/skills/<name>` references when explicit activation is unavailable

Gemini insights must not call Gemini APIs or require a database, web server, or external runtime.

## 8. Non-functional requirements

### 8.1 Local-first

The plugin must work without internet access.

### 8.2 Deterministic behavior

The plugin must not require an LLM to generate or validate the skill.

### 8.3 Safe writes

The plugin must not overwrite files silently.

### 8.4 Testability

Core logic must be testable without launching IntelliJ.

Testable components:

- skill name normalization
- path resolution
- template rendering
- validation rules

### 8.5 Maintainability

IntelliJ-specific code must be separated from domain logic.

## 9. Out of scope

The product does not include:

- OpenAI API calls
- local LLM integration
- remote official documentation fetching
- automatic AI repair
- marketplace publishing automation as product behavior
- custom editor inspections
- project-wide skill indexing

Publishing operations are documented in the runbook, not implemented as product behavior.

## 10. Acceptance criteria

The first complete version is done when:

- `./gradlew runIde` starts the sandbox IDE
- `SkillOps` appears in the project-view New menu
- `Validate SkillOps` appears for skill folders
- a skill is generated under `.agents/skills/<skill-name>/`
- `SKILL.md` is generated and small
- required reference files are generated
- duplicate skill folders are rejected
- generated skill is validated
- validation messages are actionable
- core logic has unit tests

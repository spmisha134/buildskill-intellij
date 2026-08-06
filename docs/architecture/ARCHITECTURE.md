# Architecture: SkillOps for IntelliJ IDEA

## 1. Overview

SkillOps is an IntelliJ IDEA plugin.

The plugin has five responsibilities:

1. expose IntelliJ project-view actions
2. collect skill input from the user
3. generate an official repository-scoped Codex skill structure
4. validate generated or existing skills
5. show actionable success, warning, and error messages

The architecture separates IntelliJ Platform integration from domain logic and follows the sibling reference package structure from `../plugin-reference/sonarlint-intellij` at a smaller scale.

## 2. High-level flow

```text
IntelliJ Project View
  ↓
CreateSkillAction
  ↓
CreateSkillDialog
  ↓
SkillDefinition
  ↓
SkillGenerator
  ↓
.agents/skills/<skill-name>/
  ├── SKILL.md
  ├── references/instructions.md
  ├── references/validation.md
  ├── references/examples.md
  ├── scripts/
  ├── assets/
  └── agents/openai.yaml
  ↓
SkillValidator
  ↓
User feedback
```

Validation flow:

```text
IntelliJ Project View
  ↓
ValidateSkillAction
  ↓
SkillPathDetector
  ↓
SkillValidator
  ↓
ValidationResultPresenter
```

## 3. Repository structure

```text
.
├── src/                 # Single plugin module
├── docs/
└── gradle/
```

This is a single Gradle module. Follow the SonarLint reference in package organization only; do not split this project into feature modules unless a future spec explicitly requires it.

## 4. Package structure

```text
src/main/kotlin/com/spmisha134/skillops/
  actions/
    CreateSkillAction.kt
    ValidateSkillAction.kt

  ui/
    CreateSkillDialog.kt
    CreateSkillForm.kt

  presentation/
    ValidationResultPresenter.kt
    NotificationPresenter.kt

  model/
    SkillDefinition.kt
    SkillGenerationRequest.kt
    SkillGenerationResult.kt
    SkillValidationIssue.kt
    SkillValidationResult.kt
    SkillValidationSeverity.kt

  generator/
    SkillNameNormalizer.kt
    SkillPathResolver.kt
    SkillTemplateRenderer.kt
    ReferenceTemplateRenderer.kt
    SkillGenerator.kt

  copy/
    SkillCopyService.kt       # Copy orchestration
    discovery/                # Repository skill discovery
    conversion/               # Portable-to-target conversion and SKILL.md parsing
    io/                       # Safe source reading and staged target writing
    validation/               # Target-platform validation

  validator/
    SkillValidationContext.kt
    SkillValidator.kt
    SkillRule.kt
    rules/
      SkillLocationRule.kt
      SkillMdExistsRule.kt
      FrontMatterRule.kt
      RequiredFieldsRule.kt
      SmallSkillMdRule.kt
      RequiredReferencesRule.kt
      ProjectInstructionDiscoveryRule.kt
      SafeFolderNameRule.kt
      OpenAiYamlRule.kt

  insights/
    codex/                 # Codex session scanning, matching, usage, and efficiency
    claude/                # Claude session scanning, matching, and usage
    gemini/                # Gemini session scanning, matching, and usage
    parser/                # Provider-neutral JSONL event parsing
    run/                   # Shared report models and skill catalog
    usage/                 # Shared token-usage model
    presentation/          # Shared report formatting
    settings/              # Local insight settings
    ui/                    # Shared insights dialog

  sessions/
    model/                 # Pure resumable-session models
    discovery/             # Codex metadata and user-command extraction
    service/               # Project-session discovery orchestration
    terminal/              # IntelliJ terminal launch and resume command construction
    ui/                    # Searchable session selection dialog

src/main/resources/
  META-INF/plugin.xml
  templates/

src/test/kotlin/com/spmisha134/skillops/
  copy/
  generator/
  validator/
```

## 5. Component responsibilities

### 5.1 `actions`

IntelliJ entry points.

Responsibilities:

- register context menu actions
- read current project
- resolve selected directory
- open dialogs
- call generator and validator
- show results through presenter

Actions must stay thin.

### 5.2 `ui`

Dialog and form code.

Responsibilities:

- collect user input
- perform simple required-field checks
- create `SkillDefinition`

UI must not write files directly.

### 5.3 `model`

Plain Kotlin data classes.

These classes must not depend on IntelliJ APIs.

### 5.4 `generator`

Deterministic file generation.

Responsibilities:

- normalize skill name
- resolve `.agents/skills/<skill-name>/`
- render `SKILL.md`
- render reference files
- create optional folders/files
- reject duplicates

Generator must not show UI messages.

### 5.5 `validator`

Rule-based validation.

Responsibilities:

- validate folder location
- validate `SKILL.md`
- validate reference files
- validate project instruction discovery
- validate optional YAML
- return errors and warnings

Validator must not mutate files.

### 5.6 `presentation`

Transforms generation and validation results into IDE messages.

### 5.7 `insights`

Reads local agent session files and produces a shared run-insights report without
network calls. Provider-specific behavior belongs in `codex/`, `claude/`, or
`gemini/`. The `parser/`, `run/`, and `usage/` packages contain only neutral
infrastructure and models shared across providers.

### 5.8 `copy`

Deterministic cross-platform skill conversion.

Responsibilities:

- discover repository skills for each supported platform
- read source skills into provider-neutral models
- convert target-specific structure and metadata
- validate staged targets before installation
- write atomically and restore replaced targets on failure

`SkillCopyService` owns orchestration only. Discovery, conversion, filesystem I/O,
and validation belong in their respective subpackages. Copy domain models live in
`model/copy` as one-purpose files and must not depend on IntelliJ APIs.

## 6. Domain model

### `SkillDefinition`

```kotlin
data class SkillDefinition(
    val name: String,
    val description: String,
    val createScriptsDirectory: Boolean = true,
    val createAssetsDirectory: Boolean = true
)
```

### `SkillGenerationRequest`

```kotlin
data class SkillGenerationRequest(
    val projectBasePath: String,
    val selectedDirectoryPath: String,
    val skillDefinition: SkillDefinition
)
```

### `SkillGenerationResult`

```kotlin
data class SkillGenerationResult(
    val skillDirectoryPath: String,
    val skillMdPath: String,
    val createdFiles: List<String>,
    val createdDirectories: List<String>
)
```

### `SkillValidationIssue`

```kotlin
data class SkillValidationIssue(
    val severity: SkillValidationSeverity,
    val message: String,
    val filePath: String? = null
)

enum class SkillValidationSeverity {
    ERROR,
    WARNING
}
```

### `SkillValidationResult`

```kotlin
data class SkillValidationResult(
    val issues: List<SkillValidationIssue>
) {
    val hasErrors: Boolean
        get() = issues.any { it.severity == SkillValidationSeverity.ERROR }

    val hasWarnings: Boolean
        get() = issues.any { it.severity == SkillValidationSeverity.WARNING }
}
```

## 7. File generation rules

The plugin must generate repository-scoped skills under:

```text
.agents/skills/<normalized-skill-name>/
```

The selected directory is used only to find the project root. The final skill location is always repository-scoped.

The plugin must create:

```text
SKILL.md
references/instructions.md
references/validation.md
references/examples.md
agents/openai.yaml
```

The plugin may create:

```text
scripts/
assets/
```

## 7. `SKILL.md` design

`SKILL.md` must be short and act as an entrypoint.

It must include:

- front matter
- use trigger
- project instruction discovery
- references to detailed files

It must not include long detailed workflows.

## 8. Reference files design

### `references/instructions.md`

Detailed workflow and behavior rules.

### `references/validation.md`

Checklist and validation expectations.

### `references/examples.md`

Good and bad examples.

## 9. Validation design

Validation is rule-based.

```kotlin
interface SkillRule {
    fun validate(context: SkillValidationContext): List<SkillValidationIssue>
}
```

```kotlin
class SkillValidator(
    private val rules: List<SkillRule>
) {
    fun validate(context: SkillValidationContext): SkillValidationResult {
        return SkillValidationResult(
            issues = rules.flatMap { it.validate(context) }
        )
    }
}
```

Initial rules:

- `SkillLocationRule`
- `SkillMdExistsRule`
- `FrontMatterRule`
- `RequiredFieldsRule`
- `SmallSkillMdRule`
- `RequiredReferencesRule`
- `ProjectInstructionDiscoveryRule`
- `SafeFolderNameRule`
- `OpenAiYamlRule`

## 10. IntelliJ integration

The plugin integrates through:

- `plugin.xml`
- IntelliJ Action System
- Project View context menu
- Virtual File System APIs
- write actions for file creation
- notification/message APIs

Action names:

```text
SkillOps
Validate SkillOps
```

## 11. Error handling

The plugin must handle:

- no open project
- no selected folder
- invalid skill name
- duplicate skill folder
- write failure
- malformed `SKILL.md`
- missing reference files
- missing or invalid `agents/openai.yaml`

The plugin must not crash the IDE.

## 12. Testing strategy

Unit tests:

- `SkillNameNormalizerTest`
- `SkillPathResolverTest`
- `SkillTemplateRendererTest`
- `ReferenceTemplateRendererTest`
- `SkillValidatorTest`
- rule-specific tests

Manual sandbox verification:

- run `./gradlew runIde`
- create a skill
- validate the skill
- validate a broken skill
- try duplicate creation

## 13. Architectural constraints

- Domain logic must not depend on IntelliJ APIs.
- Actions must stay thin.
- UI must not write files directly.
- Generator must not show notifications.
- Validator must not mutate files.
- Reference files must be generated with useful content.

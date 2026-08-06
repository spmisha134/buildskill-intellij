# SkillOps - Agent Guide

## Prerequisites

- **Java 21** — install via your preferred toolchain manager.
- **Gradle** — use the `./gradlew` wrapper; no separate Gradle installation is required.

For local development, JDK 21 and the wrapper are sufficient.

## Architecture Overview

SkillOps is an IntelliJ IDEA plugin that creates and validates repository-scoped Codex/OpenAI skills under:

```text
.agents/skills/<skill-name>/
```

The plugin is local-first and deterministic. It does not call OpenAI APIs, remote documentation, or marketplace services at runtime.

**IntelliJ integration**

- `actions/CreateSkillAction.kt` registers the `New → SkillOps` entry point.
- `actions/ValidateSkillAction.kt` validates an existing skill folder.
- `ui/` owns IntelliJ/Swing dialog code.
- `presentation/` owns IDE messages and validation result presentation.

**Domain logic**

- `model/` contains plain Kotlin request/result/validation data classes.
- `generator/` normalizes names, renders templates, resolves paths, and writes skill files.
- `validator/` runs deterministic validation rules against generated or existing skills.

**Where to look by change type**

| Change area | Start here |
|---|---|
| Create action behavior | `src/main/kotlin/com/spmisha134/skillops/actions/CreateSkillAction.kt` |
| Validate action behavior | `src/main/kotlin/com/spmisha134/skillops/actions/ValidateSkillAction.kt` |
| Dialog fields and validation | `src/main/kotlin/com/spmisha134/skillops/ui/` |
| Skill file generation | `src/main/kotlin/com/spmisha134/skillops/generator/SkillGenerator.kt` |
| Generated templates | `src/main/resources/templates/generated-skill/` |
| Validation rules | `src/main/kotlin/com/spmisha134/skillops/validator/rules/` |
| Product behavior | `docs/product/PRODUCT_REQUIREMENTS.md` |

## Module Structure

This is a single-module IntelliJ plugin.

| Path | Purpose |
|---|---|
| `src/main/kotlin/` | Main plugin and domain code |
| `src/main/resources/META-INF/plugin.xml` | IntelliJ plugin descriptor and action registration |
| `src/main/resources/templates/` | Generated skill templates |
| `src/test/kotlin/` | Unit tests for generation and validation |
| `docs/product/` | Product requirements |
| `docs/architecture/` | Architecture notes |
| `docs/development/` | Build, release, and publishing runbook |

## Package Structure Rules

Package structure is a required part of every implementation, not cleanup to perform afterward.

Before adding the first source file for a feature:

1. Read `docs/architecture/ARCHITECTURE.md`.
2. Identify the feature root package and the responsibilities the feature introduces.
3. Define the intended package tree before implementing classes.
4. Reuse an existing package only when the new class has the same responsibility as that package.

Do not place an entire multi-responsibility feature into one flat package. Split feature code by responsibility using the established structure:

```text
<feature>/
  model/          # Plain Kotlin data classes and enums
  discovery/      # Filesystem discovery, scanning, parsing, and metadata extraction
  service/        # Use-case orchestration
  ui/             # IntelliJ/Swing dialogs, forms, tables, and renderers
  presentation/   # User-facing formatting and result presentation
  terminal/       # IntelliJ terminal integration and command construction
  settings/       # Persistent settings and configuration UI
```

Create only the subpackages that the feature actually needs. Other responsibility-specific names such as `generator/`, `validator/`, `conversion/`, `io/`, or `rules/` are appropriate when they describe the domain more precisely.

Mandatory boundaries:

- Keep one primary model per file. Do not collect unrelated request, result, metadata, and target models in one Kotlin file.
- Keep models free of IntelliJ Platform, Swing, filesystem I/O, and presentation dependencies.
- Services orchestrate use cases; they must not construct dialogs, show notifications, or directly own Swing components.
- Discovery and parser classes read and interpret data; they must not launch terminals or render UI.
- UI classes collect input and render state; they must delegate scanning, business decisions, filesystem access, and command execution.
- IntelliJ-specific behavior belongs in `actions/`, `ui/`, `presentation/`, `terminal/`, or another clearly named integration package.
- Shared concepts belong in a neutral shared or feature-domain package, not inside one consumer package. For example, a session resume target must not be owned by Run Insights merely because Run Insights displays it.
- Keep actions thin: resolve project context, invoke a service, and hand results to presentation or UI.
- Mirror production package structure under `src/test/kotlin/`.

Before considering an implementation complete:

- inspect the resulting feature tree and imports
- confirm every class is in the package matching its responsibility
- split any flat package that mixes models, orchestration, parsing, UI, and platform integration
- update `docs/architecture/ARCHITECTURE.md` when introducing or changing package boundaries
- run compile and tests after package moves to catch stale imports and instrumentation output

Current examples to follow:

- `copy/` separates discovery, conversion, I/O, and validation.
- `insights/` separates provider logic, parsing, run models, usage, presentation, settings, and UI.
- `sessions/` separates models, discovery, services, terminal integration, and UI.

## Build and Validation

### Available commands

**Compile only** — fastest, catches syntax/type errors:

```bash
./gradlew compileKotlin compileTestKotlin
```

**Unit tests and plugin checks**:

```bash
./gradlew check
```

**Build plugin only** — produces the ZIP without running tests:

```bash
./gradlew buildPlugin
```

**Full local release check**:

```bash
./gradlew check buildPlugin
```

**Plugin compatibility verification**:

```bash
./gradlew verifyPlugin
```

### Running the plugin locally

To launch an IntelliJ instance with the plugin installed for manual validation:

```bash
./gradlew runIde
```

This launches the latest development target. To launch the oldest supported IDE:

```bash
./gradlew runIdeOldest
```

The sandbox is stored under `build/idea-sandbox` by the IntelliJ Platform Gradle Plugin defaults. Running `./gradlew clean` may remove local build and sandbox state.

## Development Rules

- Read `README.md` before making changes.
- Read `docs/product/PRODUCT_REQUIREMENTS.md` before implementing product behavior.
- Read `docs/architecture/ARCHITECTURE.md` before changing package structure or core design.
- Plan and create responsibility-based packages before implementing a new multi-class feature.
- Treat misplaced classes and mixed-responsibility flat packages as implementation defects.
- Keep changes small and focused.
- Keep IntelliJ-specific code out of pure generator and validator logic.
- Do not introduce AI calls, remote documentation fetching, marketplace publishing automation, or unrelated features unless the relevant spec requires it.
- After changes, explain:
  - what changed
  - how to test it
  - which files were affected

## Dependency Management

Dependencies are declared directly in `build.gradle.kts`.

Before adding a dependency:

- prefer existing IntelliJ Platform APIs or Kotlin/JDK APIs
- keep domain logic testable without launching IntelliJ
- avoid runtime network dependencies

After changing build dependencies, run:

```bash
./gradlew check buildPlugin
```

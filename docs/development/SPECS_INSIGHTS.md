# Specs Insights

This document tracks implementation progress, slice results, decisions, and verification outcomes for SkillOps.

Keep product requirements in `docs/product/PRODUCT_REQUIREMENTS.md`. Keep broad implementation planning in `docs/development/IMPLEMENTATION_PLAN.md`. Use this file for the current execution state, implementation slice results, follow-up work, and lessons learned while building or publishing the plugin.

## Current Status

SkillOps has a complete first-release implementation for repository-scoped Codex/OpenAI skill creation and validation.

The plugin can generate `.agents/skills/<skill-name>/`, render `SKILL.md`, render required reference files, create optional support folders, write mandatory `agents/openai.yaml`, validate generated or existing skills, and package successfully. The latest release-blocking verifier issue was fixed by renaming the plugin display name to `SkillOps`.

## Status Legend

| Status | Meaning |
|---|---|
| Done | Implemented and verified with automated or explicit local validation. |
| Ready for manual check | Implemented, but depends on IDE sandbox/manual behavior to fully confirm. |
| Follow-up | Not blocking the current release, but should be addressed for polish, consistency, or stronger coverage. |

## Slice Results

| Slice | Status | Implementation Result | Verification |
|---|---|---|---|
| Plugin identity and packaging | Done | Plugin id is `com.spmisha134.skillops`; display name is `SkillOps`; ZIP builds under `build/distributions/`. | `./gradlew verifyPlugin` passed against all configured IDE builds. |
| IntelliJ action registration | Ready for manual check | `Tools → SkillOps` contains Codex, Claude, and Gemini creation submenus plus `Show Run Insights`; `Validate SkillOps` remains available from the project-view context menu. | Descriptor reviewed in `src/main/resources/META-INF/plugin.xml`; manual `runIde` check still recommended before publication. |
| Create skill workflow | Done | `CreateSkillAction` opens the dialog, writes through `SkillGenerator`, refreshes VFS, validates the generated skill, and presents the validation result. | Covered indirectly through generator and validator tests; IDE action wiring requires manual check. |
| Creation dialog | Ready for manual check | Dialog collects skill name, description, `scripts/` option, and `assets/` option; `agents/openai.yaml` is mandatory and not user-toggleable. | Code reviewed in `CreateSkillDialog` and `CreateSkillForm`; manual UI check recommended. |
| Name normalization | Done | Names are trimmed, lowercased, hyphen-normalized, stripped of unsupported characters, and empty results are rejected. | `SkillNameNormalizerTest` covers common names, repeated hyphens, and empty names. |
| Path resolution | Done | Skill directories resolve under `<project-root>/.agents/skills/<normalized-name>` even when a nested directory is selected. | `SkillPathResolverTest` and `SkillGeneratorTest` cover project-root targeting. |
| File generation | Done | Generator writes `SKILL.md`, required references, optional `scripts/` and `assets/`, and mandatory `agents/openai.yaml`. | `SkillGeneratorTest`, `SkillTemplateRendererTest`, `ReferenceTemplateRendererTest`, and `OpenAiYamlRendererTest`. |
| Duplicate and safe write handling | Done | Existing skill directories are rejected; individual file writes refuse overwrites. | `SkillGeneratorTest` covers duplicate skill directory rejection. |
| Generated `SKILL.md` content | Done | Entrypoint remains small and delegates detailed workflow, validation, and examples to references while requiring project instruction discovery. | `SkillTemplateRendererTest` validates size and required instruction references. |
| Reference templates | Done | `instructions.md`, `validation.md`, and `examples.md` are generated with non-empty starter content and expected sections. | `ReferenceTemplateRendererTest` and `SkillGeneratorTest`. |
| OpenAI agent metadata | Done | `agents/openai.yaml` is rendered from a template with display name, short description, and default prompt. | `OpenAiYamlRendererTest`; `SkillValidatorTest` covers missing or incomplete metadata. |
| Validation orchestration | Done | `SkillValidator` runs structure, content, reference, safe-name, and OpenAI YAML rules. | `SkillValidatorTest` plus rule-specific tests for front matter and required fields. |
| Validate skill workflow | Done | `ValidateSkillAction` validates either a selected skill folder or all child skill folders under `.agents/skills/`. Directory detection lives in pure `SkillPathDetector`. | `SkillPathDetectorTest` covers selected skill, skills root, project root, and unrelated folder cases; project-view menu behavior still needs manual IDE validation. |
| Run insights workflow | Done | `Show SkillOps Run Insights` scans recent Codex JSONL sessions, matches generated SkillOps skill references, extracts token usage, calculates efficiency metrics, and shows a scrollable IDE report. Codex-specific implementation is isolated under `insights/codex`; neutral JSONL parsing and report models remain shared. | `CodexRunInsightsServiceTest`, `CodexSkillUsageMatcherTest`, `CodexEfficiencySummaryCalculatorTest`, `RunInsightsReportFormatterTest`, `./gradlew check`, `./gradlew buildPlugin`, and `./gradlew verifyPlugin`. |
| Claude run insights | Done | `Tools → SkillOps → Claude → Show Run Insights` scans local Claude transcripts, filters by project `cwd`, merges subagents, deduplicates assistant calls, matches `attributionSkill`, and reports input/output/cache/tool/search metrics without external dependencies. | `ClaudeSessionFileScannerTest`, `ClaudeUsageExtractorTest`, and `ClaudeRunInsightsServiceTest`. |
| Gemini run insights | Done | `Tools → SkillOps → Gemini → Show Run Insights` maps cached sessions through `.project_root`, aggregates Gemini's recorded token categories, matches `activate_skill`, and reports tool/search metrics without external dependencies. | `GeminiSessionFileScannerTest`, `GeminiUsageExtractorTest`, and `GeminiRunInsightsServiceTest`. |
| User feedback | Done | Creation and validation paths show success, warning, or error dialogs with actionable messages. | Code reviewed in presenters and exception handler; manual UI check recommended for exact IDE wording. |
| Local-first deterministic behavior | Done | Generation and validation use local templates and deterministic Kotlin logic; no runtime network or AI calls. | Dependency/code review; no OpenAI API or remote documentation calls are present. |
| Documentation branding consistency | Done | User-facing docs, project instructions, changelog, and foundation spec use `SkillOps` as the display name. | `rg -n "SkillOps Intelli[j]" .` returns no matches. |

## Follow-Ups

| Item | Priority | Reason |
|---|---:|---|
| Run `./gradlew runIde` for run-insights dialog validation. | Medium | The new Tools-menu report should be checked against a real Codex session after a skill run. |
| Add status bar auto-refresh for run insights. | Low | Manual Tools-menu report is implemented first; auto-refresh can layer on top after UX validation. |

## Decisions

| Date | Decision | Rationale |
|---|---|---|
| 2026-07-04 | Use `SkillOps` as the plugin display name. | JetBrains Plugin Verifier rejects plugin names that include `IntelliJ`. |
| 2026-07-04 | Keep specs insights in `docs/development/SPECS_INSIGHTS.md`. | Slice progress and implementation results are development state, separate from product requirements and architecture docs. |
| 2026-07-04 | Keep `.agents/skills/` reserved for generated skills and fixtures. | Progress tracking should not be mixed with product output directories. |
| 2026-07-27 | Isolate Codex insights in `insights/codex`. | Each provider owns its session format logic; only neutral parsing, report models, settings, and presentation are shared. |

## Open Questions

| Question | Current Leaning |
|---|---|
| Should `Validate SkillOps` validate only one selected skill or bulk-validate all skills under `.agents/skills/`? | Current implementation supports both; keep unless manual UX testing shows the bulk behavior is surprising. |

## Verification History

| Date | Command | Result | Notes |
|---|---|---|---|
| 2026-07-04 | `./gradlew verifyPlugin` | Passed | Plugin Verifier marked `com.spmisha134.skillops:0.1.0` compatible against all configured IDE builds. |
| 2026-07-04 | `./gradlew -Dtest.single=SkillPathDetectorTest test` | Passed | Focused coverage for skill-directory detection. |
| 2026-07-04 | `./gradlew check` | Passed | Full project check after specs insight slice implementation. |
| 2026-07-11 | `./gradlew check` | Passed | Full check after run-insights implementation. |
| 2026-07-11 | `./gradlew buildPlugin` | Passed | Plugin ZIP builds with `Show SkillOps Run Insights`. |
| 2026-07-11 | `./gradlew verifyPlugin` | Passed | Plugin Verifier marked `com.spmisha134.skillops:0.1.0` compatible against all configured IDE builds after run-insights implementation. |
| 2026-07-27 | `./gradlew check buildPlugin` | Passed | Full test and packaging check after adding dependency-free local Claude run insights. |
| 2026-07-27 | `./gradlew check buildPlugin` | Passed | Full regression and packaging check after adding local Gemini run insights. |
| 2026-07-27 | `./gradlew verifyPlugin` | Passed | Gemini-enabled package is compatible with all four configured IntelliJ builds. |
| 2026-07-27 | `./gradlew --no-configuration-cache check buildPlugin` | Passed | Full regression and packaging check after isolating Codex insights in its provider package. |
| 2026-07-27 | `./gradlew --no-configuration-cache verifyPlugin` | Passed | Refactored package remains compatible with all four configured IntelliJ builds. |

## Manual Validation Checklist

- [ ] Run `./gradlew runIde`.
- [ ] Confirm `SkillOps` appears under project-view `New`.
- [ ] Confirm `Validate SkillOps` appears for skill folders.
- [ ] Create a skill from the project root.
- [ ] Create a skill while a nested folder is selected and confirm output still goes under project root `.agents/skills/`.
- [ ] Confirm duplicate skill creation reports `A skill folder with this name already exists.`
- [ ] Validate a generated skill and confirm success feedback.
- [ ] Delete `references/examples.md`, validate again, and confirm an actionable validation error.
- [ ] Confirm IDE message titles use `SkillOps`.
- [ ] Run a Codex task that references a generated SkillOps skill.
- [ ] Confirm `Tools → SkillOps → Show Run Insights` opens a report.
- [ ] Confirm `Tools → SkillOps → Claude → Show Run Insights` opens a report from a real local Claude session.
- [ ] Confirm `Tools → SkillOps → Gemini → Show Run Insights` opens a report from a real local Gemini session.
- [ ] Confirm the report shows token usage and detected skill for the latest session.

# Development and Publishing Runbook

This runbook describes how to build, verify, package, and manually publish SkillOps for IntelliJ IDEA.

## 1. Local development

### Run the plugin in a sandbox IDE

```bash
./gradlew runIde
```

This launches the latest development target. Use the oldest supported IDE for compatibility-focused manual testing:

```bash
./gradlew runIdeOldest
```

### Run checks

```bash
./gradlew check
```

### Build the plugin

```bash
./gradlew buildPlugin
```

The plugin ZIP is generated under:

```text
build/distributions/
```

## 2. Before first release

Complete the repository cleanup checklist:

- [ ] Set the Gradle project group in `gradle.properties`
- [ ] Set the plugin id in `src/main/resources/META-INF/plugin.xml`
- [ ] Set the plugin name in `src/main/resources/META-INF/plugin.xml`
- [ ] Set the plugin description in `src/main/resources/META-INF/plugin.xml`
- [ ] Replace template README content with project-specific documentation
- [ ] Confirm source package name matches the project
- [ ] Confirm `./gradlew runIde` works
- [ ] Confirm `./gradlew check` passes
- [ ] Confirm `./gradlew buildPlugin` produces a plugin ZIP

## 3. Plugin identity

Recommended values:

```text
Plugin name: SkillOps
Repository: skillops-intellij
Default package: com.spmisha134.skillops
```

Recommended plugin description:

```text
Create repository-scoped skills for Codex, Claude Code, and Gemini CLI directly inside IntelliJ IDEA.
```

## 4. Manual installation from local build

Build the plugin:

```bash
./gradlew buildPlugin
```

Install the ZIP manually:

```text
IntelliJ IDEA
→ Settings/Preferences
→ Plugins
→ ⚙️
→ Install Plugin from Disk...
→ Select ZIP from build/distributions/
```

### Clean-profile verification

Before uploading a release candidate:

1. Start IntelliJ IDEA with a new configuration directory or use a separate clean IDE profile.
2. Install only the ZIP from `build/distributions/`.
3. Restart the IDE when requested.
4. Open a disposable repository and verify Codex, Claude, and Gemini skill creation.
5. Run a Codex skill and confirm its run history, invocation command, token usage, and efficiency metrics.
6. Open `Tools → SkillOps → Codex → Resume Session`, verify the explicit user request is shown, and resume it in a new terminal.
7. Resume a selected session from Codex Run Insights and confirm the dialog closes and the terminal receives focus.
8. Confirm the IDE log contains no `Plugin 'SkillOps' failed to load` or `Cannot load com.spmisha134.skillops` errors.

Do not use a real project or real session details in Marketplace screenshots. Use a disposable repository and generic skill names.

## 5. Manual first publication

The first JetBrains Marketplace publication must be uploaded manually.

Steps:

1. Build the plugin ZIP.

```bash
./gradlew buildPlugin
```

2. Log in to JetBrains Marketplace.

3. Create or select the vendor profile.

4. Upload the plugin ZIP from:

```text
build/distributions/
```

5. Complete Marketplace listing:

```text
Plugin name
Description
Version
Change notes
Vendor
License
Tags
Compatibility
Screenshots if available
```

6. Submit for approval.

## 6. Marketplace ID

After approval, update README badges by replacing:

```text
MARKETPLACE_ID
```

with the real plugin ID from JetBrains Marketplace.

## 7. Plugin signing

Plugin signing should be configured before automated publishing.

Required secret values must not be committed to Git:

```text
CERTIFICATE_CHAIN
PRIVATE_KEY
PRIVATE_KEY_PASSWORD
```

## 8. Deployment token

No JetBrains Marketplace deployment token is required in GitHub because automated publishing is
disabled. Manual publication uses the Marketplace web interface and the locally built ZIP.

## 9. Automated publishing

Marketplace publishing from GitHub Actions is intentionally disabled. The release workflow is
manual-only and its publishing job is disabled. Do not add `PUBLISH_TOKEN` or re-enable the job
unless automated publication is explicitly approved.

Marketplace publication must be performed manually using the ZIP from `build/distributions/`.

The following checklist is retained only as a future review checklist:

Before enabling automated publishing:

- [ ] First manual Marketplace publication completed
- [ ] Marketplace ID known
- [ ] README badges updated
- [ ] Plugin signing secrets configured
- [ ] Deployment token configured
- [ ] Release workflow verified
- [ ] Plugin version updated
- [ ] Change notes updated

## 10. Release checklist

Before creating a release:

- [ ] Product behavior matches `docs/product/PRODUCT_REQUIREMENTS.md`
- [ ] Architecture matches `docs/architecture/ARCHITECTURE.md`
- [ ] Specs are implemented or explicitly deferred
- [ ] `./gradlew check` passes
- [ ] `./gradlew buildPlugin` succeeds
- [ ] `./gradlew verifyPlugin` passes for every configured IDE
- [ ] Plugin ZIP installs manually
- [ ] `Tools → SkillOps → Codex → Create Skill` works
- [ ] `Tools → SkillOps → Claude → Create Skill` works
- [ ] `Tools → SkillOps → Gemini → Create Skill` works
- [ ] `Tools → SkillOps → Copy Skills` discovers repository skills
- [ ] Copy Skills converts a skill between two platforms and preserves supporting files
- [ ] Copy Skills prompts with replace, rename, and skip only when the target already exists
- [ ] `Tools → SkillOps → Codex → Show Run Insights` works
- [ ] `Tools → SkillOps → Codex → Resume Session` shows the real user request and resumes the selected session
- [ ] Run Insights resumes the selected Codex session, closes, and focuses the terminal
- [ ] `Validate SkillOps` action works
- [ ] Generated skill is created under `.agents/skills/<skill-name>/`
- [ ] Claude skill is created under `.claude/skills/<skill-name>/`
- [ ] Gemini skill is created under `.gemini/skills/<skill-name>/`
- [ ] Generated `SKILL.md` is small
- [ ] Generated reference files are created
- [ ] Generated `agents/openai.yaml` is created
- [ ] Generated skill includes instruction discovery for:
  - `AGENTS.md`
  - `agents.md`
  - `CLAUDE.md`
  - `claude.md`
  - `GEMINI.md`
  - `gemini.md`

## 11. Versioning

Use semantic versioning.

Recommended first public version:

```text
0.1.0
```

## 12. Do not publish until

- plugin name is final
- plugin id is final
- package name is final
- README is project-specific
- Marketplace description is project-specific
- generated plugin works in a sandbox IDE

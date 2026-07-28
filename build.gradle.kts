import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

val pluginRepositoryUrl: String by project

dependencies {
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2025.2.6.2")
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.spmisha134.skillops"
        name = "SkillOps"
        description = """
            <p><strong>Create reliable, repository-scoped skills for Codex, Claude Code, and Gemini CLI without leaving IntelliJ IDEA.</strong></p>
            <p>SkillOps removes the repetitive setup from skill authoring and helps you understand how Codex, Claude, and Gemini sessions use context and tokens.</p>
            <ul>
                <li>Create platform-specific skills with structured <code>SKILL.md</code> content and supporting references.</li>
                <li>Generate optional scripts and assets, plus Codex interface metadata.</li>
                <li>Validate Codex skills before committing them.</li>
                <li>Review local Codex, Claude, and Gemini run history, token usage, cache behavior, efficiency, and sessions without skills.</li>
            </ul>
            <p><strong>Private by design:</strong> generation, validation, and session analysis run locally. SkillOps does not upload project files, prompts, session logs, credentials, or analytics.</p>
        """.trimIndent()
        changeNotes = """
            <ul>
                <li>Adds local run insights for Claude Code and Gemini CLI alongside Codex.</li>
                <li>Shows provider-specific token, cache, tool-call, search, and skill-attribution metrics.</li>
                <li>Merges Claude subagent activity into parent sessions and deduplicates assistant usage.</li>
                <li>Uses Gemini project mappings for exact repository ownership and structured skill activation.</li>
                <li>Refactors session analysis into shared provider-neutral infrastructure while preserving Codex insights.</li>
            </ul>
        """.trimIndent()

        vendor {
            name = "Sollymanul Islam"
            email = "spmisha134@gmail.com"
            url = pluginRepositoryUrl
        }

        ideaVersion {
            sinceBuild = "252"
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

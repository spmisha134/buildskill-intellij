import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

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
        intellijIdea("2024.2.5")
        bundledPlugin("org.jetbrains.plugins.terminal")
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
                <li>Copy and convert repository skills between Codex, Claude Code, and Gemini CLI.</li>
                <li>Generate optional scripts and assets, plus Codex interface metadata.</li>
                <li>Validate Codex skills before committing them.</li>
                <li>Review local Codex, Claude, and Gemini run history, token usage, cache behavior, efficiency, and sessions without skills.</li>
                <li>Search and resume previous project Codex sessions in a new IntelliJ terminal.</li>
            </ul>
            <p><strong>Private by design:</strong> generation, validation, and session analysis run locally. SkillOps does not upload project files, prompts, session logs, credentials, or analytics.</p>
        """.trimIndent()
        changeNotes = """
            <ul>
                <li>Preserves and displays Codex prompt history for Resume Session and Run Insights.</li>
                <li>Derives concise session titles from the latest meaningful prompt and filters injected project instructions.</li>
                <li>Prioritizes token metrics and efficiency details before prompts in Run Insights.</li>
                <li>Explains transcript-size warnings in terms of scan time and context overhead.</li>
                <li>Supports IntelliJ IDEA from 2024.2 onward and verifies compatibility through 2025.2.</li>
            </ul>
        """.trimIndent()

        vendor {
            name = "Sollymanul Islam"
            email = "spmisha134@gmail.com"
            url = pluginRepositoryUrl
        }

        ideaVersion {
            sinceBuild = "242"
        }
    }

    pluginVerification {
        ides {
            create("IC", "2024.2.5")
            create("IC", "2024.3.7")
            create("IC", "2025.1.7")
            create("IC", "2025.2.6.2")
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

val runIdeLatest by intellijPlatformTesting.runIde.registering {
    type = IntelliJPlatformType.IntellijIdeaCommunity
    version = "2025.2.6.2"
    plugins {
        bundledPlugin("org.jetbrains.plugins.terminal")
    }
}

val runIdeOldest by intellijPlatformTesting.runIde.registering {
    type = IntelliJPlatformType.IntellijIdeaCommunity
    version = "2024.2.5"
    plugins {
        bundledPlugin("org.jetbrains.plugins.terminal")
    }
}

tasks.named("runIde") {
    description = "Runs the plugin in the latest supported IntelliJ IDEA (2025.2.6.2)."
    setDependsOn(listOf(runIdeLatest))
    enabled = false
}

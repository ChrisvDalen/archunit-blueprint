package com.github.chrisvdalen.archunitgen.generator

import com.github.chrisvdalen.archunitgen.model.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentGuardrailsGeneratorTest {

    private val generator = AgentGuardrailsGenerator()

    private val testFile = GeneratedTestFile(
        packageName = "com.example.architecture",
        className = "ArchitectureRulesTest",
        code = "",
        relativePath = "src/test/java/com/example/architecture/ArchitectureRulesTest.java",
    )

    @Test
    fun `writes instructions for every supported agent plus the Claude Code hook`() {
        val files = generator.generate(listOf(rule()), testFile)

        assertThat(files.map { it.relativePath }).containsExactly(
            "AGENTS.md",
            "CLAUDE.md",
            ".github/copilot-instructions.md",
            ".claude/hooks/archunit-check.sh",
            ".claude/settings.json",
        )
    }

    @Test
    fun `instructions contain only accepted rules with their ArchUnit code`() {
        val accepted = rule(title = "Controllers must not depend on Repositories")
        val pending = rule(title = "Pending rule", status = RuleStatus.PENDING)
        val ignored = rule(title = "Ignored rule", status = RuleStatus.IGNORED)

        val section = instructions(generator.generate(listOf(accepted, pending, ignored), testFile))

        assertThat(section).contains("### Controllers must not depend on Repositories")
        assertThat(section).contains("noClasses().should().bePublic()")
        assertThat(section).doesNotContain("Pending rule").doesNotContain("Ignored rule")
    }

    @Test
    fun `instructions point the agent at the enforcing test`() {
        val section = instructions(generator.generate(listOf(rule()), testFile))

        assertThat(section).startsWith(ManagedSection.BEGIN).endsWith(ManagedSection.END)
        assertThat(section).contains("`com.example.architecture.ArchitectureRulesTest`")
        assertThat(section).contains("./gradlew test --tests com.example.architecture.ArchitectureRulesTest")
        assertThat(section).contains("./mvnw test -Dtest=ArchitectureRulesTest")
    }

    @Test
    fun `hook script runs the generated test and blocks with exit code 2`() {
        val script = generator.generate(listOf(rule()), testFile)
            .single { it.relativePath == AgentGuardrailsGenerator.HOOK_SCRIPT_PATH }

        assertThat(script.mode).isEqualTo(WriteMode.OVERWRITE)
        assertThat(script.content).startsWith("#!/usr/bin/env sh")
        assertThat(script.content).contains("--tests 'com.example.architecture.ArchitectureRulesTest'")
        assertThat(script.content).contains("-Dtest='ArchitectureRulesTest'")
        assertThat(script.content).contains("stop_hook_active")
        assertThat(script.content).contains("exit 2")
    }

    @Test
    fun `Claude settings register the hook on Stop and are never overwritten`() {
        val settings = generator.generate(listOf(rule()), testFile)
            .single { it.relativePath == AgentGuardrailsGenerator.CLAUDE_SETTINGS_PATH }

        assertThat(settings.mode).isEqualTo(WriteMode.CREATE_ONLY)
        assertThat(settings.content).contains("\"Stop\"")
        assertThat(settings.content).contains("\$CLAUDE_PROJECT_DIR/.claude/hooks/archunit-check.sh")
        assertThat(WriteMode.CREATE_ONLY.contentToWrite("{}", settings.content)).isNull()
        assertThat(WriteMode.CREATE_ONLY.contentToWrite(null, settings.content)).isEqualTo(settings.content)
    }

    @Test
    fun `managed section is created in a new file`() {
        assertThat(ManagedSection.merge(null, section("v1"))).isEqualTo(section("v1") + "\n")
    }

    @Test
    fun `managed section is appended after existing content`() {
        val merged = ManagedSection.merge("# Team notes\nUse Java 21.\n", section("v1"))
        assertThat(merged).isEqualTo("# Team notes\nUse Java 21.\n\n${section("v1")}\n")
    }

    @Test
    fun `managed section is replaced in place, keeping the user's text around it`() {
        val existing = "# Team notes\n\n${section("v1")}\n\n## More notes\n"
        val merged = ManagedSection.merge(existing, section("v2"))

        assertThat(merged).isEqualTo("# Team notes\n\n${section("v2")}\n\n## More notes\n")
    }

    @Test
    fun `regenerating twice gives the same file`() {
        val once = ManagedSection.merge("# Notes\n", section("v1"))
        assertThat(ManagedSection.merge(once, section("v1"))).isEqualTo(once)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun instructions(files: List<GuardrailFile>): String =
        files.single { it.relativePath == "AGENTS.md" }.content

    private fun section(body: String) = "${ManagedSection.BEGIN}\n$body\n${ManagedSection.END}"

    private fun rule(title: String = "Test Rule", status: RuleStatus = RuleStatus.ACCEPTED) = RuleSuggestion(
        id = title.lowercase().replace(' ', '-'),
        category = RuleCategory.LAYER_DEPENDENCY,
        title = title,
        description = "desc",
        rationale = "rationale",
        ruleTemplate = RuleTemplate(
            methodName = "my_test_rule",
            codeLines = listOf("noClasses().should().bePublic()"),
            imports = emptySet(),
        ),
        status = status,
        confidence = Confidence.HIGH,
    )
}

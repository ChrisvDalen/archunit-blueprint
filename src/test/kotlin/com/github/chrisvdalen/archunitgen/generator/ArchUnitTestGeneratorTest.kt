package com.github.chrisvdalen.archunitgen.generator

import com.github.chrisvdalen.archunitgen.config.PluginConfig
import com.github.chrisvdalen.archunitgen.model.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ArchUnitTestGeneratorTest {

    private val generator = ArchUnitTestGenerator()

    @Test
    fun `generated code starts with correct package declaration`() {
        val result = generator.generate(listOf(acceptedRule()), PluginConfig(), "com.example")
        assertThat(result.code).startsWith("package com.example.architecture;")
    }

    @Test
    fun `generated code contains @AnalyzeClasses with correct base package`() {
        val result = generator.generate(listOf(acceptedRule()), PluginConfig(), "com.example.myapp")
        assertThat(result.code).contains("@AnalyzeClasses(packages = \"com.example.myapp\")")
    }

    @Test
    fun `generated code contains @ArchTest annotation`() {
        val result = generator.generate(listOf(acceptedRule()), PluginConfig(), "com.example")
        assertThat(result.code).contains("@ArchTest")
    }

    @Test
    fun `generated code uses static final ArchRule field`() {
        val result = generator.generate(listOf(acceptedRule()), PluginConfig(), "com.example")
        assertThat(result.code).contains("static final ArchRule my_test_rule =")
    }

    @Test
    fun `generated code contains the rule code lines`() {
        val result = generator.generate(listOf(acceptedRule()), PluginConfig(), "com.example")
        assertThat(result.code).contains("noClasses().should().bePublic()")
    }

    @Test
    fun `only accepted rules are included in output`() {
        val accepted = acceptedRule(id = "accepted-rule", methodName = "accepted_rule")
        val pending = rule(id = "pending-rule", methodName = "pending_rule", status = RuleStatus.PENDING)
        val ignored = rule(id = "ignored-rule", methodName = "ignored_rule", status = RuleStatus.IGNORED)

        val result = generator.generate(listOf(accepted, pending, ignored), PluginConfig(), "com.example")

        assertThat(result.code).contains("accepted_rule")
        assertThat(result.code).doesNotContain("pending_rule")
        assertThat(result.code).doesNotContain("ignored_rule")
    }

    @Test
    fun `relative path uses correct directory separator for package`() {
        val config = PluginConfig(testOutputPackage = "architecture", testClassName = "ArchitectureRulesTest")
        val result = generator.generate(listOf(acceptedRule()), config, "com.example.app")
        assertThat(result.relativePath)
            .isEqualTo("src/test/java/com/example/app/architecture/ArchitectureRulesTest.java")
    }

    @Test
    fun `imports are sorted and deduplicated`() {
        val rule1 = acceptedRule(imports = setOf("com.tngtech.archunit.B", "com.tngtech.archunit.A"))
        val rule2 = acceptedRule(id = "r2", methodName = "r2", imports = setOf("com.tngtech.archunit.A"))

        val result = generator.generate(listOf(rule1, rule2), PluginConfig(), "com.example")
        val importLines = result.code.lines().filter { it.startsWith("import ") }

        // Each import appears only once
        assertThat(importLines).doesNotHaveDuplicates()
        // Imports are alphabetically sorted
        val importValues = importLines.map { it.removePrefix("import ").removeSuffix(";") }
        assertThat(importValues).isSortedAccordingTo(compareBy { it })
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun acceptedRule(
        id: String = "my-test-rule",
        methodName: String = "my_test_rule",
        imports: Set<String> = setOf("com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses"),
    ) = rule(id, methodName, RuleStatus.ACCEPTED, imports)

    private fun rule(
        id: String,
        methodName: String,
        status: RuleStatus,
        imports: Set<String> = emptySet(),
    ) = RuleSuggestion(
        id = id,
        category = RuleCategory.LAYER_DEPENDENCY,
        title = "Test Rule",
        description = "desc",
        rationale = "rationale",
        ruleTemplate = RuleTemplate(
            methodName = methodName,
            codeLines = listOf("noClasses().should().bePublic()"),
            imports = imports,
        ),
        status = status,
        confidence = Confidence.HIGH,
    )
}

package com.github.chrisvdalen.archunitgen.config

import com.github.chrisvdalen.archunitgen.model.Confidence
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * ConfigLoader depends on [Project.basePath], so we test [ConfigLoader.parseForTest]
 * which accepts a raw Map directly – decoupling the test from the IntelliJ Platform.
 */
class ConfigLoaderParserTest {

    @Test
    fun `returns defaults when no file is present`() {
        val config = ConfigLoader.parseForTest(emptyMap())
        assertThat(config).isEqualTo(PluginConfig())
    }

    @Test
    fun `parses base-package correctly`() {
        val config = ConfigLoader.parseForTest(yamlMap(basePackage = "com.example.app"))
        assertThat(config.basePackage).isEqualTo("com.example.app")
    }

    @Test
    fun `parses minimum-confidence HIGH`() {
        val config = ConfigLoader.parseForTest(yamlMap(minimumConfidence = "HIGH"))
        assertThat(config.minimumConfidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun `defaults to MEDIUM when confidence key is absent`() {
        val config = ConfigLoader.parseForTest(yamlMap())
        assertThat(config.minimumConfidence).isEqualTo(Confidence.MEDIUM)
    }

    @Test
    fun `parses excluded-packages list`() {
        val config = ConfigLoader.parseForTest(
            yamlMap(excludedPackages = listOf("com.example.gen", "com.example.test"))
        )
        assertThat(config.excludedPackages).containsExactly("com.example.gen", "com.example.test")
    }

    @Test
    fun `parses excluded-rule-ids list`() {
        val config = ConfigLoader.parseForTest(
            yamlMap(excludedRuleIds = listOf("controller-only-services"))
        )
        assertThat(config.excludedRuleIds).containsExactly("controller-only-services")
    }

    @Test
    fun `parses custom layers`() {
        val raw = mapOf(
            "archunit-generator" to mapOf(
                "base-package" to "com.example",
                "custom-layers" to listOf(
                    mapOf("name" to "EventHandler", "package-pattern" to "com.example.events..")
                )
            )
        )
        val config = ConfigLoader.parseForTest(raw)
        assertThat(config.customLayers).hasSize(1)
        assertThat(config.customLayers.first().name).isEqualTo("EventHandler")
        assertThat(config.customLayers.first().packagePattern).isEqualTo("com.example.events..")
    }

    @Test
    fun `ignores unknown confidence value and falls back to MEDIUM`() {
        val config = ConfigLoader.parseForTest(yamlMap(minimumConfidence = "EXTREME"))
        assertThat(config.minimumConfidence).isEqualTo(Confidence.MEDIUM)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun yamlMap(
        basePackage: String = "",
        minimumConfidence: String? = null,
        excludedPackages: List<String> = emptyList(),
        excludedRuleIds: List<String> = emptyList(),
    ): Map<String, Any> {
        val gen = mutableMapOf<String, Any>()
        if (basePackage.isNotBlank()) gen["base-package"] = basePackage

        val filter = mutableMapOf<String, Any>()
        if (minimumConfidence != null) filter["minimum-confidence"] = minimumConfidence
        if (excludedPackages.isNotEmpty()) filter["excluded-packages"] = excludedPackages
        if (excludedRuleIds.isNotEmpty()) filter["excluded-rule-ids"] = excludedRuleIds
        if (filter.isNotEmpty()) gen["filter"] = filter

        return if (gen.isEmpty()) emptyMap() else mapOf("archunit-generator" to gen)
    }
}

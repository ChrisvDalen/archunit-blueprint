package com.github.chrisvdalen.archunitgen.config

import com.github.chrisvdalen.archunitgen.model.Confidence
import com.github.chrisvdalen.archunitgen.model.LayerDefinition
import com.intellij.openapi.project.Project
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Loads `archunit-rule-generator.yml` from the project root.
 * Returns [PluginConfig] with defaults when the file is absent or malformed.
 *
 * Expected YAML structure:
 * ```yaml
 * archunit-generator:
 *   base-package: com.example.myapp
 *   output:
 *     package: architecture
 *     class-name: ArchitectureRulesTest
 *   filter:
 *     minimum-confidence: MEDIUM
 *     excluded-packages:
 *       - com.example.myapp.generated
 *     excluded-rule-ids:
 *       - controller-only-services
 *   custom-layers:
 *     - name: EventHandler
 *       package-pattern: com.example.myapp.events..
 * ```
 */
class ConfigLoader(private val project: Project) {

    companion object {
        const val CONFIG_FILE_NAME = "archunit-rule-generator.yml"

        /**
         * Testability entry-point: parses a pre-loaded YAML map without
         * requiring an IntelliJ [Project] instance.
         */
        fun parseForTest(raw: Map<String, Any>): PluginConfig = parse(raw)

        @Suppress("UNCHECKED_CAST")
        private fun parse(root: Map<String, Any>): PluginConfig {
            val gen = root["archunit-generator"] as? Map<String, Any> ?: return PluginConfig()
            val output = gen["output"] as? Map<String, Any> ?: emptyMap<String, Any>()
            val filter = gen["filter"] as? Map<String, Any> ?: emptyMap<String, Any>()

            val confidence = runCatching {
                Confidence.valueOf(((filter["minimum-confidence"] as? String) ?: "MEDIUM").uppercase())
            }.getOrDefault(Confidence.MEDIUM)

            val customLayers = (gen["custom-layers"] as? List<Map<String, Any>>)
                ?.mapNotNull { entry ->
                    val name = entry["name"] as? String ?: return@mapNotNull null
                    val pattern = entry["package-pattern"] as? String ?: return@mapNotNull null
                    LayerDefinition(name, pattern)
                } ?: emptyList()

            return PluginConfig(
                basePackage = gen["base-package"] as? String ?: "",
                testOutputPackage = output["package"] as? String ?: "architecture",
                testClassName = output["class-name"] as? String ?: "ArchitectureRulesTest",
                minimumConfidence = confidence,
                excludedPackages = (filter["excluded-packages"] as? List<String>) ?: emptyList(),
                excludedRuleIds = (filter["excluded-rule-ids"] as? List<String>) ?: emptyList(),
                customLayers = customLayers,
            )
        }
    }

    fun load(): PluginConfig {
        val configFile = File(project.basePath ?: return PluginConfig(), CONFIG_FILE_NAME)
        if (!configFile.exists()) return PluginConfig()

        return runCatching {
            val raw: Map<String, Any> = Yaml().load(configFile.inputStream()) ?: return PluginConfig()
            parse(raw)
        }.getOrDefault(PluginConfig())
    }
}

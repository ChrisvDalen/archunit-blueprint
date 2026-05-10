package com.github.chrisvdalen.archunitgen.config

import com.github.chrisvdalen.archunitgen.model.Confidence
import com.github.chrisvdalen.archunitgen.model.LayerDefinition

/**
 * Runtime configuration, populated from `archunit-rule-generator.yml` if present,
 * otherwise from sensible defaults.
 */
data class PluginConfig(
    /** Root package of the application under test (fed to `@AnalyzeClasses`). */
    val basePackage: String = "",
    /** Sub-package under [basePackage] where the generated test class is placed. */
    val testOutputPackage: String = "architecture",
    /** Simple class name of the generated test. */
    val testClassName: String = "ArchitectureRulesTest",
    /** Suggestions below this threshold are silently dropped. */
    val minimumConfidence: Confidence = Confidence.MEDIUM,
    /** Packages excluded from scanning (exact prefix match). */
    val excludedPackages: List<String> = emptyList(),
    /** Rule IDs that the user permanently ignored via the yml file. */
    val excludedRuleIds: List<String> = emptyList(),
    /** Additional layers defined by the user that complement auto-detected ones. */
    val customLayers: List<LayerDefinition> = emptyList(),
)

package com.github.chrisvdalen.archunitgen.engine

import com.github.chrisvdalen.archunitgen.config.PluginConfig
import com.github.chrisvdalen.archunitgen.model.*
import com.github.chrisvdalen.archunitgen.scanner.OpenApiScanResult
import com.github.chrisvdalen.archunitgen.scanner.SpringScanResult

/**
 * Pure, side-effect-free engine that converts scan results into a ranked list of
 * [RuleSuggestion] objects.
 *
 * Rules are suppressed when the data they depend on is absent (e.g. no Repository
 * classes → no repository-access rule). This is the primary false-positive defence.
 */
class RuleSuggestionEngine {

    // Imports shared by virtually every generated rule
    private val baseImports = setOf(
        "com.tngtech.archunit.junit.AnalyzeClasses",
        "com.tngtech.archunit.junit.ArchTest",
        "com.tngtech.archunit.lang.ArchRule",
        "com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses",
        "com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes",
    )

    fun suggest(
        scanResult: SpringScanResult,
        layers: List<LayerDefinition>,
        openApiResult: OpenApiScanResult,
        config: PluginConfig,
    ): List<RuleSuggestion> {
        val candidates = buildList {
            add(controllerNoRepository(scanResult))
            add(controllerOnlyServices(scanResult))
            add(domainNoSpring(scanResult, layers, config))
            add(repositoryOnlyFromService(scanResult))
            add(openApiIsolation(openApiResult, layers))
            add(layeredArchitecture(layers))
            add(dtoNotInDomain(layers))
        }

        return candidates
            .filterNotNull()
            .filter { it.confidence.level >= config.minimumConfidence.level }
            .filter { suggestion -> config.excludedRuleIds.none { it == suggestion.id } }
    }

    // ── Individual rule builders ──────────────────────────────────────────────

    private fun controllerNoRepository(scan: SpringScanResult): RuleSuggestion? {
        val controllers = scan.forStereotype(SpringStereotype.CONTROLLER, SpringStereotype.REST_CONTROLLER)
        val repositories = scan.forStereotype(SpringStereotype.REPOSITORY)
        if (controllers.isEmpty() || repositories.isEmpty()) return null

        val repoPkgs = repositories.map { it.packageName }.toSet()
        val violations = controllers.filter { ctrl ->
            ctrl.imports.any { imp -> repoPkgs.any { rp -> imp.startsWith(rp) } }
        }

        return RuleSuggestion(
            id = "controller-no-repository",
            category = RuleCategory.LAYER_DEPENDENCY,
            title = "Controllers must not depend on Repositories",
            description = "No class annotated with @Controller or @RestController may directly import or inject a @Repository.",
            rationale = "Bypassing the service layer exposes persistence internals to the HTTP layer, " +
                "breaks encapsulation, and makes unit-testing controllers significantly harder.",
            ruleTemplate = RuleTemplate(
                methodName = "controllers_should_not_depend_on_repositories",
                codeLines = listOf(
                    "noClasses()",
                    "    .that().areAnnotatedWith(RestController.class)",
                    "    .or().areAnnotatedWith(Controller.class)",
                    "    .should().dependOnClassesThat().areAnnotatedWith(Repository.class)",
                    "    .because(\"Controllers must delegate to Services, not access Repositories directly\")",
                ),
                imports = baseImports + setOf(
                    "org.springframework.stereotype.Controller",
                    "org.springframework.stereotype.Repository",
                    "org.springframework.web.bind.annotation.RestController",
                ),
            ),
            confidence = Confidence.HIGH,
            evidence = violations.map { "${it.qualifiedName} imports a Repository class" },
        )
    }

    private fun controllerOnlyServices(scan: SpringScanResult): RuleSuggestion? {
        val controllers = scan.forStereotype(SpringStereotype.CONTROLLER, SpringStereotype.REST_CONTROLLER)
        val services = scan.forStereotype(SpringStereotype.SERVICE)
        if (controllers.isEmpty() || services.isEmpty()) return null

        return RuleSuggestion(
            id = "controller-only-services",
            category = RuleCategory.LAYER_DEPENDENCY,
            title = "Controllers should only depend on Services",
            description = "Controller classes must not take dependencies on anything other than @Service classes, standard library types, and Spring's own web types.",
            rationale = "A thin controller layer that talks only to services keeps business logic out of HTTP handlers and simplifies testing.",
            ruleTemplate = RuleTemplate(
                methodName = "controllers_should_only_depend_on_services",
                codeLines = listOf(
                    "noClasses()",
                    "    .that().areAnnotatedWith(RestController.class)",
                    "    .or().areAnnotatedWith(Controller.class)",
                    "    .should().dependOnClassesThat()",
                    "    .resideOutsideOfPackages(",
                    "        \"org.springframework.stereotype..\",",
                    "        \"org.springframework.web..\",",
                    "        \"java..\",",
                    "        \"jakarta..\",",
                    "        \"kotlin..\"",
                    "    )",
                ),
                imports = baseImports + setOf(
                    "org.springframework.stereotype.Controller",
                    "org.springframework.web.bind.annotation.RestController",
                ),
            ),
            confidence = Confidence.MEDIUM,
        )
    }

    private fun domainNoSpring(
        scan: SpringScanResult,
        layers: List<LayerDefinition>,
        config: PluginConfig,
    ): RuleSuggestion? {
        val domainLayer = layers.find { it.name in setOf("Domain", "Model") } ?: return null

        return RuleSuggestion(
            id = "domain-no-spring",
            category = RuleCategory.SPRING_STEREOTYPE_PLACEMENT,
            title = "Domain / model classes must not import Spring",
            description = "Classes in the domain or model layer should be plain Java objects with no Spring dependencies.",
            rationale = "Coupling domain objects to Spring makes them impossible to use outside a Spring context, " +
                "hampers unit-testing, and conflates infrastructure concerns with business rules.",
            ruleTemplate = RuleTemplate(
                methodName = "domain_classes_should_not_depend_on_spring",
                codeLines = listOf(
                    "noClasses()",
                    "    .that().resideInAPackage(\"${domainLayer.packagePattern}\")",
                    "    .should().dependOnClassesThat().resideInAPackage(\"org.springframework..\")",
                    "    .because(\"Domain classes must be framework-agnostic\")",
                ),
                imports = baseImports,
            ),
            confidence = Confidence.HIGH,
        )
    }

    private fun repositoryOnlyFromService(scan: SpringScanResult): RuleSuggestion? {
        val repos = scan.forStereotype(SpringStereotype.REPOSITORY)
        val services = scan.forStereotype(SpringStereotype.SERVICE)
        if (repos.isEmpty() || services.isEmpty()) return null

        return RuleSuggestion(
            id = "repository-only-from-service",
            category = RuleCategory.LAYER_DEPENDENCY,
            title = "Repositories should only be accessed from Services",
            description = "Only @Service classes (and test classes) may hold a reference to a @Repository.",
            rationale = "Centralising all data-access calls in the service layer ensures business rules are applied consistently before any persistence operation.",
            ruleTemplate = RuleTemplate(
                methodName = "repositories_should_only_be_accessed_by_services",
                codeLines = listOf(
                    "classes()",
                    "    .that().areAnnotatedWith(Repository.class)",
                    "    .should().onlyHaveDependentClassesThat()",
                    "    .areAnnotatedWith(Service.class)",
                    "    .orShould().onlyHaveDependentClassesThat().resideInAPackage(\"..test..\")",
                    "    .because(\"Repositories must only be accessed from the service layer\")",
                ),
                imports = baseImports + setOf(
                    "org.springframework.stereotype.Repository",
                    "org.springframework.stereotype.Service",
                ),
            ),
            confidence = Confidence.HIGH,
        )
    }

    private fun openApiIsolation(openApi: OpenApiScanResult, layers: List<LayerDefinition>): RuleSuggestion? {
        if (!openApi.hasGeneratedCode) return null
        val domainLayer = layers.find { it.name in setOf("Domain", "Model") }
        val generatedPattern = openApi.detectedPackages.minByOrNull { it.length }?.let { "$it.." } ?: "..generated.."

        val codeLines = if (domainLayer != null) {
            listOf(
                "noClasses()",
                "    .that().resideInAPackage(\"${domainLayer.packagePattern}\")",
                "    .should().dependOnClassesThat().resideInAPackage(\"$generatedPattern\")",
                "    .because(\"Generated OpenAPI types must not pollute the domain layer\")",
            )
        } else {
            listOf(
                "noClasses()",
                "    .that().resideOutsideOfPackage(\"$generatedPattern\")",
                "    .should().dependOnClassesThat().resideInAPackage(\"$generatedPattern\")",
                "    .because(\"Generated OpenAPI types must be isolated from business code\")",
            )
        }

        return RuleSuggestion(
            id = "openapi-no-domain-leak",
            category = RuleCategory.OPENAPI_ISOLATION,
            title = "Generated OpenAPI classes must not leak into domain / model",
            description = "Domain or model classes must not import generated OpenAPI types.",
            rationale = "Generated code regenerates with every OpenAPI spec change. Domain classes that depend on " +
                "generated types become fragile and couple the persistence model to the API contract.",
            ruleTemplate = RuleTemplate(
                methodName = "generated_openapi_classes_should_not_leak_into_domain",
                codeLines = codeLines,
                imports = baseImports,
            ),
            confidence = Confidence.HIGH,
            evidence = openApi.sampleClasses.map { "Detected generated class: $it" },
        )
    }

    private fun layeredArchitecture(layers: List<LayerDefinition>): RuleSuggestion? {
        val relevant = layers.filter {
            it.name in setOf("Controller", "Web", "Service", "Application", "Repository", "Domain", "Model")
        }
        if (relevant.size < 3) return null

        val names = relevant.map { it.name }.toSet()

        val layerDeclarations = relevant.map { "    .layer(\"${it.name}\").definedBy(\"${it.packagePattern}\")" }

        val accessRules = buildList {
            val ctrlLayer = names.firstOrNull { it in setOf("Controller", "Web") }
            if (ctrlLayer != null) add("    .whereLayer(\"$ctrlLayer\").mayNotBeAccessedByAnyLayer()")

            val repoLayer = if ("Repository" in names) "Repository" else null
            val svcLayer = names.firstOrNull { it in setOf("Service", "Application") }
            if (repoLayer != null && svcLayer != null) {
                add("    .whereLayer(\"$repoLayer\").mayOnlyBeAccessedByLayers(\"$svcLayer\")")
            }

            val domainLayer = names.firstOrNull { it in setOf("Domain", "Model") }
            if (domainLayer != null) {
                val permitted = listOf("Service", "Application", "Controller", "Web", "Repository")
                    .filter { it in names }
                    .joinToString(", ") { "\"$it\"" }
                add("    .whereLayer(\"$domainLayer\").mayOnlyBeAccessedByLayers($permitted)")
            }
        }

        return RuleSuggestion(
            id = "layered-architecture",
            category = RuleCategory.LAYER_DEPENDENCY,
            title = "Enforce layered architecture dependency directions",
            description = "Use ArchUnit's layered-architecture DSL to codify which layers may depend on which.",
            rationale = "Explicit layer rules prevent architectural drift. Once encoded, any violation becomes a test failure, not a code-review comment.",
            ruleTemplate = RuleTemplate(
                methodName = "layered_architecture_should_be_respected",
                codeLines = listOf("layeredArchitecture().consideringAllDependencies()") +
                    layerDeclarations + accessRules,
                imports = baseImports + setOf(
                    "com.tngtech.archunit.library.Architectures.layeredArchitecture",
                ),
            ),
            confidence = Confidence.HIGH,
        )
    }

    private fun dtoNotInDomain(layers: List<LayerDefinition>): RuleSuggestion? {
        val dtoLayer = layers.find { it.name == "DTO" } ?: return null
        val domainLayer = layers.find { it.name in setOf("Domain", "Model") } ?: return null

        return RuleSuggestion(
            id = "dto-not-in-domain",
            category = RuleCategory.DTO_ISOLATION,
            title = "DTO classes must not reside in or depend on the domain layer",
            description = "Data-transfer objects belong in the API / web layer, not in the domain model.",
            rationale = "Mixing DTOs with domain objects blurs the anti-corruption boundary. DTOs change with " +
                "API contracts; domain objects change with business rules. Keeping them separate makes each independently evolvable.",
            ruleTemplate = RuleTemplate(
                methodName = "dto_classes_should_not_reside_in_domain",
                codeLines = listOf(
                    "noClasses()",
                    "    .that().resideInAPackage(\"${dtoLayer.packagePattern}\")",
                    "    .should().resideInAPackage(\"${domainLayer.packagePattern}\")",
                    "    .because(\"DTOs are API-layer types and must not pollute the domain model\")",
                ),
                imports = baseImports,
            ),
            confidence = Confidence.MEDIUM,
        )
    }
}

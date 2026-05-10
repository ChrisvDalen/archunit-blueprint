package com.github.chrisvdalen.archunitgen.engine

import com.github.chrisvdalen.archunitgen.config.PluginConfig
import com.github.chrisvdalen.archunitgen.model.Confidence
import com.github.chrisvdalen.archunitgen.model.LayerDefinition
import com.github.chrisvdalen.archunitgen.model.SpringStereotype
import com.github.chrisvdalen.archunitgen.scanner.OpenApiScanResult
import com.github.chrisvdalen.archunitgen.scanner.ScannedClass
import com.github.chrisvdalen.archunitgen.scanner.SpringScanResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RuleSuggestionEngineTest {

    private val engine = RuleSuggestionEngine()
    private val defaultConfig = PluginConfig(minimumConfidence = Confidence.LOW)
    private val noOpenApi = OpenApiScanResult(emptySet(), emptyList(), false)

    // ── controller-no-repository ──────────────────────────────────────────────

    @Test
    fun `suggests controller-no-repository rule when both stereotypes exist`() {
        val scan = scanWith(
            SpringStereotype.REST_CONTROLLER to listOf(controller("com.example.web.UserController", "com.example.web")),
            SpringStereotype.REPOSITORY to listOf(repo("com.example.repository.UserRepository", "com.example.repository")),
        )

        val suggestions = engine.suggest(scan, emptyList(), noOpenApi, defaultConfig)

        assertThat(suggestions).anyMatch { it.id == "controller-no-repository" }
    }

    @Test
    fun `does not suggest controller-no-repository when no repositories exist`() {
        val scan = scanWith(
            SpringStereotype.REST_CONTROLLER to listOf(controller("com.example.web.UserController", "com.example.web")),
        )

        val suggestions = engine.suggest(scan, emptyList(), noOpenApi, defaultConfig)

        assertThat(suggestions).noneMatch { it.id == "controller-no-repository" }
    }

    @Test
    fun `controller-no-repository carries HIGH confidence`() {
        val scan = scanWith(
            SpringStereotype.CONTROLLER to listOf(controller("com.example.web.Ctrl", "com.example.web")),
            SpringStereotype.REPOSITORY to listOf(repo("com.example.data.Repo", "com.example.data")),
        )

        val rule = engine.suggest(scan, emptyList(), noOpenApi, defaultConfig)
            .first { it.id == "controller-no-repository" }

        assertThat(rule.confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun `controller-no-repository includes evidence when controller imports repo package`() {
        val ctrlWithDirectImport = ScannedClass(
            qualifiedName = "com.example.web.BadController",
            simpleName = "BadController",
            packageName = "com.example.web",
            stereotype = SpringStereotype.REST_CONTROLLER,
            imports = listOf("com.example.repository.UserRepository"),
        )
        val scan = scanWith(
            SpringStereotype.REST_CONTROLLER to listOf(ctrlWithDirectImport),
            SpringStereotype.REPOSITORY to listOf(repo("com.example.repository.UserRepository", "com.example.repository")),
        )

        val rule = engine.suggest(scan, emptyList(), noOpenApi, defaultConfig)
            .first { it.id == "controller-no-repository" }

        assertThat(rule.evidence).isNotEmpty
        assertThat(rule.evidence.first()).contains("BadController")
    }

    // ── domain-no-spring ─────────────────────────────────────────────────────

    @Test
    fun `suggests domain-no-spring rule when domain layer is detected`() {
        val layers = listOf(LayerDefinition("Domain", "com.example.domain.."))
        val scan = scanWith(SpringStereotype.SERVICE to listOf(svc("com.example.service.Svc", "com.example.service")))

        val suggestions = engine.suggest(scan, layers, noOpenApi, defaultConfig)

        assertThat(suggestions).anyMatch { it.id == "domain-no-spring" }
    }

    @Test
    fun `does not suggest domain-no-spring when no domain layer detected`() {
        val scan = scanWith(SpringStereotype.SERVICE to listOf(svc("com.example.service.Svc", "com.example.service")))

        val suggestions = engine.suggest(scan, emptyList(), noOpenApi, defaultConfig)

        assertThat(suggestions).noneMatch { it.id == "domain-no-spring" }
    }

    // ── excluded-rule-ids ─────────────────────────────────────────────────────

    @Test
    fun `excludes rule when its id is in config excludedRuleIds`() {
        val config = PluginConfig(minimumConfidence = Confidence.LOW, excludedRuleIds = listOf("controller-no-repository"))
        val scan = scanWith(
            SpringStereotype.REST_CONTROLLER to listOf(controller("com.example.web.Ctrl", "com.example.web")),
            SpringStereotype.REPOSITORY to listOf(repo("com.example.data.Repo", "com.example.data")),
        )

        val suggestions = engine.suggest(scan, emptyList(), noOpenApi, config)

        assertThat(suggestions).noneMatch { it.id == "controller-no-repository" }
    }

    // ── confidence filter ─────────────────────────────────────────────────────

    @Test
    fun `filters out LOW confidence suggestions when threshold is MEDIUM`() {
        val config = PluginConfig(minimumConfidence = Confidence.MEDIUM)
        val layers = listOf(
            LayerDefinition("Controller", "com.example.web.."),
            LayerDefinition("Service", "com.example.service.."),
            LayerDefinition("Repository", "com.example.repository.."),
            LayerDefinition("Domain", "com.example.domain.."),
        )
        val scan = scanWith(
            SpringStereotype.CONTROLLER to listOf(controller("com.example.web.Ctrl", "com.example.web")),
            SpringStereotype.SERVICE to listOf(svc("com.example.service.Svc", "com.example.service")),
            SpringStereotype.REPOSITORY to listOf(repo("com.example.data.Repo", "com.example.data")),
        )

        val suggestions = engine.suggest(scan, layers, noOpenApi, config)

        assertThat(suggestions).allMatch { it.confidence.level >= Confidence.MEDIUM.level }
    }

    // ── OpenAPI isolation ─────────────────────────────────────────────────────

    @Test
    fun `suggests openapi-no-domain-leak when generated code is detected`() {
        val openApi = OpenApiScanResult(
            detectedPackages = setOf("com.example.generated"),
            sampleClasses = listOf("com.example.generated.PetDto"),
            hasGeneratedCode = true,
        )
        val layers = listOf(LayerDefinition("Domain", "com.example.domain.."))

        val suggestions = engine.suggest(SpringScanResult(emptyMap()), layers, openApi, defaultConfig)

        assertThat(suggestions).anyMatch { it.id == "openapi-no-domain-leak" }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun scanWith(vararg pairs: Pair<SpringStereotype, List<ScannedClass>>): SpringScanResult =
        SpringScanResult(mapOf(*pairs))

    private fun controller(fqn: String, pkg: String) = ScannedClass(fqn, fqn.substringAfterLast("."), pkg, SpringStereotype.CONTROLLER, emptyList())
    private fun repo(fqn: String, pkg: String) = ScannedClass(fqn, fqn.substringAfterLast("."), pkg, SpringStereotype.REPOSITORY, emptyList())
    private fun svc(fqn: String, pkg: String) = ScannedClass(fqn, fqn.substringAfterLast("."), pkg, SpringStereotype.SERVICE, emptyList())
}

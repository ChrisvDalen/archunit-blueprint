package com.github.chrisvdalen.archunitgen.scanner

import com.github.chrisvdalen.archunitgen.model.LayerDefinition
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AllClassesSearch

/**
 * Infers architecture layers from the package tree and collects cross-package
 * import dependencies between already-scanned Spring components.
 *
 * Layer detection uses a keyword list so it works on arbitrary package structures
 * (com.acme.order.web, nl.bank.klant.controller, etc.).
 */
class PackageRelationshipAnalyzer(private val project: Project) {

    companion object {
        // Ordered: longer/more-specific keywords first so that "controllers" beats "control".
        private val LAYER_KEYWORDS: LinkedHashMap<String, String> = linkedMapOf(
            "controllers" to "Controller",
            "controller" to "Controller",
            "web" to "Web",
            "rest" to "Web",
            "services" to "Service",
            "service" to "Service",
            "application" to "Application",
            "usecases" to "Application",
            "repositories" to "Repository",
            "repository" to "Repository",
            "repo" to "Repository",
            "persistence" to "Repository",
            "domain" to "Domain",
            "model" to "Model",
            "infrastructure" to "Infrastructure",
            "infra" to "Infrastructure",
            "adapters" to "Adapter",
            "adapter" to "Adapter",
            "ports" to "Port",
            "dto" to "DTO",
            "dtos" to "DTO",
            "api" to "API",
            "openapi" to "OpenAPI",
            "generated" to "Generated",
        )
    }

    /**
     * Walks all project packages and heuristically maps each to a logical layer.
     * Returns at most one [LayerDefinition] per layer name (shortest matching package wins).
     */
    fun detectLayers(): List<LayerDefinition> {
        val allPackages = collectAllPackages()
        // layerName → shortest matching base package
        val found = mutableMapOf<String, String>()

        for (pkg in allPackages) {
            val segments = pkg.split(".")
            for (segment in segments) {
                val layerName = LAYER_KEYWORDS[segment.lowercase()] ?: continue
                val existing = found[layerName]
                if (existing == null || pkg.length < existing.length) {
                    found[layerName] = pkg
                }
            }
        }

        return found
            .map { (layerName, basePkg) -> LayerDefinition(layerName, "$basePkg..") }
            .sortedBy { it.name }
    }

    /**
     * For every scanned class, resolves which foreign package each import
     * points to and emits a [PackageDependency].
     */
    fun analyzeDependencies(allClasses: List<ScannedClass>): List<PackageDependency> =
        allClasses.flatMap { cls ->
            cls.imports.mapNotNull { imp ->
                val importPkg = imp.substringBeforeLast(".")
                if (importPkg != cls.packageName && importPkg.isNotBlank()) {
                    PackageDependency(fromPackage = cls.packageName, toPackage = importPkg, fromClass = cls)
                } else null
            }
        }

    private fun collectAllPackages(): Set<String> = ReadAction.compute<Set<String>, RuntimeException> {
        val scope = GlobalSearchScope.projectScope(project)
        AllClassesSearch.search(scope, project)
            .findAll()
            .mapNotNull { cls -> (cls.containingFile as? PsiJavaFile)?.packageName }
            .filter { it.isNotBlank() }
            .toSet()
    }
}

data class PackageDependency(
    val fromPackage: String,
    val toPackage: String,
    val fromClass: ScannedClass,
)

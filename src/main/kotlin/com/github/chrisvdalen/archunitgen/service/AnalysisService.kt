package com.github.chrisvdalen.archunitgen.service

import com.github.chrisvdalen.archunitgen.config.ConfigLoader
import com.github.chrisvdalen.archunitgen.engine.RuleSuggestionEngine
import com.github.chrisvdalen.archunitgen.model.RuleSuggestion
import com.github.chrisvdalen.archunitgen.scanner.OpenApiCodeDetector
import com.github.chrisvdalen.archunitgen.scanner.PackageRelationshipAnalyzer
import com.github.chrisvdalen.archunitgen.scanner.SpringComponentScanner
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project

/**
 * IntelliJ project-scoped service that coordinates the full analysis pipeline:
 * config → scan → layer detection → suggestion engine.
 *
 * All heavy work runs in a background task so the UI stays responsive.
 * Results are delivered on the EDT via [onComplete].
 */
@Service(Service.Level.PROJECT)
class AnalysisService(private val project: Project) {

    private val engine = RuleSuggestionEngine()

    fun analyzeAsync(onComplete: (List<RuleSuggestion>) -> Unit) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "ArchUnit Rule Generator: analysing project…",
            /* canBeCancelled = */ true,
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false

                indicator.text = "Loading configuration…"
                indicator.fraction = 0.10
                val config = ConfigLoader(project).load()

                indicator.checkCanceled()
                indicator.text = "Scanning Spring components…"
                indicator.fraction = 0.30
                val springResult = SpringComponentScanner(project).scan()

                indicator.checkCanceled()
                indicator.text = "Analysing package relationships…"
                indicator.fraction = 0.55
                val pkgAnalyzer = PackageRelationshipAnalyzer(project)
                val layers = (pkgAnalyzer.detectLayers() + config.customLayers).distinctBy { it.name }

                indicator.checkCanceled()
                indicator.text = "Detecting generated OpenAPI code…"
                indicator.fraction = 0.75
                val openApiResult = OpenApiCodeDetector(project).detect()

                indicator.checkCanceled()
                indicator.text = "Building rule suggestions…"
                indicator.fraction = 0.90
                val suggestions = engine.suggest(springResult, layers, openApiResult, config)

                indicator.fraction = 1.0
                onComplete(suggestions)
            }
        })
    }
}

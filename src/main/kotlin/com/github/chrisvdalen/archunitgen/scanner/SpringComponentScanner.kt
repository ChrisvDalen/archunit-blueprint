package com.github.chrisvdalen.archunitgen.scanner

import com.github.chrisvdalen.archunitgen.model.SpringStereotype
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch

/**
 * Walks the entire project scope via IntelliJ PSI and collects every class
 * that carries one of the five core Spring stereotype annotations.
 *
 * Uses [AnnotatedElementsSearch] rather than raw text search so that
 * inherited / meta-annotations (e.g. @RestController → @Controller) are
 * handled correctly by the IntelliJ index.
 */
class SpringComponentScanner(private val project: Project) {

    fun scan(): SpringScanResult {
        val facade = JavaPsiFacade.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)

        val byStereotype = SpringStereotype.entries.associateWith { stereotype ->
            val annotationClass = facade.findClass(stereotype.annotationFqn, scope)
                ?: return@associateWith emptyList()

            AnnotatedElementsSearch
                .searchPsiClasses(annotationClass, scope)
                .findAll()
                .mapNotNull { psiClass -> toScannedClass(psiClass, stereotype) }
        }

        return SpringScanResult(byStereotype)
    }

    private fun toScannedClass(psiClass: PsiClass, stereotype: SpringStereotype): ScannedClass? {
        val file = psiClass.containingFile as? PsiJavaFile ?: return null
        return ScannedClass(
            qualifiedName = psiClass.qualifiedName ?: return null,
            simpleName = psiClass.name ?: return null,
            packageName = file.packageName,
            stereotype = stereotype,
            imports = extractImports(file),
        )
    }

    private fun extractImports(file: PsiJavaFile): List<String> =
        file.importList?.importStatements?.mapNotNull { it.qualifiedName } ?: emptyList()
}

/** Flat representation of a class found during scanning – no live PSI references kept. */
data class ScannedClass(
    val qualifiedName: String,
    val simpleName: String,
    val packageName: String,
    val stereotype: SpringStereotype,
    /** Fully-qualified import names present in the source file. */
    val imports: List<String>,
)

data class SpringScanResult(
    val byStereotype: Map<SpringStereotype, List<ScannedClass>>,
) {
    fun forStereotype(vararg types: SpringStereotype): List<ScannedClass> =
        types.flatMap { byStereotype[it] ?: emptyList() }

    fun allClasses(): List<ScannedClass> = byStereotype.values.flatten()

    val isEmpty: Boolean get() = byStereotype.values.all { it.isEmpty() }
}

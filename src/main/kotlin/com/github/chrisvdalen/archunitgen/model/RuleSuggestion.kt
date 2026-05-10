package com.github.chrisvdalen.archunitgen.model

/**
 * A single architecture rule proposed by the engine.
 *
 * [id] is stable across runs so that user decisions (ACCEPTED / IGNORED) can be persisted.
 * [evidence] lists concrete class names or imports that triggered this suggestion.
 */
data class RuleSuggestion(
    val id: String,
    val category: RuleCategory,
    val title: String,
    val description: String,
    val rationale: String,
    val ruleTemplate: RuleTemplate,
    var status: RuleStatus = RuleStatus.PENDING,
    val confidence: Confidence = Confidence.HIGH,
    val evidence: List<String> = emptyList(),
) {
    fun accept() {
        status = RuleStatus.ACCEPTED
    }

    fun ignore() {
        status = RuleStatus.IGNORED
    }
}

enum class RuleStatus(val displayName: String) {
    PENDING("Pending"),
    ACCEPTED("Accepted"),
    IGNORED("Ignored"),
}

/**
 * Code template that will be rendered inside the generated test class.
 *
 * [methodName] becomes the Java field/method name (snake_case for ArchUnit convention).
 * [codeLines] is the fluent ArchUnit expression, one physical line per entry.
 * [imports] are the fully-qualified types needed in the generated file.
 */
data class RuleTemplate(
    val methodName: String,
    val codeLines: List<String>,
    val imports: Set<String>,
)

package com.github.chrisvdalen.archunitgen.model

/**
 * How certain the engine is that this rule is applicable and correct.
 * LOW rules are only emitted when the user explicitly lowers the threshold in config.
 */
enum class Confidence(val displayName: String, val level: Int) {
    HIGH("High", 3),
    MEDIUM("Medium", 2),
    LOW("Low", 1),
}

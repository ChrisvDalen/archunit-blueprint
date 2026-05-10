package com.github.chrisvdalen.archunitgen.model

/** Maps a logical architecture layer name to an ArchUnit package pattern (e.g. `com.example.web..`). */
data class LayerDefinition(
    val name: String,
    val packagePattern: String,
)

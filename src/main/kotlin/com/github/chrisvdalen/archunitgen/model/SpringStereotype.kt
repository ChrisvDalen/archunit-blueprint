package com.github.chrisvdalen.archunitgen.model

enum class SpringStereotype(val annotationFqn: String, val shortName: String) {
    CONTROLLER("org.springframework.stereotype.Controller", "Controller"),
    REST_CONTROLLER("org.springframework.web.bind.annotation.RestController", "RestController"),
    SERVICE("org.springframework.stereotype.Service", "Service"),
    REPOSITORY("org.springframework.stereotype.Repository", "Repository"),
    COMPONENT("org.springframework.stereotype.Component", "Component");

    companion object {
        fun fromFqn(fqn: String): SpringStereotype? = entries.find { it.annotationFqn == fqn }

        val CONTROLLER_TYPES: Set<SpringStereotype> = setOf(CONTROLLER, REST_CONTROLLER)
    }
}

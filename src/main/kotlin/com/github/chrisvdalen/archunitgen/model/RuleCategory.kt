package com.github.chrisvdalen.archunitgen.model

enum class RuleCategory(val displayName: String) {
    LAYER_DEPENDENCY("Layer Dependency"),
    SPRING_STEREOTYPE_PLACEMENT("Spring Stereotype Placement"),
    PACKAGE_NAMING("Package Naming"),
    OPENAPI_ISOLATION("OpenAPI Isolation"),
    DTO_ISOLATION("DTO Isolation"),
    INFRASTRUCTURE_ISOLATION("Infrastructure / Domain Isolation"),
    IMPORT_RESTRICTION("Import Restriction"),
}

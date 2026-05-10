<div align="center">

# ArchUnit Blueprint

### Generate architecture rules from your Java project — directly inside IntelliJ IDEA.

![Status](https://img.shields.io/badge/status-work%20in%20progress-orange)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin)
![IntelliJ Platform](https://img.shields.io/badge/IntelliJ%20Platform-2024.3.3-000000?logo=intellijidea)
![Java](https://img.shields.io/badge/JVM-21-blue)
![License](https://img.shields.io/badge/license-TBD-lightgrey)

</div>

---

## What is ArchUnit Blueprint?

**ArchUnit Blueprint** is an IntelliJ IDEA plugin that analyzes Java and Spring Boot projects and suggests concrete **ArchUnit architecture rules** based on the existing project structure.

Instead of manually writing architecture tests from scratch, the plugin helps you discover useful rules from your codebase and generate ready-to-run JUnit 5 / ArchUnit tests.

The goal is simple:

> Turn architecture intentions into executable tests.

---

## Why this exists

Architecture rules are valuable, but teams often skip them because writing good ArchUnit tests takes time, context, and experience.

This plugin aims to reduce that friction by scanning the project and proposing rules such as:

- Controllers should not depend directly on repositories
- Domain/model code should not depend on Spring
- Services should live in service packages
- Repositories should only be used from service/application layers
- Generated OpenAPI code should not leak into the domain model
- Forbidden dependencies between layers or modules should be enforced

---

## Features

Current / planned capabilities:

- Detect Spring stereotypes:
  - `@Controller`
  - `@Service`
  - `@Repository`
  - `@Component`
- Analyze package dependency relationships across layers
- Detect generated OpenAPI code
- Suggest isolation rules for generated code
- Propose layered architecture rules
- Generate JUnit 5 / ArchUnit test classes
- Support project-level configuration via YAML
- Filter suggestions by confidence level
- Exclude packages or rule IDs
- Add custom architectural layers manually

---

## Example generated rule

```java
@AnalyzeClasses(packages = "com.example.myapp")
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule controllers_should_not_depend_on_repositories =
        noClasses()
            .that()
            .resideInAPackage("..controller..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..repository..");
}

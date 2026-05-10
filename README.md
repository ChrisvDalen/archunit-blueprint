<div align="center">

# ArchUnit Rule Generator

**An IntelliJ IDEA plugin that analyses your Java / Spring Boot project and proposes ready-to-run ArchUnit architecture rules — no manual rule-writing required.**

[![Build](https://img.shields.io/badge/build-passing-brightgreen?style=flat-square)](#building-locally)
[![IntelliJ Platform](https://img.shields.io/badge/IntelliJ%20Platform-2024.3%2B-orange?style=flat-square&logo=intellij-idea)](https://plugins.jetbrains.com/docs/intellij/)
[![Java](https://img.shields.io/badge/Java-21-blue?style=flat-square&logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![ArchUnit](https://img.shields.io/badge/ArchUnit-1.3-purple?style=flat-square)](https://www.archunit.org/)
[![License](https://img.shields.io/badge/license-Apache%202.0-lightgrey?style=flat-square)](LICENSE)

</div>

---

## The problem

Most Java teams _want_ architecture rules, but writing them by hand is tedious and easy to skip. The result: controllers that directly inject repositories, domain objects that import Spring annotations, generated OpenAPI types leaking into business logic. By the time someone notices, refactoring is expensive.

## The solution

Open the plugin, click **Analyse Project**. It inspects your package structure, Spring stereotypes, import graph and generated code in seconds. You get a table of concrete rule suggestions. Accept what makes sense, ignore what does not, click **Generate Test Class** — and commit a fully working ArchUnit JUnit 5 test.

---

## Features

| | |
|---|---|
| **Spring stereotype detection** | Finds `@Controller`, `@RestController`, `@Service`, `@Repository`, `@Component` via IntelliJ PSI |
| **Layer auto-detection** | Recognises `web`, `service`, `repository`, `domain`, `dto`, `infrastructure`, `adapter`, `openapi` and more from your actual package tree |
| **OpenAPI isolation** | Detects generated code via `@Generated` annotations, package keywords and file-header comments |
| **Seven built-in rules** | Controllers → Repos, domain no-Spring, layered architecture DSL, DTO isolation, OpenAPI boundary, and more |
| **Review before you commit** | Nothing is written to disk until you explicitly accept rules and click Generate |
| **Configurable** | Override defaults via `archunit-rule-generator.yml` in the project root |
| **False-positive defence** | Rules are only proposed when the required evidence is actually present |

---

## Quick start

**1. Install** — from the JetBrains Marketplace _(coming soon)_ or via **Settings → Plugins → Install from disk** using the ZIP from [Releases](#).

**2. Open the tool window** — use **Tools → Analyze Project for ArchUnit Rules** or click the *ArchUnit Rules* panel on the right sidebar.

**3. Analyse** — click **Analyse Project**. The plugin scans your project in a background thread and fills the table.

**4. Review** — read each suggestion, check the rationale and confidence level.

**5. Accept or ignore** — select rows and click **Accept Selected** or **Ignore Selected**. Use **Accept All** to take everything.

**6. Generate** — click **Generate Test Class**. The file is written to `src/test/java/.../architecture/ArchitectureRulesTest.java` and appears in your project view immediately.

**7. Run and commit** — `./mvnw test` or `./gradlew test`. Green? Commit the file.

---

## Example — generated test class

```java
@AnalyzeClasses(packages = "com.example.myapp")
class ArchitectureRulesTest {

    // Bypassing the service layer exposes persistence internals to the HTTP layer.
    @ArchTest
    static final ArchRule controllers_should_not_depend_on_repositories =
        noClasses()
            .that().areAnnotatedWith(RestController.class)
            .or().areAnnotatedWith(Controller.class)
            .should().dependOnClassesThat().areAnnotatedWith(Repository.class)
            .because("Controllers must delegate to Services, not access Repositories directly");

    // Domain objects must be framework-agnostic.
    @ArchTest
    static final ArchRule domain_classes_should_not_depend_on_spring =
        noClasses()
            .that().resideInAPackage("com.example.myapp.domain..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..")
            .because("Domain classes must be framework-agnostic");

    // Explicit layer boundaries prevent drift over time.
    @ArchTest
    static final ArchRule layered_architecture_should_be_respected =
        layeredArchitecture().consideringAllDependencies()
            .layer("Controller").definedBy("com.example.myapp.web..")
            .layer("Service").definedBy("com.example.myapp.service..")
            .layer("Repository").definedBy("com.example.myapp.repository..")
            .layer("Domain").definedBy("com.example.myapp.domain..")
            .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
            .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Service", "Repository");
}
```

See the [full example](docs/example-generated-test/ArchitectureRulesTest.java) in `docs/`.

---

## Rules catalogue

| Rule ID | Triggered when | Confidence |
|---|---|---|
| `controller-no-repository` | `@Controller`/`@RestController` + `@Repository` both present | HIGH |
| `controller-only-services` | Controllers + Services present | MEDIUM |
| `domain-no-spring` | A `domain` or `model` package detected | HIGH |
| `repository-only-from-service` | Repositories + Services present | HIGH |
| `openapi-no-domain-leak` | Generated code detected + domain layer present | HIGH |
| `layered-architecture` | ≥ 3 recognised architecture layers | HIGH |
| `dto-not-in-domain` | A `dto` package + domain layer present | MEDIUM |

---

## Configuration

Drop `archunit-rule-generator.yml` in the project root:

```yaml
archunit-generator:
  base-package: com.example.myapp          # fed to @AnalyzeClasses
  output:
    package: architecture                  # sub-package for the generated test
    class-name: ArchitectureRulesTest
  filter:
    minimum-confidence: MEDIUM             # HIGH | MEDIUM | LOW
    excluded-packages:
      - com.example.myapp.generated
    excluded-rule-ids:
      - controller-only-services           # too strict for our current codebase
  custom-layers:
    - name: EventHandler
      package-pattern: com.example.myapp.events..
```

Full reference: [`docs/archunit-rule-generator.yml.example`](docs/archunit-rule-generator.yml.example).

---

## Required dependency in the target project

```xml
<!-- Maven -->
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <version>1.3.0</version>
    <scope>test</scope>
</dependency>
```

```kotlin
// Gradle (Kotlin DSL)
testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
```

---

## Building locally

```bash
# Bootstrap the Gradle wrapper (first time only)
gradle wrapper --gradle-version 8.11.1

# Run unit tests — no IntelliJ sandbox needed
./gradlew test

# Build the distributable plugin ZIP
./gradlew buildPlugin

# Launch in a sandboxed IntelliJ IDEA instance
./gradlew runIde
```

---

## Project layout

```
src/main/kotlin/com/github/chrisvdalen/archunitgen/
├── model/          RuleSuggestion · RuleCategory · Confidence · SpringStereotype · LayerDefinition
├── scanner/        PSI-based Spring scanner · package-layer detector · OpenAPI detector
├── engine/         RuleSuggestionEngine  (pure Kotlin, no IntelliJ dependency)
├── generator/      ArchUnitTestGenerator · TestFileWriter
├── config/         PluginConfig · ConfigLoader  (SnakeYAML)
├── service/        AnalysisService  (IntelliJ project service, background task)
└── ui/             ArchUnitToolWindowFactory · RuleSuggestionPanel · GenerateArchUnitRulesAction

src/test/kotlin/com/github/chrisvdalen/archunitgen/
├── engine/         RuleSuggestionEngineTest     (8 test cases)
├── generator/      ArchUnitTestGeneratorTest    (8 test cases)
└── config/         ConfigLoaderTest             (8 test cases)

docs/
├── archunit-rule-generator.yml.example
└── example-generated-test/ArchitectureRulesTest.java
```

---

## How false positives are prevented

1. **Presence check** — a rule is only proposed when both sides of the constraint are found (e.g. controllers _and_ repositories must exist before suggesting the controller→repository rule).
2. **Confidence threshold** — `LOW` confidence rules are filtered out by default; raise or lower the threshold in the YAML config.
3. **Excluded rule IDs** — permanently suppress any rule by its stable ID in config.
4. **Excluded packages** — skip generated, test, or legacy packages from scanning.
5. **Mandatory review step** — nothing is written to disk until you explicitly accept rules and click **Generate Test Class**.

---

## Roadmap

- [ ] Persist accept/ignore decisions between IDE sessions (project-level storage)
- [ ] Hexagonal / ports-and-adapters architecture rule template
- [ ] Preview pane — see the generated code before writing it
- [ ] Custom rule builder in the UI
- [ ] `@ArchIgnore` annotation scaffolding in generated tests
- [ ] Multi-module Maven/Gradle project support
- [ ] JetBrains Marketplace publication pipeline

---

## License

[Apache 2.0](LICENSE)

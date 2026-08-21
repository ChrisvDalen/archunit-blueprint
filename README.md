<div align="center">

# ArchUnit Rule Generator

**An IntelliJ IDEA plugin that analyses your Java / Spring Boot project and proposes ready-to-run ArchUnit architecture rules — no manual rule-writing required.**

[![Build](https://img.shields.io/github/actions/workflow/status/ChrisvDalen/archunit-blueprint/ci.yml?branch=main&style=flat-square)](https://github.com/ChrisvDalen/archunit-blueprint/actions/workflows/ci.yml)
[![IntelliJ Platform](https://img.shields.io/badge/IntelliJ%20Platform-2026.2%2B-orange?style=flat-square&logo=intellij-idea)](https://plugins.jetbrains.com/docs/intellij/)
[![Java](https://img.shields.io/badge/Java-25-blue?style=flat-square&logo=openjdk)](https://openjdk.org/projects/jdk/25/)
[![ArchUnit](https://img.shields.io/badge/ArchUnit-1.5-purple?style=flat-square)](https://www.archunit.org/)

</div>

---

## The problem

Most Java teams _want_ architecture rules, but writing them by hand is tedious and easy to skip. The result: controllers that directly inject repositories, domain objects that import Spring annotations, generated OpenAPI types leaking into business logic. By the time someone notices, refactoring is expensive.

## The solution

Open the plugin, click **Analyse Project**. It inspects your package structure, Spring stereotypes, import graph and generated code in seconds. You get a table of concrete rule suggestions. Accept what makes sense, ignore what does not, click **Generate Test Class** — and commit a fully working ArchUnit JUnit 5 test.

---

## Running the plugin

### Option A — Install from disk (recommended for trying it out)

1. **Prerequisites:** IntelliJ IDEA 2026.2 or newer, JDK 25.

2. **Build the plugin ZIP:**

   ```bash
   # Clone the repository
   git clone https://github.com/ChrisvDalen/archunit-blueprint.git
   cd archunit-blueprint

   # Build the distributable ZIP
   ./gradlew buildPlugin
   ```

   The ZIP is produced at `build/distributions/archunit-rule-generator-0.1.0-SNAPSHOT.zip`.

3. **Install in IntelliJ IDEA:**
   - Open **Settings / Preferences → Plugins**
   - Click the ⚙️ gear icon → **Install Plugin from Disk…**
   - Select the ZIP from `build/distributions/`
   - Restart the IDE when prompted.

### Option B — Run in a sandboxed IDE (for development)

```bash
# Starts a fresh IntelliJ IDEA Community instance with the plugin pre-loaded.
# No installation step needed — changes to the source hot-reload on the next run.
./gradlew runIde
```

This downloads a sandboxed IntelliJ IDEA on first run (~800 MB). Open any Java/Spring Boot project inside that IDE instance to test the plugin.

### Option C — Run the unit tests only

The core engine and generator have no IntelliJ Platform dependency, so tests run with plain JUnit 5:

```bash
./gradlew test
```

Test report: `build/reports/tests/test/index.html`

---

## Using the plugin

Once installed and a Java/Spring Boot project is open:

**1. Open the tool window**

Either:
- **Tools → Analyze Project for ArchUnit Rules**, or
- Click the **ArchUnit Rules** panel in the right sidebar.

**2. Analyse**

Click **Analyse Project**. The plugin scans your project in a background thread (progress bar visible in the status bar) and fills the suggestion table.

**3. Review the suggestions**

Each row shows:
| Column | What it means |
|---|---|
| Rule | Short name for the constraint |
| Category | Type of architectural concern |
| Confidence | How certain the engine is this rule applies to your project |
| Status | `Pending` → `Accepted` or `Ignored` |

**4. Accept or ignore**

- Select one or more rows, then click **Accept Selected** or **Ignore Selected**.
- Click **Accept All** to accept every suggestion at once.

**5. Generate the test class**

Click **Generate Test Class**. You will be prompted for the root package if it was not found in `archunit-rule-generator.yml`.

The file is written to:
```
src/test/java/<your-package>/architecture/ArchitectureRulesTest.java
```
and appears in the project view immediately.

**6. Run it**

```bash
# Maven
./mvnw test -pl . -Dtest=ArchitectureRulesTest

# Gradle
./gradlew test --tests "*.ArchitectureRulesTest"
```

Green? Commit the file. Any future violation becomes a test failure.

---

## Configuration (optional)

Drop `archunit-rule-generator.yml` in the project root to customise behaviour:

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

The _generated test file_ depends on ArchUnit. Add this to the project where the test will live:

```xml
<!-- Maven -->
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <version>1.5.0</version>
    <scope>test</scope>
</dependency>
```

```kotlin
// Gradle (Kotlin DSL)
testImplementation("com.tngtech.archunit:archunit-junit5:1.5.0")
```

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

## How false positives are prevented

1. **Presence check** — a rule is only proposed when both sides of the constraint are found.
2. **Confidence threshold** — `LOW` confidence rules are filtered out by default.
3. **Excluded rule IDs** — permanently suppress any rule by its stable ID in config.
4. **Excluded packages** — skip generated, test, or legacy packages from scanning.
5. **Mandatory review step** — nothing is written to disk until you explicitly accept rules and click **Generate Test Class**.

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

## Roadmap

- [ ] Persist accept/ignore decisions between IDE sessions
- [ ] Hexagonal / ports-and-adapters architecture rule template
- [ ] Preview pane — see generated code before writing it
- [ ] Custom rule builder in the UI
- [ ] `@ArchIgnore` annotation scaffolding in generated tests
- [ ] Multi-module Maven/Gradle project support
- [ ] JetBrains Marketplace publication

---

## License

[Apache 2.0](LICENSE)

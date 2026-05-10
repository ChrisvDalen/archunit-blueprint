# ArchUnit Rule Generator — IntelliJ Plugin

> Scans your Java/Spring Boot project and proposes concrete ArchUnit rules. You review, accept or ignore, then generate a ready-to-run JUnit 5 test class with one click.

---

## Why

Writing good ArchUnit rules takes time and domain knowledge. Most teams end up with zero rules until someone discovers a controller that directly injects a repository. This plugin flips that: it analyses your project first and gives you a starting set of rules that fit your actual package structure.

---

## Quick start

1. Open your Java/Spring Boot project in IntelliJ IDEA 2024.3+.
2. Install the plugin (from the marketplace or via **Settings → Plugins → Install from disk**).
3. Open **Tools → Analyze Project for ArchUnit Rules** or click the *ArchUnit Rules* tool window on the right sidebar.
4. Click **Analyse Project**.
5. Review the suggested rules in the table.
6. Accept the rules you want, ignore the ones you don't.
7. Click **Generate Test Class**.
8. Add the generated file to version control and run it with `./mvnw test` or `./gradlew test`.

---

## Configuration

Drop an `archunit-rule-generator.yml` in the project root to customise behaviour:

```yaml
archunit-generator:
  base-package: com.example.myapp
  output:
    package: architecture
    class-name: ArchitectureRulesTest
  filter:
    minimum-confidence: MEDIUM
    excluded-packages:
      - com.example.myapp.generated
    excluded-rule-ids:
      - controller-only-services
  custom-layers:
    - name: EventHandler
      package-pattern: com.example.myapp.events..
```

See [`docs/archunit-rule-generator.yml.example`](docs/archunit-rule-generator.yml.example) for the full reference.

---

## Rules the plugin can suggest

| ID | Category | Condition |
|---|---|---|
| `controller-no-repository` | Layer Dependency | Controllers found in the project + Repositories found |
| `controller-only-services` | Layer Dependency | Controllers + Services found |
| `domain-no-spring` | Spring Stereotypes | A `domain` or `model` package detected |
| `repository-only-from-service` | Layer Dependency | Repositories + Services found |
| `openapi-no-domain-leak` | OpenAPI Isolation | Generated code detected |
| `layered-architecture` | Layer Dependency | ≥ 3 recognised layers detected |
| `dto-not-in-domain` | DTO Isolation | A `dto` package + domain layer detected |

---

## Example generated test

See [`docs/example-generated-test/ArchitectureRulesTest.java`](docs/example-generated-test/ArchitectureRulesTest.java).

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

## Building the plugin

```bash
# Bootstrap the Gradle wrapper (first time only)
gradle wrapper --gradle-version 8.11.1

# Run unit tests (no IntelliJ sandbox needed)
./gradlew test

# Build the plugin ZIP
./gradlew buildPlugin

# Run in a sandboxed IntelliJ instance
./gradlew runIde
```

---

## Project structure

```
src/
├── main/kotlin/com/github/chrisvdalen/archunitgen/
│   ├── model/          # RuleSuggestion, RuleCategory, Confidence, …
│   ├── scanner/        # PSI-based Spring + OpenAPI scanners
│   ├── engine/         # Pure rule-suggestion logic (no IntelliJ dependencies)
│   ├── generator/      # Java test-code generator + file writer
│   ├── config/         # YAML config loader
│   ├── service/        # IntelliJ project service (orchestration)
│   └── ui/             # Tool window, table model, action
└── test/kotlin/…
    ├── engine/         # RuleSuggestionEngineTest
    ├── generator/      # ArchUnitTestGeneratorTest
    └── config/         # ConfigLoaderTest
docs/
├── archunit-rule-generator.yml.example
└── example-generated-test/ArchitectureRulesTest.java
```

---

## MVP scope

- [x] Spring stereotype scanner via IntelliJ PSI
- [x] Package-layer auto-detection
- [x] OpenAPI / generated-code detection
- [x] Rule suggestion engine (7 rules)
- [x] Tool window UI with review table
- [x] Accept / ignore individual rules
- [x] Java 21 test-class generator
- [x] YAML configuration
- [x] Unit tests for engine and generator

## Roadmap

- [ ] Persist accept/ignore decisions between IDE sessions
- [ ] Hexagonal / ports-and-adapters architecture template
- [ ] Custom rule builder in the UI
- [ ] Preview pane showing the generated code before writing
- [ ] `@ArchIgnore` annotation support in generated tests
- [ ] Multi-module Maven/Gradle project support
- [ ] GitHub Actions workflow template
- [ ] JetBrains Marketplace publication pipeline

---

## False-positive prevention

1. **Presence check**: a rule is only proposed when both parties exist (e.g. controllers *and* repositories).
2. **Confidence threshold**: rules with `LOW` confidence are filtered out by default.
3. **excludedRuleIds**: permanently suppress a rule by ID in the YAML config.
4. **excludedPackages**: exclude generated or test packages from scanning.
5. **User review step**: nothing is written to disk until you explicitly click *Generate Test Class* after accepting rules.

---

## License

Apache 2.0

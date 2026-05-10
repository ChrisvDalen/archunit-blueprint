import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.3.0"
}

group = "com.github.chrisvdalen"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // YAML config parsing (bundled into plugin)
    implementation("org.yaml:snakeyaml:2.3")

    // Unit tests – no IntelliJ Platform required
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    intellijPlatform {
        intellijIdeaCommunity("2024.3.3")
        instrumentationTools()
        pluginVerifier()

        // Java PSI support
        bundledPlugin("com.intellij.java")

        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "ArchUnit Rule Generator"
        version = project.version.toString()

        description = """
            Analyzes Java/Spring Boot projects and automatically suggests and generates
            ArchUnit architecture test rules based on package structure, Spring stereotypes,
            and dependency analysis.
        """.trimIndent()

        ideaVersion {
            sinceBuild = "243"
            untilBuild = provider { null }
        }
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

kotlin {
    jvmToolchain(21)
}

tasks {
    test {
        useJUnitPlatform()
    }

    // Suppress default test run inside sandboxed IDE – we use plain JUnit for unit tests
    runIde {
        jvmArgs("-Xmx1g")
    }
}

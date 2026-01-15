// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.6" apply false
    id("com.github.ben-manes.versions") version "0.51.0"
}

val detektConfigFile = rootProject.file("config/detekt/detekt.yml")

subprojects {
    plugins.withId("org.jetbrains.kotlin.android") {
        apply(plugin = "io.gitlab.arturbosch.detekt")

        extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
            toolVersion = "1.23.6"
            config.setFrom(detektConfigFile)
            buildUponDefaultConfig = true
            allRules = false
            autoCorrect = false
        }
    }
}

tasks.register("osvScan") {
    group = "verification"
    description = "Runs osv-scanner if it is available on PATH."
    doLast {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val checkCommand = if (isWindows) listOf("where", "osv-scanner") else listOf("which", "osv-scanner")
        val checkResult = exec {
            commandLine(checkCommand)
            isIgnoreExitValue = true
        }
        if (checkResult.exitValue != 0) {
            throw GradleException(
                "osv-scanner not found on PATH. Install it and re-run :osvScan."
            )
        }
        exec {
            commandLine("osv-scanner", "--config", "config/osv-scanner.toml", "-r", ".")
        }
    }
}
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // Declared here with `apply false` so every AGP variant resolves from one
    // classpath entry. A subproject requesting `com.android.library` with its
    // own version fails: AGP is already on the classpath from the line below,
    // and Gradle cannot check the two for compatibility.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.detekt)
}

// ── Análisis estático ────────────────────────────────────────────────────────
// detekt is configured at the root so that a future `:domain` or `:data` module
// is covered the moment it is created, without copying this block.
subprojects {
    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))

        // Fall back to detekt's defaults for anything the config omits, so
        // upgrading detekt adds new rules instead of silently dropping them.
        buildUponDefaultConfig = true

        // Findings are reported, not fatal. A build that fails on style is a
        // build people learn to bypass; CI publishes the report instead.
        ignoreFailures = true

        basePath = rootProject.projectDir.absolutePath
    }

    dependencies {
        add("detektPlugins", rootProject.libs.detekt.formatting)
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "17"
        reports {
            // SARIF is the format GitHub code scanning ingests.
            sarif.required.set(true)
            html.required.set(true)
            xml.required.set(false)
            txt.required.set(false)
            md.required.set(false)
        }
        // Generated sources have no author to fix them.
        exclude("**/build/**", "**/generated/**")
    }

    tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
        jvmTarget = "17"
    }
}

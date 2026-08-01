pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Tien"
include(":app")

// Self-contained client for the UNSA aula virtual (Moodle). A separate module,
// not a package inside :app, so the boundary is enforced by the compiler: this
// module cannot reach into Tien's domain, and Tien can only use what it exports.
include(":dutic")

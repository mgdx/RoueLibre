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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Roue Libre"

// :core ne dépend d'aucune API Android. La séparation est structurelle et non
// conventionnelle : le compilateur refuse un import Android dans ce module,
// ce qui garantit que la logique métier reste testable sur la JVM sans
// émulateur, comme l'exige le SPEC §14.
include(":core")
include(":app")

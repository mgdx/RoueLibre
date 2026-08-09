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

// BRouter est consommé comme BUILD COMPOSITE plutôt qu'inclus module par
// module. Il apporte son propre `buildSrc`, ses conventions de compilation et
// ses dépôts de dépendances ; l'inclure directement les mêlerait aux nôtres et
// se heurterait notamment à `FAIL_ON_PROJECT_REPOS` ci-dessus. En build
// composite, il se construit chez lui et Gradle substitue simplement la
// coordonnée `org.btools:brouter-core` par le projet du sous-module.
//
// Le sous-module est épinglé sur l'étiquette v1.7.10 : la reproductibilité du
// build F-Droid en dépend. Ne pas y suivre `master`.
//
// Ne jamais créer de `local.properties` dans le sous-module : sa présence
// ferait entrer l'application Android de BRouter dans la construction, avec
// tout le poids qu'elle traîne.
includeBuild("third_party/brouter")

// :core ne dépend d'aucune API Android. La séparation est structurelle et non
// conventionnelle : le compilateur refuse un import Android dans ce module,
// ce qui garantit que la logique métier reste testable sur la JVM sans
// émulateur, comme l'exige le SPEC §14.
include(":core")
include(":app")

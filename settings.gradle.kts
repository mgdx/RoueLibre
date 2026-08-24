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

// No `foojay-resolver-convention` here, whatever the Android Studio template
// proposes: it resolves a missing Java toolchain by downloading a JDK, and
// F-Droid's scanner refuses a build able to fetch one — theirs runs with
// `org.gradle.java.installations.auto-download=false` anyway. The JDK 21 the
// modules ask for is the one their image already carries.
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Roue Libre"

// BRouter is consumed as a COMPOSITE BUILD rather than included module by
// module. It brings its own `buildSrc`, its own build conventions and its own
// dependency repositories; including it directly would mix them with ours and
// would run into `FAIL_ON_PROJECT_REPOS` above in particular. As a composite
// build, it builds at home and Gradle simply substitutes the coordinate
// `org.btools:brouter-core` with the submodule's project.
//
// The submodule is pinned to the tag v1.7.10: the reproducibility of the
// F-Droid build depends on it. Do not follow `master` there.
//
// Never create a `local.properties` inside the submodule: its presence would
// pull BRouter's own Android application into the build, with all the weight
// it drags along.
includeBuild("third_party/brouter")

// :core depends on no Android API. The separation is structural rather than
// conventional: the compiler refuses an Android import in this module, which
// guarantees that the business logic stays testable on the JVM without an
// emulator, as SPEC §14 requires.
include(":core")
include(":app")

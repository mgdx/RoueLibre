plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

// Pure business-logic module: GBFS feed parsing, journey algorithm, address
// resolution. No Android dependency, and therefore testable on the JVM without
// an emulator (SPEC §14).
//
// Binary compatibility targets Java 11, the application module's, so that the
// same code runs on a device at API 26.
kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        // The business code is audited; a warning left unhandled there is a
        // debt, not an acceptable nuisance (SPEC §14).
        allWarningsAsErrors.set(true)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

// The street-name normalisation rules live at the root of the repository, one
// file per language: they are the very files the indexing script reads, and the
// test verifies precisely that the two implementations draw the same result
// from them (SPEC §4.3).
tasks.withType<Test>().configureEach {
    systemProperty(
        "rouelibre.normalizationRules",
        rootProject.file("config/address-normalization").absolutePath,
    )
    // The catalogue as actually published: the test replays it rather than an
    // example written for the occasion, which proves that the generator and
    // the reader agree (SPEC §15).
    systemProperty(
        "rouelibre.cityCatalogue",
        rootProject.file("config/catalogue.json").absolutePath,
    )
    // The reference cases, one file per generated city: the test replays them
    // all, which extends the proof to every new network.
    systemProperty(
        "rouelibre.normalizationFixtures",
        file("src/test/resources/normalization_fixtures").absolutePath,
    )
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

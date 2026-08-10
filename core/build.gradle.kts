plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

// Module de logique métier pur : analyse des flux GBFS, algorithme de trajet,
// résolution d'adresses. Aucune dépendance Android, donc testable sur la JVM
// sans émulateur (SPEC §14).
//
// La compatibilité binaire vise Java 11, celle du module applicatif, pour que
// le même code s'exécute sur un appareil à l'API 26.
kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        // Le code métier est audité ; un avertissement non traité y est une
        // dette, pas une nuisance acceptable (SPEC §14).
        allWarningsAsErrors.set(true)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

// Les règles de normalisation des noms de voies vivent à la racine du dépôt :
// c'est le même fichier que lit le script d'indexation, et le test vérifie
// justement que les deux implémentations en tirent le même résultat (SPEC §4.3).
tasks.withType<Test>().configureEach {
    systemProperty(
        "rouelibre.normalizationRules",
        rootProject.file("config/address_normalization.json").absolutePath,
    )
    // Les cas de référence, un fichier par ville générée : le test les rejoue
    // tous, ce qui étend la preuve à chaque nouveau réseau.
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

import java.util.Properties

plugins {
    // AGP 9 embarque le support Kotlin : appliquer en plus
    // « org.jetbrains.kotlin.android » est désormais une erreur.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "io.github.mgdx.rouelibre"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.mgdx.rouelibre"
        // API 26 : java.time nativement disponible, donc pas de désucrage à
        // configurer, icônes adaptatives, et surtout une pile TLS à jour
        // (SPEC §3).
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        // Alpha : l'application fait le tour de son sujet — carte, recherche
        // d'adresses, itinéraire porte-à-porte, et trois agglomérations au
        // choix — mais rien n'a encore été publié à télécharger, et les écrans
        // du premier lancement n'ont jamais tourné sur un appareil.
        // Voir CHANGELOG.md.
        versionName = "0.2.0-alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Aucune sauvegarde automatique vers le cloud (SPEC §8).
        manifestPlaceholders["allowBackup"] = "false"
    }

    androidResources {
        // Déclare le français comme seule langue fournie. Sans cela, Android
        // ne sait pas quelle langue contient `values/` : sur un appareil en
        // anglais il servait les textes français avec des dates anglaises.
        // Élague au passage les traductions des bibliothèques, qui pèsent
        // plus que les nôtres.
        localeFilters += listOf("fr", "en")
    }

    buildFeatures {
        viewBinding = true
        // Pas de Compose : son poids est incompatible avec la contrainte de
        // taille C4 (SPEC §3).
        buildConfig = true
    }

    /**
     * Signature des compilations de release faites ici.
     *
     * F-Droid recompile et signe lui-même : cette clé ne sert donc jamais à ce
     * qui sera publié là-bas. Elle ne sert qu'aux versions que l'on installe
     * soi-même pour les essayer, comme cette alpha.
     *
     * Le fichier `keystore.properties` est ignoré par Git et n'existe
     * généralement pas. Sans lui, la release est signée par la clé de debug —
     * suffisant pour installer un essai, et surtout **aucune clé n'est
     * inventée en douce** : le jour où le projet aura sa clé de publication,
     * ce sera une décision prise, pas un fichier apparu tout seul.
     */
    val signingProperties = rootProject.file("keystore.properties")
    signingConfigs {
        create("selfSigned") {
            if (signingProperties.exists()) {
                val values = Properties()
                signingProperties.inputStream().use { values.load(it) }
                storeFile = rootProject.file(values.getProperty("storeFile"))
                storePassword = values.getProperty("storePassword")
                keyAlias = values.getProperty("keyAlias")
                keyPassword = values.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (signingProperties.exists()) {
                signingConfigs.getByName("selfSigned")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    // Les bibliothèques natives de MapLibre ne doivent pas être livrées quatre
    // fois dans le même APK (SPEC §3). Le plafond de 12 Mo est par
    // architecture, celui de 15 Mo pour l'APK universel.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    // F-Droid recompile depuis les sources et vérifie que le résultat
    // correspond : le bloc de dépendances signé par AGP, non reproductible,
    // n'a pas sa place dans l'artefact.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        jniLibs {
            // Compresse les bibliothèques natives dans l'APK. Android les
            // extrait alors à l'installation, ce qui occupe un peu plus de
            // place sur l'appareil mais divise par deux le téléchargement —
            // treize mégaoctets de MapLibre en arm64. Sur un dépôt comme
            // F-Droid, c'est le poids du téléchargement qui compte.
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.kotlin_module",
                "DebugProbesKt.bin",
            )
        }
    }

    lint {
        // Aucun avertissement toléré en release (SPEC §14).
        warningsAsErrors = true
        abortOnError = true
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

ksp {
    // Le schéma de la base est versionné dans le dépôt : c'est ce qui rend
    // les migrations relisibles et vérifiables par un contributeur.
    arg("room.schemaLocation", "$projectDir/schemas")
}

/**
 * Copie les fichiers de configuration partagés dans les ressources de l'APK.
 *
 * La configuration de ville est la source unique de tout ce qui est propre à
 * une agglomération (SPEC §15), et les règles de normalisation des noms de
 * voies sont partagées avec le script d'indexation (SPEC §4.3). Les deux
 * vivent à la racine du dépôt. Les copier au moment du build évite d'en
 * maintenir un second exemplaire, qui finirait par diverger — et une
 * divergence sur les règles de normalisation rendrait des rues introuvables.
 */
abstract class CopySharedConfigurationTask : DefaultTask() {

    /**
     * Les configurations de ville, une par agglomération servie.
     *
     * Toutes sont livrées, aucune n'est privilégiée : l'application ne connaît
     * pas de ville par défaut, elle en propose une d'après la position et
     * retient celle qu'on a choisie (SPEC §15).
     */
    @get:InputDirectory
    abstract val cityConfigurations: DirectoryProperty

    /**
     * L'index de ces villes, produit par `tools/build_catalogue.py`.
     *
     * Livré comme secours : le catalogue publié est téléchargeable et peut
     * citer des villes plus récentes, mais un premier lancement sans réseau
     * doit montrer une liste plutôt qu'un écran vide.
     */
    @get:InputFile
    abstract val cityCatalogue: RegularFileProperty

    @get:InputFile
    abstract val normalizationRules: RegularFileProperty

    /**
     * Les notes de version des métadonnées F-Droid.
     *
     * Elles sont la SOURCE UNIQUE de ce que l'écran « nouveautés » affiche
     * (SPEC §7.10) : les recopier dans les ressources en ferait deux versions
     * à tenir, et la seconde finirait par mentir. Le dossier peut être absent
     * d'un clone partiel, auquel cas l'écran n'a simplement rien à montrer.
     */
    @get:InputDirectory
    @get:Optional
    abstract val changelogs: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun copyConfiguration() {
        val target = outputDirectory.get().asFile
        target.mkdirs()
        cityCatalogue.get().asFile.copyTo(target.resolve("catalogue.json"), overwrite = true)

        // Chaque configuration est rangée sous l'identifiant de son réseau, et
        // non sous le nom de son fichier : c'est cet identifiant que porte le
        // catalogue, et l'application n'a alors rien à deviner pour retrouver
        // la configuration d'une ville qu'on vient de choisir.
        val cities = target.resolve("cities")
        cities.deleteRecursively()
        cities.mkdirs()
        cityConfigurations.get().asFile.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.forEach { configuration ->
                val document = groovy.json.JsonSlurper().parse(configuration)
                val network = (document as Map<*, *>)["network"] as Map<*, *>
                val identifier = network["id"] as String
                configuration.copyTo(cities.resolve("$identifier.json"), overwrite = true)
            }

        normalizationRules.get().asFile
            .copyTo(target.resolve("address_normalization.json"), overwrite = true)

        val notes = target.resolve("changelogs")
        notes.deleteRecursively()
        notes.mkdirs()
        changelogs.orNull?.asFile?.listFiles()
            ?.filter { it.isFile && it.extension == "txt" }
            ?.forEach { it.copyTo(notes.resolve(it.name), overwrite = true) }
    }
}

androidComponents {
    onVariants { variant ->
        val copyTask = tasks.register<CopySharedConfigurationTask>(
            "copySharedConfigurationFor${variant.name.replaceFirstChar { it.uppercase() }}",
        ) {
            cityConfigurations.set(rootProject.file("config/cities"))
            cityCatalogue.set(rootProject.file("config/catalogue.json"))
            normalizationRules.set(rootProject.file("config/address_normalization.json"))
            val notes = rootProject.file("fastlane/metadata/android/fr/changelogs")
            if (notes.isDirectory) changelogs.set(notes)
        }
        variant.sources.assets?.addGeneratedSourceDirectory(
            copyTask,
            CopySharedConfigurationTask::outputDirectory,
        )
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)
    implementation(libs.maplibre)
    implementation(libs.brouter.core)
    implementation(libs.brouter.mapaccess)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

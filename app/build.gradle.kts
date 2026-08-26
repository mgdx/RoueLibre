import com.android.build.api.variant.FilterConfiguration.FilterType.ABI
import java.util.Properties

plugins {
    // AGP 9 carries Kotlin support: applying "org.jetbrains.kotlin.android"
    // on top of it is now an error.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
}

/**
 * What each architecture adds to ten times the base version code.
 *
 * A repository cannot hold two APKs of one application under the same version
 * code — F-Droid publishes one file per architecture and needs to tell them
 * apart. The order is the usual one, least capable first, so that a device
 * able to run several is offered the highest.
 *
 * Ten times the base leaves the units to the architecture and keeps the
 * numbering readable: version 4 gives 41 to 44, version 5 gives 51 to 54. Each
 * architecture's series therefore rises with the releases, which is the only
 * thing Android asks of it.
 *
 * Only the manifest's number changes: `BuildConfig.VERSION_CODE` stays at the
 * base, and the what's-new screen keys its notes on it (SPEC §7.10). That is
 * deliberate — a release publishes one set of notes, not one per architecture —
 * so `changelogs/<base>.txt` is the file to write, and the two numbers are
 * meant to differ.
 */
val architectureVersionCodes = mapOf(
    "armeabi-v7a" to 1,
    "x86" to 2,
    "x86_64" to 3,
    "arm64-v8a" to 4,
)

android {
    namespace = "io.github.mgdx.rouelibre"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.mgdx.rouelibre"
        // API 26: java.time available natively, so no desugaring to set up,
        // adaptive icons, and above all an up-to-date TLS stack (SPEC §3).
        minSdk = 26
        targetSdk = 37
        // Written as a number rather than read from a constant: F-Droid's
        // update checker greps this very line for a literal, and a name here
        // leaves it unable to tell one release from the next. The
        // architectures derive their own codes from it below, so the value is
        // still written once.
        versionCode = 7
        // A patch: one fix and nothing added. MapLibre is shipped as its
        // OpenGL ES artefact rather than the default one, whose manifest
        // marks Vulkan 1.0 as a required feature and barred installation on
        // API 26+ devices without it — against the promise of minSdk 26.
        // See CHANGELOG.md.
        versionName = "1.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // No automatic backup to the cloud (SPEC §8).
        manifestPlaceholders["allowBackup"] = "false"
    }

    /**
     * The NDK that strips the native libraries, named rather than inferred.
     *
     * The application compiles no native code of its own, so this is not about
     * building: it is about **stripping**. AGP removes the symbols from the
     * native libraries its dependencies ship — DataStore's counter goes from
     * 8,432 bytes to 5,916 — and it does so with whichever NDK it finds. Two
     * machines carrying different ones write two different files, which is
     * enough to make the APK unreproducible: F-Droid rebuilt version 1.0.0 and
     * that single file was all that differed.
     *
     * Naming the version settles it, and the F-Droid recipe names the same one
     * in its `ndk` field, so their build strips with the tool this one did.
     * Moving it is therefore a decision taken on both sides at once.
     *
     * MapLibre's library needs none of this — it arrives already stripped, so
     * stripping it again changes nothing.
     */
    ndkVersion = "28.2.13676358"

    androidResources {
        // Declares which languages are supplied. Without it, Android does not
        // know what language `values/` holds: on an English device it served
        // the French texts with English dates. It also prunes the libraries'
        // translations along the way, which weigh more than ours.
        //
        // The languages beyond French and English are started files, whose
        // strings are still the English ones. Listing them here is what makes
        // a device set to German serve `values-de/` — without it the folder
        // would be dropped from the APK and the file would be dead weight.
        // Every language a translation exists in, started or finished: a folder
        // absent from this list is dropped from the APK (SPEC §9). The set
        // follows the catalogue — one entry per language spoken where a
        // network is served — plus the widely spoken ones the project started
        // with.
        localeFilters += listOf(
            "en", "fr", "ar", "de", "es", "it", "nl", "pl", "pt", "zh",
            "bs", "ca", "cs", "da", "el", "eu", "fi", "gl", "hr", "hu",
            "ja", "lt", "lv", "nb", "ro", "sk", "sl", "sq", "sr", "sv",
            "tr",
        )
    }

    buildFeatures {
        viewBinding = true
        // No Compose: its weight is incompatible with the size constraint C4
        // (SPEC §3).
        buildConfig = true
    }

    /**
     * Signing of the release builds made here.
     *
     * F-Droid rebuilds and signs for itself: this key therefore never signs
     * what will be published there. It only signs the versions one installs
     * oneself to try them out, like this alpha.
     *
     * The `keystore.properties` file is ignored by Git and usually does not
     * exist. Without it, the release is signed by the debug key — enough to
     * install a trial build, and above all **no key is invented on the sly**:
     * the day the project has its publishing key, that will be a decision
     * taken, not a file that appeared by itself.
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
            // The v3 scheme carries the certificate's lineage, and that is
            // what would let the project change key one day without losing
            // everybody who installed it: an APK signed under v3 can show the
            // new key was itself signed by the old one. The v2 scheme alone,
            // which is all Android needs to install from 7.0 on and therefore
            // all AGP enables by default, offers no such way back — a key lost
            // or compromised would end the application's upgrade path.
            enableV3Signing = true
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

    // MapLibre's native libraries must not ship four times in the same APK
    // (SPEC §3). The 12 MB ceiling is per architecture, the 15 MB one is for
    // the universal APK.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    // F-Droid rebuilds from source and checks that the result matches: the
    // dependency block signed by AGP, which is not reproducible, has no place
    // in the artefact.
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
            // Compresses the native libraries inside the APK. Android then
            // extracts them at install time, which takes a little more room on
            // the device but halves the download — thirteen megabytes of
            // MapLibre on arm64. On a repository like F-Droid, it is the
            // download weight that counts.
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
        // No warning tolerated in release (SPEC §14).
        warningsAsErrors = true
        abortOnError = true
    }
}

// The glyphs the map draws its labels with. The test reads the very directory
// tools/build_glyphs.js writes, because a range missing there does not blank a
// character but the whole tile that needed it (SPEC §4.2).
tasks.withType<Test>().configureEach {
    systemProperty(
        "rouelibre.glyphs",
        file("src/main/assets/glyphs").absolutePath,
    )
    // The languages Android's per-application settings offer. That XML is the
    // one place the list of translations is written a second time — Android
    // reads it from the resources, so it cannot be computed — and the test
    // reads the file itself to check the two still agree (SPEC §9).
    systemProperty(
        "rouelibre.locales",
        file("src/main/res").absolutePath,
    )
}

kotlin {
    // Java 21, because that is what F-Droid's build image carries — Debian
    // trixie's `default-jdk-headless` — and it builds with toolchain
    // auto-provisioning turned off, so a version it does not already have is
    // not fetched but refused. This says which compiler runs; what the
    // application ships is decided by `jvmTarget` below, which stays at 11 for
    // API 26.
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

ksp {
    // The database schema is versioned in the repository: that is what makes
    // migrations re-readable and checkable by a contributor.
    arg("room.schemaLocation", "$projectDir/schemas")
}

/**
 * Copies the shared configuration files into the APK's assets.
 *
 * The city configuration is the single source of everything specific to a
 * conurbation (SPEC §15), and the street-name normalisation rules are shared
 * with the indexing script (SPEC §4.3). Both live at the root of the
 * repository. Copying them at build time avoids maintaining a second copy,
 * which would end up diverging — and a divergence in the normalisation rules
 * would make streets unfindable.
 */
abstract class CopySharedConfigurationTask : DefaultTask() {

    /**
     * The city configurations, one per conurbation served.
     *
     * All of them ship, none is favoured: the application knows no default
     * city, it proposes one from the position and remembers the one chosen
     * (SPEC §15).
     */
    @get:InputDirectory
    abstract val cityConfigurations: DirectoryProperty

    /**
     * The index of those cities, produced by `tools/build_catalogue.py`.
     *
     * Shipped as a fallback: the published catalogue is downloadable and may
     * name more recent cities, but a first launch with no network must show a
     * list rather than an empty screen.
     */
    @get:InputFile
    abstract val cityCatalogue: RegularFileProperty

    /**
     * The street-name normalisation rules, one file per language.
     *
     * All of them ship: which one applies is decided by the address index
     * being searched, which says what it was built with (SPEC §15.1), and a
     * user who installs a second city must not have to update the application
     * to be able to search in it.
     */
    @get:InputDirectory
    abstract val normalizationRules: DirectoryProperty

    /**
     * The F-Droid metadata, whose release notes the application reads.
     *
     * They are the SINGLE SOURCE of what the "what's new" screen shows
     * (SPEC §7.10): copying them into the resources would make two versions to
     * keep, and the second would end up lying. Every locale published there is
     * carried into the assets, so the screen can show its notes in the
     * language the interface is speaking. The folder may be absent from a
     * partial clone, in which case the screen simply has nothing to show.
     */
    @get:InputDirectory
    @get:Optional
    abstract val storeMetadata: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun copyConfiguration() {
        val target = outputDirectory.get().asFile
        target.mkdirs()
        cityCatalogue.get().asFile.copyTo(target.resolve("catalogue.json"), overwrite = true)

        // Each configuration is filed under its network identifier rather
        // than under its file name: that identifier is the one the catalogue
        // carries, so the application has nothing to guess when looking up the
        // configuration of a city just chosen.
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

        val rules = target.resolve("address-normalization")
        rules.deleteRecursively()
        rules.mkdirs()
        normalizationRules.get().asFile.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.forEach { it.copyTo(rules.resolve(it.name), overwrite = true) }

        // One folder per locale published — changelogs/en-US, changelogs/fr —
        // keeping the store's own directory names, which is what lets the
        // screen match them against the device's language.
        val notes = target.resolve("changelogs")
        notes.deleteRecursively()
        notes.mkdirs()
        storeMetadata.orNull?.asFile?.listFiles()
            ?.filter { it.isDirectory }
            ?.forEach { locale ->
                val published = locale.resolve("changelogs").listFiles()
                    .orEmpty()
                    .filter { it.isFile && it.extension == "txt" }
                if (published.isEmpty()) return@forEach
                val target = notes.resolve(locale.name).apply { mkdirs() }
                published.forEach { it.copyTo(target.resolve(it.name), overwrite = true) }
            }
    }
}

androidComponents {
    onVariants { variant ->
        // The architecture APKs take their own version code (see
        // `architectureVersionCodes`). The universal one carries no ABI
        // filter, so it keeps the base and is left alone here.
        val base = android.defaultConfig.versionCode!!
        variant.outputs.forEach { output ->
            val architecture = output.filters
                .firstOrNull { it.filterType == ABI }
                ?.identifier
                ?: return@forEach
            output.versionCode.set(
                base * 10 + architectureVersionCodes.getValue(architecture),
            )
        }

        val copyTask = tasks.register<CopySharedConfigurationTask>(
            "copySharedConfigurationFor${variant.name.replaceFirstChar { it.uppercase() }}",
        ) {
            cityConfigurations.set(rootProject.file("config/cities"))
            cityCatalogue.set(rootProject.file("config/catalogue.json"))
            normalizationRules.set(rootProject.file("config/address-normalization"))
            val metadata = rootProject.file("fastlane/metadata/android")
            if (metadata.isDirectory) storeMetadata.set(metadata)
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

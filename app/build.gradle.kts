import com.android.build.api.variant.FilterConfiguration.FilterType.ABI
import java.io.File
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
/**
 * Whether this invocation builds an app bundle rather than APKs.
 *
 * The two cannot be built together: asked for a bundle while the ABI splits
 * below are on, AGP 9 stops at `buildReleasePreBundle` with "Multiple
 * shrunk-resources files found", having shrunk the resources once per
 * architecture and finding no single file to put in the bundle
 * (issuetracker.google.com/402800800). Its own advice is to turn the splits
 * off for that build.
 *
 * Nothing is lost by doing so: a bundle already carries every architecture and
 * Google Play cuts it per device, which is what the splits do for the APKs
 * published everywhere else. So the splits stay on for `assembleRelease`, the
 * five files of SPEC §3 and of the F-Droid recipe, and step aside for
 * `bundleRelease` alone.
 *
 * Read from the task names rather than from a property, so that no release
 * command has to be remembered differently from the one in `docs/release.md`.
 */
val buildsAnAppBundle = gradle.startParameter.taskNames.any {
    it.contains("bundle", ignoreCase = true)
}

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
        versionCode = 12
        // The bikes a network leaves outside its stations are drawn on
        // request, and a journey may set off from one. A station's sheet says
        // how full the batteries standing at it are. Three American networks
        // join the catalogue, admitted by a dock rule that no longer asks a
        // producer to publish a capacity it measures live. The APK sheds half
        // a megabyte it was carrying for nothing. And a test campaign's
        // eleven anomalies are closed, the sideways search screens and the
        // Arabic titles among them. See CHANGELOG.md.
        versionName = "1.4.0"

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
        // The list of public suffixes OkHttp ships as an asset — a hundred and
        // thirty kilobytes of it. OkHttp reads it in two places only: the
        // cookie jar, and `HttpUrl.topPrivateDomain()`. The application
        // installs no cookie jar, so the default one keeps no cookie and never
        // asks a domain its suffix, and R8 confirms it by removing every caller
        // of the reader from the minified code.
        //
        // Should a request ever need cookies, or the private domain of a URL,
        // OkHttp would fail on its first call and this line is what to undo.
        // An asset of a library cannot be dropped any other way: packaging
        // excludes filter Java resources, not assets.
        ignoreAssetsPatterns += "PublicSuffixDatabase.list"

        // Declares which languages are supplied. Without it, Android does not
        // know what language `values/` holds: on an English device it served
        // the French texts with English dates. It also prunes the libraries'
        // translations along the way, which weigh more than ours.
        //
        // Listing a language here is what makes a device set to German serve
        // `values-de/` — without it the folder is dropped from the APK and the
        // file becomes dead weight. Every language a translation exists in
        // therefore belongs here (SPEC §9).
        //
        // They are real translations, not English text waiting for one: every
        // file was compared string by string against `values/` on 11 September
        // 2026, and the closest to English is Danish, at thirteen per cent of
        // shared values — the application's name, "km", a dash, a positional
        // format. The set
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
     * Two keys, and they do not do the same work.
     *
     * The **publishing key** is the identity of Roue Libre: the APKs of the
     * releases page carry it, and F-Droid carries it too — its recipe names
     * the certificate in `AllowedAPKSigningKeys` and checks its own rebuild
     * against the published file rather than signing one of its own. So the
     * two channels are one installation, and an update passes from either to
     * either.
     *
     * The **upload key** is not an identity at all. Google Play signs what it
     * serves with a key it holds itself, and this one only proves who is
     * uploading. That is why it is a separate key: it is replaceable — a lost
     * upload key is a ticket to Google, where a lost publishing key would end
     * the upgrade path of every installation outside Play — and the fewer
     * places the publishing key is used, the better.
     *
     * Both are read from `keystore.properties`, which Git ignores and which
     * usually does not exist. Without it the release is signed by the debug
     * key — enough to install a trial build, and above all **no key is
     * invented on the sly**.
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
        create("playUpload") {
            if (signingProperties.exists()) {
                val values = Properties()
                signingProperties.inputStream().use { values.load(it) }
                // Absent from the file on a machine that never uploads to
                // Play, and then this configuration signs nothing: the bundle
                // falls back on the publishing key below.
                values.getProperty("uploadStoreFile")?.let {
                    storeFile = rootProject.file(it)
                    storePassword = values.getProperty("uploadStorePassword")
                    keyAlias = values.getProperty("uploadKeyAlias")
                    keyPassword = values.getProperty("uploadKeyPassword")
                }
            }
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
            // Which key signs depends on where the artefact is going. A
            // bundle goes to Google Play alone, and to Play one uploads under
            // the upload key; everything else — the five APKs of the releases
            // page, which F-Droid verifies against — is signed by the
            // publishing key. Should the upload key be missing from
            // `keystore.properties`, the bundle is signed by the publishing
            // key, and Play would then register that certificate as the one it
            // expects uploads from: it still works, and it spends the key in
            // one more place than it needs to be.
            signingConfig = when {
                !signingProperties.exists() -> signingConfigs.getByName("debug")
                buildsAnAppBundle && signingConfigs.getByName("playUpload").storeFile != null ->
                    signingConfigs.getByName("playUpload")
                else -> signingConfigs.getByName("selfSigned")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    // MapLibre's native libraries must not ship four times in the APK anybody
    // installs (SPEC §3). The 12 MB ceiling is per architecture; the universal
    // APK, which does stack the four, is held to 30 MB instead.
    splits {
        abi {
            isEnable = !buildsAnAppBundle
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
                // Eight AndroidX artefacts each ship the Apache 2.0 text, byte
                // for byte the same 10,175 of them, and a ninth ships
                // protobuf's BSD notice. The licence still has to travel with
                // the application, so the two texts are carried once, in
                // `assets/licences/`, where the licences screen already reads
                // MapLibre's, BRouter's and the fonts'. Redistribution keeps
                // its notices and the APK loses eight copies of them.
                "/META-INF/androidx/**",
                // One file per AndroidX artefact holding its version number,
                // six to thirteen bytes each. Google's own SDKs read them to
                // report what an application was built with; nothing here does,
                // and there is no telemetry to serve (SPEC §2, C3). Sixty-nine
                // directory entries cost more than the numbers they hold.
                "/META-INF/*.version",
                // The metadata Kotlin's reflection reads to rebuild the types
                // of the standard library. Full reflection is not on the
                // release classpath, and `kotlin_builtins` appears nowhere in
                // the minified code: nothing shipped can open these files.
                "/kotlin/**",
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

    /**
     * The version code of the build, which bounds the notes worth shipping.
     *
     * The store publishes one note per architecture version code — 41 to 44
     * repeat word for word what 4 says — while the what's-new screen only ever
     * reads the base codes (see `architectureVersionCodes`). Carrying the
     * repeats would ship every note five times over, in every language.
     */
    @get:Input
    abstract val versionCode: Property<Int>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    /**
     * The same document with every `$comment` key removed, however deep.
     *
     * The recursion is the whole point: a city configuration is headed by one
     * such block, then carries one more inside each section a contributor may
     * edit — the fleet, the bounding box, the centring, the network. Filtering
     * the top level alone left five sixths of them in place, and the 337
     * configurations shipped weighed 704 KB instead of 241.
     */
    private fun stripComments(node: Any?): Any? = when (node) {
        is Map<*, *> ->
            node.filterKeys { it != "\$comment" }
                .mapValues { stripComments(it.value) }

        is List<*> -> node.map { stripComments(it) }
        else -> node
    }

    /**
     * Writes a JSON file into the assets stripped of what only a human reads.
     *
     * The files under `config/` are laid out for a contributor: indented, and
     * commented. The application reads none of it — every reader of these
     * files ignores the keys it does not know — and repeated over every
     * configuration shipped it costs half a megabyte of APK. The source keeps
     * its layout, the copy loses it, so the file one edits stays the readable
     * one.
     */
    private fun copyStripped(source: File, destination: File) {
        val document = groovy.json.JsonSlurper().parse(source)
        destination.writeText(groovy.json.JsonOutput.toJson(stripComments(document)))
    }

    /**
     * Writes the city configurations as one file, and an index into it.
     *
     * They used to ship one file per city, filed under the network identifier
     * the catalogue carries. Three hundred and thirty-seven files of the same
     * shape, and each one an entry of the APK's zip compressed on its own,
     * blind to the three hundred and thirty-six others it repeats almost word
     * for word: 247 KB where a single stream holding the same bytes takes 71.
     *
     * So the configurations are concatenated, in the order of their
     * identifiers, and `cities-index.json` says where each one begins and how
     * far it runs. The index is small and read at startup to know which cities
     * this build serves; the big file is never read whole, only the slice a
     * chosen city needs.
     */
    private fun writeCityConfigurations(target: File) {
        val configurations = cityConfigurations.get().asFile.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.map { source ->
                val document = groovy.json.JsonSlurper().parse(source)
                val network = (document as Map<*, *>)["network"] as Map<*, *>
                network["id"] as String to groovy.json.JsonOutput.toJson(stripComments(document))
            }
            // Sorted so that two builds of the same sources write the same two
            // files, byte for byte, as F-Droid's rebuild asks (SPEC §2, C1).
            ?.sortedBy { it.first }
            .orEmpty()

        val index = linkedMapOf<String, List<Int>>()
        val stream = target.resolve("cities.json").outputStream().buffered()
        stream.use { output ->
            var offset = 0
            configurations.forEach { (identifier, document) ->
                val bytes = document.toByteArray(Charsets.UTF_8)
                output.write(bytes)
                index[identifier] = listOf(offset, bytes.size)
                offset += bytes.size
            }
        }
        target.resolve("cities-index.json")
            .writeText(groovy.json.JsonOutput.toJson(index))
    }

    @TaskAction
    fun copyConfiguration() {
        val target = outputDirectory.get().asFile
        // Emptied rather than written over: the task keeps its output between
        // runs, so a file it no longer produces would go on shipping. The day
        // the configurations became one stream, the folder of three hundred and
        // thirty-seven files stayed in the APK beside it, and the assets grew
        // instead of shrinking.
        target.deleteRecursively()
        target.mkdirs()
        copyStripped(cityCatalogue.get().asFile, target.resolve("catalogue.json"))

        writeCityConfigurations(target)

        val rules = target.resolve("address-normalization")
        rules.mkdirs()
        normalizationRules.get().asFile.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.forEach { copyStripped(it, rules.resolve(it.name)) }

        // One folder per locale published — changelogs/en-US, changelogs/fr —
        // keeping the store's own directory names, which is what lets the
        // screen match them against the device's language.
        val notes = target.resolve("changelogs")
        notes.mkdirs()
        storeMetadata.orNull?.asFile?.listFiles()
            ?.filter { it.isDirectory }
            ?.forEach { locale ->
                val published = locale.resolve("changelogs").listFiles()
                    .orEmpty()
                    .filter { it.isFile && it.extension == "txt" }
                    // The same filter the screen applies when it reads them:
                    // beyond the base version code lie the architecture codes,
                    // which repeat notes already shipped.
                    .filter {
                        val code = it.nameWithoutExtension.toIntOrNull()
                        code != null && code <= versionCode.get()
                    }
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
            versionCode.set(base)
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

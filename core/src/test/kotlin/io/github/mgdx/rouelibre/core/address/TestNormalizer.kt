package io.github.mgdx.rouelibre.core.address

import io.github.mgdx.rouelibre.core.Outcome
import java.io.File

/**
 * The normalisers built from the repository's **real** rule files.
 *
 * The tests do not give themselves a rule set of their own: what they verify is
 * the behaviour of the ones that will ship in the APK and be read by the
 * indexing script. The directory is supplied by the build (see
 * `core/build.gradle.kts`), so as not to depend on the working directory.
 */
internal object TestRules {

    /** The directory holding one file of rules per language. */
    val directory: File
        get() = File(
            checkNotNull(System.getProperty("rouelibre.normalizationRules")) {
                "normalisation rules directory not supplied by the build"
            },
        )

    /** The languages whose street vocabulary is written down. */
    fun languages(): List<String> = directory.listFiles().orEmpty()
        .filter { it.isFile && it.extension == "json" }
        .map { it.nameWithoutExtension }
        .sorted()

    /** The normaliser of one language, as it will be read on the device. */
    fun of(language: String): AddressNormalizer {
        val file = File(directory, "$language.json")
        check(file.isFile) { "no normalisation rules for \"$language\"" }
        return when (val outcome = AddressNormalizerReader.read(file.readText())) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> error("unreadable rules for \"$language\": ${outcome.error}")
        }
    }
}

/**
 * The French rules, which the tests written before there was more than one
 * language read, and which the French examples in them still belong to.
 */
internal fun testNormalizer(): AddressNormalizer = TestRules.of("fr")

package io.github.mgdx.rouelibre.core.address

import io.github.mgdx.rouelibre.core.Outcome
import java.io.File

/**
 * The normaliser built from the repository's **real** rules file.
 *
 * The tests do not give themselves a rule set of their own: what they verify is
 * the behaviour of the one that will ship in the APK and be read by the
 * indexing script. The path is supplied by the build (see
 * `core/build.gradle.kts`), so as not to depend on the working directory.
 */
internal fun testNormalizer(): AddressNormalizer {
    val path = checkNotNull(System.getProperty("rouelibre.normalizationRules")) {
        "normalisation rules path not supplied by the build"
    }
    return when (val outcome = AddressNormalizerReader.read(File(path).readText())) {
        is Outcome.Success -> outcome.value
        is Outcome.Failure -> error("unreadable normalisation rules: ${outcome.error}")
    }
}

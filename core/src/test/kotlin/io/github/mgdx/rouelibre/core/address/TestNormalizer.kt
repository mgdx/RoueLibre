package io.github.mgdx.rouelibre.core.address

import io.github.mgdx.rouelibre.core.Outcome
import java.io.File

/**
 * Le normalisateur construit à partir du **vrai** fichier de règles du dépôt.
 *
 * Les tests ne se donnent pas un jeu de règles à eux : ce qu'ils vérifient,
 * c'est le comportement de celui qui sera embarqué dans l'APK et lu par le
 * script d'indexation. Le chemin est fourni par le build (voir
 * `core/build.gradle.kts`), pour ne pas dépendre du répertoire d'exécution.
 */
internal fun testNormalizer(): AddressNormalizer {
    val path = checkNotNull(System.getProperty("rouelibre.normalizationRules")) {
        "chemin des règles de normalisation non fourni par le build"
    }
    return when (val outcome = AddressNormalizerReader.read(File(path).readText())) {
        is Outcome.Success -> outcome.value
        is Outcome.Failure -> error("règles de normalisation illisibles : ${outcome.error}")
    }
}

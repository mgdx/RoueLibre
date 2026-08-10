package io.github.mgdx.rouelibre.ui

import android.content.Intent
import io.github.mgdx.rouelibre.core.intent.PlaceRequest
import io.github.mgdx.rouelibre.core.intent.findPlaceInText
import io.github.mgdx.rouelibre.core.intent.parsePlaceUri

/**
 * Ce qu'une intention entrante demande, s'il y a lieu (SPEC §7.8).
 *
 * L'analyse elle-même vit dans le module métier, testable sur la JVM : ici, il
 * ne reste qu'à savoir où lire, selon que le lieu arrive par une URI ou par du
 * texte partagé.
 *
 * @return le lieu demandé, ou `null` si cette intention n'en porte pas.
 */
fun Intent.toPlaceRequest(): PlaceRequest? = when (action) {
    Intent.ACTION_VIEW -> data?.toString()?.let(::parsePlaceUri)

    Intent.ACTION_SEND -> {
        // Le sujet porte parfois le lieu et le corps un commentaire ; on prend
        // le premier des deux qui donne quelque chose d'exploitable.
        val body = getStringExtra(Intent.EXTRA_TEXT)
        val subject = getStringExtra(Intent.EXTRA_SUBJECT)
        listOfNotNull(body, subject)
            .firstNotNullOfOrNull { text -> findPlaceInText(text) }
    }

    else -> null
}

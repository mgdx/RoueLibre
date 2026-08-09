package io.github.mgdx.rouelibre.core.station

import java.text.Normalizer

/**
 * Filtrage de la liste des stations par leur nom.
 *
 * À ne pas confondre avec la recherche d'adresses du SPEC §4.3, qui interroge
 * un index hors ligne de centaines de milliers de numéros de voirie. Ici il
 * s'agit seulement de retrouver une station parmi les quelques centaines déjà
 * en mémoire, ce qui ne demande ni index ni anti-rebond : le parcours est
 * immédiat et peut avoir lieu à chaque frappe.
 */

/**
 * Réduit un texte à sa forme comparable.
 *
 * Le flux du réseau lillois publie ses noms sans accents — « Metropole
 * Europeenne de Lille » — alors que l'utilisateur, lui, les tape. Sans ce
 * repli, chercher « théâtre » ne trouverait pas « Theatre ».
 */
public fun foldForSearch(text: String): String {
    val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
    val builder = StringBuilder(decomposed.length)
    for (character in decomposed) {
        when {
            // Les marques diacritiques disparaissent avec leur lettre de base
            // déjà séparée par la décomposition.
            character.isMarkCharacter() -> Unit
            character.isLetterOrDigit() -> builder.append(character.lowercaseChar())
            // Tirets, apostrophes et points deviennent des séparations de mots,
            // pour que « Saint-André » se cherche aussi bien en deux mots.
            else -> builder.append(' ')
        }
    }
    return builder.toString().trim().replace(WHITESPACE_RUN, " ")
}

private val WHITESPACE_RUN = Regex("\\s+")

private fun Char.isMarkCharacter(): Boolean = Character.getType(this).let {
    it == Character.NON_SPACING_MARK.toInt() ||
        it == Character.COMBINING_SPACING_MARK.toInt() ||
        it == Character.ENCLOSING_MARK.toInt()
}

/**
 * Indique si une station répond à la saisie.
 *
 * Chaque mot de la saisie doit préfixer un mot du texte cherché, dans
 * n'importe quel ordre : « gare lille » trouve « Gare Lille Flandres », et
 * « flandres gare » aussi. La correspondance par préfixe couvre la frappe en
 * cours, pendant laquelle le dernier mot est toujours incomplet.
 *
 * Le code postal fait partie du texte cherché : il est affiché sur la ligne,
 * donc l'utilisateur peut légitimement s'attendre à pouvoir le taper.
 */
internal fun stationMatches(entry: StationWithAvailability, foldedQuery: String): Boolean {
    if (foldedQuery.isEmpty()) return true
    val haystack = foldForSearch(
        listOfNotNull(entry.station.name, entry.station.postalCode).joinToString(" "),
    )
    val words = haystack.split(' ')
    return foldedQuery.split(' ').all { term ->
        words.any { it.startsWith(term) }
    }
}

/**
 * Ne conserve que les stations répondant à la saisie.
 *
 * @param stations les stations connues, dans leur ordre d'affichage.
 * @param query ce que l'utilisateur a tapé, brut.
 * @return les stations retenues, dans le même ordre. Une saisie vide ou faite
 *   de seule ponctuation rend la liste intacte, plutôt que vide : effacer un
 *   champ de recherche doit tout ramener.
 */
public fun filterStations(
    stations: List<StationWithAvailability>,
    query: String,
): List<StationWithAvailability> {
    val folded = foldForSearch(query)
    if (folded.isEmpty()) return stations
    return stations.filter { stationMatches(it, folded) }
}

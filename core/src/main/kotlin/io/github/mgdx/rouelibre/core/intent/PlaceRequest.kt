package io.github.mgdx.rouelibre.core.intent

import io.github.mgdx.rouelibre.core.geo.Coordinates

/**
 * Un lieu reçu d'une autre application (SPEC §7.8).
 *
 * Deux formes seulement, parce qu'elles appellent deux suites différentes :
 * un point est déjà placé, une recherche doit encore être résolue par l'index
 * d'adresses — sur l'appareil, sans qu'aucune requête ne parte.
 */
public sealed interface PlaceRequest {

    /** Le libellé reçu, s'il y en avait un. */
    public val label: String?

    /**
     * Un point désigné par ses coordonnées.
     *
     * @property coordinates l'endroit visé.
     * @property label ce que l'expéditeur en a dit, à afficher plutôt que des
     *   coordonnées brutes.
     */
    public data class Point(
        public val coordinates: Coordinates,
        override val label: String? = null,
    ) : PlaceRequest

    /**
     * Un lieu décrit en toutes lettres, à chercher dans l'index.
     *
     * @property text ce que l'expéditeur a écrit.
     */
    public data class Search(public val text: String) : PlaceRequest {
        override val label: String? get() = text
    }
}

/**
 * Analyse une URI `geo:` ou `google.navigation:` (SPEC §7.8).
 *
 * Toutes les formes rencontrées en pratique sont acceptées, parce qu'aucune
 * n'est rare :
 *
 * ```
 * geo:50.6371,3.0630
 * geo:50.6371,3.0630?z=17
 * geo:0,0?q=50.6371,3.0630
 * geo:0,0?q=50.6371,3.0630(Grand-Place)
 * geo:0,0?q=12+rue+Nationale+Lille
 * google.navigation:q=50.6371,3.0630
 * ```
 *
 * Le `geo:0,0` d'en-tête est un usage établi : il veut dire « le point est
 * dans la requête, pas ici ». Le prendre au pied de la lettre enverrait
 * l'utilisateur au large du golfe de Guinée.
 *
 * @param uri l'URI reçue, telle quelle.
 * @return le lieu demandé, ou `null` si l'URI ne décrit rien d'exploitable.
 */
public fun parsePlaceUri(uri: String): PlaceRequest? {
    val trimmed = uri.trim()
    val scheme = trimmed.substringBefore(':', missingDelimiterValue = "").lowercase()
    val body = trimmed.substringAfter(':', missingDelimiterValue = "")
    if (body.isEmpty()) return null

    return when (scheme) {
        "geo" -> parseGeoBody(body)
        "google.navigation" -> parseQuery(parametersOf(body)["q"])
        "http", "https" -> parseWebMapLink(trimmed)
        else -> null
    }
}

/**
 * Extrait un lieu d'un lien web cartographique (SPEC §7.8).
 *
 * Ces liens ne peuvent pas être vérifiés automatiquement : les domaines
 * concernés n'appartiennent pas au projet, et depuis Android 12 ils ne
 * parviennent à l'application que si l'utilisateur l'autorise dans les
 * paramètres du système. La marche à suivre est expliquée dans l'écran « À
 * propos » et dans le `README.md`, faute de quoi le comportement passerait
 * pour un défaut.
 *
 * Trois écritures couvrent l'essentiel de ce qui circule :
 *
 * ```
 * https://www.google.com/maps/@50.6371,3.0630,17z
 * https://www.google.com/maps?q=50.6371,3.0630
 * https://www.openstreetmap.org/#map=17/50.6371/3.0630
 * ```
 *
 * Rien n'est téléchargé pour les résoudre : un lien raccourci, dont le lieu
 * n'apparaît qu'après redirection, n'est donc pas reconnu. Suivre la
 * redirection ferait sortir une requête vers un tiers — et lui dirait où va
 * l'utilisateur, ce que la contrainte C3 interdit.
 */
private fun parseWebMapLink(uri: String): PlaceRequest? {
    AT_COORDINATES.find(uri)?.let { match ->
        parseCoordinates("${match.groupValues[1]},${match.groupValues[2]}")
            ?.let { return PlaceRequest.Point(it) }
    }
    OSM_HASH_COORDINATES.find(uri)?.let { match ->
        parseCoordinates("${match.groupValues[1]},${match.groupValues[2]}")
            ?.let { return PlaceRequest.Point(it) }
    }
    // Un « q= » de lien web porte parfois des coordonnées, parfois une
    // adresse ; les deux se résolvent comme ailleurs.
    return parseQuery(parametersOf(uri.substringAfter("://"))["q"])
}

/** « /@50.6371,3.0630 » — l'écriture de Google Maps. */
private val AT_COORDINATES = Regex("""@(-?\d{1,3}\.\d+),(-?\d{1,3}\.\d+)""")

/** « #map=17/50.6371/3.0630 » — celle d'OpenStreetMap. */
private val OSM_HASH_COORDINATES =
    Regex("""#map=[\d.]+/(-?\d{1,3}\.\d+)/(-?\d{1,3}\.\d+)""")

private fun parseGeoBody(body: String): PlaceRequest? {
    val path = body.substringBefore('?')
    val parameters = parametersOf(body)

    // La requête prime sur le chemin : quand les deux sont là, le chemin ne
    // porte que le « 0,0 » de convention.
    parseQuery(parameters["q"])?.let { return it }
    return parseCoordinates(path)?.let { PlaceRequest.Point(it) }
}

/**
 * Analyse la valeur d'un paramètre `q`.
 *
 * Elle porte soit des coordonnées, éventuellement suivies d'un libellé entre
 * parenthèses, soit une adresse en toutes lettres.
 */
private fun parseQuery(rawQuery: String?): PlaceRequest? {
    val query = rawQuery?.let(::decodeUriComponent)?.trim()?.takeIf { it.isNotEmpty() }
        ?: return null

    val label = query.substringAfter('(', missingDelimiterValue = "")
        .substringBeforeLast(')')
        .trim()
        .takeIf { it.isNotEmpty() }
    val beforeLabel = query.substringBefore('(').trim()

    parseCoordinates(beforeLabel)?.let { return PlaceRequest.Point(it, label) }
    return PlaceRequest.Search(query)
}

/**
 * Lit un couple « latitude,longitude ».
 *
 * @return les coordonnées, ou `null` si le texte n'en est pas un couple
 *   plausible. Une valeur hors des bornes terrestres est refusée plutôt que
 *   ramenée de force : elle trahit une autre convention, pas une position.
 */
private fun parseCoordinates(text: String): Coordinates? {
    val parts = text.split(',')
    if (parts.size != 2) return null
    val latitude = parts[0].trim().toDoubleOrNull() ?: return null
    val longitude = parts[1].trim().toDoubleOrNull() ?: return null
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
    // « geo:0,0 » est l'en-tête conventionnel d'une URI dont le lieu est dans
    // la requête ; ce n'est pas une destination au milieu de l'Atlantique.
    if (latitude == 0.0 && longitude == 0.0) return null
    return Coordinates(latitude, longitude)
}

private fun parametersOf(body: String): Map<String, String> {
    // « geo: » sépare ses paramètres par un point d'interrogation, mais
    // « google.navigation: » les met directement dans le corps : les deux
    // formes se rencontrent, et la seconde n'a pas de séparateur à chercher.
    val query = if ('?' in body) body.substringAfter('?') else body
    if (query.isEmpty()) return emptyMap()
    return query.split('&').mapNotNull { parameter ->
        val name = parameter.substringBefore('=', missingDelimiterValue = "")
        val value = parameter.substringAfter('=', missingDelimiterValue = "")
        if (name.isEmpty() || value.isEmpty()) null else name.lowercase() to value
    }.toMap()
}

/**
 * Décode les échappements d'une URI, sans dépendre d'Android.
 *
 * Le module métier ne connaît pas `Uri.decode` ; il doit rester compilable et
 * testable sur la JVM (SPEC §14). Le `+` vaut espace, comme dans une requête
 * HTTP — c'est ainsi que les applications de cartographie écrivent leurs
 * adresses.
 */
private fun decodeUriComponent(value: String): String {
    val builder = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        val character = value[index]
        when {
            character == '+' -> {
                builder.append(' ')
                index++
            }

            character == '%' && index + 2 < value.length -> {
                val code = value.substring(index + 1, index + 3).toIntOrNull(radix = 16)
                if (code == null) {
                    builder.append(character)
                    index++
                } else {
                    builder.append(code.toChar())
                    index += 3
                }
            }

            else -> {
                builder.append(character)
                index++
            }
        }
    }
    return builder.toString()
}

/**
 * Cherche un lieu dans un texte partagé (SPEC §7.8).
 *
 * C'est le cas d'usage le plus fréquent en pratique : une adresse reçue par
 * messagerie, que l'on partage vers l'application. Le texte peut être une
 * adresse, un couple de coordonnées, une URI `geo:` collée, ou tout cela noyé
 * dans une phrase.
 *
 * @param text le texte partagé, tel quel.
 * @return le lieu reconnu, ou `null` si le texte est vide.
 */
public fun findPlaceInText(text: String): PlaceRequest? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null

    // Une URI collée dans le texte reste une URI : la reconnaître évite de
    // chercher « geo:50.6371,3.0630 » dans l'index d'adresses.
    URI_IN_TEXT.find(trimmed)?.let { match ->
        parsePlaceUri(match.value)?.let { return it }
    }

    COORDINATES_IN_TEXT.find(trimmed)?.let { match ->
        val latitude = match.groupValues[1].toDoubleOrNull()
        val longitude = match.groupValues[2].toDoubleOrNull()
        if (latitude != null &&
            longitude != null &&
            latitude in -90.0..90.0 &&
            longitude in -180.0..180.0
        ) {
            return PlaceRequest.Point(Coordinates(latitude, longitude))
        }
    }

    return PlaceRequest.Search(trimmed)
}

/** Une URI `geo:` ou `google.navigation:` posée au milieu d'un texte. */
private val URI_IN_TEXT = Regex("""(?:geo|google\.navigation):\S+""", RegexOption.IGNORE_CASE)

/**
 * Un couple de coordonnées décimales dans un texte.
 *
 * Le séparateur admet la virgule seule ou suivie d'espaces : les deux
 * s'écrivent, et une messagerie ajoute volontiers l'espace. La notation
 * française à virgule décimale — « 50,6371 » — n'est pas reconnue : elle est
 * indistinguable d'un couple de deux entiers.
 */
private val COORDINATES_IN_TEXT = Regex("""(-?\d{1,3}\.\d{3,})\s*,\s*(-?\d{1,3}\.\d{3,})""")

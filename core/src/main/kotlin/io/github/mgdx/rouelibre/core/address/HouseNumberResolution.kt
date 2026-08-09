package io.github.mgdx.rouelibre.core.address

import io.github.mgdx.rouelibre.core.geo.Coordinates
import kotlin.math.abs

/**
 * Place un numéro dans une voie (SPEC §4.3).
 *
 * Certaines artères lilloises dépassent le kilomètre : retomber sur le centre
 * de la rue quand le numéro demandé n'est pas dans l'index produirait une
 * erreur de plusieurs centaines de mètres — assez pour désigner la mauvaise
 * station de départ, et donc pour calculer un itinéraire faux. D'où
 * l'interpolation, qui ramène l'erreur à la longueur de quelques immeubles.
 *
 * @param requestedNumber le numéro cherché.
 * @param requestedSuffix son indice — « bis », « a » — ou une chaîne vide.
 * @param knownNumbers les numéros que l'index rattache à cette voie, dans
 *   n'importe quel ordre.
 * @param streetPosition le point représentatif de la voie, dernier recours.
 * @return la position retenue et la façon dont elle a été obtenue.
 */
public fun resolveHouseNumber(
    requestedNumber: Int,
    requestedSuffix: String,
    knownNumbers: List<KnownHouseNumber>,
    streetPosition: Coordinates,
): ResolvedPosition {
    if (knownNumbers.isEmpty()) {
        return ResolvedPosition(streetPosition, PositionPrecision.StreetOnly)
    }

    exactMatch(requestedNumber, requestedSuffix, knownNumbers)?.let { match ->
        return ResolvedPosition(match.position, PositionPrecision.Exact)
    }

    // Les numéros pairs et impairs se font face de part et d'autre de la
    // chaussée : interpoler le 13 entre le 12 et le 14 le placerait sur le
    // trottoir d'en face, et à un carrefour, dans la rue perpendiculaire. On
    // cherche donc d'abord des voisins de même parité.
    val sameParity = knownNumbers.filter { it.number % 2 == requestedNumber % 2 }
    return interpolateAmong(requestedNumber, sameParity)
        ?: interpolateAmong(requestedNumber, knownNumbers)
        ?: ResolvedPosition(streetPosition, PositionPrecision.StreetOnly)
}

/**
 * Le numéro demandé s'il figure tel quel.
 *
 * Un numéro saisi sans indice accepte le premier indice connu : quelqu'un qui
 * tape « 12 » dans une rue qui n'a qu'un « 12 bis » cherche cet immeuble-là,
 * pas le centre de la rue.
 */
private fun exactMatch(
    number: Int,
    suffix: String,
    knownNumbers: List<KnownHouseNumber>,
): KnownHouseNumber? {
    val sameNumber = knownNumbers.filter { it.number == number }
    if (sameNumber.isEmpty()) return null
    return sameNumber.firstOrNull { it.suffix == suffix }
        ?: sameNumber.minByOrNull { it.suffix }
}

/**
 * Interpole entre les deux voisins les plus proches, s'ils encadrent le numéro.
 *
 * @return `null` si la liste ne fournit aucun voisin exploitable.
 */
private fun interpolateAmong(
    requestedNumber: Int,
    candidates: List<KnownHouseNumber>,
): ResolvedPosition? {
    if (candidates.isEmpty()) return null
    val below = candidates.filter { it.number < requestedNumber }.maxByOrNull { it.number }
    val above = candidates.filter { it.number > requestedNumber }.minByOrNull { it.number }

    if (below != null && above != null) {
        val span = (above.number - below.number).toDouble()
        val progress = (requestedNumber - below.number) / span
        return ResolvedPosition(
            Coordinates(
                latitude = below.position.latitude +
                    progress * (above.position.latitude - below.position.latitude),
                longitude = below.position.longitude +
                    progress * (above.position.longitude - below.position.longitude),
            ),
            PositionPrecision.Interpolated,
        )
    }

    // Un seul côté connu : on ne prolonge pas la droite au-delà du dernier
    // numéro, faute de savoir où la voie continue — ni même si elle continue.
    // Le voisin le plus proche est une approximation honnête ; une
    // extrapolation serait une invention.
    val nearest = (below ?: above) ?: return null
    return ResolvedPosition(nearest.position, PositionPrecision.NearestKnown)
        .takeIf { abs(nearest.number - requestedNumber) <= FAR_NEIGHBOUR_LIMIT }
}

/**
 * Écart au-delà duquel un voisin unique ne dit plus rien.
 *
 * Quarante numéros valent en gros deux cents mètres de façade. Au-delà, rendre
 * la position de ce voisin laisserait croire à une précision qui n'existe pas ;
 * le point représentatif de la voie est alors plus honnête.
 */
private const val FAR_NEIGHBOUR_LIMIT = 40

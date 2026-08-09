package io.github.mgdx.rouelibre.core.address

import io.github.mgdx.rouelibre.core.geo.Coordinates

/**
 * Nature d'une entrée de l'index d'adresses.
 *
 * Les points de repère — gares, universités, hôpitaux, grandes places — sont
 * indexés comme des voies (SPEC §4.3) : ils se cherchent de la même façon, et
 * seul l'affichage les distingue.
 */
public enum class AddressEntryKind {
    /** Une voie, à laquelle des numéros peuvent être rattachés. */
    Street,

    /** Un point de repère extrait d'OpenStreetMap, sans numéro. */
    Landmark,
    ;

    public companion object {
        /**
         * Traduit le code stocké dans l'index.
         *
         * @return la nature correspondante, ou [Street] pour un code inconnu :
         *   un index plus récent qui introduirait une catégorie ne doit pas
         *   rendre ses entrées invisibles.
         */
        public fun fromCode(code: Int): AddressEntryKind =
            if (code == LANDMARK_CODE) Landmark else Street

        private const val LANDMARK_CODE = 1
    }
}

/**
 * Une entrée de l'index réduite à ce que la recherche a besoin de comparer.
 *
 * Volontairement sans texte affichable : les vingt mille entrées sont tenues
 * en mémoire pour le rattrapage flou (SPEC §4.3), et y garder les noms
 * d'origine, les communes et les codes postaux tripleraient cette empreinte
 * pour des champs dont seuls les huit résultats retenus ont l'usage.
 *
 * @property id identifiant de la voie dans l'index, qui sert à retrouver la
 *   ligne complète et ses numéros.
 * @property normalizedType type de voie normalisé, ou `null`.
 * @property normalizedName nom propre normalisé, sans le type.
 * @property normalizedCity commune normalisée.
 * @property normalizedFormerCity commune absorbée, normalisée, ou `null`. Un
 *   habitant de Lomme ou d'Hellemmes tape le nom de sa commune, que la Base
 *   Adresse Nationale rattache pourtant à Lille : sans ce champ, il ne
 *   trouverait pas sa rue.
 * @property position point représentatif de la voie.
 */
public data class SearchableStreet(
    public val id: Long,
    public val normalizedType: String?,
    public val normalizedName: String,
    public val normalizedCity: String,
    public val position: Coordinates,
    public val normalizedFormerCity: String? = null,
)

/**
 * Un numéro connu de l'index, rattaché à une voie.
 *
 * @property number le numéro lui-même.
 * @property suffix indice de répétition normalisé — « bis », « ter », « a » —
 *   ou une chaîne vide.
 * @property position position exacte du numéro.
 */
public data class KnownHouseNumber(
    public val number: Int,
    public val suffix: String,
    public val position: Coordinates,
)

/**
 * Une voie retenue par la recherche, avec ce qui l'a placée là.
 *
 * @property street la voie.
 * @property matchQuality qualité de la correspondance, de 0 à 1.
 * @property distanceInMetres distance au point de référence, ou `null` si
 *   aucun n'était connu.
 */
public data class ScoredStreet(
    public val street: SearchableStreet,
    public val matchQuality: Double,
    public val distanceInMetres: Double?,
)

/** Ce qui a permis de placer un point : à distinguer pour ne pas surpromettre. */
public enum class PositionPrecision {
    /** Le numéro demandé figure tel quel dans l'index. */
    Exact,

    /** Le numéro a été interpolé entre deux numéros connus de la voie. */
    Interpolated,

    /** Un seul voisin était connu : sa position sert de repli. */
    NearestKnown,

    /** La voie ne porte aucun numéro : seul son point représentatif existe. */
    StreetOnly,
}

/**
 * Une position déduite pour un numéro dans une voie.
 *
 * @property coordinates le point retenu.
 * @property precision comment il a été obtenu.
 */
public data class ResolvedPosition(
    public val coordinates: Coordinates,
    public val precision: PositionPrecision,
)

/**
 * Une adresse trouvée, prête à être affichée puis désignée sur la carte.
 *
 * Aucun champ ne porte de texte destiné à l'écran : le nom et la commune sont
 * des données, et c'est la couche Android qui les compose avec les ressources
 * de chaînes (SPEC §9).
 *
 * @property streetId identifiant de la voie dans l'index.
 * @property houseNumber le numéro demandé, s'il y en avait un.
 * @property houseNumberSuffix son indice, ou une chaîne vide.
 * @property streetName le nom de la voie tel qu'il s'écrit, accents compris.
 * @property city la commune.
 * @property postcode le code postal, s'il est connu.
 * @property kind voie ou point de repère.
 * @property position le point retenu pour cette adresse.
 * @property precision ce qui a permis de le placer.
 * @property distanceInMetres distance au point de référence, ou `null`.
 */
public data class AddressResult(
    public val streetId: Long,
    public val houseNumber: Int?,
    public val houseNumberSuffix: String,
    public val streetName: String,
    public val city: String,
    public val postcode: String?,
    public val kind: AddressEntryKind,
    public val position: Coordinates,
    public val precision: PositionPrecision,
    public val distanceInMetres: Double?,
)

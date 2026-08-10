package io.github.mgdx.rouelibre.core.address

import io.github.mgdx.rouelibre.core.geo.Coordinates

/**
 * The nature of an entry in the address index.
 *
 * Landmarks — railway stations, universities, hospitals, major squares — are
 * indexed as streets (SPEC §4.3): they are searched the same way, and only the
 * display tells them apart.
 */
public enum class AddressEntryKind {
    /** A street, to which house numbers may be attached. */
    Street,

    /** A landmark extracted from OpenStreetMap, with no house number. */
    Landmark,
    ;

    public companion object {
        /**
         * Translates the code stored in the index.
         *
         * @return the matching nature, or [Street] for an unknown code: a newer
         *   index introducing a category must not make its entries invisible.
         */
        public fun fromCode(code: Int): AddressEntryKind =
            if (code == LANDMARK_CODE) Landmark else Street

        private const val LANDMARK_CODE = 1
    }
}

/**
 * An index entry reduced to what the search needs to compare.
 *
 * Deliberately without displayable text: the twenty thousand entries are held
 * in memory for the fuzzy fallback (SPEC §4.3), and keeping the original names,
 * the municipalities and the postcodes there would triple that footprint for
 * fields only the eight retained results ever use.
 *
 * @property id the street's identifier in the index, used to fetch the full row
 *   and its house numbers.
 * @property normalizedType the normalised street type, or `null`.
 * @property normalizedName the normalised proper name, without the type.
 * @property normalizedCity the normalised municipality.
 * @property normalizedFormerCity the normalised absorbed municipality, or
 *   `null`. Someone living in Lomme or Hellemmes types the name of their own
 *   municipality, which the Base Adresse Nationale nevertheless attaches to
 *   Lille: without this field, they would not find their street.
 * @property position the street's representative point.
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
 * A house number known to the index, attached to a street.
 *
 * @property number the number itself.
 * @property suffix the normalised repetition mark — "bis", "ter", "a" — or an
 *   empty string.
 * @property position the number's exact position.
 */
public data class KnownHouseNumber(
    public val number: Int,
    public val suffix: String,
    public val position: Coordinates,
)

/**
 * A street retained by the search, with what placed it there.
 *
 * @property street the street.
 * @property matchQuality the match quality, from 0 to 1.
 * @property distanceInMetres the distance to the reference point, or `null` if
 *   none was known.
 */
public data class ScoredStreet(
    public val street: SearchableStreet,
    public val matchQuality: Double,
    public val distanceInMetres: Double?,
)

/** How a point was placed: distinguished so as not to overpromise. */
public enum class PositionPrecision {
    /** The requested number appears as such in the index. */
    Exact,

    /** The number was interpolated between two known numbers of the street. */
    Interpolated,

    /** Only one neighbour was known: its position serves as a fallback. */
    NearestKnown,

    /** The street carries no number at all: only its representative point. */
    StreetOnly,
}

/**
 * A position derived for a number in a street.
 *
 * @property coordinates the point retained.
 * @property precision how it was obtained.
 */
public data class ResolvedPosition(
    public val coordinates: Coordinates,
    public val precision: PositionPrecision,
)

/**
 * An address found, ready to be displayed and then pointed at on the map.
 *
 * No field carries text meant for the screen: the name and the municipality are
 * data, and it is the Android layer that composes them with the string
 * resources (SPEC §9).
 *
 * @property streetId the street's identifier in the index.
 * @property houseNumber the number asked for, if there was one.
 * @property houseNumberSuffix its repetition mark, or an empty string.
 * @property streetName the street's name as it is written, accents included.
 * @property city the municipality.
 * @property postcode the postcode, if known.
 * @property kind street or landmark.
 * @property position the point retained for this address.
 * @property precision what allowed it to be placed.
 * @property distanceInMetres the distance to the reference point, or `null`.
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

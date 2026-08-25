package io.github.mgdx.rouelibre.core.routing

/**
 * Which side of the road a country drives on.
 *
 * The routing profiles read the per-side cycleway tags — a lane on the
 * with-traffic side serves the forward direction, the other side serves
 * contraflow — and which side is which is a fact about the country, never
 * about the city or the rider (SPEC §5). The precedent is the address layout
 * (SPEC §4.3): the configuration names the country, and a table the
 * application carries answers for what the whole country does. Reading the
 * tags blind to their side was measured and rejected — it sent one campaign
 * leg down 240 m of street whose lane runs the other way.
 */
public enum class DrivingSide {
    Right,
    Left,
    ;

    public companion object {

        /**
         * The territories that drive on the left, by ISO 3166-1 alpha-2 code.
         *
         * The full list, not the fleet's: a city added tomorrow must find its
         * country already answered for. Kept in one place on purpose — whoever
         * corrects it corrects every reader at once.
         */
        private val LEFT_DRIVING_COUNTRIES = setOf(
            // Europe.
            "CY", "GB", "GG", "IE", "IM", "JE", "MT",
            // Asia.
            "BD", "BN", "BT", "HK", "ID", "IN", "JP", "LK", "MO", "MV",
            "MY", "NP", "PK", "SG", "TH", "TL",
            // Africa and the Indian Ocean.
            "BW", "KE", "LS", "MU", "MW", "MZ", "NA", "SC", "SZ", "TZ",
            "UG", "ZA", "ZM", "ZW",
            // The Caribbean and the Atlantic.
            "AG", "AI", "BB", "BM", "BS", "DM", "FK", "GD", "GY", "JM",
            "KN", "KY", "LC", "MS", "SH", "SR", "TC", "TT", "VC", "VG",
            "VI",
            // Oceania.
            "AU", "CC", "CK", "CX", "FJ", "KI", "NF", "NR", "NU", "NZ",
            "PG", "PN", "SB", "TK", "TO", "TV", "WS",
        )

        /**
         * The side driven on in [country], an ISO 3166-1 alpha-2 code.
         *
         * An unknown or absent country reads as [Right]: it is the commoner
         * side, and it is what every version before the per-side tags were
         * read assumed — a wrong left would claim provision on the wrong side
         * of the road, where a wrong right merely keeps the older reading.
         */
        public fun ofCountry(country: String?): DrivingSide =
            if (country != null && country.uppercase() in LEFT_DRIVING_COUNTRIES) Left else Right
    }
}

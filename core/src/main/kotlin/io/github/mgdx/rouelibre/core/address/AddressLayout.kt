package io.github.mgdx.rouelibre.core.address

/**
 * How an address is laid out on the page, in the country the address is in.
 *
 * A Lyon address reads "12 rue Nationale" and a Warsaw one "Marszałkowska 12",
 * and neither changes because the reader set their phone to the other language:
 * the layout of an address belongs to the place the address is in, not to the
 * person reading it (SPEC §4.3). The words **around** the address — the
 * labels, the prompts, the messages — are the interface's, and the digits the
 * number is written in are the reader's (SPEC §9); the order and the
 * punctuation between number and street are neither.
 *
 * @property numberComesFirst true where the house number opens the address,
 *   "12 rue Nationale", false where it closes it, "Bahnhofstraße 12".
 * @property streetSeparator what stands between the number and the street name.
 * @property suffixSeparator what stands between the number and its repetition
 *   mark. It does **not** follow [streetSeparator]: France writes "12 bis" with
 *   a space and Poland "12A" without, while both are otherwise ordinary.
 */
public data class AddressLayout(
    public val numberComesFirst: Boolean,
    public val streetSeparator: String,
    public val suffixSeparator: String,
) {

    /**
     * Writes a house number and a street name as the country writes them.
     *
     * @param streetName the street's name as the index holds it.
     * @param houseNumber the number, **already written in the reader's digits**
     *   (SPEC §9): this module knows nothing of numbering systems.
     * @param houseNumberSuffix its repetition mark, or an empty string.
     */
    public fun write(
        streetName: String,
        houseNumber: String,
        houseNumberSuffix: String = "",
    ): String {
        val number = if (houseNumberSuffix.isEmpty()) {
            houseNumber
        } else {
            houseNumber + suffixSeparator + houseNumberSuffix
        }
        return if (numberComesFirst) {
            number + streetSeparator + streetName
        } else {
            streetName + streetSeparator + number
        }
    }
}

/**
 * The layout applied where the table below names no country of its own.
 *
 * English's own layout, "221B Baker Street", and the same fallback the
 * normalisation rules take for a language they have no file for (SPEC §4.3):
 * an unknown address base is read in English and written in English. The
 * repetition mark is spaced, which is the safer of the two — a space between
 * two things that belong together reads as a typographic choice, while
 * "12bis" run together reads as a different number.
 */
private val DEFAULT_LAYOUT = AddressLayout(
    numberComesFirst = true,
    streetSeparator = " ",
    suffixSeparator = " ",
)

/**
 * The layout of each address base the application is likely to open, by the
 * language that base is written in.
 *
 * Every entry is a convention of the country, checked against an address that
 * exists there, and not a preference of the language's translators.
 */
private val LAYOUTS: Map<String, AddressLayout> = mapOf(
    // "12 bis rue Nationale, Lyon". French opens with the number, and its
    // repetition marks are words — bis, ter, quater — which take a space, as
    // a word does.
    "fr" to AddressLayout(numberComesFirst = true, streetSeparator = " ", suffixSeparator = " "),

    // "Bahnhofstraße 12, Karlsruhe". German closes with the number. The
    // repetition mark is kept spaced, as `res/values-de/` wrote it: German
    // does run a letter against the number, "Hauptstraße 12a", but our suffix
    // field also carries the words the base spells out, and one separator has
    // to serve both.
    "de" to AddressLayout(numberComesFirst = false, streetSeparator = " ", suffixSeparator = " "),

    // "Gran Vía, 12, Madrid". Spanish closes with the number **and puts a
    // comma before it**: the comma is not decoration, it is what tells the
    // number apart from a number the street's name carries, "Calle 20 de
    // Noviembre".
    "es" to AddressLayout(numberComesFirst = false, streetSeparator = ", ", suffixSeparator = " "),

    // "Rua Augusta, 12, Lisboa". Portuguese follows Spanish, comma included,
    // in Portugal and in Brazil alike.
    "pt" to AddressLayout(numberComesFirst = false, streetSeparator = ", ", suffixSeparator = " "),

    // "Via Roma 12, Torino". Italian closes with the number, with no comma:
    // where Spanish separates, Italian runs the two together.
    "it" to AddressLayout(numberComesFirst = false, streetSeparator = " ", suffixSeparator = " "),

    // "Kalverstraat 12A, Amsterdam". Dutch closes with the number, and runs
    // the letter that follows it hard against it: "12 A" would read as two
    // addresses on the same street rather than as one.
    "nl" to AddressLayout(numberComesFirst = false, streetSeparator = " ", suffixSeparator = ""),

    // "Marszałkowska 12A, Warszawa". Polish closes with the number — the
    // street type, *ulica*, is usually dropped in writing — and the letter is
    // closed up, as it is on the plates.
    "pl" to AddressLayout(numberComesFirst = false, streetSeparator = " ", suffixSeparator = ""),

    // "Národní 12A, Praha". Czech closes with the number and closes up the
    // letter, as Polish does.
    "cs" to AddressLayout(numberComesFirst = false, streetSeparator = " ", suffixSeparator = ""),
)

/**
 * The layout to write an address of a given base in.
 *
 * @param language the language the address base was built in, as
 *   [AddressNormalizer.language] records it — never the interface's language.
 * @return the country's layout, or [DEFAULT_LAYOUT] for a base this table does
 *   not name.
 */
public fun addressLayoutOf(language: String): AddressLayout =
    LAYOUTS[language.lowercase()] ?: DEFAULT_LAYOUT

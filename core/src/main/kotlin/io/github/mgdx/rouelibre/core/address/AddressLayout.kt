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
 * **The `suffix` field does not hold the same thing from one country to the
 * next, and a separator cannot be chosen without going to look.** Counted over
 * the generated indexes: France spells out a repetition mark, a word — 305 982
 * of them against 264 074 letters, "12 bis"; Germany, the Netherlands, Poland
 * and Italy hold a letter subdividing the number, and words in the noise —
 * Germany 755 188 letters against 539 words; and Czechia holds **a second
 * number**, the *číslo orientační*, which the plate joins to the first with a
 * slash. That is why each entry below carries its count rather than an
 * assertion: whoever adds a language reads their own data first. Getting it
 * wrong is not a typographic blemish — a Czech address closed up reads "18538",
 * a number that exists nowhere.
 *
 * @property numberComesFirst true where the house number opens the address,
 *   "12 rue Nationale", false where it closes it, "Bahnhofstraße 12".
 * @property streetSeparator what stands between the number and the street name.
 * @property suffixSeparator what stands between the number and what follows it.
 *   It does **not** follow [streetSeparator], and the three values in use are
 *   three different facts about a country: France spaces a word, "12 bis";
 *   Germany closes up a letter, "12a"; Czechia slashes a second number,
 *   "185/38".
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

    // "Bahnhofstraße 12a, Karlsruhe". German closes with the number, and closes
    // the letter up against it. `res/values-de/` used to space it, on the
    // reasoning that the suffix field also carries the words a base spells out
    // and that one separator had to serve both: counted over the generated
    // indexes, those words are 539 entries against 755 188 letters, 0.07 %.
    // A separator chosen for the 0.07 % spoils the rest, and "Hauptstraße 12 a"
    // is not how Germany writes it.
    "de" to AddressLayout(numberComesFirst = false, streetSeparator = " ", suffixSeparator = ""),

    // "Gran Vía, 12, Madrid". Spanish closes with the number **and puts a
    // comma before it**: the comma is not decoration, it is what tells the
    // number apart from a number the street's name carries, "Calle 20 de
    // Noviembre".
    "es" to AddressLayout(numberComesFirst = false, streetSeparator = ", ", suffixSeparator = " "),

    // "Rua Augusta, 12, Lisboa". Portuguese follows Spanish, comma included,
    // in Portugal and in Brazil alike.
    "pt" to AddressLayout(numberComesFirst = false, streetSeparator = ", ", suffixSeparator = " "),

    // "Via Roma 12A, Torino". Italian closes with the number, with no comma:
    // where Spanish separates, Italian runs the two together. The letter is
    // closed up, as in Germany — 23 858 letters against 28 words rules out the
    // space that stood here first. Italy also writes "12/A", commonly in the
    // north, and that form is *not* restored: the slash was in the source and
    // `split_house_number` dropped it, so the index no longer records which
    // addresses carried one, and slashing them all would invent it for those
    // that never did. Closing up is exact for those and merely tighter than the
    // plate for the others — and unlike Czechia's below, an Italian letter
    // cannot be misread as a second number, so nothing but typography is at
    // stake in the choice.
    "it" to AddressLayout(numberComesFirst = false, streetSeparator = " ", suffixSeparator = ""),

    // "Kalverstraat 12A, Amsterdam". Dutch closes with the number, and runs
    // the letter that follows it hard against it: "12 A" would read as two
    // addresses on the same street rather than as one.
    "nl" to AddressLayout(numberComesFirst = false, streetSeparator = " ", suffixSeparator = ""),

    // "Marszałkowska 12A, Warszawa". Polish closes with the number — the
    // street type, *ulica*, is usually dropped in writing — and the letter is
    // closed up, as it is on the plates.
    "pl" to AddressLayout(numberComesFirst = false, streetSeparator = " ", suffixSeparator = ""),

    // "Gen. Štefánika 185/38, Praha". Czech closes with the number, and what
    // follows the number is **not a repetition mark at all**: a Czech address
    // carries two numbers, the *číslo popisné* that identifies the parcel (185)
    // and the *číslo orientační* that places it in the street (38), and the
    // plate joins them with a slash. The index bears this out — its commonest
    // suffixes are digits, 4, 3, 1 and 2 at around eleven thousand each,
    // against 635 letters in all. `split_house_number` cuts at the leading
    // digits and strips the "/" from what remains, so the slash has to be put
    // back here: closed up, this address would read "18538", a number that
    // exists nowhere, and spaced it would read as two addresses.
    "cs" to AddressLayout(numberComesFirst = false, streetSeparator = " ", suffixSeparator = "/"),

    // "Hlavná 185/38, Košice". Slovakia inherited Czechoslovakia's two-number
    // addressing whole — *súpisné číslo* and *orientačné číslo*, same slash —
    // so it takes the Czech entry's rule. Written before the Slovak
    // translation lands: without it a Slovak base would fall on the English
    // fallback and print "12 Hlavná", which is neither the country's order nor
    // its punctuation. The layout table is keyed on the address base, not on
    // the translations that exist, and the two lists need not match.
    "sk" to AddressLayout(numberComesFirst = false, streetSeparator = " ", suffixSeparator = "/"),
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

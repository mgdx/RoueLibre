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

    // "Nørrebrogade 12A, København". Danish closes with the number, and what
    // follows it is a letter and nothing but a letter: counted over the five
    // Danish indexes, 113 081 suffixes, every one of them a letter, not one
    // number and not one word. The letter is closed up, as the plate writes
    // it — a Danish stairwell is written after the address, "2. th.", and
    // never reaches this field.
    "da" to AddressLayout(numberComesFirst = false, streetSeparator = " ", suffixSeparator = ""),

    // "Aleksanterinkatu 15 A, Helsinki". Finnish closes with the number, and
    // is the one country here whose letter is **spaced**. Counted over the ten
    // Finnish indexes: 29 696 suffixes, 26 347 of them letters, 14 898
    // lowercase against 11 449 upper. The two are not the same thing — a
    // lowercase letter subdivides the plot, an uppercase one names the
    // stairwell, the very thing a reader needs to reach a Finnish door — and
    // one separator has to serve both. Spacing is what loses least: "15 a"
    // reads as a typographic choice and still resolves, while "15A" run
    // together reads as a different number on a plate that carries none.
    "fi" to AddressLayout(numberComesFirst = false, streetSeparator = " ", suffixSeparator = " "),

    // "Trubarjeva cesta 12a, Ljubljana". Slovenia closes with the number and
    // closes the letter up against it: the *dodatek k hišni številki* of the
    // Register prostorskih enot is a lowercase letter, and the index bears it
    // out — 35 298 suffixes, 34 263 letters, 27 670 of them lowercase, against
    // 624 numbers. Slovenia never had Czechoslovakia's second number, so
    // nothing here follows the slash above it.
    "sl" to AddressLayout(numberComesFirst = false, streetSeparator = " ", suffixSeparator = ""),

    // "Ilica 12a, Zagreb". Croatian closes with the number — the street type,
    // *ulica*, is usually dropped in writing — and closes the letter up, as
    // the plates do. Counted over the four Croatian indexes: 58 491 suffixes,
    // 51 448 letters against 5 701 numbers. Those numbers are not a second
    // number of the Czech kind; they are what `split_house_number` leaves of a
    // range, and they are 10 % of the field against a letter's 88 %.
    "hr" to AddressLayout(numberComesFirst = false, streetSeparator = " ", suffixSeparator = ""),

    // "Bulevardul Magheru 12A, București". Romanian closes with the number and
    // closes the letter up. The three Romanian indexes are the smallest of the
    // wave — 15 687 numbers, 2 872 suffixes — and the most uniform: 2 757
    // letters, 2 595 of them uppercase, against 55 words and 28 numbers. The
    // *bl.*, *sc.* and *ap.* a Romanian address carries after the number are
    // parts of a flat's address, not of the street's, and no index holds them.
    "ro" to AddressLayout(numberComesFirst = false, streetSeparator = " ", suffixSeparator = ""),

    // "Storgatan 12b, Malmö". Swedish closes with the number and closes the
    // letter up. Counted over the two Swedish indexes: 10 244 suffixes, 9 964
    // of them letters, and the case is not the wash it is elsewhere — 9 669
    // lowercase against 295 upper, so nothing here is a stairwell of the
    // Finnish kind and nothing argues for the space Finland takes. Sweden
    // matters twice over: a Finnish town can be bilingual by law, and an
    // address base built in Swedish there reads by this line, not by "fi".
    "sv" to AddressLayout(numberComesFirst = false, streetSeparator = " ", suffixSeparator = ""),

    // "Karl Johans gate 12A, Oslo". Norwegian closes with the number and
    // closes the letter up. The Bergen index is the most uniform of any
    // counted for this table: 7 900 suffixes, every one a letter, every one
    // uppercase, not a number and not a word among them.
    "nb" to AddressLayout(numberComesFirst = false, streetSeparator = " ", suffixSeparator = ""),

    // "Ερμού 12α, Λευκωσία". Greek closes with the number and closes the
    // letter up. Counted over the Nicosia index: 3 733 suffixes, 2 723
    // letters against 611 numbers — those numbers are what
    // `split_house_number` leaves of a range, as in Croatia, and not a second
    // number of the Czech kind, so they do not argue for a slash.
    "el" to AddressLayout(numberComesFirst = false, streetSeparator = " ", suffixSeparator = ""),

    // "Kossuth Lajos utca 12/A, Budapest". Hungarian closes with the number
    // and joins what follows it with a **slash**, as the plate does. Counted
    // over Hungary: 755 003 house numbers, 88 095 carrying something after the
    // digits, and 67 322 of those — 76 % — open with "/", against 7 231
    // letters closed up and 12 416 dashes. Budapest alone says the same,
    // 13 435 of 20 704. Unlike Czechia below, what the Hungarian slash carries
    // is usually a letter, "/A" 20 299 and "/a" 9 555 against "/1" 3 534: the
    // separator is the same, the thing separated is not. The dashes are
    // ranges, "14-20", which `split_house_number` reduces to a bare number
    // whatever this line says; the slash is the only form still recoverable.
    "hu" to AddressLayout(numberComesFirst = false, streetSeparator = " ", suffixSeparator = "/"),

    // "Maršala Tita 12a, Zenica". Bosnian closes with the number and closes
    // the letter up, as Croatian does. Counted over Bosnia-Herzegovina:
    // 135 445 house numbers, 10 995 carrying something, and the head of that
    // list is letters and only letters — a 5 645, b 1 842, v 461, A 416 —
    // against 153 slashes, 0.7 %. Zenica's own box holds 185 numbers, too few
    // to rule on, so the country rules for it. The 1 107 values reading "bb",
    // *bez broja*, are not suffixes but the absence of a number, and
    // `split_house_number` drops them for want of leading digits.
    "bs" to AddressLayout(numberComesFirst = false, streetSeparator = " ", suffixSeparator = ""),

    // "Gedimino prospektas 9A, Vilnius". Lithuanian closes with the number and
    // closes the letter up. This is the cleanest country in the table: over
    // 1 065 322 house numbers, 943 511 are bare and 121 809 are a number with
    // a single uppercase letter welded to it — A 68 502, B 21 829, C 10 871 —
    // and that is the whole distribution. Not one slash, not one dash, not one
    // space, and one stray value in a million. Nothing here is open to choice.
    "lt" to AddressLayout(numberComesFirst = false, streetSeparator = " ", suffixSeparator = ""),

    // "Brīvības iela 12A, Rīga". Latvian closes with the number and closes the
    // letter up. Counted over Latvia: 360 367 house numbers, 79 988 carrying
    // something, 61 347 of them a letter welded on — A 37 478, B 10 946,
    // C 4 219. The competing form is Latvia's own: 16 862 values, 21 %, spell
    // a *korpuss* after a space, "3 k-1", a second building on the same plot.
    // `split_house_number` strips that space, so those come out "12k-1" under
    // this line, tighter than the plate but still the right building; spacing
    // instead would spoil the 78 % and print "12 A", which Latvia does not
    // write. The same arbitration as Germany's, on a narrower margin.
    "lv" to AddressLayout(numberComesFirst = false, streetSeparator = " ", suffixSeparator = ""),

    // "Nënë Tereza 12/1, Prishtinë". Albanian closes with the number — the
    // street type, *rruga*, is usually dropped in writing — and joins what
    // follows with a slash. Kosovo's field is the emptiest counted for this
    // table: 277 788 house numbers, 99.42 % of them bare, only 1 411 trailing
    // anything at all. But where it trails, it is a slashed **number**: 1 109
    // of the 1 411 open with "/", "/1" 794 and "/2" 152 against 67 letters,
    // and the Prishtina index bears it out — its commonest suffixes are 1, 2
    // and 3, not a, b and c. So the Czech hazard applies here too: "12/1"
    // closed up reads "121", a number on another block. Read on 0.5 % of the
    // base, and said so — but every one of those 0.5 % breaks without it.
    "sq" to AddressLayout(numberComesFirst = false, streetSeparator = " ", suffixSeparator = "/"),

    // "Mevlana Caddesi 12/A, Konya". Turkish closes with the number and
    // slashes what follows: the plate carries the *kapı no* and then, after
    // the slash, the *daire no* or the block letter. Counted over Turkey:
    // 235 200 house numbers, 60 184 carrying something, 35 575 of them — 59 %
    // — opening with "/", against 11 042 letters closed up and 8 021 spaced.
    // "/A" 9 138 and "/1" 7 982 lead, so the slash carries both kinds and
    // neither survives without it: "12/1" closed up reads "121". Konya's own
    // box holds 947 numbers, too thin to rule on alone, and agrees anyway —
    // 169 of its 276 suffixes are slashed. The *No:* a Turkish plate prints
    // before the number is a word, not punctuation, and stays out of
    // [streetSeparator].
    "tr" to AddressLayout(numberComesFirst = false, streetSeparator = " ", suffixSeparator = "/"),

    // Arabic has **no entry on purpose**, and this note is the entry. Careem
    // BIKE's Dubai box holds 22 616 house numbers, of which 15.1 % are not
    // numbers at all — "Tower A", "C-18", "2nd Floor", a Makani code — and
    // only 3.3 % trail anything: 124 glued letters against 100 slashed
    // numbers, with 334 of the 746 trailing values being a space and a
    // district name. Two samples of the same nothing. The order is no clearer:
    // Egypt writes "١٢ شارع طلعت حرب" and the Gulf writes "Villa 12, 25a
    // Street", and 46.8 % of this very index's street names are in Arabic
    // script against 53.2 % in Latin, so one order is wrong for one half
    // either way. [DEFAULT_LAYOUT] therefore serves it, and the absence of a
    // key says honestly that nobody has counted a real Arabic address base
    // yet — which an entry repeating the default would hide.
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

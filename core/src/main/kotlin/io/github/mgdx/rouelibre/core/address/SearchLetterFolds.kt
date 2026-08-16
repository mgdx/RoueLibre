package io.github.mgdx.rouelibre.core.address

import java.text.Normalizer

/**
 * The letter folds the searches that carry no language apply (SPEC §4.3).
 *
 * The address index is searched in one language, the one its base is written
 * in, and [AddressNormalizer] holds that language's rules. Two other searches
 * carry no language at all: the station list of a network that names its stops
 * in whatever it pleases, and the city catalogue, which spans three hundred and
 * thirty-two networks across some forty countries. Somebody typing "bialystok"
 * on an ordinary keyboard is looking for "Białystok" whatever the phone speaks.
 *
 * So the folds are gathered from every rule set the application ships rather
 * than written out again here: the table exists once, in
 * `config/address-normalization/`, where the indexing script reads it too. A
 * letter belongs to an alphabet, so the sets barely overlap, and where they do —
 * "đ" in Bosnian and in Croatian, "æ" in half of northern Europe — they agree.
 *
 * @param normalizers the rule sets shipped, one per language.
 * @return the letters to fold and what they fold to.
 */
public fun searchLetterFolds(normalizers: Iterable<AddressNormalizer>): Map<Char, String> =
    normalizers
        .flatMap { it.letterReplacements.entries }
        .filterNot { (letter, _) -> letter.isReachedByAccentRemoval() }
        .associate { (letter, replacement) -> letter to replacement }

/**
 * Whether decomposing this letter already separates a base letter from a mark.
 *
 * Such a letter is spoken for: the search strips the mark and keeps the base,
 * and a language's own spelling of it must not override that. Danish writes "å"
 * as "aa", which is right for a Danish address base and wrong for a field where
 * anyone may type anything — it would stop "alborg" from finding "Ålborg",
 * which works today. What is left after this filter is what accent removal
 * genuinely cannot reach: "ł", "ß", "ø", "đ", "ı", the letters SPEC §4.3 names.
 */
private fun Char.isReachedByAccentRemoval(): Boolean =
    Normalizer.normalize(toString(), Normalizer.Form.NFD).length > 1

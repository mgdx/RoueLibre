package io.github.mgdx.rouelibre.core.address

import io.github.mgdx.rouelibre.core.station.foldForSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests of the folds applied by the searches that carry no language.
 *
 * They are read from `config/address-normalization/`, the repository's only
 * table of them, so these tests also say that the table shipped still covers
 * the letters SPEC §4.3 names. Writing the pairs out here would have made a
 * second copy, which is the thing to avoid.
 */
class SearchLetterFoldsTest {

    private val folds = searchLetterFolds(TestRules.languages().map(TestRules::of))

    private fun fold(text: String) = foldForSearch(text, folds)

    @Test
    fun `the letters accent removal cannot reach are folded`() {
        assertEquals("l", fold("ł"))
        assertEquals("l", fold("Ł"))
        assertEquals("ss", fold("ß"))
        assertEquals("o", fold("ø"))
        assertEquals("d", fold("đ"))
    }

    @Test
    fun `the cities the catalogue could not find are found`() {
        assertEquals("bialystok", fold("Białystok"))
        assertEquals("giessen", fold("Gießen"))
        assertEquals("lomza", fold("Łomża"))
        assertEquals("wloclawek", fold("Włocławek"))
        assertEquals("chelm", fold("Chełm"))
        assertEquals("jaskolka", fold("JasKółka"))
        assertEquals("mlawa", fold("Mława"))
    }

    @Test
    fun `a letter accent removal already reaches keeps its base letter`() {
        // Danish spells "å" as "aa" when indexing a Danish address base, which
        // is right there and wrong in a field where anyone types anything: the
        // fold must not stop "alborg" from finding "Ålborg".
        assertEquals("alborg", fold("Ålborg"))
        assertEquals("theatre", fold("Théâtre"))
        assertEquals("nimes", fold("Nîmes"))
        assertFalse('å' in folds)
        assertFalse('ñ' in folds)
    }

    @Test
    fun `a fold that yields two letters is decomposed in its turn`() {
        // The trap AddressNormalizer met before this one: "ß" gives "ss", and
        // whatever a fold produces still has to go through accent removal.
        assertEquals("grossenhain", fold("Großenhain"))
        assertEquals("aero", fold("Ærø"))
    }

    @Test
    fun `folding stays idempotent and leaves ordinary text alone`() {
        assertEquals("gare lille flandres", fold("Gare Lille Flandres"))
        assertEquals(fold("Białystok"), fold(fold("Białystok")))
    }

    @Test
    fun `the shipped rules still cover the letters the specification names`() {
        // SPEC §4.3 names ß, ł and ø. A rules file losing one of them would
        // make cities unfindable again without any other test noticing.
        listOf('ß', 'ł', 'ø').forEach {
            assertTrue("the shipped rules no longer fold \"$it\"", it in folds)
        }
    }

    @Test
    fun `empty folds still strip the marks`() {
        // What a caller with no rule set at hand gets: the previous behaviour,
        // unchanged.
        assertEquals("theatre", foldForSearch("Théâtre", emptyMap()))
        assertEquals("bialystok", foldForSearch("Białystok", folds))
    }
}

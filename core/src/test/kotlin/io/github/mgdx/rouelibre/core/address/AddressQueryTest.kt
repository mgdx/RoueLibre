package io.github.mgdx.rouelibre.core.address

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests du démontage d'une saisie en numéro de voirie et mots de recherche. */
class AddressQueryTest {

    private val normalizer = testNormalizer()

    private fun parse(raw: String) = normalizer.parseQuery(raw)

    @Test
    fun `a leading house number is recognised`() {
        val query = parse("12 rue Nationale")

        assertEquals(12, query.houseNumber)
        assertEquals("", query.houseNumberSuffix)
        assertEquals(listOf("rue", "nationale"), query.terms)
    }

    @Test
    fun `a house number at the end of the query is recognised too`() {
        // Les deux ordres d'écriture se pratiquent.
        val query = parse("rue Nationale 12")

        assertEquals(12, query.houseNumber)
        assertEquals(listOf("rue", "nationale"), query.terms)
    }

    @Test
    fun `the repetition mark travels with the number`() {
        assertEquals("bis", parse("12 bis rue Nationale").houseNumberSuffix)
        assertEquals("ter", parse("rue Nationale 12 ter").houseNumberSuffix)
        assertEquals("a", parse("5 A boulevard de la Liberté").houseNumberSuffix)
    }

    @Test
    fun `a bare number does not become an empty search`() {
        // Sans mots, il n'y a aucune voie à chercher : mieux vaut traiter le
        // nombre comme un mot ordinaire que de chercher « rien ».
        val query = parse("12")

        assertNull(query.houseNumber)
        assertEquals(listOf("12"), query.terms)
    }

    @Test
    fun `an implausible number is not a house number`() {
        // 59000 est un code postal, pas un numéro.
        val query = parse("59000 rue Nationale")

        assertNull(query.houseNumber)
    }

    @Test
    fun `a postcode is removed from the searched words`() {
        // L'index n'indexe pas les codes postaux en texte intégral : le
        // garder ferait échouer une saisie par ailleurs juste.
        val query = parse("rue Nationale 59800 Lille")

        assertEquals(listOf("rue", "nationale", "lille"), query.terms)
    }

    @Test
    fun `an empty query searches for nothing`() {
        assertTrue(parse("").isEmpty)
        assertTrue(parse("   ").isEmpty)
        assertTrue(parse("...").isEmpty)
    }

    @Test
    fun `the query's abbreviations are expanded as they are at indexing time`() {
        assertEquals(listOf("boulevard", "victor", "hugo"), parse("bd victor hugo").terms)
    }
}

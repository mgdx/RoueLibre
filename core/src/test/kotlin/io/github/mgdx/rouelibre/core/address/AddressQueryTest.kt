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
    fun `le numero en tete est reconnu`() {
        val query = parse("12 rue Nationale")

        assertEquals(12, query.houseNumber)
        assertEquals("", query.houseNumberSuffix)
        assertEquals(listOf("rue", "nationale"), query.terms)
    }

    @Test
    fun `le numero en fin de saisie est reconnu aussi`() {
        // Les deux ordres d'écriture se pratiquent.
        val query = parse("rue Nationale 12")

        assertEquals(12, query.houseNumber)
        assertEquals(listOf("rue", "nationale"), query.terms)
    }

    @Test
    fun `l'indice de repetition accompagne le numero`() {
        assertEquals("bis", parse("12 bis rue Nationale").houseNumberSuffix)
        assertEquals("ter", parse("rue Nationale 12 ter").houseNumberSuffix)
        assertEquals("a", parse("5 A boulevard de la Liberté").houseNumberSuffix)
    }

    @Test
    fun `un numero seul ne devient pas une recherche vide`() {
        // Sans mots, il n'y a aucune voie à chercher : mieux vaut traiter le
        // nombre comme un mot ordinaire que de chercher « rien ».
        val query = parse("12")

        assertNull(query.houseNumber)
        assertEquals(listOf("12"), query.terms)
    }

    @Test
    fun `un nombre invraisemblable n'est pas un numero de voirie`() {
        // 59000 est un code postal, pas un numéro.
        val query = parse("59000 rue Nationale")

        assertNull(query.houseNumber)
    }

    @Test
    fun `un code postal est retire des mots cherches`() {
        // L'index n'indexe pas les codes postaux en texte intégral : le
        // garder ferait échouer une saisie par ailleurs juste.
        val query = parse("rue Nationale 59800 Lille")

        assertEquals(listOf("rue", "nationale", "lille"), query.terms)
    }

    @Test
    fun `une saisie vide ne cherche rien`() {
        assertTrue(parse("").isEmpty)
        assertTrue(parse("   ").isEmpty)
        assertTrue(parse("...").isEmpty)
    }

    @Test
    fun `les abreviations de la saisie sont developpees comme a l'indexation`() {
        assertEquals(listOf("boulevard", "victor", "hugo"), parse("bd victor hugo").terms)
    }
}

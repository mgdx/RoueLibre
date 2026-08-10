package io.github.mgdx.rouelibre.core.address

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests de la distance d'édition qui porte le rattrapage des fautes de frappe. */
class EditDistanceTest {

    private fun distance(source: String, target: String, maximum: Int = 3) =
        boundedDamerauLevenshteinDistance(source, target, maximum)

    @Test
    fun `two identical words are at distance zero`() {
        assertEquals(0, distance("gambetta", "gambetta"))
    }

    @Test
    fun `a letter changed, added or removed counts as one mistake`() {
        assertEquals(1, distance("gambetta", "gambetto"))
        assertEquals(1, distance("gambetta", "gambettta"))
        assertEquals(1, distance("gambetta", "gambeta"))
    }

    @Test
    fun `two transposed letters count as a single mistake`() {
        // C'est ce qui distingue Damerau-Levenshtein de Levenshtein, et c'est
        // la faute la plus courante au clavier tactile.
        assertEquals(1, distance("gambetta", "gambetat"))
        assertEquals(1, distance("nationale", "natioanle"))
    }

    @Test
    fun `the cap is respected without being silently exceeded`() {
        assertTrue(distance("lille", "roubaix", maximum = 2) > 2)
        assertTrue(distance("gambetta", "gambetta", maximum = 0) == 0)
    }

    @Test
    fun `a length difference beyond the cap is ruled out immediately`() {
        assertTrue(distance("gare", "gambetta", maximum = 2) > 2)
    }

    @Test
    fun `an empty word costs the other its length`() {
        assertEquals(0, distance("", ""))
        assertEquals(3, distance("", "rue"))
        assertEquals(3, distance("rue", ""))
    }

    @Test
    fun `the tolerance follows the word's length`() {
        // Une faute en dessous de huit caractères, deux au-delà (SPEC §4.3).
        assertEquals(1, toleratedMistakes("gare"))
        assertEquals(1, toleratedMistakes("nationa"))
        assertEquals(2, toleratedMistakes("nationale"))
    }
}

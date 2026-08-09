package io.github.mgdx.rouelibre.core.address

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests de la distance d'édition qui porte le rattrapage des fautes de frappe. */
class EditDistanceTest {

    private fun distance(source: String, target: String, maximum: Int = 3) =
        boundedDamerauLevenshteinDistance(source, target, maximum)

    @Test
    fun `deux mots identiques sont a distance nulle`() {
        assertEquals(0, distance("gambetta", "gambetta"))
    }

    @Test
    fun `une lettre changee, ajoutee ou retiree vaut une faute`() {
        assertEquals(1, distance("gambetta", "gambetto"))
        assertEquals(1, distance("gambetta", "gambettta"))
        assertEquals(1, distance("gambetta", "gambeta"))
    }

    @Test
    fun `deux lettres interverties valent une seule faute`() {
        // C'est ce qui distingue Damerau-Levenshtein de Levenshtein, et c'est
        // la faute la plus courante au clavier tactile.
        assertEquals(1, distance("gambetta", "gambetat"))
        assertEquals(1, distance("nationale", "natioanle"))
    }

    @Test
    fun `le plafond est respecte sans etre depasse silencieusement`() {
        assertTrue(distance("lille", "roubaix", maximum = 2) > 2)
        assertTrue(distance("gambetta", "gambetta", maximum = 0) == 0)
    }

    @Test
    fun `une difference de longueur superieure au plafond est ecartee tout de suite`() {
        assertTrue(distance("gare", "gambetta", maximum = 2) > 2)
    }

    @Test
    fun `un mot vide coute sa longueur a l'autre`() {
        assertEquals(0, distance("", ""))
        assertEquals(3, distance("", "rue"))
        assertEquals(3, distance("rue", ""))
    }

    @Test
    fun `la tolerance suit la longueur du mot`() {
        // Une faute en dessous de huit caractères, deux au-delà (SPEC §4.3).
        assertEquals(1, toleratedMistakes("gare"))
        assertEquals(1, toleratedMistakes("nationa"))
        assertEquals(2, toleratedMistakes("nationale"))
    }
}

package io.github.mgdx.rouelibre.core.station

import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests du filtrage de la liste des stations par leur nom. */
class StationSearchTest {

    private fun entry(name: String, postalCode: String? = "59000") = StationWithAvailability(
        station = Station(
            id = name,
            name = name,
            position = Coordinates(50.63, 3.06),
            capacity = 20,
            postalCode = postalCode,
        ),
        availability = null,
    )

    private val stations = listOf(
        entry("Gare Lille Flandres"),
        entry("Place du Théâtre"),
        entry("Metropole Europeenne de Lille (CB)"),
        entry("Saint-André", postalCode = "59350"),
        entry("Wazemmes Marché"),
        entry("4 vents", postalCode = "59260"),
    )

    private fun names(query: String) = filterStations(stations, query).map { it.station.name }

    @Test
    fun `une saisie vide rend la liste intacte`() {
        assertEquals(stations.size, filterStations(stations, "").size)
        assertEquals(stations.size, filterStations(stations, "   ").size)
    }

    @Test
    fun `une saisie faite de ponctuation seule rend la liste intacte`() {
        // Effacer un champ de recherche doit tout ramener, pas tout masquer.
        assertEquals(stations.size, filterStations(stations, "---").size)
    }

    @Test
    fun `la recherche ignore la casse`() {
        assertEquals(listOf("Wazemmes Marché"), names("WAZEMMES"))
    }

    @Test
    fun `la recherche accentuee trouve un nom publie sans accents`() {
        // Le flux lillois publie « Metropole Europeenne » sans accents, mais
        // personne ne tape comme ça.
        assertEquals(
            listOf("Metropole Europeenne de Lille (CB)"),
            names("métropole européenne"),
        )
    }

    @Test
    fun `la recherche sans accents trouve un nom accentue`() {
        assertEquals(listOf("Place du Théâtre"), names("theatre"))
    }

    @Test
    fun `l'ordre des mots n'a pas d'importance`() {
        assertEquals(listOf("Gare Lille Flandres"), names("flandres gare"))
    }

    @Test
    fun `la frappe en cours trouve deja la station`() {
        assertEquals(listOf("Wazemmes Marché"), names("waz"))
        assertEquals(listOf("Wazemmes Marché"), names("wazemmes mar"))
    }

    @Test
    fun `un tiret vaut une separation de mots`() {
        assertEquals(listOf("Saint-André"), names("andre"))
        assertEquals(listOf("Saint-André"), names("saint andre"))
    }

    @Test
    fun `le code postal est cherchable puisqu'il est affiche`() {
        assertEquals(listOf("Saint-André"), names("59350"))
    }

    @Test
    fun `une station sans code postal reste cherchable par son nom`() {
        val orphan = listOf(entry("Solférino", postalCode = null))
        assertEquals(
            listOf("Solférino"),
            filterStations(orphan, "solferino").map {
                it.station.name
            },
        )
    }

    @Test
    fun `tous les mots de la saisie doivent correspondre`() {
        assertEquals(emptyList<String>(), names("gare wazemmes"))
    }

    @Test
    fun `une saisie sans correspondance rend une liste vide`() {
        assertEquals(emptyList<String>(), names("bruxelles"))
    }

    @Test
    fun `l'ordre d'affichage est conserve`() {
        assertEquals(
            listOf("Gare Lille Flandres", "Metropole Europeenne de Lille (CB)"),
            names("lille"),
        )
    }

    @Test
    fun `un nom commencant par un chiffre se cherche par ce chiffre`() {
        assertEquals(listOf("4 vents"), names("4"))
    }
}

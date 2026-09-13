package io.github.mgdx.rouelibre.core.station

import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.Collator
import java.util.Locale

/**
 * Tests of the order the station list is shown in, and above all of the
 * alphabet it is shown in: an accented initial belongs among its own letters,
 * not at the foot of the list.
 */
class StationOrderingTest {

    private fun entry(name: String, latitude: Double = 50.633, longitude: Double = 3.053) =
        StationWithAvailability(
            station = Station(
                id = name,
                name = name,
                position = Coordinates(latitude, longitude),
                capacity = 20,
                postalCode = null,
            ),
            availability = null,
        )

    private fun namesOrderedIn(locale: Locale, names: List<String>): List<String> =
        orderStations(names.map(::entry), around = null, byName = Collator.getInstance(locale))
            .map { it.station.name }

    @Test
    fun `a French accented initial takes its place in the alphabet`() {
        // The order the Reims feed used to come out of the cache in: "BÉTHENY"
        // filed after "BUIRETTE", and "ÉPINETTES" past the very last station.
        val names = listOf(
            "BELGES",
            "BEZANNES",
            "BUIRETTE",
            "DOCKS RÉMOIS",
            "ERLON 1",
            "TINQUEUX LA HAUBETTE",
            "BÉTHENY MAIRIE ANNEXE",
            "ÉPINETTES",
        )

        assertEquals(
            listOf(
                "BELGES",
                "BÉTHENY MAIRIE ANNEXE",
                "BEZANNES",
                "BUIRETTE",
                "DOCKS RÉMOIS",
                "ÉPINETTES",
                "ERLON 1",
                "TINQUEUX LA HAUBETTE",
            ),
            namesOrderedIn(Locale.FRENCH, names),
        )
    }

    @Test
    fun `a Romanian name is read at the letter its mark is drawn over`() {
        // Sibiu, read from an interface speaking French: "ă" and "ț" are the
        // letters they are drawn over, so "Piața Sadu" falls between "Piata
        // Rahovei" and "Piata Vasile Aaron" instead of after all of them.
        val names = listOf(
            "Magnolia",
            "Maramuresului",
            "Piata Mica",
            "Piata Rahovei",
            "Piata Vasile Aaron",
            "Măgheranului",
            "Piața Sadu",
        )

        assertEquals(
            listOf(
                // "Măgheranului" before "Magnolia" and not between it and
                // "Maramuresului": once the mark is read as its base letter,
                // "magh" comes before "magn" on the fourth letter.
                "Măgheranului",
                "Magnolia",
                "Maramuresului",
                "Piata Mica",
                "Piata Rahovei",
                "Piața Sadu",
                "Piata Vasile Aaron",
            ),
            namesOrderedIn(Locale.FRENCH, names),
        )
    }

    @Test
    fun `the reader's own alphabet decides, Romanian ordering its own letters`() {
        // Romanian files "ă" after every "a" rather than over it, and a rider
        // reading the interface in Romanian is owed that order. This is why the
        // collator comes from the caller: no order suits every language at once.
        val names = listOf("Magnolia", "Măgheranului", "Maramuresului")

        assertEquals(
            listOf("Magnolia", "Maramuresului", "Măgheranului"),
            namesOrderedIn(Locale.forLanguageTag("ro"), names),
        )
    }

    @Test
    fun `Turkish keeps its dotless i apart from its dotted one`() {
        val names = listOf("Isparta", "İzmir", "Iğdır")

        assertEquals(
            listOf("Iğdır", "Isparta", "İzmir"),
            namesOrderedIn(Locale.forLanguageTag("tr"), names),
        )
    }

    @Test
    fun `a known position orders by distance and leaves the alphabet alone`() {
        val here = Coordinates(50.633, 3.053)
        val stations = listOf(
            entry("ÉPINETTES", latitude = 50.640),
            entry("BELGES", latitude = 50.700),
            entry("BEZANNES", latitude = 50.635),
        )

        assertEquals(
            listOf("BEZANNES", "ÉPINETTES", "BELGES"),
            orderStations(stations, around = here, byName = Collator.getInstance(Locale.FRENCH))
                .map { it.station.name },
        )
    }
}

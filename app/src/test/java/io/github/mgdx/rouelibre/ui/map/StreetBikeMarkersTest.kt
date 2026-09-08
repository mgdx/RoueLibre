package io.github.mgdx.rouelibre.ui.map

import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.station.StreetBike
import io.github.mgdx.rouelibre.core.station.VehicleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.Point

/**
 * Tests the features the map draws the bikes outside stations from (SPEC §7.1).
 *
 * Three things are checked, and the third is the one worth writing down: a
 * bike carries **no count**. A "1" on every marker would be the same figure
 * two hundred times over on a map whose stations use that figure to say
 * something — and the station markers' own property is what such a mistake
 * would be made of.
 */
class StreetBikeMarkersTest {

    private fun bike(id: String, typeId: String?) = StreetBike(
        id = id,
        position = Coordinates(43.3, 5.4),
        vehicleTypeId = typeId,
        chargeRatio = null,
        rangeMetres = null,
    )

    /** levélo's own table, as the vehicle type feed hands it over. */
    private val types = mapOf(
        "bike" to VehicleKind.Mechanical,
        "ebike" to VehicleKind.Electric,
    )

    @Test
    fun `every bike given becomes one feature, at its own position`() {
        val bikes = listOf(bike("a", "bike"), bike("b", "ebike"))

        val features = checkNotNull(StreetBikeMarkers.toFeatureCollection(bikes, types).features())

        assertEquals(2, features.size)
        assertEquals(
            listOf("a", "b"),
            features.map { it.getStringProperty(StreetBikeMarkers.BIKE_ID_PROPERTY) },
        )
        val point = features.first().geometry() as Point
        assertEquals(5.4, point.longitude(), 1e-9)
        assertEquals(43.3, point.latitude(), 1e-9)
    }

    @Test
    fun `the bolt is carried by the bikes the type table reads as electric`() {
        val bikes = listOf(bike("a", "bike"), bike("b", "ebike"))

        val features = checkNotNull(StreetBikeMarkers.toFeatureCollection(bikes, types).features())

        assertFalse(features[0].getBooleanProperty(StreetBikeMarkers.ELECTRIC_PROPERTY))
        assertTrue(features[1].getBooleanProperty(StreetBikeMarkers.ELECTRIC_PROPERTY))
    }

    @Test
    fun `a type the table does not know is drawn plain`() {
        // The table is read once per session and grows as the network is read
        // (SPEC §4.1): before it lands, and for a type added since, the plain
        // disc is the drawing that promises the least.
        val features = checkNotNull(
            StreetBikeMarkers.toFeatureCollection(
                listOf(bike("a", "cargo-added-last-week"), bike("b", null)),
                emptyMap(),
            ).features(),
        )

        assertTrue(features.none { it.getBooleanProperty(StreetBikeMarkers.ELECTRIC_PROPERTY) })
    }

    @Test
    fun `no bike carries a count`() {
        // A bike is one bike, and a figure would say so of every one of them
        // (SPEC §7.1). The station markers' count property is the one this
        // source must never be found holding.
        val features = checkNotNull(
            StreetBikeMarkers.toFeatureCollection(listOf(bike("a", "ebike")), types).features(),
        )

        assertNull(features.single().getProperty(StationMarkers.COUNT_PROPERTY))
    }

    @Test
    fun `a network reporting nothing outside its stations draws nothing`() {
        val features = checkNotNull(
            StreetBikeMarkers.toFeatureCollection(emptyList(), types).features(),
        )

        assertTrue(features.isEmpty())
    }
}

package io.github.mgdx.rouelibre.ui.map

import io.github.mgdx.rouelibre.core.config.MapDefaults
import io.github.mgdx.rouelibre.core.geo.BoundingBox
import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the main map looks, decided apart from the camera (SPEC §7.1).
 *
 * The anomaly the framing rules hold shut: the camera outlives the view, and
 * once outlived a change of city too, reopening the map of Paris over Lille on
 * a grey screen nothing explained. The rebuild rule holds shut its companion
 * (fb3b6e16): a map rebuilt for another city that kept the old one's framing,
 * zoom limits and served area, because the tiles' path alone could not tell a
 * city updated from a city changed.
 */
class MainMapFramingTest {

    @Test
    fun `a framing remembered inside the city holds`() {
        assertTrue(rememberedFramingSuits(IN_LILLE, LILLE))
    }

    @Test
    fun `a framing remembered in another city does not`() {
        assertFalse(rememberedFramingSuits(IN_PARIS, LILLE))
    }

    @Test
    fun `a framing of nowhere holds for no box`() {
        assertFalse(rememberedFramingSuits(null, LILLE))
    }

    @Test
    fun `a city declaring no box keeps whatever was remembered`() {
        assertTrue(rememberedFramingSuits(IN_PARIS, null))
        assertTrue(rememberedFramingSuits(null, null))
    }

    @Test
    fun `the map opens on a position held inside the city`() {
        assertTrue(opensOnThePosition(IN_LILLE, LILLE))
        assertFalse(opensOnThePosition(IN_PARIS, LILLE))
    }

    /**
     * Unlike a remembered framing: not knowing where the city stops is no
     * ground for opening on a position that may be anywhere.
     */
    @Test
    fun `a city declaring no box opens on no position`() {
        assertFalse(opensOnThePosition(IN_LILLE, null))
    }

    @Test
    fun `the opening framing is the position at the street's zoom`() {
        assertEquals(Framing(IN_LILLE, USER_POSITION_ZOOM), openingFraming(IN_LILLE, DEFAULTS))
    }

    @Test
    fun `without a position the configured centring stands`() {
        assertEquals(Framing(LILLE_CENTRE, 12.5), openingFraming(null, DEFAULTS))
    }

    @Test
    fun `a style built over the very file has nothing to do`() {
        assertEquals(
            MapRebuild.Nothing,
            mapRebuildFor(true, TILES_V1, TILES_V1, previousCityId = "vlille", cityId = "vlille"),
        )
    }

    @Test
    fun `the same city's data replaced rebuilds the style alone`() {
        assertEquals(
            MapRebuild.StyleOnly,
            mapRebuildFor(true, TILES_V1, TILES_V2, previousCityId = "vlille", cityId = "vlille"),
        )
    }

    @Test
    fun `another city rebuilds everything, framing included`() {
        assertEquals(
            MapRebuild.Everything,
            mapRebuildFor(true, TILES_V1, TILES_VELIB, previousCityId = "vlille", cityId = "velib"),
        )
    }

    @Test
    fun `a first style rebuilds everything`() {
        assertEquals(
            MapRebuild.Everything,
            mapRebuildFor(false, null, TILES_V1, previousCityId = null, cityId = "vlille"),
        )
        assertEquals(
            MapRebuild.Everything,
            mapRebuildFor(false, null, TILES_V1, previousCityId = "vlille", cityId = "vlille"),
        )
    }

    /** The tiles gone, or the city given up: the panel that says so is raised. */
    @Test
    fun `a style over tiles no longer there rebuilds everything`() {
        assertEquals(
            MapRebuild.Everything,
            mapRebuildFor(true, TILES_V1, null, previousCityId = "vlille", cityId = "vlille"),
        )
        assertEquals(
            MapRebuild.Everything,
            mapRebuildFor(true, TILES_V1, null, previousCityId = "vlille", cityId = null),
        )
    }

    private companion object {
        val LILLE = BoundingBox(south = 50.5, west = 2.9, north = 50.8, east = 3.3)
        val LILLE_CENTRE = Coordinates(50.6365, 3.0635)
        val IN_LILLE = Coordinates(50.63, 3.06)
        val IN_PARIS = Coordinates(48.8566, 2.3522)
        val DEFAULTS = MapDefaults(LILLE_CENTRE, defaultZoom = 12.5, minZoom = 10, maxZoom = 16)
        const val TILES_V1 = "/data/vlille/tiles-aaaa.mbtiles"
        const val TILES_V2 = "/data/vlille/tiles-bbbb.mbtiles"
        const val TILES_VELIB = "/data/velib/tiles-cccc.mbtiles"
    }
}

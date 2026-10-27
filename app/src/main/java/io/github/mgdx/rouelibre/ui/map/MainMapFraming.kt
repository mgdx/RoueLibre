package io.github.mgdx.rouelibre.ui.map

import io.github.mgdx.rouelibre.core.config.MapDefaults
import io.github.mgdx.rouelibre.core.geo.BoundingBox
import io.github.mgdx.rouelibre.core.geo.Coordinates

/**
 * The choices the main map makes about where it looks, apart from the camera
 * that carries them out (SPEC §7.1).
 *
 * Pure Kotlin, so that each can be read and tested without an Android runtime
 * (SPEC §14): which framing a map opens on, whether a remembered one still
 * holds, and how much of the map a city reaching the screen rebuilds.
 */

/** The zoom the map settles at on the user's position. */
internal const val USER_POSITION_ZOOM = 16.0

/** The zoom the map settles at on a found address: the street. */
internal const val PICKED_PLACE_ZOOM = 16.0

/** Where the camera looks, and from how close. */
internal data class Framing(val target: Coordinates, val zoom: Double)

/**
 * Says whether a remembered framing still holds for this city.
 *
 * The camera survives the destruction of the view, so a trip to another
 * screen does not lose everything. But it also survives a change of city:
 * taken as is, it would reopen the map of Paris over Lille, outside the
 * tiles, on a grey screen that nothing explains.
 *
 * @param target where the remembered framing looks, `null` when it names no
 *   point — and a framing of nowhere holds for no box.
 * @param servedArea the city's box, `null` when it declares none: nothing is
 *   then outside anything.
 */
internal fun rememberedFramingSuits(target: Coordinates?, servedArea: BoundingBox?): Boolean {
    val box = servedArea ?: return true
    val centre = target ?: return false
    return centre in box
}

/**
 * Whether the map may open on a position the system already holds.
 *
 * Only inside the served city: a position outside it would be framed at the
 * nearest edge the camera may reach, and passed off as where the user is. A
 * city declaring no box is no exception here — not knowing where the city
 * stops is no ground for opening on a position that may be anywhere.
 */
internal fun opensOnThePosition(position: Coordinates, servedArea: BoundingBox?): Boolean =
    servedArea?.let { position in it } == true

/**
 * How the map is framed when nothing is remembered.
 *
 * On the user's position when it is already known, since that is what they
 * came to see: the stations around them, not the middle of the conurbation.
 * Without one, the configured centring stands — never a position obtained
 * without the user's knowledge (SPEC §10).
 *
 * @param position the position to open on, already found to be inside the
 *   city by [opensOnThePosition], or `null`.
 */
internal fun openingFraming(position: Coordinates?, defaults: MapDefaults): Framing =
    if (position != null) {
        Framing(position, USER_POSITION_ZOOM)
    } else {
        Framing(defaults.centre, defaults.defaultZoom)
    }

/**
 * The framing a map opens on when it remembers nothing of its own.
 *
 * The map that picks a point is handed the main map's last framing (issue
 * #14): one zooms onto a street, goes to name an end of a journey, and the
 * street is there. A framing handed over is held to the same rule as one
 * remembered, since it may be of a city given up since; failing it, the
 * opening framing stands, as it does for a main map seen for the first time.
 *
 * @param handedOver the framing passed by whoever opened the map, `null`
 *   when there is none.
 * @param position the position to open on, asked for only when the handed
 *   framing does not hold — that is the only case it is used in.
 */
internal fun framingToOpenOn(
    handedOver: Framing?,
    servedArea: BoundingBox?,
    defaults: MapDefaults,
    position: () -> Coordinates?,
): Framing = handedOver?.takeIf { rememberedFramingSuits(it.target, servedArea) }
    ?: openingFraming(position(), defaults)

/** How much of the main map a city reaching the screen rebuilds. */
internal enum class MapRebuild {
    /** The style in place was built over this very file: nothing to do. */
    Nothing,

    /**
     * The same city, whose data has been replaced underneath: the style alone
     * is built again, over the new file. The framing, the zoom limits and the
     * served area are the ones already in force, and the reader is looking at
     * them.
     */
    StyleOnly,

    /** Everything: a first display, another city, or no base map at all. */
    Everything,
}

/**
 * Says how much of the map is to be rebuilt for a city reaching the screen.
 *
 * A style already built over this very file has nothing to do, and this is
 * the common case, every return to the screen going through here. A style
 * built over ANOTHER file is a city whose data has been updated since, and it
 * has to be built again — MapLibre holds the MBTiles it opened for as long as
 * the process lives, so a map left pointing at the old one goes on drawing it.
 *
 * Doing everything a second time would register a second click listener and
 * move the camera out from under the user, so only the style is built again.
 * The same city, though: the path alone cannot say which case it is, a path
 * carrying the city identifier and differing in both, and another city brings
 * its own framing, its own zoom limits and its own served area.
 *
 * @param styleLoaded whether a style is in place on this view's map.
 * @param loadedTilesPath the base map that style was built from.
 * @param tilesPath the base map now installed for the city, `null` when there
 *   is none or no city is chosen.
 * @param previousCityId the network identifier of the city served until now.
 * @param cityId the network identifier of the city reaching the screen.
 */
internal fun mapRebuildFor(
    styleLoaded: Boolean,
    loadedTilesPath: String?,
    tilesPath: String?,
    previousCityId: String?,
    cityId: String?,
): MapRebuild = when {
    styleLoaded && tilesPath == loadedTilesPath -> MapRebuild.Nothing
    styleLoaded && tilesPath != null && cityId == previousCityId -> MapRebuild.StyleOnly
    else -> MapRebuild.Everything
}

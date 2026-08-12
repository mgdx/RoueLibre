package io.github.mgdx.rouelibre.ui.map

import android.content.Context
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import io.github.mgdx.rouelibre.R
import java.io.File

/**
 * Composes the map style from the template embedded in the APK.
 *
 * Two things cannot be written into the style file.
 *
 * **The colours**, because they belong to the project's token set and change
 * with the theme (SPEC §7). Writing them into the style would mean maintaining
 * two nearly identical files, which would diverge.
 *
 * **The tiles' path**, because the file is installed in the application's
 * private storage and its location is only known at run time.
 */
object MapStyleLoader {

    private const val STYLE_ASSET = "map/style.json"
    private const val TILES_PLACEHOLDER = "{{tilesPath}}"

    /**
     * The colour tokens injected into the style.
     *
     * The key's name is the resource's own: a forgotten token stands out, in
     * the style as much as here.
     */
    private val COLOUR_TOKENS: Map<String, Int> = mapOf(
        "map_land" to R.color.map_land,
        "map_water" to R.color.map_water,
        "map_greenery" to R.color.map_greenery,
        "map_wood" to R.color.map_wood,
        "map_building" to R.color.map_building,
        "map_building_edge" to R.color.map_building_edge,
        "map_road_major" to R.color.map_road_major,
        "map_road_major_edge" to R.color.map_road_major_edge,
        "map_road_minor" to R.color.map_road_minor,
        "map_road_minor_edge" to R.color.map_road_minor_edge,
        "map_path" to R.color.map_path,
        "map_cycleway" to R.color.map_cycleway,
        "map_rail" to R.color.map_rail,
        "map_boundary" to R.color.map_boundary,
        "map_label" to R.color.map_label,
        "map_label_strong" to R.color.map_label_strong,
        "map_label_halo" to R.color.map_label_halo,
        "map_marker_minor" to R.color.map_marker_minor,
        "map_water_label" to R.color.map_water_label,
        "map_greenery_label" to R.color.map_greenery_label,
    )

    /**
     * Returns the style ready to be handed to MapLibre.
     *
     * @param context used to read the asset and to resolve the current theme's
     *   colours; it must therefore be a view's context, not the application's,
     *   otherwise the dark theme would be ignored.
     * @param tilesFile the installed MBTiles file.
     * @return the complete style, as JSON.
     */
    fun load(context: Context, tilesFile: File): String {
        var style = context.assets.open(STYLE_ASSET)
            .bufferedReader()
            .use { it.readText() }

        for ((token, colourResource) in COLOUR_TOKENS) {
            style = style.replace("{{$token}}", hexOf(context, colourResource))
        }
        // MapLibre reads the MBTiles straight from disk: no tile request goes
        // out on the network (SPEC §4.2).
        return style.replace(TILES_PLACEHOLDER, tilesFile.absolutePath)
    }

    /**
     * Converts a resource colour into hexadecimal notation.
     *
     * The `#rrggbb` form rather than `#aarrggbb`: the style specification
     * follows CSS notation, where alpha comes last. Every base-map colour is
     * opaque, so alpha has no need to travel.
     */
    private fun hexOf(context: Context, @ColorRes colourResource: Int): String {
        val colour = ContextCompat.getColor(context, colourResource)
        return String.format("#%06X", colour and 0xFFFFFF)
    }
}

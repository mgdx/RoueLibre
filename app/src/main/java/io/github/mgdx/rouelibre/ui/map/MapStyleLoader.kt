package io.github.mgdx.rouelibre.ui.map

import android.content.Context
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import io.github.mgdx.rouelibre.R
import java.io.File

/**
 * Compose le style de la carte à partir du modèle embarqué dans l'APK.
 *
 * Deux choses ne peuvent pas être écrites dans le fichier de style.
 *
 * **Les couleurs**, parce qu'elles appartiennent au jeu de jetons du projet et
 * changent avec le thème (SPEC §7). Les inscrire dans le style obligerait à
 * maintenir deux fichiers presque identiques, qui divergeraient.
 *
 * **Le chemin des tuiles**, parce que le fichier est installé dans le stockage
 * privé de l'application et que son emplacement n'est connu qu'à l'exécution.
 */
object MapStyleLoader {

    private const val STYLE_ASSET = "map/style.json"
    private const val TILES_PLACEHOLDER = "{{tilesPath}}"

    /**
     * Jetons de couleur injectés dans le style.
     *
     * Le nom de la clé est celui de la ressource : un jeton oublié saute aux
     * yeux, dans le style comme ici.
     */
    private val COLOUR_TOKENS: Map<String, Int> = mapOf(
        "map_land" to R.color.map_land,
        "map_water" to R.color.map_water,
        "map_greenery" to R.color.map_greenery,
        "map_building" to R.color.map_building,
        "map_building_edge" to R.color.map_building_edge,
        "map_road_major" to R.color.map_road_major,
        "map_road_major_edge" to R.color.map_road_major_edge,
        "map_road_minor" to R.color.map_road_minor,
        "map_rail" to R.color.map_rail,
        "map_boundary" to R.color.map_boundary,
        "map_label" to R.color.map_label,
        "map_label_strong" to R.color.map_label_strong,
        "map_label_halo" to R.color.map_label_halo,
        "map_marker_minor" to R.color.map_marker_minor,
    )

    /**
     * Rend le style prêt à être passé à MapLibre.
     *
     * @param context sert à lire l'asset et à résoudre les couleurs du thème
     *   courant ; il doit donc être celui d'une vue, pas celui de
     *   l'application, sinon le thème sombre serait ignoré.
     * @param tilesFile le fichier MBTiles installé.
     * @return le style complet, en JSON.
     */
    fun load(context: Context, tilesFile: File): String {
        var style = context.assets.open(STYLE_ASSET)
            .bufferedReader()
            .use { it.readText() }

        for ((token, colourResource) in COLOUR_TOKENS) {
            style = style.replace("{{$token}}", hexOf(context, colourResource))
        }
        // MapLibre lit le MBTiles directement sur le disque : aucune requête
        // de tuile ne part sur le réseau (SPEC §4.2).
        return style.replace(TILES_PLACEHOLDER, tilesFile.absolutePath)
    }

    /**
     * Convertit une couleur de ressource en notation hexadécimale.
     *
     * Le format `#rrggbb` plutôt que `#aarrggbb` : la spécification des styles
     * suit la notation CSS, où l'alpha se place en dernier. Toutes les
     * couleurs du fond de carte sont opaques, l'alpha n'a donc pas à voyager.
     */
    private fun hexOf(context: Context, @ColorRes colourResource: Int): String {
        val colour = ContextCompat.getColor(context, colourResource)
        return String.format("#%06X", colour and 0xFFFFFF)
    }
}

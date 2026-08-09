package io.github.mgdx.rouelibre.ui

import android.content.Context
import io.github.mgdx.rouelibre.R
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * Met une distance en mots, dans la langue de l'interface.
 *
 * Le formatage passe par les API de localisation, jamais par une construction
 * à la main (SPEC §9) : la virgule décimale française et le point anglais ne
 * s'écrivent pas de la même façon.
 *
 * Les valeurs sont arrondies à ce que l'affichage peut honnêtement promettre :
 * la position d'une adresse est connue à quelques mètres près, celle de
 * l'utilisateur à bien moins. Écrire « 437 m » laisserait croire à une
 * précision qui n'existe pas.
 *
 * @param metres la distance à écrire.
 * @return une distance prête à afficher, par exemple « 250 m » ou « 1,4 km ».
 */
fun Context.formatDistance(metres: Double): String {
    if (metres < METRES_PER_KILOMETRE) {
        val rounded = (metres / METRE_ROUNDING).roundToInt() * METRE_ROUNDING
        return getString(R.string.distance_metres, rounded)
    }
    val format = NumberFormat.getNumberInstance(textLocale()).apply {
        maximumFractionDigits = 1
        minimumFractionDigits = 1
    }
    return getString(
        R.string.distance_kilometres,
        format.format(metres / METRES_PER_KILOMETRE),
    )
}

private const val METRES_PER_KILOMETRE = 1_000.0

/** En dessous du kilomètre, la distance s'arrondit à la dizaine de mètres. */
private const val METRE_ROUNDING = 10
